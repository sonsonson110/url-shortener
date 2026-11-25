# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**Read [AGENTS.md](AGENTS.md) first — it is authoritative** (mentor-vs-builder collaboration mode, hard rules on never fabricating results, phase gate, benchmark/failure-test protocols, Definition of Done, end-of-task report format). Scope and phase order live in [roadmap.md](roadmap.md) (AGENTS.md calls it `ROADMAP.md`; the actual filename is lowercase). Commit messages and benchmark tags follow `.agents/skills/git-conventions/SKILL.md`.

## Commands

Maven wrapper + Makefile. The Makefile defaults `JAVA_HOME` to `/usr/local/android-studio/jbr` (override with `JAVA_HOME=... make ...`); the project targets Java 21.

```bash
make build                 # ./mvnw package -DskipTests
make test                  # ./mvnw test — needs Docker (Testcontainers starts real Postgres)
./mvnw test -Dtest=LinkRepositoryTest                 # single test class
./mvnw test -Dtest=NplusOneDemoTest#methodName        # single test method
make up-infra              # postgres, redis, prometheus, grafana, loki only (run the app from the IDE/mvn)
make up / make down        # full stack incl. app container (deploy/docker-compose.yml)
make db-shell              # psql in the running postgres container
make bench VERSION=v0-naive RATE=3000   # full run: clean bench stack, seed 10M rows, warm-up, k6, save, tear down (~11 min)
make bench-saturation VERSION=v0-naive RATE=6000   # ramp to RATE (peak) to find the knee; measure at ~70% of it
```

Notes: `make test-unit` filters on `-Dgroups="unit"` but no tests are tagged `@Tag("unit")` yet, so it runs nothing. `make format` is a placeholder. `src/test/resources/docker-java.properties` pins `api.version=1.44` for Testcontainers against newer Docker daemons — keep it.

## Architecture

Single Spring Boot 3 / Java 21 app, package-by-feature under `com.urlshortener`. Currently only `shortener`: `RedirectCache` (Redis cache-aside for the redirect, per-key lock, fail-open; ADR 0004; `LinkService.resolve` is deliberately not `@Transactional`), `LinkController` (`POST /api/v1/links`, 201), `RedirectController` (`GET /{code:[0-9A-Za-z]{1,12}}`, 302), `LinkService`, `Link` entity (assigned id from `links_id_seq`, implements `Persistable`), `Base62`, and `ShortenerExceptionHandler` (ProblemDetail 400/404/410). The manual API collection lives in Postman, not the repo; see the `postman-sync` skill. Planned feature packages (`ratelimit`, `jobs`) are not started.

- **Schema is owned by Flyway** (`src/main/resources/db/migration`); Hibernate is `ddl-auto: validate`, `open-in-view: false`. Never edit an applied migration — add a new `V<n>__*.sql`.
- **Config is env-driven** with local defaults in `application.yml` (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST/PORT`, `HIKARI_MAX_POOL_SIZE`, `SERVER_PORT`, `CACHE_TTL`, `CACHE_LOCK_*`); `.env.example` lists the older ones.
- **Observability baseline:** Actuator exposes `health,info,prometheus,metrics`; `http.server.requests` publishes percentile histograms. Prometheus scrape config and the provisioned Grafana dashboard/datasources (fixed UIDs) are under `deploy/`. New endpoints/jobs must add RED metrics in the same change.
- **Tests use real infrastructure:** `@Testcontainers` + `@DynamicPropertySource` with a Postgres container (no H2/mocks). `src/test/java/com/urlshortener/jpademo/` (with its own `model/` and `repository/`) holds deliberate JPA-pitfall demos (N+1, lazy-init, transaction boundaries/connection starvation) — they are teaching artifacts, not production code; don't "fix" the pitfalls in them.
- **Benchmarks:** `benchmarks/scripts/run-bench.sh` (via `make bench`) drives a separate compose project, `deploy/docker-compose.bench.yml` (`urlshortener-bench`, never touches dev data), and builds the app image from the **current working tree**, so check out the version's commit first. Each run writes `benchmarks/results/<version>/<timestamp>/` (`env.txt` with commit and dirty flag, `summary.json`, `timeseries.csv`, `pg_stat_statements.csv`, `docker-stats.csv`); summaries go in `benchmarks/REPORT.md`. Scenario: `benchmarks/k6/scenarios/redirect-read-heavy.js` (open model, Zipf keys; `SEED_ROWS` must match `seed.sql`). CPU limits are set with `cpu_quota` because the installed Compose v2.3.3 silently ignores `cpus:`; re-verify after changing limits. Don't run other heavy workloads during a bench. Any number in docs must come from a saved real run.
