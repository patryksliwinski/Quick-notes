# SAF `openOutputStream` returning null is silent data loss, not a no-op

The "null SAF result means unavailable, never empty" rule (see
`2026-10-30-saf-null-cursor-means-unavailable-not-empty.md`) was applied to
queries and input streams but missed the one place it is worst: the write path.
`SafNoteStore.writeText` used `resolver.openOutputStream(...)?.use { ... }`, so a
null stream skipped the body silently. On the tmp→rename path the *empty* tmp
document was then renamed over the live note — truncating it to zero bytes —
and `NoteRepository.writeNote` still reported `SaveOutcome.Written` (no journal,
no banner, no fallback). The identical staging failure on `InternalNoteStore`
throws and surfaces as `StorageError`, so default storage and SD storage
behaved differently for the same fault.

Fix (issue #87): `openOutputStreamOrThrow` — a null output stream throws
`IOException("Note document not writable ...")` exactly like
`openInputStreamOrThrow`, so the save falls back to `SaveOutcome.Journaled` and
the pending-save journal survives the unmounted SD volume. Also: never trust
the SAF provider's SQL sort hint (`COLUMN_LAST_MODIFIED DESC` is optional for
providers); sort in Kotlin like `InternalNoteStore` does so both stores return
the same order.

Rule of thumb: every SAF `open*`/`query` result must be null-checked into a
throw on the write path too — silent-empty on a write path is data loss, not an
empty read. When fixing one silent-null site, grep for the other call shapes of
the same API.
