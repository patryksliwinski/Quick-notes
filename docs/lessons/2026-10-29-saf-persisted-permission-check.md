# 2026-10-29 — SAF folders need persisted write permission checks, not just the URI

- **Context:** Folder picker design.
- **Lesson:** A stored tree URI is not enough; the OS can revoke persisted permissions. Always check `persistedUriPermissions` for write access and fall back to app-private storage with a banner, never crash or block note creation.
- **Rule now:** Encoded in `NoteStore.kt` (`FolderPrefs.hasPersistedPermission`, `resolveNoteStore`) and AGENTS.md golden rule on folder loss.
