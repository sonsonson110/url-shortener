package com.urlshortener.shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.MeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * INVARIANT: {@code links.created} counts only links that were actually committed. If the insert
 * fails, the counter must not move.
 *
 * <p>The failure is forced at commit time on purpose: ids are assigned (no IDENTITY), so
 * {@code save()} only queues the INSERT and Postgres reports the unique violation when the
 * surrounding transaction flushes on commit, after {@code create()} has run its last line.
 */
class LinkServiceCounterTest extends AbstractIntegrationTest {

    @Autowired LinkService linkService;
    @Autowired LinkRepository linkRepository;
    @Autowired MeterRegistry meterRegistry;


    @Test
    void failedInsertDoesNotIncrementCreatedCounter() {
        // The next create() will receive nextId + 1 (we burn nextId here). Pre-insert a row that
        // already owns the code of that future id, under a different, far-away id.
        long burned = linkRepository.nextId();
        long futureId = burned + 1;
        String clashingCode = Base62.encode(futureId);
        long unreachableIdOffset = 900_000_000_000L;
        long squatterId = unreachableIdOffset + futureId;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        linkRepository.save(new Link(squatterId, clashingCode, "https://example.com/squatter", now, null));

        try {
            double before = meterRegistry.counter("links.created").count();

            assertThatThrownBy(() -> linkService.create(new CreateLinkRequest("https://example.com/new", null)))
                    .isInstanceOf(DataAccessException.class);

            assertThat(meterRegistry.counter("links.created").count())
                    .as("counter must not move when the insert fails")
                    .isEqualTo(before);
        } finally {
            linkRepository.deleteById(squatterId);
        }
    }

    @Test
    void successfulCreateIncrementsCreatedCounterOnce() {
        double before = meterRegistry.counter("links.created").count();

        linkService.create(new CreateLinkRequest("https://example.com/ok", null));

        assertThat(meterRegistry.counter("links.created").count()).isEqualTo(before + 1);
    }
}
