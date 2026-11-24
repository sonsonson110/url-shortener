# CPU profile of the v0-naive app at 3000 req/s (JFR)

A profiling session, not a benchmark result: JFR adds overhead, so latency numbers from this run are
not comparable to the baseline in `benchmarks/REPORT.md`. Purpose: split the app's CPU between the
database access path and everything else, before building the Redis cache (v2).

## How it was run

- Date: 2026-11-24. Repo HEAD 797b652 (application code identical to the baseline commit a137157).
- Same bench stack and limits as `make bench` (`deploy/docker-compose.bench.yml`: app 2 CPU / 1 GB,
  Postgres 2 CPU / 2 GB, k6 2 CPU / 2 GB), 10M seeded rows, run by hand because `run-bench.sh` cannot
  take a compose override.
- App started with `-XX:StartFlightRecording=filename=/prof/prof.jfr,dumponexit=true,settings=profile`
  (the runtime image is a JRE, so there is no `jcmd`; the recording covers the whole JVM life and was
  dumped when the container stopped).
- Load: 2 minutes warm-up at 3000 redirects/s, then 3 minutes measured at 3000 redirects/s
  (window 08:44:31-08:47:33 UTC). The window was cut out of the recording by timestamp.
- Analysis: `jfr print --events jdk.ExecutionSample --stack-depth 80`, samples in the window grouped by
  the package prefixes in the table. The 58 MB recording is not committed.

## Result

Request threads (`http-nio-8080-exec-*`), 3,052 of 3,063 samples in the window. A sample can be counted
in several rows, so rows do not add to 100%.

| Area (stack contains a frame from) | Share of samples |
|---|---|
| DB access path: Spring Data / ORM / tx, Hibernate, Postgres JDBC driver, Hikari | 40.2% |
| - Hibernate / JPA (`org.hibernate`, `jakarta.persistence`) | 25.9% |
| - Hikari pool (`com.zaxxer`) | 5.7% |
| - Postgres JDBC driver (`org.postgresql`) | 4.5% |
| inside a `com.urlshortener` `*Service` frame (where the lookup runs) | 40.4% |
| Micrometer / actuator (metrics instrumentation) | 11.1% |
| Jackson / JSON | 1.5% |
| Tomcat / Coyote and Spring MVC | 78.2% / 73.4% (every request runs inside them; not informative) |

- No single hot spot: the top leaf methods are scattered JDK and Spring internals, each under 5%
  (`HashMap.getNode` 4.8%, `ConcurrentLinkedDeque.removeFirstOccurrence` 3.7%, `ObjectUtils.nullSafeEquals`
  3.0%, `String.hashCode` 2.8%).
- GC in the window: 334 collections, 3.1 s of pauses over 182 s (1.7% of wall time).
- k6 during this run: 536,680 requests, 0 failed, 8,723 dropped iterations (the baseline runs dropped
  75-654), which shows the profiled run was heavier than a normal run.

## Caveats

- Execution samples cover Java code on the CPU only: no time in native code (socket I/O), kernel time or GC
  worker threads. Shares are shares of sampled Java time, not of the container's CPU.
- JFR overhead was not measured separately; shares may shift slightly without it.
- JFR's own `jdk.CPULoad` figure could not be reconciled with the `docker stats` readings of the baseline
  runs, so it is not used.
- The 11.1% metrics share is most likely the `http.server.requests` percentile histograms, but that
  attribution was not tested.
