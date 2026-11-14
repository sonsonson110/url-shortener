package com.urlshortener.shortener;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Link} entities.
 */
public interface LinkRepository extends JpaRepository<Link, Long> {

    /**
     * Looks up a link by its short code.
     *
     * @param shortCode the base62-encoded short identifier
     * @return the matching link, or empty if not found
     */
    Optional<Link> findByShortCode(String shortCode);
}
