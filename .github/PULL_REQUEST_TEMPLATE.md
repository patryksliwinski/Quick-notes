## Summary
- What changed and why (one or two bullets per area touched)

## Type
- [ ] Feature
- [ ] Fix
- [ ] Docs / workflow
- [ ] Refactor

## Verification
- [ ] `./gradlew testDebugUnitTest` green (paste result)
- [ ] `./gradlew assembleDebug` green, APK produced
- [ ] `./gradlew lintDebug` adds no new warnings
- [ ] Manual check on device/emulator (describe what you tried) — or N/A

## Golden rules check (from AGENTS.md)
- [ ] Quick path (widget/tile/`+` → new note → editor) untouched and still dialog-free
- [ ] No disk/SAF I/O on the main thread; UI updates optimistic
- [ ] List/search still render from the in-memory index, no per-keystroke file scans
- [ ] Storage layer stays sync-agnostic (no Syncthing/conflict/trash logic)
- [ ] Strings externalized; no hardcoded UI text
- [ ] No new dependencies (or justification below)

## Tests
- [ ] Added/updated unit tests for new pure logic (`app/src/test/`)
- [ ] Instrumented test added, or reason why not (SAF/device-only paths)

## Lessons
- [ ] Added a file under `docs/lessons/` (+ index row) describing what this PR taught us (required if any non-trivial decision was made)
- [ ] Updated `AGENTS.md` / `docs/WORKFLOW.md` / `docs/ARCHITECTURE.md` if a rule changed (required if behavior contract changed)

## Screenshots / demo
- Attach if UI changed
