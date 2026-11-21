---
name: quiz-sync
description: Sync the phone quiz with the repo. `ingest` applies pasted QUIZ-LOG blocks to docs/learning-log.md; `refresh` drafts new question-bank entries from commits since the last refresh. Use when the owner pastes a QUIZ-LOG block or asks to update the quiz bank.
---

# quiz-sync

Workflow overview: `docs/quiz/README.md`. Quiz rules and the QUIZ-LOG format: `docs/quiz/project-instructions.md`.

## Mode: `ingest` (default if the input contains `QUIZ-LOG`)

Input: one or more blocks like:

```
QUIZ-LOG 2026-11-22
JPA-05 | partial | box 1->1 | re-ask 2026-11-23 | answer: ... | feedback: ...
END
```

Steps:

1. Parse every line. If a line is malformed, stop and show it to the owner. Don't guess.
2. Process blocks in date order. If the same ID appears more than once, the later date wins.
3. For each ID, find its entry in `docs/learning-log.md` under **Open questions**:
   - `LL-Qn` maps to the existing `### Qn:` entry.
   - Bank IDs (`HLD-01`, `JPA-05`, …) map to `### <ID>: <short title>`. Create it if missing, using the title and question from `docs/quiz/question-bank.md` and `Asked:` = the block date.
4. Update `Box`, `Answer` (the owner's words from the log; never rewrite or improve them), `Verdict`, `Feedback`, `Re-ask`. Keep earlier attempts as one line each under `History:` (`- <date>: <verdict>`).
5. `re-ask RETIRED` means: move the entry to **Retired**, with the retire date.
6. Check the schedule matches the Leitner rule (1/3/7/14/30 days by box; ✅ up, 🟡 stay, ❌ to box 1). If a line disagrees, fix it to the rule and tell the owner.
7. If the feedback says `STALE`, open the cited source, then propose a fix to the bank question. Don't apply it silently.
8. Show a short summary (ingested count, retired, now due) and the `git diff --stat`. Don't commit unless asked. If asked, use the commit message `docs(quiz): sync quiz results <date range>` (see `.agents/skills/git-conventions/SKILL.md`).

## Mode: `refresh`

1. Read the `Last refreshed against commit:` marker in `docs/quiz/question-bank.md`.
2. `git log --oneline <marker>..HEAD` plus the diffs of feature/fix/docs commits. Skip pure formatting.
3. For each newly taught piece (new ADR, endpoint, critical piece from AGENTS.md §2.1, failure test, benchmark), draft 2–4 questions in the bank format. Mix types and include a `two-instances` question for anything concurrent.
4. Grading points must quote or paraphrase the repo, with a `Source:`. Only use benchmark numbers that exist in `benchmarks/results/` or `benchmarks/REPORT.md`.
5. Check whether any existing question's source changed meaning. If so, propose an edit.
6. Show the drafts to the owner first. Only after approval, append them under the right topic heading (or a new one, e.g. `CACHE`), using the next free ID, and update the marker to the current `HEAD`.
7. Remind the owner: commit, push, then press **Sync** in the Claude Project.
