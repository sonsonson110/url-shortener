# URL Shortener

> **Phase 1 — URL Shortener (MVP in progress).** `POST /api/v1/links` creates a short link; `GET /{code}` redirects (302). See [docs/phase1-mvp-architecture.md](docs/phase1-mvp-architecture.md).

A production-grade URL shortener built as a learning portfolio covering system design, distributed systems, and observability. Java 21 · Spring Boot 3 · PostgreSQL · Redis · Kubernetes.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     Load Balancer                        │
└─────────────────────┬───────────────────────────────────┘
                      │
         ┌────────────▼────────────┐
         │   Spring Boot App (3x)  │
         │   Port 8080             │
         └──────┬──────────┬───────┘
                │          │
       ┌────────▼──┐  ┌────▼──────┐
       │ PostgreSQL│  │   Redis   │
       │  (read +  │  │  (cache + │
       │   write)  │  │  rate lim)│
       └───────────┘  └───────────┘
```

## Quick Start

```bash
# Start the full stack
make up

# View logs
make logs

# Open Grafana (admin/admin)
open http://localhost:3000

# Open Prometheus
open http://localhost:9090

# Stop everything
make down
```

## Project Layout

```
.
├── AGENTS.md               # AI coding agent instructions
├── roadmap.md              # Phase roadmap and benchmarking playbook
├── Dockerfile
├── Makefile
├── pom.xml
├── benchmarks/
│   ├── k6/scenarios/       # k6 load test scripts
│   ├── results/            # Raw benchmark output (JSON)
│   └── REPORT.md           # Benchmark summary table
├── deploy/
│   ├── docker-compose.yml
│   ├── prometheus/
│   └── grafana/
├── docs/
│   ├── adr/                # Architecture Decision Records
│   └── db/                 # Schema notes and EXPLAIN ANALYZE output
└── src/
    ├── main/java/com/urlshortener/
    │   └── shortener/      # Feature package
    └── test/java/com/urlshortener/
```

## Tech Stack

| Area | Choice |
|------|--------|
| Language / framework | Java 21, Spring Boot 3 |
| Database | PostgreSQL 16, Flyway, HikariCP |
| Cache | Redis 7 (Lettuce) |
| Resilience | Resilience4j |
| Testing | JUnit 5, Testcontainers |
| Load testing | k6 |
| Observability | Micrometer, Prometheus, Grafana, Loki |
| Packaging | Docker, Docker Compose, Kubernetes, Helm |

## Phases

| Phase | Status | Focus |
|-------|--------|-------|
| 0. Foundation | 🔄 In progress | Walking skeleton, metrics |
| 1. URL Shortener | ⏳ Upcoming | Create/redirect, caching, benchmarks |
| 2. Rate Limiter | ⏳ Upcoming | Distributed token bucket, Lua, concurrency proof |
| 3. Background Jobs | ⏳ Upcoming | SKIP LOCKED queue, retries, idempotency |
| 4. Observability | ⏳ Upcoming | SLOs, traces, fault injection |

## Headline Numbers

> Real numbers will appear here after each benchmark run. See `benchmarks/REPORT.md`.

## Known Limitations

- Phase 0: no business logic yet — this is the skeleton only.
- Single-machine benchmarks do not prove multi-instance behavior.

## What I Would Do Differently

> Will be written after each chapter is complete.

---

*See [`roadmap.md`](roadmap.md) for the full plan and benchmarking playbook.*
