# Benchmark Report

Redirect latency (`GET /{code}` → 302), median of 3 runs, 3000 redirects/s plus 30 creates/s (100:1),
Zipf(s=1) key popularity over 10M seeded links. Raw output: `benchmarks/results/<version>/<timestamp>/`.

| Version | Change | p50 | p95 | p99 | Max RPS @ p99<200ms | Notes |
|---------|--------|-----|-----|-----|----------------------|-------|
| v0-naive | baseline | 0.45 ms | 1.00 ms | 5.4 ms (3.6–6.1) | ~3,900 (rough, one ramp) | 0 failed requests; app CPU-bound |

---

## v0-naive — Baseline

**Hypothesis:** none beyond establishing the baseline: what the system does with no cache and no
schema tuning, so every later version has a fixed point to be compared against.

**Result:**

Measured at 3000 redirects/s (about 70% of the saturation point, see below), 5 minutes after a
2-minute warm-up at the same rate, 3 runs on the same commit (`a137157`, clean tree).

| Run | p50 | p95 | p99 | max | dropped iterations | failed |
|---|---|---|---|---|---|---|
| 1 | 0.45 ms | 1.07 ms | 6.08 ms | 257 ms | 654 | 0 |
| 2 | 0.44 ms | 0.76 ms | 3.64 ms | 102 ms | 75 | 0 |
| 3 | 0.47 ms | 1.00 ms | 5.40 ms | 169 ms | 399 | 0 |
| **Median (range)** | **0.45 (0.44–0.47)** | **1.00 (0.76–1.07)** | **5.40 (3.64–6.08)** | 169 (102–257) | 399 (75–654) | 0 |

- Each run served about 908,000 requests (0 failed). Dropped iterations were 0.01–0.07% of the offered load.
- Creates: p99 5.2–7.8 ms.
- CPU (average over the 5 minutes, limit 200% each): app 93–100%, Postgres 45–48%, k6 84–89%.
- Postgres: the redirect query averaged 0.043–0.050 ms per call.
- Latency is not steady: in each run a few 10-second windows had a p99 of 20–90 ms (run 1: 4 windows,
  run 2: 1, run 3: 3). The only repeating one is the first 10 seconds of the measurement (29–35 ms in
  all three runs). The later ones fall at different times in each run, so they are not periodic.
  Cause not identified (JVM GC, CPU contention on a shared laptop and the gap between warm-up and
  measurement are all untested candidates).

**Saturation point** (one ramp, 1 → 6000 redirects/s over 5 minutes, after a 2-minute warm-up at 1000/s):

| Ramp second | Achieved req/s | p50 | p95 | p99 | dropped |
|---|---|---|---|---|---|
| 120–150 | 2,697 | 0.5 ms | 0.9 ms | 4.2 ms | 0 |
| 150–180 | 3,294 | 0.5 ms | 2.8 ms | 28 ms | 80 |
| 180–210 | 3,867 | 0.5 ms | 39 ms | 123 ms | 892 |
| 210–240 | 4,437 | 54 ms | 188 ms | 255 ms | 1,310 |
| 240–270 | 4,442 | 191 ms | 359 ms | 462 ms | 19,213 |

Throughput plateaus at about 4,450 req/s; p99 starts rising at about 3,300 req/s and passes 200 ms
between 3,900 and 4,400 req/s. From the knee onward the app sits at its 2-CPU cap (about 200%) while
Postgres is at about 100% of 200% and k6 at 146–172%, so the load generator is not the limit. The
3,900 in the table is the highest 30-second step with a p99 under 200 ms; it comes from a single ramp
and is accurate to about one step (~270 req/s).

