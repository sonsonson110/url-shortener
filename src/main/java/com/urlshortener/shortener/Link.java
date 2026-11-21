package com.urlshortener.shortener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;

/**
 * JPA entity representing a shortened URL mapping stored in the {@code links} table.
 *
 * <p>The id is assigned by the application (taken from the Postgres sequence via
 * {@link LinkRepository#nextId()}) because the short code is derived from it. Because the id is
 * non-null before the first save, we implement {@link Persistable}: otherwise Spring Data would
 * treat the entity as existing and call {@code merge()}, which adds a pointless SELECT.
 */
@Entity
@Table(name = "links")
public class Link implements Persistable<Long> {

    @Id
    private Long id;

    @Column(name = "short_code", nullable = false, length = 12, unique = true)
    private String shortCode;

    @Column(name = "long_url", nullable = false, columnDefinition = "TEXT")
    private String longUrl;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "hit_count", nullable = false)
    private long hitCount = 0L;

    /** Not a column: true until the entity has been persisted or loaded. */
    @Transient
    private boolean isNew = true;

    // Required by JPA
    protected Link() {}

    public Link(Long id, String shortCode, String longUrl, OffsetDateTime createdAt, OffsetDateTime expiresAt) {
        this.id = id;
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.hitCount = 0L;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public void setLongUrl(String longUrl) {
        this.longUrl = longUrl;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public long getHitCount() {
        return hitCount;
    }

    public void setHitCount(long hitCount) {
        this.hitCount = hitCount;
    }
}
