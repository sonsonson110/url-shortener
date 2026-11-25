# Question Bank

Questions for daily review, drawn from what the repo actually contains. Used by the Claude Project quiz (see `docs/quiz/README.md`). Scheduling state lives in `docs/learning-log.md`, not here.

**Rules for the quiz master:** never show the grading points before the owner answers. Grade against the grading points and the cited source. If the source has changed since the question was written, trust the source and flag the question as stale.

Entry format:

```text
### <ID> · <type> · <difficulty>
- Source: <file / ADR / commit>
- Q: <question>
- Grading points: <what a correct answer must contain>
```

Types: `recall`, `explain-why`, `find-the-bug`, `two-instances`, `design`, `predict`.
Difficulty: `easy`, `medium`, `hard`.

Last refreshed against commit: `d06df71` (2026-11-25).

The learning-log questions Q1–Q5 (`docs/learning-log.md`) are part of the pool too. They are referred to as `LL-Q1` … `LL-Q5` and have no reference answer here on purpose: grade them against the cited source.

---

## HLD: High-level design and scope

### HLD-01 · recall · easy
- Source: `roadmap.md` §1, `AGENTS.md` §1
- Q: The project is "one coherent system, not four toys." Name the four parts and how each relates to the URL shortener.
- Grading points: the URL shortener is the core (read-heavy, DB + cache, scale out). A distributed rate limiter protects the create/redirect endpoints across instances. Background jobs handle click-analytics aggregation and link-expiry cleanup. Observability (metrics, logs, traces, SLOs) covers everything.

### HLD-02 · design · medium
- Source: `docs/phase1-mvp-architecture.md` §2
- Q: Walk through the create flow end to end: HTTP request → DB → response. Name each class and each SQL statement.
- Grading points: `POST /api/v1/links` → `LinkController` (validation) → `LinkService.create`. Then `SELECT nextval('links_id_seq')`, `Base62.encode(id)`, one `INSERT` into `links`, and a `201` response with `shortCode` and `shortUrl`.

### HLD-03 · design · medium
- Source: `docs/phase1-mvp-architecture.md` §2, `RedirectController.java`
- Q: Walk through the redirect flow and its three outcomes, with the status code for each.
- Grading points: `GET /{code}` → `RedirectController` (path regex `[0-9A-Za-z]{1,12}`) → `LinkService.resolve`. Not found → 404, expired → 410, found → 302 with a `Location` header.

### HLD-04 · explain-why · medium
- Source: `docs/phase1-mvp-architecture.md` §5, `RedirectController.java`
- Q: Why is the redirect in its own controller at `/{code}` instead of under `/api/v1/links`?
- Grading points: short public URLs (`host/21`). It keeps the hot read path apart from the management API, so metrics, caching and rate limiting can target it later.

### HLD-05 · explain-why · medium
- Source: `docs/phase1-mvp-architecture.md` §5, `RedirectController.java`
- Q: Why does the MVP return 302 instead of 301? What would you lose with 301?
- Grading points: browsers cache 301 (permanent), so later clicks skip the server. With 302 every click reaches the server, so expiry is enforced and counts stay accurate. Revisited with real numbers in the weeks 6–7 ADR.

### HLD-06 · explain-why · medium
- Source: `docs/phase1-mvp-architecture.md` §5
- Q: `links` has a `hit_count` column, but redirect doesn't update it. Why?
- Grading points: that would put a DB write on the hottest path, and concurrent increments of the same row would contend. Proper counting is deferred to Phase 3 (click analytics / background jobs).

