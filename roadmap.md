# System Design Self-Study Roadmap

*Java 21 · Spring Boot 3 · PostgreSQL · Redis · Kubernetes. About 36 weeks at 10–12 hours per week, with no hard deadline.*

## 1. Goal and Strategy

**Goal:** move from feature work in React and Node to engineering that shows real system design depth, and produce a portfolio that opens better job opportunities.

**Strategy:**

- Build **one coherent system** instead of four separate toy repos. A URL shortener is the core. A rate limiter protects it, background jobs power its analytics and cleanup, and observability covers all of it. One story is easier to tell in an interview than four exercises.
- Every chapter must contain **at least one engineering challenge that you solve yourself and can prove** with tests, benchmarks, or failure experiments.
- Keep the stack **boring and industry-standard** so the learning goes into system design, not tooling.
- Put observability in place **early**, because honest benchmark numbers need metrics.

## 2. Assumptions and Weekly Rhythm

- **Time:** weekends plus 2–4 hours on weekdays, roughly 10–12 hours per week. If you average less, stretch each phase by about 1.5x. The order stays the same.
- **Background:** 1.5 years of React, Node.js, and Kubernetes. Java and Spring are new, so expect a slow start in Phase 0.
- **Weekdays (2–4h):** reading (DDIA chapters, Postgres docs), small tasks, tests, and notes. This is work that survives interruption.
- **Weekends (6–8h):** deep work such as building features, running benchmarks, and failure testing. These need uninterrupted time.
- **End of every chapter:** one write-up and one 5-minute spoken walkthrough, practiced aloud or recorded.

## 3. Roadmap at a Glance

| Phase | Weeks | Focus | Output |
| --- | --- | --- | --- |
| 0 | 1–3 | Java, Spring, Postgres foundation, metrics skeleton | Walking skeleton repo |
| 1 | 4–11 | URL Shortener | Chapter 1 write-up, benchmarks, ADRs |
| 2 | 12–17 | Distributed Rate Limiter | Chapter 2 write-up, concurrency proof |
| Checkpoint | 18 | Polish, README, resume, start applying | Resume-ready portfolio |
| 3 | 19–26 | Background Job Processing | Chapter 3 write-up, crash test |
| 4 | 27–32 | Observability and incident postmortems | Chapter 4 write-up |
| 5 | 33–36 | Final polish and interview practice | Walkthroughs, resume bullets |

You can start applying for jobs after week 18. Phases 0–2 already make a strong portfolio.

## 4. Tech Stack

| Area | Choice |
| --- | --- |
| Language and framework | Java 21, Spring Boot 3 |
| Database | PostgreSQL, Flyway migrations, HikariCP |
| Cache and counters | Redis (Lettuce or Redisson) |
| Resilience | Resilience4j |
| Testing | JUnit 5, Testcontainers, Toxiproxy |
| Load testing | k6 (Gatling as an alternative) |
| Metrics, logs, traces | Micrometer, Prometheus, Grafana, Loki, OpenTelemetry, Tempo |
| Packaging and deploy | Docker, Docker Compose, Kubernetes, Helm |

## 5. Phase 0: Foundation (Weeks 1–3)

**Goal:** stop fighting the tools so the labs are about system design.

- [x] Spring Boot 3 + Java 21 CRUD app with Postgres
- [x] Flyway migrations and Testcontainers integration tests
- [x] Docker Compose with app, Postgres, Prometheus, and Grafana
- [x] Micrometer metrics wired in, with a basic latency dashboard
- [x] JPA pitfall demos: N+1 queries, lazy loading, transaction boundaries
- [ ] Read DDIA chapters 1–3 alongside

**Done when:** one command starts the whole stack and you can see request latency in Grafana.

## 6. Phase 1: URL Shortener (Weeks 4–11)

**What it teaches:** read-heavy design, database optimization, caching, scaling out.

