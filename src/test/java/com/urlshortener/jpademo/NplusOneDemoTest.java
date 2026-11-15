package com.urlshortener.jpademo;

import com.urlshortener.jpademo.model.Author;
import com.urlshortener.jpademo.model.Book;
import com.urlshortener.jpademo.repository.AuthorRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA Pitfall Demo #1 — N+1 Query Problem
 *
 * <p>This test class demonstrates how lazy loading silently fires N+1 SQL
 * queries and proves that JOIN FETCH reduces it to exactly 1 query.
 *
 * <p>Setup:
 * <ul>
 *   <li>Flyway is disabled — Hibernate creates the demo tables via create-drop.</li>
 *   <li>Hibernate statistics are enabled — we can count exactly how many SQL
 *       statements were prepared against the database.</li>
 *   <li>show-sql is on — you can scroll up in the test output to see every
 *       SELECT statement that Hibernate fired.</li>
 * </ul>
 *
 * <p>Data: 3 authors, each with 3 books (9 books total).
 */
@DataJpaTest(
        showSql = true,
        properties = {
                // Don't run Flyway migrations — Hibernate will create demo tables itself
                "spring.flyway.enabled=false",
                // create-drop: create tables when Spring starts, drop them when it shuts down
                "spring.jpa.hibernate.ddl-auto=create-drop",
                // Enable Hibernate's built-in SQL counter so we can assertThat(queryCount)
                "spring.jpa.properties.hibernate.generate_statistics=true"
        }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class NplusOneDemoTest {

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

    @Autowired
    EntityManagerFactory emf;

    // ─── Seed & Counter Reset ────────────────────────────────────────────────

    @BeforeEach
    void seedData() {
        for (int a = 1; a <= 3; a++) {
            Author author = new Author("Author " + a);
            for (int b = 1; b <= 3; b++) {
                author.addBook(new Book("Book " + b + " of Author " + a));
            }
            authorRepository.save(author);
        }

        // Write INSERTs to the DB (still inside the transaction, but now on disk)
        authorRepository.flush();

        /*
         * Clear the first-level cache (the EntityManager's in-memory map).
         *
         * Without this, Hibernate would return the already-in-memory Author
         * objects when findAll() is called, and no SQL would fire at all —
         * which would hide the N+1 problem entirely.
         *
         * Node.js equivalent: think of this as clearing a module-level Map
         * that was used as a request-scoped cache.
         */
        em.clear();

        // Reset the SQL counter AFTER seeding, so only test-method queries count
        hibernateStats().clear();
    }

    // ─── Demo Tests ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("BAD  — findAll() + lazy access triggers N+1 queries (1 + 3 = 4 total)")
    void nplusOneProblem() {
        /*
         * STEP 1: Load all authors — this fires 1 SQL query:
         *   SELECT id, name FROM demo_authors
         */
        List<Author> authors = authorRepository.findAll();

        /*
         * STEP 2: Access books for each author inside the loop.
         *
         * author.getBooks() returns a lazy proxy — a placeholder object that
         * looks like a List but hasn't touched the database yet.
         *
         * .size() is the moment it goes to the database:
         *   SELECT id, title, author_id FROM demo_books WHERE author_id = 1
         *   SELECT id, title, author_id FROM demo_books WHERE author_id = 2
         *   SELECT id, title, author_id FROM demo_books WHERE author_id = 3
         *
         * That is 3 extra queries — one per author row returned from STEP 1.
         */
        int totalBooks = 0;
        for (Author author : authors) {
            totalBooks += author.getBooks().size(); // <-- SQL fires here, every iteration
        }

        long sqlCount = hibernateStats().getPrepareStatementCount();

        System.out.printf("""
                
                ════════════════════════════════════════════════════
                  BAD: N+1 Query Problem
                ════════════════════════════════════════════════════
                  Authors loaded : %d
                  Books counted  : %d
                  SQL queries    : %d  ← expected %d (1 + N per author)
                  
                  Scroll up in this test output and count the SELECT
                  statements Hibernate printed. You will see exactly
                  %d separate queries against the database.
                ════════════════════════════════════════════════════
                %n""",
                authors.size(), totalBooks, sqlCount, 1 + authors.size(), 1 + authors.size());

        // 1 (findAll) + 3 (one per author for lazy books) = 4
        assertThat(sqlCount)
                .as("N+1: expected 1 (findAll) + 3 (lazy load per author) = 4 queries")
                .isEqualTo(4);
        assertThat(totalBooks).isEqualTo(9);
    }

    @Test
    @DisplayName("GOOD — JOIN FETCH loads authors + books in exactly 1 query")
    void joinFetchFix() {
        /*
         * findAllWithBooks() runs this single SQL query:
         *   SELECT a.id, a.name, b.id, b.title, b.author_id
         *   FROM demo_authors a
         *   JOIN demo_books b ON b.author_id = a.id
         *
         * Both authors and their books land in memory from ONE round trip.
         */
        List<Author> authors = authorRepository.findAllWithBooks();

        /*
         * Accessing books here fires ZERO additional SQL.
         * The books are already loaded — Hibernate just reads them from memory.
         */
        int totalBooks = 0;
        for (Author author : authors) {
            totalBooks += author.getBooks().size(); // <-- no SQL, reads from memory
        }

        long sqlCount = hibernateStats().getPrepareStatementCount();

        System.out.printf("""
                
                ════════════════════════════════════════════════════
                  GOOD: JOIN FETCH Fix
                ════════════════════════════════════════════════════
                  Authors loaded : %d
                  Books counted  : %d
                  SQL queries    : %d  ← expected 1
                  
                  One JOIN. Same result. Zero extra round trips.
                  As N (authors) grows, this stays at 1 query.
                  The N+1 version grows linearly with N.
                ════════════════════════════════════════════════════
                %n""",
                authors.size(), totalBooks, sqlCount);

        assertThat(sqlCount)
                .as("JOIN FETCH: expected exactly 1 query")
                .isEqualTo(1);
        assertThat(totalBooks).isEqualTo(9); // same result as N+1, but way fewer queries
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Statistics hibernateStats() {
        return emf.unwrap(SessionFactory.class).getStatistics();
    }
}