### HLD-07 · design · hard
- Source: `docs/phase1-mvp-architecture.md` §7, `roadmap.md` §6, `benchmarks/REPORT.md` (v0-naive)
- Q: The system runs at 100:1 reads to writes with no cache. What breaks first, and what does the roadmap plan to do about it?
- Grading points: every redirect is a Postgres lookup (an index scan, about 0.05 ms per call, so the query itself is cheap). Measured in v0-naive (`benchmarks/REPORT.md`): the app's own CPU (2-CPU cap, 93–100%) saturates first, at about 4,450 req/s, while Postgres sits at about 45–48% of its limit. Whether the Hikari pool (10 connections) is also a limit is not established. A cache removes the lookup but a hit still costs the HTTP handling, so the gain is expected to be smaller than the DB share suggests. The plan is Redis cache-aside with TTL and stampede protection (weeks 6–7), proven with k6 before/after benchmarks (weeks 8–9). Strong answers note that hot keys concentrate load.

### HLD-08 · recall · easy
- Source: `docs/phase1-mvp-architecture.md` §1, `docs/BACKLOG.md`
- Q: Name three things the Phase 1 MVP deliberately does NOT do, and where each one is picked up.
- Grading points: any three of: Redis cache and stampede protection (weeks 6–7); the 301-vs-302 ADR (weeks 6–7); benchmarks and `EXPLAIN ANALYZE` (weeks 8–9); multi-instance and counter ranges (week 10); click counting (Phase 3); custom aliases, accounts and UI (backlog).

### HLD-09 · recall · easy
- Source: `roadmap.md` §6
- Q: What is Phase 1's "done when" bar?
- Grading points: the README answers the key questions with real numbers. You can explain the ID strategy choice and the key decisions (ID generation, 301 vs 302, stampede protection) and their failure cases aloud without notes.

---

## ID: ID generation (ADR 0002)

### ID-01 · predict · easy
- Source: `Base62.java`, `docs/phase1-mvp-architecture.md` §4
- Q: What do `Base62.encode(0)`, `encode(10)`, `encode(61)`, `encode(62)` and `encode(125)` return?
- Grading points: `"0"`, `"a"`, `"Z"`, `"10"`, `"21"`. The alphabet order is `0-9`, `a-z`, `A-Z`, and 125 = 2·62 + 1.

### ID-02 · recall · medium
- Source: `docs/adr/0002-id-generation.md`
- Q: ADR 0002 considered three options. Name them with one pro and one con each.
- Grading points:
  - **A, sequence + base62:** simple and collision-free by construction, but guessable plus an extra round trip.
  - **B, counter ranges per instance:** fewer round trips, but needs leases, and a crash wastes a block.
  - **C, random code + retry:** not guessable, but collision probability grows, needs a retry loop, and "zero collisions" is hard to prove.

### ID-03 · two-instances · medium
- Source: `docs/adr/0002-id-generation.md`, `LinkRepository.nextId`
- Q: Two app instances create a link at the same millisecond. Why can't they get the same short code? What role does the `UNIQUE` constraint play?
- Grading points: `nextval` is atomic in Postgres, so each caller gets a distinct number, whichever instance it runs on. Base62 is one-to-one, so distinct ids give distinct codes. The UNIQUE constraint is a safety net, not the mechanism.

### ID-04 · explain-why · medium
- Source: `docs/phase1-mvp-architecture.md` §4, `LinkService.create`
- Q: Why fetch the id with `nextval` BEFORE the insert instead of letting the DB assign it on insert?
- Grading points: `short_code` is `NOT NULL` and derives from the id. Knowing the id first lets you write the row once, with no placeholder and no follow-up UPDATE.

### ID-05 · explain-why · hard
- Source: `Link.java`, `docs/adr/0002-id-generation.md` Consequences
- Q: `Link` implements `Persistable` with an `isNew` flag. What problem does that solve, and what would the SQL log show without it?
- Grading points: the id is non-null before the first save, so Spring Data would treat the entity as existing and call `merge()`. That fires a SELECT before the INSERT. `isNew` is true until `@PostPersist`/`@PostLoad`, which gives a plain INSERT.

