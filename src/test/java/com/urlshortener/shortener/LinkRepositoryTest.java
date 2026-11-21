package com.urlshortener.shortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class LinkRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private LinkRepository linkRepository;

    @Test
    void whenSaveLink_thenFindByShortCode() {
        // Arrange
        Link link = new Link(1L, "abc123", "https://www.example.com/some/very/long/path", OffsetDateTime.now(), null);

        // Act
        linkRepository.save(link);
        Optional<Link> found = linkRepository.findByShortCode("abc123");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getLongUrl()).isEqualTo("https://www.example.com/some/very/long/path");
        assertThat(found.get().getHitCount()).isZero();
    }

    @Test
    void nextIdReturnsStrictlyIncreasingValues() {
        long first = linkRepository.nextId();
        long second = linkRepository.nextId();

        assertThat(second).isGreaterThan(first);
    }
}
