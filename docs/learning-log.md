# Learning Log

Record of the quiz questions for interview-critical pieces (see `AGENTS.md` section 2.1). The owner writes the answers in their own words. The agent grades honestly and never fills in an answer on the owner's behalf.

## How to use

1. The agent adds a question under **Open questions** with a date and the piece it belongs to.
2. The owner answers in the `Answer` field, before seeing any reference answer.
3. The agent grades it: `correct`, `partial`, or `wrong`, with a short explanation of what was missing.
4. Schedule with Leitner boxes. Intervals: box 1 = 1 day, 2 = 3 days, 3 = 7 days, 4 = 14 days, 5 = 30 days. `correct` moves up one box, `partial` stays, `wrong` goes back to box 1. `Re-ask` = answer date + the new box's interval. Anything due or graded `partial`/`wrong` is re-asked at the next session start (see `AGENTS.md` section 12) or in the phone quiz.
5. A `correct` answer while in box 5 retires the entry: move it to **Retired**.

Questions come from two places: the session quizzes below (`Q<n>`, called `LL-Q<n>` in the phone quiz) and the question bank (`docs/quiz/question-bank.md`, IDs like `JPA-05`). Phone results are synced in with `/quiz-sync ingest` (see `docs/quiz/README.md`).

Entry format:

```text
### Q<n> or <BANK-ID>: <short title>
- Piece: <file or ADR>
- Asked: YYYY-MM-DD
- Question: <text, or "see question bank">
- Answer: <owner's words, or "unanswered">
- Verdict: <correct | partial | wrong | pending>
- Feedback: <what was right, what was missing>
- Box: <1-5>
- Re-ask: <YYYY-MM-DD or "after first answer">
- History: <one line per earlier attempt: "- YYYY-MM-DD: verdict"; omit if none>
```

## Open questions

### Q1: Why is the sequence safe across instances?
- Piece: `docs/adr/0002-id-generation.md`, `LinkService.create`
- Asked: 2026-11-21
- Question: Each instance calls `nextval` and base62-encodes the result. Why can two instances never produce the same code? `ConcurrentCreateTest` runs 32 threads in one JVM: what does it prove, and what would a multi-instance test prove that it does not?
- Answer: nextval returns the next number from Postgres and increments its counter atomically; the test proves concurrent calls never produce duplicate ids.
- Verdict: partial
- Feedback: Missing base62 one-to-one; test is single-JVM evidence; multi-instance test (separate JVMs and pools) not addressed.
- Box: 1
- Re-ask: 2026-11-24
- History:
  - 2026-11-22: partial

### Q2: What happens to the sequence value when the insert rolls back?
- Piece: `LinkService.create`, `LinkRepository.nextId`
- Asked: 2026-11-21
- Question: If `save` fails after `nextId`, is the consumed value reused? Why does ADR 0002 call the resulting gaps "harmless", and when would they stop being harmless?
- Answer: if fail, the id stay unused. it's trivial, as the number would keep growing (2026-11-22, written for ADR 0002)
- Verdict: partial
- Feedback: Correct: nextval is not rolled back, and codes need uniqueness, not contiguity. Missing: when gaps would stop being harmless (e.g. anything relying on contiguous ids, such as counting links by max id).
- Box: 1
- Re-ask: 2026-11-23

### Q3: Find the bug in counter placement
- Piece: `LinkService.create` (`createdCounter.increment()` inside `@Transactional`)
- Asked: 2026-11-21
- Question: The counter increments before the transaction commits. What goes wrong if the commit fails? What would you change, and which test would catch it?
- Answer: Counter still increments when the transaction fails. Use TransactionSynchronization so it increments only after save succeeds. A test for duplicate id would catch it.
- Verdict: partial
- Feedback: STALE: source already uses afterCommit. Fix right; test vague. Name LinkServiceCounterTest: force commit-time failure, assert counter unchanged.
- Box: 1
- Re-ask: 2026-11-24
- History:
  - 2026-11-22: partial

### Q4: Expiry boundary
- Piece: `LinkService.resolve`
- Asked: 2026-11-21
- Question: A link is treated as expired at the exact expiry instant (`!isAfter(now)`). Is that the right choice? Which test pins the behavior, and what would you add if none does?
- Answer: unanswered
- Verdict: pending
- Feedback:
- Box: 1
- Re-ask: after first answer

### Q5: Guessable codes
- Piece: `docs/adr/0002-id-generation.md`
- Asked: 2026-11-21
- Question: Sequential ids make codes enumerable. Who can exploit that, and what would you change (and what would it cost) when it stops being acceptable?
- Answer: very easy for others to guess people's code, as shortcode is generated from an incremental number. this would matter if someone want to mess with the server, or take ppl data (i think) (2026-11-22, written for ADR 0002)
- Verdict: partial
- Feedback: Correct that codes are enumerable; the real risk is harvesting private long URLs (shared docs, URLs with tokens), not server load, which is a rate-limit concern. Missing: what you would change and what it would cost.
- Box: 1
- Re-ask: 2026-11-23

### HLD-05: 302 vs 301 for redirects
- Piece: `docs/phase1-mvp-architecture.md` §5, `RedirectController.java`
- Asked: 2026-11-22
- Question: see question bank
- Answer: 302 isn't cached by the browser, so every click hits the server for expiry checks and analytics
- Verdict: correct
- Feedback: Correct; also say 301 is cached, so expired or edited links keep redirecting.
- Box: 2
- Re-ask: 2026-11-25

### HLD-07: What breaks first at 100:1 reads without a cache
- Piece: `docs/phase1-mvp-architecture.md` §7, `roadmap.md` §6
- Asked: 2026-11-22
- Question: see question bank
- Answer: DB load breaks first (cpu, ram), then finite shared connection pool; later requests wait, may timeout. Plan: Redis distributed cache for all instances.
- Verdict: partial
- Feedback: Missed cache-aside with TTL, stampede protection, k6 before/after benchmarks; repeat gap from last attempt.
- Box: 1
- Re-ask: 2026-11-24
- History:
  - 2026-11-22: partial

### CODE-04: Why a regex on the redirect mapping
- Piece: `RedirectController.java`
- Asked: 2026-11-22
- Question: see question bank
- Answer: Avoids conflict with other root paths like /api or /health; the regex enables strict path resolve.
- Verdict: partial
- Feedback: Main reason right; missed dotted files (favicon.ico) and valid base62 up to column length (12).
- Box: 1
- Re-ask: 2026-11-24
- History:
  - 2026-11-22: partial

### OBS-04: Scraped metric names
- Piece: `LinkService.java`
- Asked: 2026-11-22
- Question: see question bank
- Answer: link_created_total and link_redirects_total
- Verdict: partial
- Feedback: Right mechanism; actual names are links_total and links_redirects_total{outcome}; _created stripped, plural links, outcome tag missed.
- Box: 1
- Re-ask: 2026-11-24
- History:
  - 2026-11-22: wrong

## Retired

None yet.

## Pending owner work

- [x] Write the Decision and Consequences sections of ADR 0002 in your own words, with the current draft hidden, then change its status from `Proposed` to `Accepted`. Done 2026-11-22.
