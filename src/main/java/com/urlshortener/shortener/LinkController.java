package com.urlshortener.shortener;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for URL shortener endpoints.
 *
 * <p>All business logic is delegated to {@link LinkService}.
 * Endpoints will be added in Phase 1.
 */
@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    // TODO Phase 1: add POST /api/v1/links and GET /{shortCode} endpoints
}
