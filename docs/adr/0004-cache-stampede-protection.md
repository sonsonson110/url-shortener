# 0004. Cache Stampede Protection for Redirect Cache

**Status:** Proposed (draft from the owner's stated reasoning; becomes Accepted once the owner confirms the Decision and Consequences in their own words)

**Date:** 2026-11-25

## Context

v2 adds a Redis cache-aside layer in front of the redirect lookup (`GET /{code}`), TTL 60 s. Key popularity is Zipf(s=1), so a few keys take a large share of traffic (the top key gets about 6% of requests, roughly 180 req/s at 3000 req/s). When a hot key's entry expires, every request that arrives before the first reload finishes also misses and queries Postgres for the same row. That burst is a cache stampede.

Constraints:

- The baseline shows the database is cheap per lookup (about 0.05 ms) and at about 47% CPU at 3000 req/s, so a stampede may not hurt much. The value of protection here is mainly bounded DB load and a defensible design, and it must be measured, not assumed.
- Links can change (delete, expiry, and possibly update later). The cache should not keep serving a changed or removed link longer than necessary.
- Request threads are limited (Tomcat default 200 unless configured). Anything that makes requests wait must be short, or a hot key could block the whole app.

## Options Considered

### A. Per-key lock (single-flight)

On a miss, one request takes a short-lived lock for that key and loads from Postgres. Others wait briefly and re-read the cache.

**Pros:** One DB load per key per expiry; deterministic; the cached value stays a plain value; no staleness beyond the TTL.

**Cons:** Waiting requests see extra latency and hold a thread; the lock needs its own TTL in case the holder dies; one more Redis round trip on a miss.

### B. Probabilistic early refresh (XFetch)

Each hit may refresh the entry before expiry, with a probability that rises as expiry nears.

**Pros:** No waiting, no lock.

**Cons:** The cached value must carry load time and load cost metadata, which makes it larger; it is probabilistic, so there is no guarantee that only one request reloads.

### C. Serve-stale-while-refreshing

Keep a soft and a hard TTL; after the soft TTL, return the old value and refresh in the background.

**Pros:** No one waits; one reload.

**Cons:** Changed or deleted links can be served for up to the hard TTL; usually still needs a lock to make the refresh single.

## Decision

Option A, a per-key lock.

Owner's reasoning:

- Over C: when a link is updated or deleted, the cache should stay up to date, and serving stale values until a hard TTL works against that.
- Over B: it can bloat the cached value, and it is not deterministic.

Parameters (initial values, to be checked by benchmark):

| Parameter | Value | Why |
|---|---|---|
| Lock TTL | 2 s | Safety net only. The reload takes about 0.05 ms, so a live holder releases in milliseconds. The TTL matters only if the holder crashes. |
| Loser poll interval | 10 ms | A loser returns as soon as the winner fills the cache. |
| Loser wait cap | 200 ms | After this, the loser queries Postgres itself. The lookup is cheap, so a fall-through costs little. |

A fixed wait of 3 s (lock 2 s, worst case 5 s) was considered and rejected. The winner fills the cache in milliseconds, so a 3 s sleep is about 1000 times longer than needed. With about 180 req/s on the top key, roughly 540 threads would be asleep at once, more than the default 200 request threads, which would stall every key, not only the hot one. The "5 s is a common UX limit" argument did not hold: the usual response-time references are 0.1 s, 1 s and 10 s, and a redirect is expected to be near-instant.

## Consequences

Draft, to be confirmed or rewritten by the owner:

- A request that loses the lock waits at most 200 ms, then goes to Postgres. Worst-case extra latency on a miss is about 0.2 s, and only for requests that arrive during a reload of the same key.
- If the lock holder crashes, the lock expires after 2 s. Losers never wait that long because they give up after 200 ms.
- When a loser falls through, several requests may query Postgres for the same key. This only happens if the winner is slow, and it is bounded by the wait cap.
- The lock controls who reloads. It does not make the cache fresh after an update or delete; that needs explicit invalidation (delete the cache key on write) on top of the 60 s TTL. Today the API has no update or delete endpoint, so only expiry (410) applies.
- Each miss costs extra Redis calls (try to take the lock, then release it).
- Behavior when Redis is down (fail open to Postgres, or fail closed) is a separate decision, to be recorded in its own ADR.
- Revisit if the benchmark shows lock contention in the tail latency, or if Postgres shows no stampede at all without the lock (then the lock may not be worth its cost).

## Evidence

None yet. To be filled from the `v2-redis-cache` runs: number of DB loads per key per expiry, p99 with and without the lock, request thread usage on the hot key, and Postgres CPU against the prediction in `benchmarks/predictions/v2-redis-cache.md`.