**Next bottleneck:** the application's CPU (2 CPUs), not the database. The Postgres lookup is about
0.05 ms and the database is at about half its CPU limit when the app saturates. Whether the Hikari
pool (10 connections) is also a limit is not established: its exported acquire-time metric gave
values that do not agree with the request latencies (an apparent ~18 ms average acquire against a
0.45 ms median request), so it is not used as evidence here. A cache removes the database lookup,
but a hit still costs the app the HTTP handling, so the expected gain is smaller than the database
share suggests. To be measured in v2, not assumed.

### Query plan analysis (redirect lookup)

`EXPLAIN (ANALYZE, BUFFERS)` of the app's redirect query
(`select … from links where short_code = ?`) on a freshly seeded 10M-row bench database. Raw output:
`benchmarks/results/v0-naive/explain-analyze.txt` (one hand-run session, not part of `make bench`).

| Case | Plan | Pages touched | Execution time |
|---|---|---|---|
| Existing code (`w7eN`) | Index Scan on `uq_links_short_code` | 4 (hit) | 0.060 ms (second run 0.066 ms) |
| Missing code (404 path) | Index Scan on `uq_links_short_code` | 3 (hit) | 0.056 ms |
| Existing code, index/bitmap scans disabled | Parallel Seq Scan (2 workers + leader) | 113,531 (39,475 hit + 74,056 read) | 532 ms |

- `short_code` is already covered by a unique B-tree index (`uq_links_short_code`, 363 MB; heap 887 MB,
  primary key index 214 MB), so there is no missing index to add. The planner picks it on its own: the seed
  ends with `VACUUM ANALYZE`, and `pg_stats` reports `n_distinct = -1` (all unique) for the column.
- An indexed lookup reads 4 pages: 3 B-tree levels plus 1 table page (a miss reads only the 3 index
  pages). Without the index, Postgres reads every page of the table (113,531 × 8 KB ≈ 887 MB) and
  discards 3.33M rows per worker.
- The sequential scan was forced with `SET enable_indexscan/bitmapscan/indexonlyscan = off` in one session
  to show the cost of a missing index; it is not something the application does. Timings vary run to run
  (an earlier hand run gave 0.088 ms and 470 ms), so read the ratio (about four orders of magnitude), not the digits.
- Planning time (about 0.45–0.5 ms) is larger than execution time here. `pg_stat_statements` reports
  execution only, which is why the baseline shows about 0.05 ms per query. Whether the app re-plans the
  statement on every call or reuses a prepared plan was not checked.
- Conclusion: the database lookup is not the cost in v0. A separate "add an index" version would change
  nothing, and the next candidate optimization is the cache (v2).

### Method and environment

- Host: Intel i7-10510U (4 cores / 8 threads), 23 GB RAM, Linux 7.0.0-43, Docker 29.9.2, Compose v2.3.3.
- Containers: Postgres 16.16 (2 CPU, 2 GB, `shared_buffers=512MB`, `shm_size=1g`), app (2 CPU, 1 GB,
  Hikari pool 10, `-XX:MaxRAMPercentage=50`), k6 0.54.0 (2 CPU, 2 GB), all on one machine.
- Data: 10M rows (`links` is 1,465 MB), deterministic seed (`benchmarks/scripts/seed.sql`).
- Load: k6 open model (`constant-arrival-rate`), Zipf(s=1) keys scattered over ids, 100:1 redirects to creates.
- Run with `make bench VERSION=v0-naive RATE=3000` (baseline) and `make bench-saturation VERSION=v0-naive RATE=6000`.

### Caveats

- **An earlier set of runs was discarded.** Compose v2.3.3 silently ignored the `cpus:` key, so the
  first saturation runs had no CPU limits (their knee was about 5,400 req/s). CPU is now set with
  `cpu_quota`, and enforcement was verified with a CPU burn (about 199% for a 2-CPU limit).
- The saturation point comes from one ramp; the baseline from three runs. A single ramp is enough to
  choose a rate, but the "Max RPS" figure should not be read as more precise than about one ramp step.
- Everything runs on one laptop, so the load generator, app and database compete for 8 threads.
