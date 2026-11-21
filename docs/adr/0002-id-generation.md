# 0002. Short Code Generation Strategy

**Status:** Proposed (owner to confirm)  
**Date:** 2026-11-21

## Context

Every link needs a short, unique `short_code`. The service will run as several instances (week 10), so generation must stay collision-free across them. The `links.short_code` column is `NOT NULL` with a `UNIQUE` constraint.

## Options Considered

### A. Base62 of a Postgres sequence value (chosen for the MVP)
Fetch `nextval('links_id_seq')`, base62-encode it, insert the row once.  
**Pros:** Simplest; Postgres owns the counter, so collision-free by construction across instances; one write per create.  
**Cons:** Sequential, therefore guessable; one extra DB round trip per create.

### B. Counter ranges per instance
Each instance leases a block of ids (for example 1000) and hands them out from memory.  
**Pros:** Fewer DB round trips.  
**Cons:** Needs a lease mechanism; a crash wastes the unused part of a block; more moving parts. Planned as the week-10 upgrade.

### C. Random code with retry
Generate a random 7-character base62 code, retry on a UNIQUE violation.  
**Pros:** Not guessable.  
**Cons:** Collision probability grows with table size; needs a retry loop; harder to prove "zero collisions".

## Decision

Option A for the MVP. Option B is revisited in week 10 when the multi-instance deployment makes the extra round trip measurable.

## Consequences

- `Link` gets an assigned id (no `IDENTITY`) and implements `Persistable` to avoid an extra SELECT on save.
- Codes are guessable; revisit if private links or accounts are ever added.
- Sequence gaps are expected and harmless.

## Evidence

- `ConcurrentCreateTest` (32 threads x 50 creates = 1600 links, single JVM, real Postgres 16): all 1600 codes distinct and each resolves to its own URL. Passed in 4 consecutive runs (1 in the full suite, 3 standalone), 2026-11-21. Single-JVM only; says nothing yet about multiple instances.
- Week-10 3-instance collision test: TBD
