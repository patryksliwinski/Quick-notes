# Contributing

Read `AGENTS.md` first — it is the authoritative guide for agents and humans. This file covers the practical workflow.

## Setup

- JDK 17, Android SDK with `platforms;android-35` + `build-tools;35.0.0`.
- `echo "sdk.dir=/path/to/sdk" > local.properties` (git-ignored).
- `./gradlew testDebugUnitTest assembleDebug` must pass before you open a PR.

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

- Fill in every section of `.github/PULL_REQUEST_TEMPLATE.md`, including **Lessons**.
- The `pr-review` workflow posts an automated review comment; resolve its blocking findings.
- CI (`ci.yml`) must be green: unit tests, `assembleDebug`, `lintDebug`.
- Merge requires human approval + green CI + no blocking automated findings.

## Lessons log

Append to `docs/LESSONS.md` in the same PR as the change — see `docs/WORKFLOW.md` for the format. This is how the repo improves itself.

## What not to do

No sync/conflict features, no accounts/onboarding/analytics, no new dependencies without justification, no main-thread I/O, no speculative abstractions. Details and rationale in `AGENTS.md` and `docs/ARCHITECTURE.md`.
