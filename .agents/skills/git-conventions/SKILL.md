---
name: git-conventions
description: >-
  Enforces this project's git commit message format (Conventional Commits) and
  benchmark git tag conventions (vN-<label> scheme). Use this skill whenever
  the user asks to commit changes, write a commit message, create a git tag,
  or start/finish a benchmark version. Also activate when staging changes after
  completing a roadmap checklist item.
---

# Git Conventions — URL Shortener Portfolio Project

This skill covers two things:
1. **Commit messages** — Conventional Commits format, with extra rules for `perf:` commits.
2. **Benchmark tags** — the `vN-<label>` scheme used to version benchmark runs.

---

## 1. Commit Message Format

Follow [Conventional Commits](https://www.conventionalcommits.org/). Every commit must match:

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

### Types

| Type | When to use |
|------|-------------|
| `feat` | A new endpoint, feature, or behaviour |
| `fix` | A bug fix |
| `perf` | A performance optimisation (must include measured result in body) |
| `test` | Adding or fixing tests only |
| `docs` | Documentation, ADRs, READMEs only |
| `chore` | Build system, dependencies, Makefile, Docker, Flyway migrations |
| `refactor` | Code restructure with no behaviour change |
| `bench` | Benchmark script changes, k6 scenarios, seed scripts |
| `ci` | CI/CD changes |

### Scopes (optional but recommended)

Use the **feature package name** or infrastructure area:

`shortener` · `ratelimit` · `jobs` · `observability` · `db` · `cache` · `docker` · `helm` · `k6` · `adr`

### Subject line rules

- Imperative mood, lowercase, no trailing period.
- Max 72 characters.
- Do not start with a capital letter.
- Do not end with a period.

### Body rules

- Wrap at 72 characters.
- Explain **what** changed and **why**, not how.
- For `perf:` commits: **always** include the before/after numbers from the actual benchmark run. Never fabricate numbers. If numbers aren't available yet, write `perf: TBD — benchmark pending` and note what will be measured.

### Footer rules

- Reference roadmap items: `Roadmap: Phase 1 — schema and migrations`
- Reference ADRs: `ADR: docs/adr/0002-id-generation.md`
- Breaking changes: `BREAKING CHANGE: <description>`

### Examples

```
feat(shortener): add POST /api/v1/links create endpoint

Implements the create endpoint. Validates longUrl with @URL,
generates a base62 short code (stub — real logic in next commit),
persists via LinkRepository, returns 201 with Location header.

Roadmap: Phase 1 — create and redirect endpoints
```

```
perf(shortener): add composite index on short_code

Before: p99 = 240ms @ 500 RPS (sequential scan, 10M rows)
After:  p99 =  18ms @ 500 RPS (index scan)

See benchmarks/results/v1-index/<timestamp>.json for raw k6 output.
Hardware: AMD Ryzen 7, 32 GB RAM, Docker 4-CPU limit.

ADR: docs/adr/0003-index-strategy.md
Roadmap: Phase 1 — benchmark report
```

```
test(shortener): add LinkRepositoryTest with Testcontainers

Covers: findByShortCode hit, miss, and duplicate short_code constraint.
Uses real postgres:16-alpine via Testcontainers — no mocks.
```

```
docs(adr): add 0002-id-generation ADR

Records the decision to use base62 with counter ranges over random
hashing. Includes collision probability analysis and test plan.
```

---

## 2. Benchmark Git Tag Convention

Tags mark each **architecturally distinct version** of the system for reproducible benchmark comparisons. One change per tag. The Makefile `make bench VERSION=<tag>` reads these tags.

### Tag format

```
v<N>-<label>
```

- `N` — zero-padded integer, increments with each new benchmark version.
- `label` — short kebab-case description of the **single change** this version introduces.

### Labelling guide

| Situation | Tag example |
|-----------|-------------|
| Initial, no optimisations | `v0-naive` |
| Added a DB index | `v1-index` |
| Added Redis cache-aside | `v2-redis-cache` |
| Added stampede protection | `v3-stampede-protection` |
| Multi-instance deployment | `v4-multi-instance` |
| Rate limiter added | `v5-rate-limiter` |
| Lua atomic rate limiter | `v6-lua-atomic` |
| Connection pool tuned | `v7-pool-tuning` |
| Read replica added | `v8-read-replica` |

**Rules:**
- One change between consecutive tags. Never skip two optimisations between tags.
- Tag the commit **after** the benchmark result is saved to `benchmarks/results/<tag>/`.
- Tags are annotated (not lightweight) so they carry the benchmark summary.
- Never move or delete a tag once a benchmark result references it.

### How to create a benchmark tag

```bash
# After running: make bench VERSION=v1-index
# and saving results to benchmarks/results/v1-index/<timestamp>.json

git tag -a v1-index -m "v1-index: composite index on short_code

p50=8ms p95=14ms p99=18ms @ 500 RPS, error=0%
Improvement over v0-naive: p99 240ms → 18ms (13x)
Hardware: AMD Ryzen 7, 32GB RAM, 4-CPU Docker limit, 10M seeded rows"
```

### Annotated tag message format

```
<tag>: <one-line description of the change>

p50=<Xms> p95=<Xms> p99=<Xms> @ <Y> RPS, error=<Z>%
Improvement over <prev-tag>: p99 <before> → <after> (<Nx>)
Hardware: <CPU>, <RAM>, <container limits>, <dataset size>
```

If benchmark hasn't run yet (tagging ahead of time), write:
```
<tag>: <one-line description — benchmark pending>

Results: TBD — run `make bench VERSION=<tag>` to populate.
```

---

## 3. Step-by-Step Workflow

### When committing a completed roadmap task

1. Run `git diff --staged` to review what's staged. If nothing is staged, ask the user what to include.
2. Identify the correct **type** and **scope** from section 1.
3. Write a subject line ≤ 72 chars, imperative, lowercase.
4. Write a body explaining what and why (not how).
5. If this closes a roadmap checklist item, add a `Roadmap:` footer.
6. If this creates/updates an ADR, add an `ADR:` footer.
7. Present the full commit message for user review before running `git commit`.
8. Run `git commit -m "..."` only after the user confirms (or says "go ahead").

### When creating a benchmark tag

1. Confirm the benchmark result file exists at `benchmarks/results/<version>/`.
2. Read the p50/p95/p99 and RPS from the result file.
3. Build the annotated tag message from the template in section 2.
4. Present the tag command for review.
5. Run `git tag -a <tag> -m "..."` after confirmation.
6. Remind the user to push tags explicitly: `git push origin <tag>`.

### When asked "what tag should I use?"

1. Run `git tag --sort=version:refname` to list existing tags.
2. Find the highest `vN` prefix.
3. Propose `v<N+1>-<label>` where `<label>` describes the single change being introduced.
4. Confirm with the user before creating the tag.

---

## 4. Quick Reference Card

```
Commit types:  feat fix perf test docs chore refactor bench ci
Scopes:        shortener ratelimit jobs observability db cache docker helm k6 adr

Tag format:    vN-label        (e.g. v0-naive, v1-index, v2-redis-cache)
Tag command:   git tag -a vN-label -m "<message>"
Push tag:      git push origin vN-label

perf: commits MUST include real before/after numbers in the body.
      No numbers = no perf: type. Use feat: or chore: instead.

Never:
  - fabricate benchmark numbers
  - move or delete a tag referenced by a benchmark result
  - bundle two optimisations in one tag/commit
```
