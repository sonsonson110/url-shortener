package com.urlshortener.shortener;

import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cache-aside layer in front of the redirect lookup, with per-key single-flight reloads
 * (ADR 0004).
 *
 * <p>INVARIANT: for one key, at most one request reloads from Postgres at a time while the lock
 * is held; a request that loses the lock never waits longer than {@code lockWait} (it then queries
 * Postgres itself), so a hot key cannot hold all request threads.
 *
 * <p>Only live links are cached, for at most {@code ttl} and never past the link's own expiry.
 * Missing and expired links are not cached (negative caching is a separate, undecided choice).
 *
 * <p>Redis failures never fail a redirect: any Redis error is counted and the request goes to
 * Postgres (fail open). Provisional until the Redis-down ADR is written.
 */
@Component
class RedirectCache {

    private static final Logger log = LoggerFactory.getLogger(RedirectCache.class);
    private static final String KEY_PREFIX = "link:";
    private static final String LOCK_PREFIX = "lock:link:";

    /** Deletes the lock only if we still own it, so a slow holder cannot delete a newer holder's lock. */
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final CacheProperties props;
    private final MeterRegistry meters;

    RedirectCache(StringRedisTemplate redis, CacheProperties props, MeterRegistry meters) {
        this.redis = redis;
        this.props = props;
        this.meters = meters;
    }

    /**
     * Returns the link for {@code code}, from Redis if present, otherwise from {@code loader}.
     *
     * @param loader reads Postgres; returns {@code null} if the code does not exist
     * @return the link (possibly expired, the caller decides), or {@code null} if it does not exist
     */
    CachedLink getOrLoad(String code, Supplier<CachedLink> loader) {
        String key = KEY_PREFIX + code;
        String lockKey = LOCK_PREFIX + code;

        String raw;
        try {
            raw = redis.opsForValue().get(key);
        } catch (RuntimeException e) {
            return failOpen(e, loader);
        }
        if (raw != null) {
            count("hit");
            return decode(raw);
        }
        count("miss");

        String token = UUID.randomUUID().toString();
        Boolean locked;
        try {
            locked = redis.opsForValue().setIfAbsent(lockKey, token, props.lockTtl());
        } catch (RuntimeException e) {
            return failOpen(e, loader);
        }

        if (Boolean.TRUE.equals(locked)) {
            return loadAsWinner(key, lockKey, token, loader);
        }
        return waitForWinner(key, lockKey, loader);
    }

    private CachedLink loadAsWinner(String key, String lockKey, String token, Supplier<CachedLink> loader) {
        try {
            // The previous holder may have filled the cache between our GET and our lock.
            String raw = readQuietly(key);
            if (raw != null) {
                countLock("recheck_hit");
                return decode(raw);
            }
            countLock("won");
            CachedLink loaded = loader.get();
            store(key, loaded);
            return loaded;
        } finally {
            release(lockKey, token);
        }
    }

    /**
     * Polls the cache until the winner fills it, the lock disappears without a value (nothing to
     * cache, e.g. unknown code), or {@code lockWait} runs out. Then queries Postgres directly.
     */
    private CachedLink waitForWinner(String key, String lockKey, Supplier<CachedLink> loader) {
        long deadline = System.nanoTime() + props.lockWait().toNanos();
        long pollMillis = Math.max(1, props.lockPoll().toMillis());
        String outcome = "wait_timeout";
        try {
            while (System.nanoTime() < deadline) {
                Thread.sleep(pollMillis);
                String raw = redis.opsForValue().get(key);
                if (raw != null) {
                    countLock("waited_hit");
                    return decode(raw);
                }
                if (!Boolean.TRUE.equals(redis.hasKey(lockKey))) {
                    outcome = "lock_gone_empty";
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome = "interrupted";
        } catch (RuntimeException e) {
            logError(e);
            outcome = "error";
        }
        countLock(outcome);
        CachedLink loaded = loader.get();
        store(key, loaded);
        return loaded;
    }

    private String readQuietly(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (RuntimeException e) {
            logError(e);
            return null;
        }
    }

    private void store(String key, CachedLink link) {
        if (link == null) {
            return;
        }
        Duration ttl = props.ttl();
        if (link.expiresAt() != null) {
            Duration untilExpiry = Duration.between(OffsetDateTime.now(ZoneOffset.UTC), link.expiresAt());
            if (untilExpiry.compareTo(ttl) < 0) {
                ttl = untilExpiry;
            }
        }
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redis.opsForValue().set(key, encode(link), ttl);
        } catch (RuntimeException e) {
            logError(e);
        }
    }

    private void release(String lockKey, String token) {
        try {
            redis.execute(RELEASE_LOCK, List.of(lockKey), token);
        } catch (RuntimeException e) {
            logError(e); // the lock expires on its own after lockTtl
        }
    }

    private CachedLink failOpen(RuntimeException e, Supplier<CachedLink> loader) {
        logError(e);
        return loader.get();
    }

    private void logError(RuntimeException e) {
        count("error");
        log.warn("Redis unavailable, falling back to Postgres: {}", e.toString());
    }

    private void count(String result) {
        meters.counter("links.cache", "result", result).increment();
    }

    private void countLock(String outcome) {
        meters.counter("links.cache.lock", "outcome", outcome).increment();
    }

    // Format: "<expiresAtEpochMillis or empty>|<longUrl>". Split on the first '|' only; URLs may contain it.
    static String encode(CachedLink link) {
        String expiry = link.expiresAt() == null ? "" : Long.toString(link.expiresAt().toInstant().toEpochMilli());
        return expiry + "|" + link.longUrl();
    }

    static CachedLink decode(String raw) {
        int sep = raw.indexOf('|');
        String expiry = raw.substring(0, sep);
        String url = raw.substring(sep + 1);
        OffsetDateTime expiresAt = expiry.isEmpty()
                ? null
                : OffsetDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(expiry)), ZoneOffset.UTC);
        return new CachedLink(url, expiresAt);
    }
}