### ID-06 · explain-why · medium
- Source: `Base62.java` Javadoc, `Base62Test.distinctNumbersGiveDistinctCodes`
- Q: Base62 must be "one-to-one". What property of the encoder guarantees that, and which test checks it?
- Grading points: there are no leading zeros except for `"0"` itself, so each number has exactly one representation (it's positional notation). `Base62Test.distinctNumbersGiveDistinctCodes` (plus the round-trip tests).

### ID-07 · predict · medium
- Source: `Link.java` (`length = 12`), `Base62Test.longMaxValueFitsInShortCodeColumn`
- Q: `short_code` is `VARCHAR(12)`. Can any `long` id overflow it? How does the repo prove the answer?
- Grading points: no. `Long.MAX_VALUE` in base62 is 11 characters (62^10 < 2^63 − 1 < 62^11), and `longMaxValueFitsInShortCodeColumn` asserts it fits.

### ID-08 · find-the-bug · medium
- Source: `V1__create_links_table.sql`, `Base62.java`
- Q: The V1 migration comments `short_code` as "Base62-encoded short identifier, 6–8 chars". Is that true of what the code produces today?
- Grading points: no. Codes start at 1 char (id 1 → `"1"`) and grow with the sequence: 62 ids per length step at the low end. The comment is stale/aspirational. You fix it in a new migration (`COMMENT ON` in `V2__…`), never by editing V1.

### ID-09 · design · hard
- Source: `docs/adr/0002-id-generation.md` Option B, `roadmap.md` week 10
- Q: Sketch counter ranges (option B): how an instance leases a block, what happens when it crashes, and what you gain.
- Grading points: the instance atomically reserves a block of N ids in the DB (e.g. an `UPDATE … RETURNING` on a counter row, or a sequence with a large increment) and hands them out from memory. A crash wastes the unused rest of the block (gaps, which are harmless). Gain: about one DB round trip per N creates instead of per create. Cost: a lease mechanism and more moving parts.

---

## CODE: Phase 1 code

### CODE-01 · recall · easy
- Source: `LinkService.validateUrl`, `LinkApiIntegrationTest.invalidUrlsReturn400`
- Q: What makes a long URL invalid in `LinkService`? Give two examples from the tests.
- Grading points: longer than `max-url-length` (2048 default), malformed URI, scheme not http/https, or no host. Test examples: `"not a url"`, `"ftp://…"`, `"javascript:alert(1)"`, `"https://"`.

### CODE-02 · explain-why · medium
- Source: `CreateLinkRequest.java`, `LinkApiIntegrationTest.pastExpiryReturns400`
- Q: Where is "expiresAt must be in the future" enforced, and how does it become a 400?
- Grading points: `@Future` on the record field plus `@Valid` in `LinkController`. The failed validation is a standard Spring exception that `ShortenerExceptionHandler` (which extends `ResponseEntityExceptionHandler`) turns into a 400 ProblemDetail.

### CODE-03 · recall · medium
- Source: `ShortenerExceptionHandler.java`
- Q: Which exceptions map to which status codes, and what response format is used?
- Grading points: `InvalidUrlException` → 400, `LinkNotFoundException` → 404, `LinkExpiredException` → 410 GONE. The format is RFC 7807 `ProblemDetail`. Extending `ResponseEntityExceptionHandler` also covers bad JSON and `@Valid` failures.

### CODE-04 · explain-why · medium
- Source: `RedirectController.java`
- Q: Why does the redirect mapping use the regex `{code:[0-9A-Za-z]{1,12}}` instead of plain `/{code}`?
- Grading points: it only matches valid base62 codes up to the column length (`short_code` is `VARCHAR(12)`), so dotted paths like `favicon.ico` / `index.html`, other characters and over-long segments are never treated as codes. Note: the regex does NOT exclude letter-only words such as `actuator` or `api` (they match it); those are not at risk because actuator is served by its own handler mapping and `/actuator/health`, `/api/v1/links` have several path segments. (Read from the code, not tested.)

### CODE-05 · explain-why · easy
- Source: `LinkService.resolve`
- Q: `resolve` is `@Transactional(readOnly = true)`. What does `readOnly` give you?
- Grading points: Hibernate skips dirty checking/flush for the session (cheaper). It documents intent, and drivers/routers can use it (e.g. to send reads to a replica later). Strong answers say it is a hint, not a permission check.

### CODE-06 · predict · medium
- Source: `LinkApiIntegrationTest.sameLongUrlTwiceGivesDifferentCodes`
- Q: Create the same long URL twice. Same code or different? Why was it designed that way?
- Grading points: different codes. Every create takes a new sequence value, and there is no dedup lookup. Dedup would need an index on `long_url` plus a read before each write, and links can differ by expiry. Strong answers mention it as a trade-off, not a bug.

### CODE-07 · recall · easy
- Source: `ShortenerProperties.java`, `ShortenerConfig.java`, `application.yml`
- Q: How does `app.shortener.base-url` get into `LinkService`?
- Grading points: `ShortenerProperties` is a `@ConfigurationProperties("app.shortener")` record, enabled by `@EnableConfigurationProperties` in `ShortenerConfig`, and constructor-injected into `LinkService`. The value comes from env `SHORTENER_BASE_URL`, defaulting to `http://localhost:8080`.

### CODE-08 · find-the-bug · hard
- Source: `LinkService.resolve`
- Q: Look at the expiry check `!expiresAt.isAfter(now)`, which uses `OffsetDateTime.now(UTC)` on the app server. Name one correctness risk once there are several instances.
- Grading points: each instance uses its own wall clock, so clock skew between instances (or between app and DB) makes the same link expired on one instance and live on another near the boundary. Fixes: NTP discipline, or compare in the DB with `now()`. Also note that the boundary instant counts as expired.

---

## JPA: JPA, Spring and transactions

### JPA-01 · explain-why · medium
- Source: `NplusOneDemoTest.java`
- Q: What is the N+1 problem? In the demo with 3 authors × 3 books, how many queries run with `findAll()` + lazy access versus `JOIN FETCH`?
- Grading points: one query loads the parents, then one query per parent when its lazy collection is touched. The demo has 1 + 3 = 4 queries versus exactly 1 with `findAllWithBooks()` (JOIN FETCH). The N+1 cost grows linearly with N.

### JPA-02 · explain-why · medium
- Source: `NplusOneDemoTest.seedData`
- Q: Why does the N+1 demo call `em.clear()` after seeding? What would happen without it?
- Grading points: it clears the first-level cache (the persistence context). Without it, `findAll()` would return the in-memory entities with books already attached, so no SQL fires and the N+1 problem would be hidden.

### JPA-03 · explain-why · medium
- Source: `LazyInitializationDemoTest.java`
- Q: When does `LazyInitializationException` happen, and how does it happen "naturally" in a real app?
- Grading points: when a lazy proxy/collection is accessed after its session (persistence context) is closed, i.e. on a detached entity ("could not initialize proxy – no Session"). In real code this is when a `@Transactional` service returns an entity and the controller touches a lazy field. Fixes: JOIN FETCH or a DTO projection.

### JPA-04 · explain-why · hard
- Source: `application.yml` (`open-in-view: false`), `docs/adr/0001-project-foundation.md` Consequences
- Q: The project sets `spring.jpa.open-in-view=false`. What does OSIV do, and why turn it off?
- Grading points: OSIV keeps the session open for the whole web request, so lazy loads in the controller/serialization "just work". That silently enables N+1 and holds a DB connection for the whole request. Turning it off forces explicit fetching inside the service transaction.

### JPA-05 · predict · hard
- Source: `TransactionBoundaryDemoTest.java`
- Q: Pool size 2, connection timeout 1000 ms, 6 concurrent requests, each `@Transactional` method sleeps 800 ms (a fake HTTP call) before saving. What happens? What changes if the sleep moves outside the transaction?
- Grading points: each transaction holds a connection for about 800 ms, so later requests wait for a connection and some time out ("Connection is not available"). With the call outside the transaction, the connection is borrowed only for the quick save, so all 6 succeed. This is AGENTS.md's rule: no remote calls inside a DB transaction.

### JPA-06 · find-the-bug · hard
- Source: `TransactionBoundaryDemoTest.DemoLinkService.createWithSlowCallOutsideTx`
- Q: `createWithSlowCallOutsideTx` calls `this.saveLinkInShortTransaction(...)`, which is `@Transactional`. Does that annotation actually apply on a self-call? Why does the demo still pass?
- Grading points: no. Spring's `@Transactional` works through a proxy, so a self-invocation bypasses it and no new transaction starts there. The demo passes anyway because `linkRepository.save` is transactional itself (Spring Data repository methods are), so the connection is still held only briefly. The fix would be calling through another bean or the proxy. (This is a teaching artifact: don't "fix" it in the repo.)

