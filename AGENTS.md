# AGENTS.md — Quick Notes

Instructions for AI coding agents working in this repository. Read this before editing anything.

## Project in one paragraph

Quick Notes is a minimal, fast Android app for quick markdown notes. Notes are plain `.md` files in a user-picked folder (Storage Access Framework) or app-private storage by default. Sync (Syncthing, git, Dropbox) is the user's business — the app is deliberately sync-agnostic. The quick-note entry points (home widget, QS tile, `+` button) must **always create a new note**, never reopen an existing one. See `docs/ARCHITECTURE.md`.

## Golden rules

1. **The quick path stays pure.** The new-note flow (widget/tile → `ACTION_NEW_NOTE` → create → editor) must never depend on settings, sync state, or network. No dialogs before the editor is open.
2. **All disk/SAF I/O off the main thread** (`Dispatchers.IO`), UI updates optimistic. Enable `StrictMode` in debug builds if you touch this.
3. **Never scan or parse note files on the main thread or per keystroke.** The list renders from an in-memory index (`NoteMeta`), max a few hundred entries. Search filters that index.
4. **Stay sync-agnostic.** No Syncthing-specific logic, no conflict handling, no trash folders. Only generic hygiene: ignore dotfiles, hard deletes, unique filenames.
5. **Smallest correct change.** No new dependencies unless already used or required by the change. No speculative abstractions.
6. **Docs are code.** Any behavioral decision that took thought goes into `docs/lessons/` (see docs/WORKFLOW.md).

## Build & verify

```bash
./gradlew testDebugUnitTest   # unit tests — must pass
./gradlew :app:koverVerifyDebug  # coverage gate — must pass (>= 80% line AND branch)
./gradlew assembleDebug       # must produce app-debug.apk
./gradlew lintDebug           # must not add new warnings
```

Requires JDK 17, Android SDK with `platforms;android-35` and `build-tools;35.0.0`. `local.properties` with `sdk.dir` is generated locally and git-ignored.

## Code standards

- Kotlin + Jetpack Compose, MVVM. One ViewModel per screen; repository mediates all storage.
- File/class naming: PascalCase classes, camelCase functions. Package: `com.patryksliwinski.quicknotes.<area>` (`data`, `ui`, `receiver`, `tile`).
- No comments in code unless the *why* is non-obvious; prefer self-explanatory names.
- Strings in `res/values/strings.xml`, never hardcoded in UI.
- Filenames for notes: `yyyyMMdd-HHmmss-<4 random base36>.md`.
- Error handling: I/O failures surface as a snackbar/banner, never a crash; folder-permission loss falls back to app-private storage with a one-line banner.

## Test requirements

- Every PR must keep `testDebugUnitTest` green and add tests for new pure logic (naming, title/snippet derivation, filtering).
- **Coverage is enforced**: Kover verifies >= 80% line **and** branch coverage of unit-testable code on every PR (`./gradlew :app:koverVerifyDebug`, also a CI gate). HTML/XML reports land in `app/build/reports/kover/`.
- Device-only code is excluded from the gate in `app/build.gradle.kts` (Compose screens, `MainActivity`, widget/tile receivers, `SafNoteStore`) — they need an emulator and are covered by `connectedDebugAndroidTest` instead.
- Storage logic (`NoteStore`) is covered via unit tests where possible (`InternalNoteStore` runs on plain JVM; SAF paths need a device/emulator — mark them `@Ignore` in CI if no emulator is available, but state that in the PR).
- No UI snapshot tests yet; if you add Compose UI tests, they must run under `testDebugUnitTest` (Robolectric) or `connectedDebugAndroidTest` in CI with an emulator.

## Workflow (summary — full version in docs/WORKFLOW.md)

- Work happens on short-lived branches `vibe/<slug>` or `feat/<slug>`; PRs target `main`.
- Every PR goes through the automated review pipeline (`.github/workflows/pr.yml`) plus the review checklist in `.github/PULL_REQUEST_TEMPLATE.md`. Reviews are done by the agent itself: after implementing a ticket, the agent reviews its own diff, pushes the review as comments on the PR, then addresses its own comments in follow-up commits. No human review step.
- After each merged task, add a lesson file under `docs/lessons/` — the repo's self-improving log. **Do not skip this step.**

## Do not

- Do not add sync/conflict/tag/folder-hierarchy features. That is the Markor trap; it erodes the sub-second promise.
- Do not add accounts, onboarding, or analytics.
- Do not restructure packages or rename public classes without a `docs/lessons/` entry explaining why.
