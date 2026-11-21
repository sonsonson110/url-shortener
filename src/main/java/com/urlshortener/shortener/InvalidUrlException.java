package com.urlshortener.shortener;

/** Thrown when a submitted URL is malformed, not http/https, or too long. Maps to HTTP 400. */
public class InvalidUrlException extends RuntimeException {

    public InvalidUrlException(String message) {
        super(message);
    }
}