### JPA-07 · recall · easy
- Source: `application.yml` hikari block, `docs/adr/0001-project-foundation.md`
- Q: What is HikariCP, and which pool settings does the app configure? Name two with their values.
- Grading points: Spring Boot's default JDBC connection pool. Settings: `maximum-pool-size` (env `HIKARI_MAX_POOL_SIZE`, default 10), `minimum-idle` 2, `connection-timeout` 3000 ms, `idle-timeout` 600000, `max-lifetime` 1800000.

---

## FND: Foundation (ADR 0001, schema, config)

### FND-01 · explain-why · medium
- Source: `docs/adr/0001-project-foundation.md`
- Q: Why Spring Boot 3 + Maven rather than Gradle or Quarkus/Micronaut?
- Grading points: industry standard with a big ecosystem, plus Micrometer and Actuator (`/actuator/prometheus`) for free, and the Maven wrapper needs no install. Gradle is one more thing to learn while Java is new, and Maven is verbose but transparent. Quarkus/Micronaut have a smaller community and less employer recognition. Slower startup isn't a bottleneck here.

### FND-02 · explain-why · medium
- Source: `application.yml` (`ddl-auto: validate`), `CLAUDE.md`
- Q: Who owns the schema? What does `ddl-auto: validate` do, and what's the rule for changing an applied migration?
- Grading points: Flyway owns it (`db/migration`). Hibernate only checks that entities match the schema and fails at startup if they don't; it never alters the schema. Never edit an applied migration: add a new `V<n>__*.sql`, because Flyway checksums applied files.

