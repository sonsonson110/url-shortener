package com.urlshortener.shortener;

/** Thrown when a link exists but has expired. Maps to HTTP 410. */
public class LinkExpiredException extends RuntimeException {

    public LinkExpiredException(String shortCode) {
        super("Link expired: " + shortCode);
    }
}
