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
- Answer: unanswered
- Verdict: pending
- Feedback:
- Box: 1
- Re-ask: after first answer

### Q2: What happens to the sequence value when the insert rolls back?
- Piece: `LinkService.create`, `LinkRepository.nextId`
- Asked: 2026-11-21
- Question: If `save` fails after `nextId`, is the consumed value reused? Why does ADR 0002 call the resulting gaps "harmless", and when would they stop being harmless?
- Answer: unanswered
- Verdict: pending
- Feedback:
- Box: 1
- Re-ask: after first answer

### Q3: Find the bug in counter placement
- Piece: `LinkService.create` (`createdCounter.increment()` inside `@Transactional`)
- Asked: 2026-11-21
- Question: The counter increments before the transaction commits. What goes wrong if the commit fails? What would you change, and which test would catch it?
- Answer: Counter still increments if commit fails; remove @Transactional from create, call a separate transactional method, increment after it completes
- Verdict: partial
- Feedback: Self-invocation bypasses the proxy; use another bean or TransactionTemplate; no test named.
- Box: 1
- Re-ask: 2026-11-23

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
- Answer: unanswered
- Verdict: pending
- Feedback:
- Box: 1
- Re-ask: after first answer

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
- Answer: Database breaks first under load; roadmap plans caching for the hot path
- Verdict: partial
- Feedback: Missing connection-pool saturation, Redis cache-aside, stampede protection, k6 benchmarks.
- Box: 1
- Re-ask: 2026-11-23

### CODE-04: Why a regex on the redirect mapping
- Piece: `RedirectController.java`
- Asked: 2026-11-22
- Question: see question bank
- Answer: Code is base62 so only [0-9A-Za-z]; {1,12} enforces at least one character and a maximum length
- Verdict: partial
- Feedback: Missed main reason: avoid shadowing root paths like /actuator, /api, favicon.ico.
- Box: 1
- Re-ask: 2026-11-23

### OBS-04: Scraped metric names
- Piece: `LinkService.java`
- Asked: 2026-11-22
- Question: see question bank
- Answer: Said a counter, rate per minute; didn't know the scraped metric names
- Verdict: wrong
- Feedback: Names are links_created_total and links_redirects_total{outcome}; dots become underscores, _total added.
- Box: 1
- Re-ask: 2026-11-23

## Retired

None yet.

## Pending owner work

- [ ] Write the Decision and Consequences sections of ADR 0002 in your own words, with the current draft hidden, then change its status from `Proposed` to `Accepted`.