### FND-03 · predict · hard
- Source: `V1__create_links_table.sql`, `Link.java`
- Q: V1 declares `id BIGSERIAL`, but `Link.id` has no `@GeneratedValue`. Where does `links_id_seq` come from, and is the DB default ever used by the app?
- Grading points: `BIGSERIAL` creates `links_id_seq` and sets the column default to `nextval(...)`. The app calls `nextval('links_id_seq')` explicitly through `LinkRepository.nextId()` and inserts that id, so the default is never used by app inserts. Both draw from the same sequence, so a manual SQL insert without an id still can't collide.

### FND-04 · recall · easy
- Source: `CLAUDE.md` Architecture, `application.yml`
- Q: How is configuration provided per environment? Name three env vars.
- Grading points: env-driven with local defaults in `application.yml` (`${VAR:default}`); `.env.example` lists them. Any three of: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `HIKARI_MAX_POOL_SIZE`, `SERVER_PORT`, `SHORTENER_BASE_URL`, `SHORTENER_MAX_URL_LENGTH`.

### FND-05 · explain-why · easy
- Source: `CLAUDE.md` Architecture
- Q: The code is "package-by-feature" (`com.urlshortener.shortener`). What does that mean versus package-by-layer, and why prefer it here?
- Grading points: all classes for one feature (controller, service, repository, entity, DTOs) live together instead of in `controllers/`, `services/` and so on. Features stay cohesive, package-private visibility works (e.g. `ShortenerConfig` is package-private), and future features (`ratelimit`, `jobs`) slot in cleanly.

---

## TEST: Testing

### TEST-01 · explain-why · medium
- Source: `AbstractIntegrationTest.java`, `docs/adr/0001-project-foundation.md`
- Q: Why Testcontainers with real Postgres instead of H2 or mocks?
- Grading points: tests hit the real dialect, sequences, constraints and Flyway migrations, so behaviour matches production (H2 differs). It's honest evidence (the AGENTS.md rule). The cost is needing Docker.

