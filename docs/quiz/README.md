# Daily Review on Your Phone

This is a spaced-repetition quiz over everything this repo has taught: design, code, JPA/Spring, testing and observability. You take it in the **Claude mobile app** through a Claude Project. The repo stays the source of truth.

```text
 repo (GitHub)                       Claude Project (phone)            Claude Code (laptop)
 ─────────────                       ──────────────────────            ────────────────────
 question-bank.md  ──sync──▶  knowledge     "quiz me"  ──▶ QUIZ-LOG ──paste──▶ /quiz-sync ingest
 learning-log.md   ──sync──▶  knowledge                                         │
        ▲                                                                       │
        └──────────────────── updates learning-log.md ◀─────────────────────────┘
```

| File | Purpose |
| --- | --- |
| `question-bank.md` | Questions with grading points, grouped by topic (`HLD`, `ID`, `CODE`, `JPA`, `FND`, `TEST`, `OBS`) |
| `project-instructions.md` | Custom instructions for the Claude Project (quiz rules, Leitner schedule, `QUIZ-LOG` format) |
| `../learning-log.md` | Scheduling state: box, verdict and re-ask date per question |
| `../../.claude/skills/quiz-sync/SKILL.md` | Claude Code skill: ingest results and refresh the bank after new work |

## One-time setup (about 10 minutes, on claude.ai in a browser)

1. Go to claude.ai → **Projects** → **Create project** and name it `URL Shortener Review`.
2. Open **Instructions**, then paste everything below the line in `docs/quiz/project-instructions.md`.
3. Under **Project knowledge**, add the repo content.
   - **Preferred:** **Add content → GitHub**, connect `sonsonson110/url-shortener`, and select:
     - `docs/` (all of it), `roadmap.md`, `AGENTS.md`
     - `src/main/java/com/urlshortener/shortener/`
     - `src/main/resources/application.yml`, `src/main/resources/db/migration/`
     - `src/test/java/com/urlshortener/` (the jpademo and shortener tests are quiz material)
     - `deploy/grafana/provisioning/datasources/datasources.yml`
   - **Fallback:** run `/send-to-project` in Claude Code and pick these files.
4. Open the Claude app on your phone. The project shows up under Projects.

GitHub content in a Project is a snapshot. After you push new commits, press **Sync** on the GitHub source in the project's knowledge panel. Otherwise the phone quizzes against old code.

## Daily loop (5 questions, about 10 minutes)

1. A 07:30 phone reminder (set it in your phone's Reminders or Clock app) says "quiz me".
2. Open **URL Shortener Review** in the Claude app and type `quiz me`.
   - Variants: `quiz me 3`, `quiz me on jpa`, `what's due`, `hint`, `skip`, `explain JPA-05`.
3. Answer in your own words. Short is fine; voice dictation works well.
4. At the end, Claude prints a `QUIZ-LOG … END` block. Leave it in the chat (or copy it to a note).

## Sync back (whenever you're at the laptop, e.g. weekly)

1. Copy the `QUIZ-LOG` blocks from the phone chats.
2. In Claude Code, in this repo, run `/quiz-sync ingest` and paste the blocks.
3. Review the `docs/learning-log.md` diff, commit, push, then press **Sync** in the Claude Project.

Unsynced phone sessions don't break anything. The project just doesn't know your latest boxes yet, so it may re-ask something you already nailed.

## Adding questions after new work

After finishing a feature (e.g. Redis cache-aside), run `/quiz-sync refresh` in Claude Code. It reads commits since the bank's "Last refreshed" marker and drafts new questions. You review them, then commit, push and press **Sync**.
