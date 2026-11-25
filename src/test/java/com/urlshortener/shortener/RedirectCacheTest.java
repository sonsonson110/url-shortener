package com.urlshortener.shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.MeterRegistry;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * INVARIANTS (ADR 0004): a cached link is served without touching Postgres; a cold hot key is
 * loaded from Postgres once, not once per concurrent request; expired links are never cached.
 */
class RedirectCacheTest extends AbstractIntegrationTest {

    @Autowired LinkService linkService;
    @Autowired LinkRepository linkRepository;
    @Autowired MeterRegistry meterRegistry;
    @Autowired StringRedisTemplate redis;
    @Autowired EntityManagerFactory entityManagerFactory;

    private Statistics stats() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private String createLink(String url, OffsetDateTime expiresAt) {
        return linkService.create(new CreateLinkRequest(url, expiresAt)).shortCode();
    }

    @Test
    void secondResolveIsServedFromRedisWithoutQueryingPostgres() {
        String code = createLink("https://example.com/cached", null);

        assertThat(linkService.resolve(code)).isEqualTo("https://example.com/cached");
        long queriesAfterFirst = stats().getPrepareStatementCount();

        assertThat(linkService.resolve(code)).isEqualTo("https://example.com/cached");

        assertThat(stats().getPrepareStatementCount())
                .as("second resolve must not prepare any SQL statement")
                .isEqualTo(queriesAfterFirst);
        assertThat(redis.getExpire("link:" + code)).isBetween(1L, 60L);
    }

    @Test
    void expiredLinkIsRejectedAndNotCached() {
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        long id = linkRepository.nextId();
        String code = Base62.encode(id);
        linkRepository.save(new Link(id, code, "https://example.com/old", past.minusDays(1), past));

        assertThatThrownBy(() -> linkService.resolve(code)).isInstanceOf(LinkExpiredException.class);

        assertThat(redis.hasKey("link:" + code)).isFalse();
    }

    @Test
    void unknownCodeIsNotFoundAndNotCached() {
        assertThatThrownBy(() -> linkService.resolve("zzzzzzzz")).isInstanceOf(LinkNotFoundException.class);

        assertThat(redis.hasKey("link:zzzzzzzz")).isFalse();
    }

    @Test
    void cachedTtlIsCappedAtLinkExpiry() {
        String code = createLink("https://example.com/soon", OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(20));

        linkService.resolve(code);

        assertThat(redis.getExpire("link:" + code)).isBetween(1L, 20L);
    }

    @Test
    void concurrentColdRequestsForOneKeyLoadPostgresOnce() throws Exception {
        String code = createLink("https://example.com/hot", null);
        redis.delete("link:" + code);
        int threads = 32;
        long before = stats().getQueryExecutionCount();
        double winnersBefore = meterRegistry.counter("links.cache.lock", "outcome", "won").count();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return linkService.resolve(code);
            }));
        }
        go.countDown();
        for (Future<String> f : results) {
            assertThat(f.get()).isEqualTo("https://example.com/hot");
        }
        pool.shutdown();

        assertThat(stats().getQueryExecutionCount() - before)
                .as("Postgres queries for 32 concurrent cold requests of one key")
                .isEqualTo(1);
        assertThat(meterRegistry.counter("links.cache.lock", "outcome", "won").count() - winnersBefore)
                .isEqualTo(1);
    }
}