### TEST-02 · explain-why · medium
- Source: `AbstractIntegrationTest.java`
- Q: `AbstractIntegrationTest` starts the container in a `static {}` block instead of using `@Container`. Why?
- Grading points: the singleton container pattern: one Postgres shared by all subclasses in the JVM, so the suite doesn't pay container start-up per class. `@DynamicPropertySource` points the datasource at it.

### TEST-03 · explain-why · hard
- Source: `AbstractIntegrationTest.java` Javadoc
- Q: The integration tests set `ddl-auto=none` instead of `validate`. Why?
- Grading points: the app context also scans the test-only `jpademo` entities, whose tables aren't in the Flyway schema, so `validate` would fail. Flyway still builds the real `links` schema, and the tests read and write every column.

### TEST-04 · recall · medium
- Source: commit `c1bd345`, `src/test/resources/docker-java.properties`
- Q: What does `docker-java.properties` with `api.version=1.44` fix?
- Grading points: Testcontainers' docker-java client negotiated an API version that the newer Docker Engine (29.x) no longer accepts. Pinning 1.44 makes Testcontainers work. Keep it.

### TEST-05 · two-instances · hard
- Source: `ConcurrentCreateTest.java`, `docs/adr/0002-id-generation.md` Evidence
- Q: `ConcurrentCreateTest` runs 32 threads × 50 creates. What does the `CountDownLatch` do, what does the test assert, and what does it NOT prove?
- Grading points: the latch releases all threads at once to maximise overlap. It asserts all 1600 codes are distinct and each resolves to its own URL. It doesn't prove multi-instance safety (separate JVMs and connection pools), which is the week-10 3-instance test. It is still meaningful because the guarantee comes from Postgres, not Java locks.

### TEST-06 · find-the-bug · medium
- Source: `LinkApiIntegrationTest.metricsCountCreatesAndRedirectOutcomes`
- Q: The metrics test reads the counter values before acting and asserts `+1` after, instead of asserting absolute values. Why?
- Grading points: the Spring context and `MeterRegistry` are shared across test classes and methods (context caching), so counters already hold values from other tests. Deltas make the test order-independent.

---

## OBS: Observability

### OBS-01 · recall · easy
- Source: `docs/phase1-mvp-architecture.md` §6, `AGENTS.md`/`CLAUDE.md`
- Q: What are RED metrics, and where does each come from for the redirect endpoint?
- Grading points: Rate, Errors, Duration. All three come from the automatic `http_server_requests` timer (count, status/outcome tags, duration), plus `links_redirects_total{outcome=hit|not_found|expired}`. New endpoints must add RED metrics in the same change.

### OBS-02 · explain-why · medium
- Source: commit `7a4ebea`, `application.yml`
- Q: Why enable `percentiles-histogram` for `http.server.requests`? What could you not see before?
- Grading points: it publishes histogram buckets (`_bucket` series) so Prometheus can compute p95/p99 with `histogram_quantile` and aggregate across instances. Without it, the Grafana latency dashboard had no percentiles, only count/sum/max.

### OBS-03 · explain-why · medium
- Source: commit `7a4ebea`, `deploy/grafana/provisioning/datasources/datasources.yml`
- Q: Why give the provisioned Prometheus and Loki datasources fixed `uid`s?
- Grading points: dashboard JSON panels reference datasources by UID. Without fixed UIDs, Grafana generates random ones at provisioning time, so the provisioned dashboard panels can't resolve their datasource.

### OBS-04 · predict · medium
- Source: `LinkService.java`
- Q: The code registers `Counter.builder("links.created")` and `meterRegistry.counter("links.redirects", "outcome", …)`. What metric names does Prometheus scrape?
- Grading points: `links_total` (not `links_created_total`) and `links_redirects_total{outcome="…"}`. Micrometer converts dots to underscores and adds `_total` to counters, plus the common tag `application="url-shortener"`. The Prometheus client 1.x reserves the `_created` suffix and strips it, so `links.created` is exposed as `links_total`. Verified against `/actuator/prometheus` on 2026-11-22.

