package com.urlshortener.shortener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Service layer for URL shortening operations.
 *
 * <p>Create flow: validate URL, take the next id from the Postgres sequence, encode it as base62,
 * insert once. Postgres owns the counter, so instances cannot generate the same code.
 */
@Service
public class LinkService {

    private final LinkRepository linkRepository;
    private final ShortenerProperties properties;
    private final MeterRegistry meterRegistry;
    private final RedirectCache redirectCache;
    private final Counter createdCounter;

    public LinkService(LinkRepository linkRepository, ShortenerProperties properties, MeterRegistry meterRegistry,
                       RedirectCache redirectCache) {
        this.linkRepository = linkRepository;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.redirectCache = redirectCache;
        this.createdCounter = Counter.builder("links.created")
                .description("Short links created")
                .register(meterRegistry);
    }

    @Transactional
    public LinkResponse create(CreateLinkRequest request) {
        String longUrl = request.longUrl().trim();
        validateUrl(longUrl);

        long id = linkRepository.nextId();
        String shortCode = Base62.encode(id);
        Link link = new Link(id, shortCode, longUrl, OffsetDateTime.now(ZoneOffset.UTC), request.expiresAt());
        linkRepository.save(link);

        // Count only after the INSERT is durable: with assigned ids, save() merely queues the
        // INSERT and constraint violations surface at commit, after this method has returned.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                createdCounter.increment();
            }
        });
        return new LinkResponse(shortCode, shortUrl(shortCode), longUrl, link.getExpiresAt());
    }

    /**
     * Resolves a short code to its long URL, through the Redis cache (ADR 0004).
     *
     * <p>Deliberately not {@code @Transactional}: a transaction would hold a Hikari connection
     * while the request waits on Redis. The repository call opens its own read-only transaction,
     * so a connection is held only for the query itself.
     *
     * @throws LinkNotFoundException if no such code exists
     * @throws LinkExpiredException  if the link has expired
     */
    public String resolve(String shortCode) {
        CachedLink link = redirectCache.getOrLoad(shortCode, () -> loadFromDatabase(shortCode));
        if (link == null) {
            countRedirect("not_found");
            throw new LinkNotFoundException(shortCode);
        }
        if (link.isExpired(OffsetDateTime.now(ZoneOffset.UTC))) {
            countRedirect("expired");
            throw new LinkExpiredException(shortCode);
        }
        countRedirect("hit");
        return link.longUrl();
    }

    private CachedLink loadFromDatabase(String shortCode) {
        return linkRepository.findByShortCode(shortCode)
                .map(l -> new CachedLink(l.getLongUrl(), l.getExpiresAt()))
                .orElse(null);
    }

    private void countRedirect(String outcome) {
        meterRegistry.counter("links.redirects", "outcome", outcome).increment();
    }

    private String shortUrl(String shortCode) {
        String base = properties.baseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + shortCode;
    }

    private void validateUrl(String url) {
        if (url.length() > properties.maxUrlLength()) {
            throw new InvalidUrlException("URL exceeds max length of " + properties.maxUrlLength());
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("Malformed URL");
        }
        String scheme = uri.getScheme();
        boolean httpScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        if (!httpScheme || uri.getHost() == null) {
            throw new InvalidUrlException("URL must be an absolute http or https URL");
        }
    }
}
