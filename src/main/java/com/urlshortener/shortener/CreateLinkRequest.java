package com.urlshortener.shortener;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

/** Body of {@code POST /api/v1/links}. {@code expiresAt} is optional (null = never expires). */
public record CreateLinkRequest(@NotBlank String longUrl, @Future OffsetDateTime expiresAt) {
}
