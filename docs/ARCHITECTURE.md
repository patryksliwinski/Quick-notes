# Architecture

## Layout

```
app/src/main/java/com/patryksliwinski/quicknotes/
├── data/
│   ├── NoteStore.kt        # NoteStore interface + InternalNoteStore + SafNoteStore, folder prefs, filename/title helpers
│   └── NoteRepository.kt   # single source of truth: in-memory StateFlow<List<NoteMeta>>, optimistic mutations
├── ui/
│   ├── MainActivity.kt     # single activity, screen state machine (List / Editor / Settings), ACTION_NEW_NOTE
│   ├── NotesListScreen.kt  # search field + LazyColumn + swipe-to-delete + undo snackbar
│   ├── NotesViewModel.kt
│   ├── EditorScreen.kt     # monospace TextField, debounced autosave
│   ├── EditorViewModel.kt  # 400 ms debounce, saveNow() on dispose/back
│   └── SettingsScreen.kt   # SAF folder picker (ACTION_OPEN_DOCUMENT_TREE + persisted URI permission)
├── receiver/NewNoteWidgetProvider.kt  # home-screen widget -> ACTION_NEW_NOTE
└── tile/QuickNoteTileService.kt       # QS tile -> ACTION_NEW_NOTE
```

## Data flow

1. `NoteRepository.refresh()` resolves the active `NoteStore` (SAF tree if a persisted writable URI permission exists, else internal dir) and loads `List<NoteMeta>` (uri, name, mtime, title, snippet) into a `StateFlow`.
2. The list screen renders from that flow; search is a `combine()` filter over it — no disk access.
3. Create: optimistic insert into the flow + background `store.createNote()` (empty file). Editor opens immediately.
4. Edit: `EditorViewModel.onContentChange` debounces 400 ms, then `repository.writeNote` on `Dispatchers.IO`; the flow entry's title/snippet/mtime update optimistically in-memory.
5. Delete: remove from flow instantly, delete the document on `Dispatchers.IO`, keep the last deleted note for one-tap undo (restores the file with original content).
6. `onResume` re-runs `refresh()` — this is how externally synced changes appear (no FileObserver on SAF paths).

## Storage decisions

- **Markdown files are the source of truth**; the in-memory list is a rebuildable index. Never persist derived state that can't be rebuilt from the folder.
- Filenames `yyyyMMdd-HHmmss-<rand36>.md` are sortable and collision-resistant across devices.
- Dotfiles are ignored (generic hygiene — keeps sync-tool marker files out of the list without naming any tool).
- Deletes are hard deletes. Recovery is the sync tool's versioning, not ours.
- SAF writes go through `ContentResolver`; they can be slow (cloud folders) — that's fine because they're off the main thread and the UI is optimistic.

## Performance contract

- Tap widget/tile/`+` → editable note well under 1 s (no heavy init on the quick path; DB-free).
- List scroll, search, delete feel instant at ≤ a few hundred notes.
- No I/O on the main thread, ever (enforced by convention + debug `StrictMode`).
