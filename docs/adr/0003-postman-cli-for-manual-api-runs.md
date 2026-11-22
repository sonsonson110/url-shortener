# 0003. Postman CLI for Running the Manual API Collection

**Status:** Accepted  
**Date:** 2026-11-22

## Context

The manual API checks live in the "URL Shortener" Postman collection (22 requests, 5 folders, 13 test scripts) in the owner's personal Postman workspace. The repo does not keep a copy of the collection JSON; the `postman-sync` skill (`.claude/skills/postman-sync/SKILL.md`) keeps the workspace in line with the controllers through the Postman MCP connector.

The connector can create and edit collections but cannot run them. Postman's cloud runners also cannot reach `localhost:8080`. A run therefore has to happen on the developer machine.

AGENTS.md §1 and §3.6 forbid tools outside the fixed stack without an ADR. Postman is not in that stack. This ADR covers only a **local developer tool**. Nothing here changes the app, its dependencies, the Docker images, or the tests run by `make test`.

## Options Considered

### A. Postman desktop app, Collection Runner (manual)
**Pros:** Already installed (snap). No new tool. Visual results.  
**Cons:** Manual clicks; the agent can't run it or read the results, so results reach the repo only if the owner copies them over.

### B. Postman CLI (`postman collection run <collection-uid> -e <env-uid>`)
**Pros:** Official Postman tool. Runs the collection straight from the workspace, so there is no exported JSON to drift. Runs locally, so it reaches the local stack. The agent can run it and report real per-request results. It exits non-zero when a test fails (usable in CI later).  
**Cons:**
- A new binary on the machine (`~/.local/bin/postman`).
- It needs a Postman login.
- By default it uploads run results to Postman Cloud.
- Its installer is a remote shell script that, by default, edits `~/.claude/CLAUDE.md`, `~/.codex/AGENTS.md` and the shell rc file. These defaults are turned off with flags.
- The installer does not check the binary's signature on Linux.

### C. Newman (`npx newman run`)
**Pros:** Open source. No Postman login needed for a file-based run.  
**Cons:** Needs the collection as a file, either an exported JSON in the repo (which drifts from the workspace) or a Postman API key in the URL. Older runner that Postman is moving away from.

### D. A curl/jq scenario script in the repo
**Pros:** No new tool. Fully in the repo.  
**Cons:** A second copy of every request and check, which has to be kept in sync with the collection by hand. Tried as `scripts/scenarios.sh` and dropped by the owner on 2026-11-22.

## Decision

Option B: use the Postman CLI to run the collection against the local stack. `postman-sync` step 6 checks `/actuator/health` first and reports the real pass/fail per request. Option A stays available for ad-hoc runs in the app.


## Consequences

- **Install and login are done by the owner** (via `!` commands), never by the agent:
  - `curl -fsSL https://dl-cli.pstmn.io/install/linux64.sh | sh -s -- --prefix "$HOME/.local" --no-modify-path --skip agent-discovery-hints,agent-plugins --yes`
  - `postman login`
  The API key and session stay in the owner's home directory, never in the repo or `.env`.
- **Runs write real data:** the `POST /api/v1/links` requests create rows in the local database.
- **Results leave the machine:** each run uploads its results (localhost URLs, test payloads) to the owner's Postman workspace. This is acceptable for local test data. Revisit before running against any environment with real data.
- **Postman results don't count as benchmark or test evidence.** They are a manual smoke check. Automated correctness stays in JUnit/Testcontainers (`make test`), and performance numbers stay in k6 (`benchmarks/`).
- **9 of the 22 requests have no test script** (the metrics, info, and Prometheus/Grafana queries). A run checks only that they respond, not what they return.
- **Revisit if:**
  - the CLI is wanted in CI (it would need a non-interactive API key from a secret store);
  - Postman changes the CLI's licensing or login requirements;
  - the collection moves back into the repo, which would make Newman (option C) viable again.

## Evidence

- First CLI run, 2026-11-22, Postman CLI v1.93.0, local stack via Docker Compose (health `UP`, db `UP`, redis `UP`): 22 requests executed, 0 failed; 13 test scripts and 21 assertions, 0 failed; 428 ms total. Raw output was not saved in the repo (it was saved only to a session scratchpad). The run is in the Postman workspace as run `27398777-4ea01227-109a-4bd4-84de-b75d416a4ed3`.
