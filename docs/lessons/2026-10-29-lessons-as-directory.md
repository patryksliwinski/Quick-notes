# 2026-10-29 — Lessons live in a directory, not one big file
- **Context:** Repo workflow setup (PR #1) — the lessons log was a single `docs/LESSONS.md`, which would grow unbounded.
- **Lesson:** One file per lesson under `docs/lessons/` with an index (`docs/lessons/README.md`) scales better: no merge conflicts on the log, each lesson is linkable, and folding into AGENTS.md is a per-file operation.
- **Rule now:** New lessons are `docs/lessons/YYYY-MM-DD-short-slug.md` + one index row; `docs/WORKFLOW.md`, `AGENTS.md`, the PR template, and the `pr-review.yml` scan all reference `docs/lessons/`.
