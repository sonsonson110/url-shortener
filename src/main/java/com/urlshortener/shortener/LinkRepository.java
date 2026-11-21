package com.urlshortener.shortener;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    /**
     * Atomically takes the next value from the {@code links.id} sequence. Postgres guarantees
     * that concurrent callers (on any app instance) never receive the same value.
     */
    @Query(value = "SELECT nextval('links_id_seq')", nativeQuery = true)
    long nextId();
}
