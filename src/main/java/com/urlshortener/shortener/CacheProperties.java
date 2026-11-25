package com.urlshortener.shortener;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings under {@code app.cache.*} for the redirect cache (see ADR 0004).
 *
 * @param ttl      how long a link stays cached (capped at the link's own expiry)
 * @param lockTtl  lifetime of the per-key reload lock; only matters if the lock holder dies
 * @param lockWait longest a request that lost the lock waits for the winner before querying Postgres itself
 * @param lockPoll how often a waiting request re-reads the cache
 */
@ConfigurationProperties("app.cache")
public record CacheProperties(Duration ttl, Duration lockTtl, Duration lockWait, Duration lockPoll) {
}
