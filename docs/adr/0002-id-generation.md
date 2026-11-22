# 0002. Short Code Generation Strategy

**Status:** Accepted  
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

Option A for the MVP.

A uses Postgres and its id generator (the `links_id_seq` sequence). Multiple instances all ask Postgres for the next unique id. Every time Postgres returns an id, it also increases the id counter, atomically.

B can save a DB round trip to fetch the record id for every link creation, but when an instance crashes, its assigned block is unusable for the entire system. C has the advantage of being random and unguessable, but as the links grow, the chance that a random id collides gets higher, which means more retries and DB hits.

## Consequences

- `LinkService` first calls the DB to get the next id, then base62-encodes it into the short code, then makes a second DB round trip to save the record. To do this, the `Link` entity implements `Persistable` with an `isNew` flag that defaults to true on creation, so Spring Data skips the check for id existence when we provide the id on save. After saving, `isNew` is set to false.
- It is very easy for others to guess people's codes, because the short code is generated from an incremental number. This would matter if someone wants to mess with the server or take people's data.
- If an insert fails, the id stays unused. This is trivial, as the number keeps growing.

## Evidence

- `ConcurrentCreateTest` (32 threads x 50 creates = 1600 links, single JVM, real Postgres 16): all 1600 codes distinct and each resolves to its own URL. Passed in 4 consecutive runs (1 in the full suite, 3 standalone), 2026-11-21. Single-JVM only; says nothing yet about multiple instances.
- Week-10 3-instance collision test: TBD
