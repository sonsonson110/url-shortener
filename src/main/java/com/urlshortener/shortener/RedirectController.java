package com.urlshortener.shortener;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Public redirect endpoint ({@code GET /{code}}), kept separate from the management API because
 * it is the read-heavy hot path. Returns 302 (not 301) so every click reaches the server and
 * expiry stays accurate; revisited in the 301-vs-302 ADR.
 */
@RestController
public class RedirectController {

    private final LinkService linkService;

    public RedirectController(LinkService linkService) {
        this.linkService = linkService;
    }

    // Regex only matches valid base62 codes up to the column length: it rejects dotted paths
    // (favicon.ico, index.html) and anything longer than 12 chars. It does not exclude words like
    // "actuator" or "api"; those are safe because they are served by other handler mappings / have
    // more than one path segment.
    @GetMapping("/{code:[0-9A-Za-z]{1,12}}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String longUrl = linkService.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(longUrl)).build();
    }
}