| Weeks | Work |
| --- | --- |
| 4–5 | MVP: create and redirect, base62 ID generation, schema, integration tests |
| 6–7 | Redis caching: cache-aside, TTL, stampede protection; 301 vs 302 ADR |
| 8–9 | k6 benchmarking: baseline, then before/after for each optimization; `EXPLAIN ANALYZE` and index analysis |
| 10 | Multi-instance deployment: 3 replicas behind a load balancer, proving ID generation stays collision-free |
| 11 | Kubernetes + Helm, failure tests (kill Redis, kill a pod), write-up. Partly buffer time |

**Engineering challenges to solve and prove:**

- Collision-free ID generation across multiple instances (base62 with counter ranges vs. hashing)
- Cache stampede and hot-key handling
- 301 vs 302 redirect tradeoff
- Postgres index and query plan analysis

**MVP cutline:** skip custom aliases, user accounts, and a UI. Add them only if you are ahead.

**Checklist:**

- [ ] Schema and migrations
- [ ] Create and redirect endpoints with tests
- [ ] ID generation ADR (alternatives considered)
- [ ] Redis cache-aside with stampede protection
- [ ] Benchmark report with before/after table
- [ ] 3-instance deployment with collision test
- [ ] Failure tests: Redis down, pod killed
- [ ] Helm chart
- [ ] README with architecture diagram and real numbers

**Done when:** the README answers all the key questions with real numbers, and you can explain why you chose your ID strategy.

## 7. Phase 2: Distributed Rate Limiter (Weeks 12–17)

**What it teaches:** concurrency control across multiple application instances, Redis, and distributed-systems tradeoffs.

| Weeks | Work |
| --- | --- |
| 12–13 | Fixed window and token bucket, first in memory, then in Redis |
| 14 | Atomic check-and-decrement with a Lua script; concurrency test proving zero over-admission across N instances |
| 15 | Sliding window comparison; ADR on token bucket vs. sliding window |
| 16 | Integrate into the shortener; fail-open vs. fail-closed test using Toxiproxy |
| 17 | Benchmark added latency per request, write-up. Partly buffer time |

**Engineering challenges to solve and prove:**

- Atomicity under concurrency (why a naive read-then-write breaks)
- Zero over-admission across multiple instances
- Behavior when Redis dies, and the justification for your choice

**Checklist:**

- [ ] In-memory token bucket with unit tests
- [ ] Redis version with Lua script
- [ ] Concurrency test: 5 instances, concurrent load, never exceeds the limit
- [ ] Sliding window implementation and comparison ADR
- [ ] Integration into create and redirect endpoints
- [ ] Toxiproxy test for Redis failure, with documented fail-open or fail-closed decision
- [ ] Overhead benchmark and write-up

**Done when:** you can show a test where 5 instances under concurrent load never exceed the limit.

## 8. Checkpoint: Polish and Start Applying (Week 18)

- [ ] Clean up READMEs and add architecture diagrams
- [ ] Add “Known limitations” and “What I’d do differently” sections
- [ ] Write 2–3 resume bullets (see section 13)
- [ ] Practice the 5-minute walkthrough for chapters 1 and 2
- [ ] Start applying. Interviews take weeks to arrange, and you keep building meanwhile

## 9. Phase 3: Background Job Processing (Weeks 19–26)

This is the hardest chapter, so it gets 8 weeks.

**What it teaches:** async processing, reliability, retries, idempotency.

| Weeks | Work |
| --- | --- |
| 19–20 | Postgres queue with `SKIP LOCKED`: enqueue, claim, complete |
| 21–22 | Retries with exponential backoff, dead-letter table, idempotency keys |
| 23–24 | Real use cases: click-analytics aggregation and link-expiry cleanup |
| 25 | `kill -9` a worker mid-job; prove no loss and no duplicate effects |
| 26 | ADR comparing the Postgres queue with RabbitMQ or Kafka; write-up. Partly buffer time |

**Engineering challenges to solve and prove:**

- At-least-once delivery with idempotent handlers
- Crash recovery: no lost jobs and no duplicated side effects
- Poison messages handled through retries and a dead-letter table
- Tradeoff analysis: database-backed queue vs. a message broker

