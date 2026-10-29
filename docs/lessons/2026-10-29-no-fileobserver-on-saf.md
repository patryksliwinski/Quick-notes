# 2026-10-29 — FileObserver doesn't work on SAF paths; rescan on resume instead

- **Context:** Syncthing will change files externally.
- **Lesson:** `FileObserver` only works on app-accessible dirs. With ≤ a few hundred notes, a background rescan on `onResume` comparing mtimes is simpler and fast enough; the list renders from an in-memory index either way.
- **Rule now:** `MainActivity.onResume` → `NotesViewModel.refresh()`; documented in ARCHITECTURE.md.
