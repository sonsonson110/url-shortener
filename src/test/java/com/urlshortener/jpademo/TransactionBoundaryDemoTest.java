package com.urlshortener.jpademo;

import com.urlshortener.shortener.Link;
import com.urlshortener.shortener.LinkRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA Pitfall Demo #3 — Transaction Boundaries & Connection Starvation
 *
 * <p>Demonstrates how performing slow external calls (HTTP, 3rd party APIs, Redis)
 * inside a @Transactional method holds onto database connections from HikariCP,
 * quickly starving the pool under concurrent requests.
 */
@SpringBootTest(properties = {
        // Restrict HikariCP pool to 2 connections and a short 1000ms timeout
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.connection-timeout=1000",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers
@Import(TransactionBoundaryDemoTest.TestServiceConfig.class)
class TransactionBoundaryDemoTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    DemoLinkService demoLinkService;

    @Autowired
    DataSource dataSource;

    @Test
    @DisplayName("BAD — Slow external call inside @Transactional starves the 2-connection pool")
    void slowCallInsideTransactionCausesStarvation() throws Exception {
        int concurrentRequests = 6;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        AtomicInteger failures = new AtomicInteger(0);
        AtomicInteger successes = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    demoLinkService.createWithSlowCallInsideTx("bad-" + index, "https://example.com/" + index);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    // Pool timeout expected: HikariPool - Connection is not available, request timed out
                    failures.incrementAndGet();
                }
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("\n════════════════════════════════════════════════════");
        System.out.println("  BAD: Slow call INSIDE @Transactional");
        System.out.println("  Concurrent requests : " + concurrentRequests);
        System.out.println("  Pool size           : 2");
        System.out.println("  Successes           : " + successes.get());
        System.out.println("  Failures (timed out): " + failures.get());
        System.out.println("════════════════════════════════════════════════════\n");

        // With pool size 2, timeout 1000ms, and 800ms sleep, trailing requests will time out waiting for connections
        assertThat(failures.get())
                .as("At least some requests should time out due to HikariCP connection starvation")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("GOOD — Slow external call OUTSIDE @Transactional frees connections immediately")
    void slowCallOutsideTransactionSucceeds() throws Exception {
        int concurrentRequests = 6;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        AtomicInteger failures = new AtomicInteger(0);
        AtomicInteger successes = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    demoLinkService.createWithSlowCallOutsideTx("good-" + index, "https://example.com/" + index);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("\n════════════════════════════════════════════════════");
        System.out.println("  GOOD: Slow call OUTSIDE @Transactional");
        System.out.println("  Concurrent requests : " + concurrentRequests);
        System.out.println("  Pool size           : 2");
        System.out.println("  Successes           : " + successes.get());
        System.out.println("  Failures            : " + failures.get());
        System.out.println("════════════════════════════════════════════════════\n");

        // All succeed because the DB transaction only lasts for ~2ms (the actual INSERT)
        assertThat(successes.get()).isEqualTo(concurrentRequests);
        assertThat(failures.get()).isZero();
    }

    // ─── Service & Helper Configuration ──────────────────────────────────────

    @TestConfiguration
    static class TestServiceConfig {
        @Bean
        public DemoLinkService demoLinkService(LinkRepository linkRepository) {
            return new DemoLinkService(linkRepository);
        }
    }

    public static class DemoLinkService {
        // Link ids are now assigned by the app; this demo schema (create-drop) has no sequence.
        private static final java.util.concurrent.atomic.AtomicLong NEXT_ID =
                new java.util.concurrent.atomic.AtomicLong();

        private final LinkRepository linkRepository;

        public DemoLinkService(LinkRepository linkRepository) {
            this.linkRepository = linkRepository;
        }

        /**
         * BAD: @Transactional wraps the entire method, including the 800ms external network simulation.
         * The Hikari connection is held hostage for the full 800ms!
         */
        @Transactional
        public void createWithSlowCallInsideTx(String code, String url) {
            // Simulated slow external HTTP call (e.g., SafeBrowsing API or remote check)
            simulateExternalNetworkCall(800);

            Link link = new Link(NEXT_ID.incrementAndGet(), code, url, OffsetDateTime.now(), null);
            linkRepository.save(link);
        }

        /**
         * GOOD: External network call runs outside of any database transaction.
         * The database transaction only wraps the fast save operation.
         */
        public void createWithSlowCallOutsideTx(String code, String url) {
            // External call happens first, with NO database connection held!
            simulateExternalNetworkCall(800);

            // Fast DB transaction only borrows connection for ~2ms
            saveLinkInShortTransaction(code, url);
        }

        @Transactional
        public void saveLinkInShortTransaction(String code, String url) {
            Link link = new Link(NEXT_ID.incrementAndGet(), code, url, OffsetDateTime.now(), null);
            linkRepository.save(link);
        }

        private void simulateExternalNetworkCall(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }
}
