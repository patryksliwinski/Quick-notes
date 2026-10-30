# Contributing

Read `AGENTS.md` first — it is the authoritative guide for agents and humans. This file covers the practical workflow.

## Setup

- JDK 17, Android SDK with `platforms;android-35` + `build-tools;35.0.0`.
- `echo "sdk.dir=/path/to/sdk" > local.properties` (git-ignored).
- `./gradlew testDebugUnitTest :app:koverVerifyDebug assembleDebug` must pass before you open a PR (the Kover task enforces >= 80% line and branch coverage; see `app/build.gradle.kts` for the excluded device-only classes).

## Branching & pushing

- Branch from `main`: `feat/<slug>`, `fix/<slug>`, or `vibe/<slug>` for agent work.
- If you are running in the Vibe sandbox and plain `git push` returns 403, use:
  ```bash
  git -c credential.helper='!gh auth git-credential get' push -u origin <branch>
  ```
- Push the branch **before** opening the PR, and ensure it has at least one commit ahead of `main`:
  ```bash
  gh pr create --repo patryksliwinski/Quick-notes --draft --head <branch> --base main --title "..." --body-file pr-body.md
  ```

## Pull requests

<<<<<<< HEAD
- Fill in `.github/PULL_REQUEST_TEMPLATE.md` — **What & why**, verification run, and the golden rules a machine can't check. Add a `docs/lessons/` entry only if the PR taught something non-obvious.
- The `CI` workflow (`pr.yml`) posts an automated review comment on PRs; resolve its blocking findings.
- Its `build-and-test` job must be green: unit tests, `assembleDebug`, `lintDebug`, coverage gate.
- Merge requires the **final head's** CI run green (`gh pr checks --watch`) + no blocking automated findings. There is no human approval step: the implementing agent self-reviews its diff **after** that green run, posts the review as PR comments, then fixes those comments in follow-up commits and re-runs CI on the new head (see docs/WORKFLOW.md).
=======
- Fill in every section of `.github/PULL_REQUEST_TEMPLATE.md`, including **Lessons**.
- The `pr-review` workflow posts an automated review comment; resolve its blocking findings.
- CI (`ci.yml`) must be green: unit tests, `assembleDebug`, `lintDebug`.
- Merge requires the **final head's** CI run green (`gh pr checks --watch`) + no blocking automated findings. There is no human approval step: the implementing agent self-reviews its diff **after** that green run, posts the review as PR comments, then fixes those comments in follow-up commits and re-runs CI on the new head (see docs/WORKFLOW.md).
>>>>>>> d11ae3e (process: CI-first review loop; drop never-productive Copilot review request and 'edited' trigger (#49))

## Lessons log

Add a file under `docs/lessons/` in the same PR as the change — see `docs/WORKFLOW.md` for the format. This is how the repo improves itself.

## What not to do

No sync/conflict features, no accounts/onboarding/analytics, no new dependencies without justification, no main-thread I/O, no speculative abstractions. Details and rationale in `AGENTS.md` and `docs/ARCHITECTURE.md`.
