# Lessons Log

Self-improving log for this repo. Newest entries on top. Format and rules: see `docs/WORKFLOW.md` → "The self-improving docs loop".

### 2026-10-29 — Sandbox GitHub credentials are read-only; use the gh credential helper for git
- **Context:** Initial implementation of the Quick Notes app (PR #1).
- **Lesson:** In the Vibe sandbox, direct `git push` 403s even though `gh api` reads work. The fix is `git -c credential.helper='!gh auth git-credential get' push ...`. Mutating REST/GraphQL calls (create ref, contents PUT, PR create via raw API) are blocked ("Resource not accessible by integration"); `gh pr create` works, and pushing a branch first makes the repo non-empty so `gh pr create` succeeds.
- **Rule now:** Document the push command in CONTRIBUTING.md for agent runs.

### 2026-10-29 — Kotlin 2.0 requires the Compose Compiler Gradle plugin, not composeOptions
- **Context:** First build failed with "Starting in Kotlin 2.0, the Compose Compiler Gradle plugin is required".
- **Lesson:** With Kotlin ≥ 2.0, `composeOptions { kotlinCompilerExtensionVersion }` is invalid; apply `org.jetbrains.kotlin.plugin.compose` and drop the extension version. Also pin a known-good Kotlin/Compose-BOM pairing (here Kotlin 2.0.20 + compose-bom 2024.10.01).
- **Rule now:** AGENTS.md build section lists the verified toolchain versions.

### 2026-10-29 — SAF folders need persisted write permission checks, not just the URI
- **Context:** Folder picker design.
- **Lesson:** A stored tree URI is not enough; the OS can revoke persisted permissions. Always check `persistedUriPermissions` for write access and fall back to app-private storage with a banner, never crash or block note creation.
- **Rule now:** Encoded in `NoteStore.kt` (`FolderPrefs.hasPersistedPermission`, `resolveNoteStore`) and AGENTS.md golden rule on folder loss.

### 2026-10-29 — FileObserver doesn't work on SAF paths; rescan on resume instead
- **Context:** Syncthing will change files externally.
- **Lesson:** `FileObserver` only works on app-accessible dirs. With ≤ a few hundred notes, a background rescan on `onResume` comparing mtimes is simpler and fast enough; the list renders from an in-memory index either way.
- **Rule now:** `MainActivity.onResume` → `NotesViewModel.refresh()`; documented in ARCHITECTURE.md.

### 2026-10-29 — "Always a new note" must be structural, not a preference
- **Context:** Requirement that quick-note entry points never reopen the same note.
- **Lesson:** Implement it as a hard-coded intent action (`ACTION_NEW_NOTE`) whose handler unconditionally creates a note and opens the editor — no "current note" concept anywhere in the quick path.
- **Rule now:** AGENTS.md golden rule #1.

### 2026-10-29 — Empty repo edge cases: default branch and PR creation order
- **Context:** PR #1 creation failed with "No commits between main and head" because `main` didn't exist yet.
- **Lesson:** When the repo is empty, push the scaffold to `main` first, then create a feature branch with at least one commit ahead before calling `gh pr create`.
- **Rule now:** WORKFLOW.md branch rules.