**Checklist:**

- [ ] Jobs table and `SKIP LOCKED` claim logic
- [ ] Visibility timeout or lease so stuck jobs are reclaimed
- [ ] Retry with exponential backoff and jitter
- [ ] Dead-letter table and a way to inspect and replay it
- [ ] Idempotency keys on side effects
- [ ] Click-analytics aggregation job
- [ ] Link-expiry cleanup job
- [ ] Crash test with `kill -9`, with results documented
- [ ] Queue vs. broker ADR

**Optional stretch:** a broker-based variant, only after everything else is done.

## 10. Phase 4: Observability (Weeks 27–32)

Metrics already exist from Phase 0, so this phase is about using them. Do not build your own metrics platform. Use the standard stack, and make troubleshooting the engineering challenge.

| Weeks | Work |
| --- | --- |
| 27–28 | Define RED metrics and SLOs, add alerts, add structured logs (Loki) |
| 29–30 | Distributed tracing with OpenTelemetry and Tempo; trace one request end to end |
| 31 | Inject 2–3 faults (slow query, dying Redis, stuck worker) and diagnose each from dashboards and traces only |
| 32 | Write 2 incident postmortems and the chapter write-up. Partly buffer time |

**Checklist:**

- [ ] RED dashboard (rate, errors, duration) for every service
- [ ] At least one SLO and one alert tied to it
- [ ] Structured logs with trace IDs
- [ ] End-to-end trace across app, Redis, Postgres, and worker
- [ ] Fault 1 injected, diagnosed, and written up
- [ ] Fault 2 injected, diagnosed, and written up
- [ ] Two incident postmortems (timeline, root cause, fix, prevention)

## 11. Phase 5: Final Polish and Interview Prep (Weeks 33–36)

- [ ] Add “Known limitations” and “What I’d do differently” to every README
- [ ] Practice the 5-minute walkthrough for each chapter
- [ ] Practice answering “what would break first?” for every component
- [ ] Update resume with final bullets
- [ ] Use leftover time on Postgres depth: PgBouncer, partitioning, read replicas, vacuum and MVCC basics

## 12. Benchmarking Playbook

Use this for every “naive vs. optimized” comparison in all chapters.

**Core principle:** change one thing at a time, and run the exact same experiment against every version.

**Keeping versions in the codebase:**

- **Git tags** for big architectural steps: `v0-naive`, `v1-index`, `v2-redis-cache`, `v3-stampede-protection`. Each optimization is one commit or PR, so the diff is easy to show.
- **Feature flags or Spring profiles** for cheap runtime toggles such as `cache.enabled`. Faster to iterate, but flags can interact with each other.

**One command per run:** `make bench VERSION=v2-redis-cache` should:

1. Select the version
2. Start a clean stack with fixed CPU and memory limits per container
3. Run migrations and seed the same dataset (for example 10M URLs, same random seed)
4. Warm up for 1–2 minutes and discard those results
5. Run the k6 scenario
6. Export results to `benchmarks/results/<version>/<timestamp>.json`
7. Tear everything down

**Workload design:**

- Use a realistic read/write ratio (about 100:1 for a URL shortener)
- Use a skewed, Zipf-like key distribution, not uniform and not a single key
- Seed millions of rows, because small tables hide index problems
- Use k6 `constant-arrival-rate`, not fixed virtual users, to avoid coordinated omission and see true tail latency

**What to measure:**

| Metric | Why |
| --- | --- |
| p50 / p95 / p99 latency | Averages hide tail problems |
| Throughput at a target error rate | “Max RPS while p99 < 200ms and errors < 0.1%” is meaningful |
| Error rate | A fast system returning 500s is not fast |
| CPU, memory, DB connections, cache hit ratio | Shows why numbers moved |
| `EXPLAIN ANALYZE`, `pg_stat_statements` | Proves where the database time goes |

**Fairness rules:**

