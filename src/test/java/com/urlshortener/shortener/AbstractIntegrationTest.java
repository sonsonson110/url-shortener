package com.urlshortener.shortener;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for full-stack tests: real Spring context, real Flyway migrations, real Postgres
 * and real Redis (no H2, no mocks). One container is shared by every subclass in the JVM (singleton pattern) so
 * the suite does not pay container start-up per class.
 *
 * <p>{@code ddl-auto=none}: the app context scans the test-only {@code jpademo} entities, whose
 * tables do not exist in the Flyway schema, so Hibernate's {@code validate} would fail on them.
 * Flyway still builds the real schema, and these tests read and write every {@code links} column.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        // Lets tests count how many queries reached Postgres (see RedirectCacheTest).
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
