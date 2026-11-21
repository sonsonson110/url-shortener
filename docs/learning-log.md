# Learning Log

Record of the quiz questions for interview-critical pieces (see `AGENTS.md` section 2.1). The owner writes the answers in their own words. The agent grades honestly and never fills in an answer on the owner's behalf.

## How to use

1. The agent adds a question under **Open questions** with a date and the piece it belongs to.
2. The owner answers in the `Answer` field, before seeing any reference answer.
3. The agent grades it: `correct`, `partial`, or `wrong`, with a short explanation of what was missing.
4. Set `Re-ask` to about 7 days after the answer. Anything graded `partial` or `wrong` is re-asked at the next session start (see `AGENTS.md` section 12).
5. After two consecutive `correct` answers, move the entry to **Retired**.

Entry format:

```text
### Q<n>: <short title>
- Piece: <file or ADR>
- Asked: YYYY-MM-DD
- Question: <text>
- Answer: <owner's words, or "unanswered">
- Verdict: <correct | partial | wrong | pending>
- Feedback: <what was right, what was missing>
- Re-ask: <YYYY-MM-DD or "after first answer">
```

## Open questions

### Q1: Why is the sequence safe across instances?
- Piece: `docs/adr/0002-id-generation.md`, `LinkService.create`
- Asked: 2026-11-21
- Question: Each instance calls `nextval` and base62-encodes the result. Why can two instances never produce the same code? `ConcurrentCreateTest` runs 32 threads in one JVM: what does it prove, and what would a multi-instance test prove that it does not?
- Answer: unanswered
- Verdict: pending
- Feedback:
- Re-ask: after first answer

### Q2: What happens to the sequence value when the insert rolls back?
- Piece: `LinkService.create`, `LinkRepository.nextId`
- Asked: 2026-11-21
- Question: If `save` fails after `nextId`, is the consumed value reused? Why does ADR 0002 call the resulting gaps "harmless", and when would they stop being harmless?
- Answer: unanswered
- Verdict: pending
- Feedback:
- Re-ask: after first answer

### Q3: Find the bug in counter placement
- Piece: `LinkService.create` (`createdCounter.increment()` inside `@Transactional`)
- Asked: 2026-11-21
- Question: The counter increments before the transaction commits. What goes wrong if the commit fails? What would you change, and which test would catch it?
- Answer: unanswered
- Verdict: pending
- Feedback:
- Re-ask: after first answer

### Q4: Expiry boundary
- Piece: `LinkService.resolve`
- Asked: 2026-11-21
- Question: A link is treated as expired at the exact expiry instant (`!isAfter(now)`). Is that the right choice? Which test pins the behavior, and what would you add if none does?
- Answer: unanswered
- Verdict: pending
- Feedback:
- Re-ask: after first answer

### Q5: Guessable codes
- Piece: `docs/adr/0002-id-generation.md`
- Asked: 2026-11-21
- Question: Sequential ids make codes enumerable. Who can exploit that, and what would you change (and what would it cost) when it stops being acceptable?
- Answer: unanswered
- Verdict: pending
- Feedback:
- Re-ask: after first answer

## Retired

None yet.

## Pending owner work

- [ ] Write the Decision and Consequences sections of ADR 0002 in your own words, with the current draft hidden, then change its status from `Proposed` to `Accepted`.
