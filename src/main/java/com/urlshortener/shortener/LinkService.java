package com.urlshortener.shortener;

import org.springframework.stereotype.Service;

/**
 * Service layer for URL shortening operations.
 *
 * <p>Constructor-injected dependencies follow Spring best practices for testability.
 */
@Service
public class LinkService {

    private final LinkRepository linkRepository;

    public LinkService(LinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    // TODO Phase 1: implement create and redirect logic
}