### OBS-05 · design · hard
- Source: `LinkService.resolve`
- Q: `links.redirects` has an `outcome` tag. Why would tagging by `shortCode` be a bad idea?
- Grading points: unbounded cardinality: one time series per link, which blows up Prometheus memory and query cost. Tags must have a small, bounded set of values (like `outcome`).

### OBS-06 · recall · easy
- Source: `application.yml` management block
- Q: Which Actuator endpoints are exposed over HTTP, and which one does Prometheus scrape?
- Grading points: `health`, `info`, `prometheus`, `metrics`. Prometheus scrapes `/actuator/prometheus`.

### OBS-07 · explain-why · hard
- Source: `LinkService.create`, `LinkServiceCounterTest`, commit `2e99805`
- Q: Why is `links.created` incremented in `TransactionSynchronization.afterCommit` instead of right after `save()`? How does `LinkServiceCounterTest` force the failure without mocks?
- Grading points: `Link` has an assigned id, so `save()` only queues the INSERT. A constraint violation surfaces when the transaction flushes at commit, after `create()` has returned, so a counter bumped inside would count a row that was rolled back. The test burns `nextId()`, pre-inserts a "squatter" row under a different id with the short code of the next id, then asserts `create()` throws `DataAccessException` and the counter is unchanged. A second test asserts a normal create increments by exactly 1. It runs on real Postgres via Testcontainers.

---

## BENCH: Benchmarks and measurement

### BENCH-01 · explain-why · medium
- Source: `benchmarks/k6/scenarios/redirect-read-heavy.js` header, `benchmarks/REPORT.md` Method
- Q: The k6 scenario uses an open model (`constant-arrival-rate`) instead of a fixed number of looping virtual users. Why does that matter when the server gets slow?
- Grading points: in an open model requests arrive at a fixed rate whether or not earlier ones finished, so slow responses cannot throttle the offered load. A closed model would send fewer requests when the server slows, which hides the slowdown (coordinated omission). When the app can't keep up, k6 reports `dropped_iterations` instead.

### BENCH-02 · explain-why · medium
- Source: `benchmarks/k6/scenarios/redirect-read-heavy.js` (key distribution)
- Q: Keys are drawn from a Zipf(s=1) distribution and then scattered over ids with a bijection (multiplier 1000003). Why Zipf, and why scatter?
- Grading points: real traffic is skewed, with a few hot links taking most requests. That matters for a cache and for realistic DB page-cache behaviour. Uniform keys would understate hit rates. The scatter keeps the hot keys from being physically adjacent rows, which "would flatter the database". `SEED_ROWS` must be coprime with the multiplier for the mapping to be a bijection.

### BENCH-03 · recall · medium
- Source: `benchmarks/REPORT.md` v0-naive (Saturation point), `roadmap.md` §benchmarks
- Q: How did the v0-naive baseline choose its measurement rate (3000 req/s), and what was the saturation result? What number should you not over-trust?
- Grading points: a ramp (1 → 6000 req/s) found the knee. Throughput plateaus at about 4,450 req/s, p99 starts rising near 3,300 and passes 200 ms between 3,900 and 4,400. The measurement is run at about 70% of the saturation point. The "max RPS ≈ 3,900" comes from a single ramp and is only accurate to about one 30-second step (~270 req/s).

### BENCH-04 · predict · hard
- Source: `benchmarks/REPORT.md` v0-naive (Next bottleneck), `benchmarks/results/v0-naive/profile.md`
- Q: At the knee, which resource is saturated: app CPU, Postgres CPU or the load generator? What did the JFR profile say about how much of the app's CPU is the DB access path, and what does that imply for a cache's gain?
- Grading points: the app is at its 2-CPU cap (~200%). Postgres is at about half its limit (45–48% average at 3000 req/s) and k6 is not the limit. The profile at 3000 req/s attributed 40.2% of sampled request-thread time to the DB access path (Hibernate/JPA 25.9%, Hikari 5.7%, JDBC driver 4.5%), with 11.1% in Micrometer. So a cache hit removes at most about 40% of the app's work, and the gain is smaller than the DB share suggests. These are sampled Java time only, from a profiling run (not comparable to the baseline latencies).

