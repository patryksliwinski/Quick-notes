# Workflow

How work happens in this repo: branches, PRs, pipelines, reviews, and the self-improving docs loop.

## Branches

- `main` — default branch, always buildable, `assembleDebug` + `testDebugUnitTest` green.
- Task branches: `vibe/<short-slug>` (agent) or `feat/<slug>` / `fix/<slug>` (human). Short-lived; delete after merge.
- Never push directly to `main`; everything goes through a PR.

## Pull requests

1. Open a PR (draft while iterating). Fill in `.github/PULL_REQUEST_TEMPLATE.md` completely — including the **Lessons** section.
2. The `pr-review` pipeline runs automatically (see `.github/workflows/pr-review.yml`): build, unit tests, lint, and an automated review pass that checks the PR against `AGENTS.md` golden rules and leaves a comment with findings.
3. A human reviews the code and the checklist in the PR template.
4. Merge only when: CI green, automated review has no blocking findings, human approval, and the Lessons section is filled.

## The self-improving docs loop (LESSONS.md)

`docs/LESSONS.md` is the repo's memory. It is updated **in the same PR** that introduces the change it describes.

Rules:

- One entry per lesson, newest at top, format:
  ```markdown
  ### YYYY-MM-DD — <short title>
  - **Context:** what task/PR prompted this
  - **Lesson:** the takeaway in one or two sentences
  - **Rule now:** the concrete rule added to AGENTS.md / this doc / code (if any)
  ```
- A lesson is anything that would have changed the outcome if known earlier: a perf trap, an API gotcha, a UX decision, a CI failure, a review finding.
- If a PR adds a rule to `AGENTS.md`, it must also add a LESSONS.md entry explaining why.
- Periodically (every ~10 entries), fold recurring lessons into `AGENTS.md` / `docs/WORKFLOW.md` / `docs/ARCHITECTURE.md` and mark the folded entries as `(folded)`.

## Pipelines (`.github/workflows/`)

| Workflow | Trigger | Does |
|---|---|---|
| `ci.yml` | push to `main`, PRs | `testDebugUnitTest`, `assembleDebug`, `lintDebug`; uploads APK artifact on PRs |
| `pr-review.yml` | PR opened/synchronized | runs CI checks, then an automated review that posts a comment: checklist vs `AGENTS.md`, test coverage of changed files, lessons-section presence, forbidden patterns (sync-specific code, main-thread I/O) |

## Test requirements per PR

- `testDebugUnitTest` green; new pure logic gets unit tests in `app/src/test/`.
- Storage/SAF behavior: instrumented test or explicit note why not; CI skips emulator tests unless the `ci.yml` emulator job is enabled.
- `lintDebug` adds no new warnings.

## Code review checklist (human)

Mirror of the PR template: quick-path purity, no main-thread I/O, optimistic UI, sync-agnostic storage, strings externalized, tests added, docs/LESSONS updated.
