package com.urlshortener.shortener;

import java.time.OffsetDateTime;

/**
 * What the redirect path needs from a link: the target and when it stops being valid.
 *
 * @param longUrl   redirect target
 * @param expiresAt expiry time, or {@code null} if the link never expires
 */
record CachedLink(String longUrl, OffsetDateTime expiresAt) {

    boolean isExpired(OffsetDateTime now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
