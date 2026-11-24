# Prediction: v2-redis-cache (written before any implementation)

Owner's prediction, recorded 2026-11-24 against the `v0-naive` baseline (median of 3 runs at 3000
redirects/s, 10M rows, 1 app instance). The text under "Owner's prediction" is the owner's own words.

## Owner's prediction

Change: Redis cache-aside with a TTL of 60 s (owner's choice), 1 app instance.

- p99: improve to a steady 3 ms
- hit rate: about 40% with cache-aside TTL
- Postgres CPU: 25-30%
- app CPU: stays at the cap, 95-100%
- knee: 5000 rps

Earlier reasoning (owner): the app is the main bottleneck for handling the request and serializing the
response; the database is cheap.

## Baseline to compare against (from benchmarks/REPORT.md)

| Metric | v0-naive |
|---|---|
| redirect p99 at 3000 req/s | 5.40 ms median (3.64-6.08) |
| max throughput / knee | about 4,450 req/s (p99 under 200 ms up to about 3,900) |
| app CPU at 3000 req/s | 93-100% (docker stats; 1 CPU = 100%, cap 200%) |
| Postgres CPU at 3000 req/s | 45-48% (same units) |

## Open items before running

- TTL: 60 s, chosen by the owner after the prediction was written. The model in the agent notes estimates about
  a 59% hit rate at 60 s, above the predicted 40%; the owner has not revised the numbers (see below).
- The load point for "app CPU 95-100%" needs stating: at 3000 req/s that is the same as the baseline.

## Agent's notes (not part of the prediction)

- Internal consistency: with a 40% hit rate, Postgres CPU falls roughly in proportion to the queries it
  no longer serves, 47% x 0.6 = about 28%, which matches "25-30%".
- Knee 5000 (+12%) implies the database path (Hibernate/JDBC) is roughly a quarter to a third of the
  app's CPU per request and that a Redis call costs less than that. Not measured yet; a CPU profile of
  the app at 3000 req/s would test it.
- Hit rate depends mainly on the TTL. Estimate for Zipf(s=1) over 10M keys at 3000 req/s, TTL cache
  without refresh, unbounded memory, warm cache (rough model, to be checked against the run):

  | TTL | estimated hit rate |
  |---|---|
  | 5 s | 0.44 |
  | 30 s | 0.55 |
  | 60 s | 0.59 |
  | 300 s | 0.69 |
  | 3600 s | 0.83 |

  So a 40% hit rate corresponds to a TTL of a few seconds.
- p99 "steady 3 ms" is the weakest part: the stalls seen in the baseline (10-second windows with a p99
  of 20-90 ms) have no identified cause, and about 60% of requests would still go to Postgres.
