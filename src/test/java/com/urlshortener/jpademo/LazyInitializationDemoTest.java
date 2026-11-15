package com.urlshortener.jpademo;

import com.urlshortener.jpademo.model.Author;
import com.urlshortener.jpademo.model.Book;
import com.urlshortener.jpademo.repository.AuthorRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JPA Pitfall Demo #2 — LazyInitializationException
 *
 * <p>Demonstrates what happens when a lazy collection is accessed on a
 * "detached" entity after the persistence context (session) has closed.
 */
@DataJpaTest(
        showSql = true,
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class LazyInitializationDemoTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    AuthorRepository authorRepository;

    @Autowired
    EntityManager em;

    private Long savedAuthorId;

    @BeforeEach
    void seedData() {
        Author author = new Author("Martin Fowler");
        author.addBook(new Book("Patterns of Enterprise Application Architecture"));
        author.addBook(new Book("Refactoring"));

        Author saved = authorRepository.save(author);
        authorRepository.flush();
        savedAuthorId = saved.getId();

        // Clear session so each test starts fresh
        em.clear();
    }

    @Test
    @DisplayName("BAD — Accessing lazy collection on detached entity throws LazyInitializationException")
    void lazyInitializationThrowsWhenSessionClosed() {
        // 1. Fetch Author using standard findById (lazy books)
        Author author = authorRepository.findById(savedAuthorId).orElseThrow();

        /*
         * 2. Detach the entity from the EntityManager / Session.
         *
         * In real production code, this detachment happens automatically
         * when a @Transactional service method returns to the controller.
         */
        em.detach(author);

        /*
         * 3. Now try to access the lazy books collection.
         *
         * The lazy proxy cannot fetch data because its session is disconnected.
         * It throws LazyInitializationException!
         */
        assertThatThrownBy(() -> {
            int bookCount = author.getBooks().size();
            System.out.println("Books counted: " + bookCount);
        })
        .isInstanceOf(LazyInitializationException.class)
        .hasMessageContaining("could not initialize proxy - no Session");

        System.out.println("\n════════════════════════════════════════════════════");
        System.out.println("  VERIFIED: LazyInitializationException caught!");
        System.out.println("  Author was detached; Hibernate refused to query DB");
        System.out.println("  without an active session.");
        System.out.println("════════════════════════════════════════════════════\n");
    }

    @Test
    @DisplayName("GOOD — JOIN FETCH loads collection eagerly so detached entity has data in-memory")
    void joinFetchPreventsLazyInitializationException() {
        /*
         * 1. Fetch Author using JOIN FETCH query.
         * Both Author AND books are loaded in that single query.
         */
        Author author = authorRepository.findAllWithBooks()
                .stream()
                .filter(a -> a.getId().equals(savedAuthorId))
                .findFirst()
                .orElseThrow();

        /*
         * 2. Even when detached (session closed / method returned)...
         */
        em.detach(author);

        /*
         * 3. Accessing books succeeds with ZERO errors because the data
         * is already in Java memory!
         */
        int bookCount = author.getBooks().size();

        assertThat(bookCount).isEqualTo(2);

        System.out.println("\n════════════════════════════════════════════════════");
        System.out.println("  VERIFIED: JOIN FETCH prevents LazyInitializationException!");
        System.out.println("  Books were loaded before session closed.");
        System.out.println("  Book count: " + bookCount);
        System.out.println("════════════════════════════════════════════════════\n");
    }
}
