# Workflow

How work happens in this repo: branches, PRs, pipelines, reviews, and the self-improving docs loop.

## Branches

- `main` — default branch, always buildable, `assembleDebug` + `testDebugUnitTest` green.
- Task branches: `vibe/<short-slug>` (agent) or `feat/<slug>` / `fix/<slug>` (human). Short-lived; delete after merge.
- Never push directly to `main`; everything goes through a PR.

## Pull requests

1. Open a PR (draft while iterating). Fill in `.github/PULL_REQUEST_TEMPLATE.md`: **What & why**, which verification commands you ran, and the golden rules a machine can't check.
2. The `CI` pipeline (`.github/workflows/pr.yml`) runs automatically in one workflow with two jobs on PR opened/synchronized (not on title/description edits): `build-and-test` runs the Gradle checks once (`testDebugUnitTest`, `:app:koverVerifyDebug`, `lintDebug`, `assembleDebug`) on pushes to `main` and PRs, and — on PRs only, after the build job — `automated-review` does a fast static golden-rule grep and posts a comment. The old Copilot review-request step was removed — across all 25 closed PRs it never produced a single review, so it only burned an API call per push (see `docs/lessons/2026-10-30-copilot-review-request-never-produced-reviews.md`). A Mistral agent can additionally review the diff and post a formal review. See `.github/workflows/pr.yml`.
3. The implementing agent reviews its own work **after CI on the final head is green**: run `gh pr checks --watch` (or wait for the `build-and-test` job of the current head), then read your own diff together with the CI/lint/coverage results against `AGENTS.md` and the PR-template checklist, push the review as comments on the PR, then address and fix those comments in follow-up commits. Re-run CI on the new head after fixing.
4. Merge only when: the **final head's** CI run is green (verify with `gh pr checks <pr> --watch`), automated review has no blocking findings, and the agent's self-review comments are resolved. (A `docs/lessons/` entry is expected only when the PR taught something non-obvious.)

## The self-improving docs loop (docs/lessons/)

`docs/lessons/` is the repo's memory. It is updated **in the same PR** that introduces the change it describes — but only for lessons worth keeping (see below).

Rules:

- One **file** per lesson: `docs/lessons/YYYY-MM-DD-short-slug.md`, linked from the index in `docs/lessons/README.md`. File format:
  ```markdown
  # YYYY-MM-DD — Short title
  - **Context:** what task/PR prompted this
  - **Lesson:** the takeaway in one or two sentences
  - **Rule now:** the concrete rule added to AGENTS.md / docs / code (if any)
  ```
- **Write a lesson only when it is non-obvious and would have changed the outcome if known earlier**: a perf trap, an API gotcha, a UX decision, a CI failure, a review finding, a decision you'd make differently. Never one per PR to tick a box — a required-per-PR artifact becomes noise and buries the valuable entries (see `docs/lessons/2026-10-30-lessons-only-when-non-obvious.md`).
- If a PR adds or changes a rule in `AGENTS.md`, it must also add a `docs/lessons/` file explaining why.
- Keep the log small and findable: the index in `docs/lessons/README.md` is grouped **by topic** (storage/SAF, widgets, CI/release, process, app behavior), newest first within each group, with a one-line takeaway per row. If the log grows past ~40 entries, delete or fold noise entries instead of letting it sprawl — fold recurring lessons into `AGENTS.md` / `docs/WORKFLOW.md` / `docs/ARCHITECTURE.md` and mark the lesson file `(folded)` in the index.

## Pipelines (`.github/workflows/`)

| Workflow | Trigger | Does |
|---|---|---|
| `pr.yml` (`CI`) | push to `main`, PRs (opened/synchronized; not `edited` — title/body edits don't change code) | One workflow, two jobs. `build-and-test`: `gradle/actions/setup-gradle@v4` (caches `~/.gradle/caches` + the wrapper dist), then `testDebugUnitTest`, `:app:koverVerifyDebug` (fails below 80% line/branch), `lintDebug`, `assembleDebug`; uploads APK + Kover coverage report + test-result artifacts. `automated-review` (PRs only, `needs: build-and-test`): golden-rule grep against the diff, posts the automated-review comment (reporting the build job's result, no rebuild). Copilot review request removed — it never produced reviews (issue #49). Layer 3 (agent): a Mistral agent reviews the diff and posts a formal review via the GitHub App; the implementing agent self-reviews and fixes its own comments |
| `release.yml` | tag `v*` pushed, or manual `workflow_dispatch` | Same `gradle/actions/setup-gradle@v4` caching, then runs `testDebugUnitTest`, builds `assembleRelease`, signs the APK with an ephemeral CI-generated keystore (`keytool` + `apksigner`), verifies the signature, and publishes `app-release.apk` to a GitHub Release for the tag. Release notes are generated from the commits since the previous tag (`git log prev..HEAD`, merge commits condensed to `(#N) title`) and attached via `--notes-file`, so every release always carries notes (`contents: write` scoped to this job only) |

## Test requirements per PR

- `testDebugUnitTest` green; new pure logic gets unit tests in `app/src/test/`.
- Coverage gate: `:app:koverVerifyDebug` must pass — Kover rules in `app/build.gradle.kts` require >= 80% line and branch coverage of unit-testable code (device-only classes: Compose screens, `MainActivity`, widget/tile, `SafNoteStore` are excluded; they need an emulator).
- Storage/SAF behavior: instrumented test or explicit note why not; CI skips emulator tests unless the `pr.yml` emulator job is enabled.
- `lintDebug` adds no new warnings.

## Code review checklist

Self-review checklist (AGENTS.md golden rules): quick-path purity, no main-thread I/O, optimistic UI, sync-agnostic storage, strings externalized, tests added, docs/lessons/ entry if the PR taught something non-obvious. Enforced by the implementing agent as part of its self-review (implement → wait for final-head CI green via `gh pr checks --watch` → self-review diff **and** CI/lint/coverage results → push review comments → fix the comments → re-run CI on the new head → merge only on the final head's green run).
