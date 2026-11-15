package com.urlshortener.jpademo.repository;

import com.urlshortener.jpademo.model.Author;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Demo repository used only for JPA pitfall demonstrations (Phase 0).
 *
 * <p>It intentionally exposes two methods that produce very different SQL:
 * <ul>
 *   <li>{@code findAll()} — inherited from JpaRepository. Loads authors lazily,
 *       causing N+1 queries when you access books in a loop.</li>
 *   <li>{@code findAllWithBooks()} — uses JOIN FETCH. Loads authors AND their
 *       books in a single SQL query.</li>
 * </ul>
 */
public interface AuthorRepository extends JpaRepository<Author, Long> {

    /**
     * Loads all authors together with their books using a single SQL JOIN.
     *
     * <p>The JPQL {@code JOIN FETCH} keyword tells Hibernate: "Do not use lazy
     * loading for this relationship — include it in the same query via a JOIN."
     *
     * <p>Generated SQL (approximately):
     * <pre>
     *   SELECT a.*, b.*
     *   FROM demo_authors a
     *   JOIN demo_books b ON b.author_id = a.id
     * </pre>
     */
    @Query("SELECT a FROM Author a JOIN FETCH a.books")
    List<Author> findAllWithBooks();
}