- Run the load generator on a separate machine, or cap its CPU
- Run each test 3–5 times and report the median or range
- Close heavy apps, plug in the laptop, disable power saving
- Same warm-up and duration every time (5+ minutes per measured run)
- Reset state between runs (fresh containers, flushed Redis unless testing a warm cache)
- Find the saturation point first, then benchmark at about 70% of it

**Repo layout:**

```
benchmarks/
  k6/scenarios/redirect-read-heavy.js
  k6/scenarios/create-burst.js
  scripts/run-bench.sh
  results/v0-naive/<timestamp>.json
  REPORT.md
```

**REPORT.md format:** one table plus one paragraph per row covering the hypothesis, the result, and what became the next bottleneck.

| Version | Change | p50 | p95 | p99 | Max RPS @ p99<200ms |
| --- | --- | --- | --- | --- | --- |
| v0-naive | baseline | – | – | – | – |
| v1-index | composite index | – | – | – | – |

**Common mistakes:**

- Benchmarking before verifying correctness (add a test that the optimized version returns the same results as the naive one)
- Optimizing without profiling first
- Reporting one big number without hardware, dataset size, and duration
- Changing two things between versions and claiming credit for one
- Claiming “handles 1M users” from a single-machine test. Say what you measured and what you expect to break next

## 13. Evidence and README Standards

**Evidence per chapter:**

| Area | Evidence |
| --- | --- |
| Architecture | Architecture diagram, ADRs |
| Database | Schema, indexes, query analysis |
| Performance | Benchmark report with real numbers |
| Scalability | Multi-instance deployment |
| Reliability | Failure testing results |
| Infrastructure | Docker, Kubernetes, Helm |
| Quality | Automated tests |

**Every README should answer:**

- What problem does this system solve?
- What are the architectural decisions?
- What alternatives did you consider?
- What are the bottlenecks?
- How did you test scalability?
- What happens when dependencies fail?
- Known limitations
- What I would do differently

**Layout tip:** put a one-paragraph summary, an architecture diagram, and your headline numbers at the top. Recruiters give you about 30 seconds.

**Resume bullet format:** one line, one decision, one number. Example: “Designed a Redis-backed distributed rate limiter (Lua, token bucket); verified zero over-admission across 5 instances at 3k RPS.”

## 14. Rules to Keep This Realistic

1. **Timebox each phase.** If you are two weeks over, cut scope rather than extend.
2. **Use the buffer weeks** (11, 17, 26, 32) for illness, work crunch, or rabbit holes.
3. **Write as you go.** Reconstructing numbers weeks later produces weak write-ups.
4. **Skipping is fine.** Phases 0–2 plus a thin Phase 4 is still better than most portfolios.
5. **Two deep chapters beat four shallow ones.**
6. **Use your day job as a lab.** Add a dashboard or find one real bottleneck at work. That story is worth more than any side-project number.
7. **Read while building.** Loop: read, form a hypothesis, experiment, measure, write up. *Designing Data-Intensive Applications* maps closely onto these labs.

## 15. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Scope creep | Define an MVP for each phase and enforce the cutline |
| Java learning slows Phase 0 | Keep the stack boring, and allow Phase 0 to stretch to 4 weeks |
| Burnout over a long timeline | Take a lighter week every 6–8 weeks, with no guilt |
| Numbers that are not credible | Follow the benchmarking playbook and document hardware and method |
| Building without applying | Start applying at week 18, not week 36 |

## 16. Progress Tracker

| Phase | Status | Start date | End date | Notes |
| --- | --- | --- | --- | --- |
| 0. Foundation | Completed | 2026-11-14 | 2026-11-15 | Skeleton, metrics wiring, JPA pitfall demos verified |
| 1. URL Shortener | In progress | 2026-11-15 |  |  |
| 2. Rate Limiter | Not started |  |  |  |
| Checkpoint | Not started |  |  |  |
| 3. Background Jobs | Not started |  |  |  |
| 4. Observability | Not started |  |  |  |
| 5. Final Polish | Not started |  |  |  |
