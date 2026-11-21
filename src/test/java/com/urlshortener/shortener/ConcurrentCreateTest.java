package com.urlshortener.shortener;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * INVARIANT: under concurrent creation, every created link receives a distinct short code, and
 * each code resolves to the URL it was created for (no overwritten or crossed rows).
 *
 * <p>This is the single-JVM version. It exercises the real guarantee, because collision-freedom
 * comes from the Postgres sequence, not from Java locks. The 3-instance version is week 10.
 */
class ConcurrentCreateTest extends AbstractIntegrationTest {

    private static final int THREADS = 32;
    private static final int CREATES_PER_THREAD = 50;

    @Autowired LinkService linkService;

    @Test
    void concurrentCreatesNeverProduceDuplicateCodes() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<List<LinkResponse>>> futures = new ArrayList<>();

        for (int t = 0; t < THREADS; t++) {
            final int thread = t;
            Callable<List<LinkResponse>> task = () -> {
                startGate.await();
                List<LinkResponse> created = new ArrayList<>();
                for (int i = 0; i < CREATES_PER_THREAD; i++) {
                    created.add(linkService.create(
                            new CreateLinkRequest("https://example.com/t" + thread + "/i" + i, null)));
                }
                return created;
            };
            futures.add(pool.submit(task));
        }

        startGate.countDown();
        List<LinkResponse> all = new ArrayList<>();
        for (Future<List<LinkResponse>> f : futures) {
            all.addAll(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        int expected = THREADS * CREATES_PER_THREAD;
        assertThat(all).hasSize(expected);
        Set<String> codes = all.stream().map(LinkResponse::shortCode).collect(java.util.stream.Collectors.toSet());
        assertThat(codes).as("all short codes distinct").hasSize(expected);

        for (LinkResponse r : all) {
            assertThat(linkService.resolve(r.shortCode())).isEqualTo(r.longUrl());
        }
    }
}