### BENCH-05 · find-the-bug · hard
- Source: `deploy/docker-compose.bench.yml` header, commit `5b077c1`
- Q: The bench stack declared `cpus: 2` for the app and Postgres, but a saturation run showed the app above 200% CPU in `docker stats`. What was wrong, how was it fixed, and what happened to the earlier results?
- Grading points: Compose v2.3.3 silently ignores service-level `cpus:` (and mis-converts `deploy.resources.limits.cpus`), so the limits were never applied. Fix: `cpu_period: 100000` / `cpu_quota: 200000`, verified with `docker inspect` (CpuQuota=200000) and a CPU burn that held at about 199%. Results from before the fix ran without limits (knee about 5,400 req/s) and were discarded as not comparable.

### BENCH-06 · explain-why · hard
- Source: `benchmarks/predictions/v2-redis-cache.md`, `AGENTS.md` benchmark protocol
- Q: Why does the repo store a written prediction for v2-redis-cache before the cache exists, and what were the numbers predicted? Name one weakness the agent's notes found.
- Grading points: so the result can be compared with what you expected, and you can't rewrite expectations afterwards. Prediction (TTL 60 s, 1 instance): p99 steady 3 ms, hit rate about 40%, Postgres CPU 25–30%, app CPU 95–100%, knee 5000 rps. Weakness: the agent's rough model estimated about a 59% hit rate at a 60 s TTL, above the predicted 40%, and the knee of 5000 (+12%) assumes the DB path is a quarter to a third of the app's CPU per request. The owner left the numbers unchanged.

---

## DB: Query plans

### DB-01 · predict · medium
- Source: `benchmarks/results/v0-naive/explain-analyze.txt`, `benchmarks/REPORT.md` (Query plan analysis)
- Q: On 10M rows, what plan does `select … from links where short_code = ?` use, and how many pages does it touch for an existing code vs. a missing code?
- Grading points: Index Scan using `uq_links_short_code` (the unique B-tree index). 4 pages for a hit (3 index levels plus 1 table page), 3 for a miss (index only). Execution time about 0.06 ms.

### DB-02 · explain-why · hard
- Source: `benchmarks/results/v0-naive/explain-analyze.txt`, `benchmarks/REPORT.md`
- Q: The report says a separate "add an index" benchmark version would change nothing. Why? What did forcing a sequential scan show?
- Grading points: `short_code` already has a unique index (created by the UNIQUE constraint), and the planner picks it (the seed ends with `VACUUM ANALYZE`, `n_distinct = -1`). With index, bitmap and index-only scans disabled, Postgres did a Parallel Seq Scan over 113,531 pages (about 887 MB) in 532 ms vs 0.060 ms, about four orders of magnitude. That was a hand-forced session, not something the app does. Strong answers add that planning time (about 0.45–0.5 ms) exceeds execution time, and that whether the app reuses prepared plans was not checked.

---

## TOOL: Developer tooling

### TOOL-01 · explain-why · medium
- Source: `docs/adr/0003-postman-cli-for-manual-api-runs.md`
- Q: ADR 0003 picks the Postman CLI over Newman, the desktop Runner and a curl script. Why, and name two costs the ADR accepts.
- Grading points: it runs the collection straight from the workspace (no exported JSON to drift), runs locally so it can reach `localhost:8080` (cloud runners can't), the agent can run it and read real results, and it exits non-zero on failure. Costs: a new binary and a Postman login, results are uploaded to Postman Cloud, runs write real rows to the local DB, 9 of the 22 requests have no test script, and the installer edits agent config files unless turned off by flags. Postman results do not count as test or benchmark evidence.
