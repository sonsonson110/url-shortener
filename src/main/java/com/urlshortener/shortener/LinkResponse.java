package com.urlshortener.shortener;

import java.time.OffsetDateTime;

/** Response of {@code POST /api/v1/links}. */
public record LinkResponse(String shortCode, String shortUrl, String longUrl, OffsetDateTime expiresAt) {
}
