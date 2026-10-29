# Workflow

How work happens in this repo: branches, PRs, pipelines, reviews, and the self-improving docs loop.

## Branches

- `main` — default branch, always buildable, `assembleDebug` + `testDebugUnitTest` green.
- Task branches: `vibe/<short-slug>` (agent) or `feat/<slug>` / `fix/<slug>` (human). Short-lived; delete after merge.
- Never push directly to `main`; everything goes through a PR.

## Pull requests

1. Open a PR (draft while iterating). Fill in `.github/PULL_REQUEST_TEMPLATE.md` completely — including the **Lessons** section.
2. The `pr-review` pipeline runs automatically in layers: (a) a fast static scan (build/tests/lint + golden-rule grep) posts a comment, and (b) a Copilot AI review is requested on the PR. A Mistral agent can additionally review the diff and post a formal review. See `.github/workflows/pr-review.yml`.
3. The implementing agent reviews its own work: it reads its own diff against `AGENTS.md` and the PR-template checklist, pushes the review as comments on the PR, then addresses and fixes those comments in follow-up commits.
4. Merge only when: CI green, automated review has no blocking findings, the agent's self-review comments are resolved, and the Lessons section is filled.

## The self-improving docs loop (docs/lessons/)

`docs/lessons/` is the repo's memory. It is updated **in the same PR** that introduces the change it describes.

Rules:

- One **file** per lesson: `docs/lessons/YYYY-MM-DD-short-slug.md`, linked from the index table in `docs/lessons/README.md` (newest first). File format:
  ```markdown
  # YYYY-MM-DD — Short title
  - **Context:** what task/PR prompted this
  - **Lesson:** the takeaway in one or two sentences
  - **Rule now:** the concrete rule added to AGENTS.md / docs / code (if any)
  ```
- A lesson is anything that would have changed the outcome if known earlier: a perf trap, an API gotcha, a UX decision, a CI failure, a review finding.
- If a PR adds a rule to `AGENTS.md`, it must also add a `docs/lessons/` file explaining why.
- Periodically (every ~10 lessons), fold recurring ones into `AGENTS.md` / `docs/WORKFLOW.md` / `docs/ARCHITECTURE.md` and mark the lesson file `(folded)` in the index.

## Pipelines (`.github/workflows/`)

| Workflow | Trigger | Does |
|---|---|---|
| `ci.yml` | push to `main`, PRs | `testDebugUnitTest`, `assembleDebug`, `lintDebug`; uploads APK artifact on PRs |
| `pr-review.yml` | PR opened/synchronized | Layer 1 (static, fast): CI checks + golden-rule grep, posts a comment. Layer 2 (AI): requests a Copilot code review on the PR. Layer 3 (agent): a Mistral agent reviews the diff and posts a formal review via the GitHub App; the implementing agent self-reviews and fixes its own comments |
| `release.yml` | tag `v*` pushed, or manual `workflow_dispatch` | Runs `testDebugUnitTest`, builds `assembleRelease`, signs the APK with an ephemeral CI-generated keystore (`keytool` + `apksigner`), verifies the signature, and publishes `app-release.apk` to a GitHub Release for the tag. Release notes are generated from the commits since the previous tag (`git log prev..HEAD`, merge commits condensed to `(#N) title`) and attached via `--notes-file`, so every release always carries notes (`contents: write` scoped to this job only) |

## Test requirements per PR

- `testDebugUnitTest` green; new pure logic gets unit tests in `app/src/test/`.
- Storage/SAF behavior: instrumented test or explicit note why not; CI skips emulator tests unless the `ci.yml` emulator job is enabled.
- `lintDebug` adds no new warnings.

## Code review checklist

Mirror of the PR template: quick-path purity, no main-thread I/O, optimistic UI, sync-agnostic storage, strings externalized, tests added, docs/lessons/ updated. Filled in and enforced by the implementing agent as part of its self-review (implement → self-review → push review comments → fix the comments).
