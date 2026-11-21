package com.urlshortener.shortener;

/** Thrown when no link exists for a short code. Maps to HTTP 404. */
public class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException(String shortCode) {
        super("No link for code: " + shortCode);
    }
}
