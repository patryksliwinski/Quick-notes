# Architecture

## Layout

```
app/src/main/java/com/patryksliwinski/quicknotes/
├── data/
│   ├── NoteStore.kt        # NoteStore interface + InternalNoteStore + SafNoteStore, folder prefs, filename/title helpers; ShareIntentText.kt extracts shared text from ACTION_SEND
│   ├── NoteRepository.kt   # single source of truth: in-memory StateFlow<List<NoteMeta>>, optimistic mutations
│   └── EditorPrefs.kt      # editor font size / autosave debounce / theme in SharedPreferences (reads off the main thread)
├── ui/
│   ├── MainActivity.kt     # single activity, screen state machine (List / Editor / Settings), ACTION_NEW_NOTE / ACTION_OPEN_NOTE / ACTION_SEND (share into new note)
│   ├── NewNoteIntentRouter.kt # pure intent -> IncomingNoteRequest mapping (ACTION_NEW_NOTE / ACTION_OPEN_NOTE / ACTION_SEND), unit-tested without an emulator (issue #60)
│   ├── NotesListScreen.kt  # search field + LazyColumn + swipe-to-delete + undo snackbar
│   ├── NotesViewModel.kt
│   ├── EditorScreen.kt     # monospace TextField, debounced autosave, undo/redo + overflow (timestamp/share/copy), word-count status line
│   ├── EditorViewModel.kt  # configurable debounce, in-memory UndoStack (≤50), stats derived from content, saveNow() on dispose/back
│   ├── UndoStack.kt        # pure in-memory undo/redo history (unit-tested)
│   ├── EditorText.kt       # pure TextStats + timestamp-line helpers (unit-tested)
│   ├── NoteRepositoryHolder.kt # process-wide singleton: both ViewModels share one NoteRepository (issue #57)
│   └── SettingsScreen.kt   # SAF folder picker (ACTION_OPEN_DOCUMENT_TREE + persisted URI permission) + font size / debounce / theme rows
├── receiver/
│   ├── BaseWidgetProvider.kt   # abstract AppWidgetProvider: builds RemoteViews, applies shared widget_background, attaches PendingIntents, pushes updates
│   ├── NewNoteWidgetProvider.kt     # home-screen widget -> ACTION_NEW_NOTE (extends the base)
│   ├── RecentNotesWidgetProvider.kt # home-screen widget: 5 most recent notes -> ACTION_OPEN_NOTE per row (goAsync + WidgetNoteLoader)
│   └── WidgetNoteLoader.kt         # off-main-thread helper: active NoteStore (goAsync + Dispatchers.IO) -> recent List<NoteMeta> + widgetRows() mapping for RemoteViews
└── tile/QuickNoteTileService.kt       # QS tile -> ACTION_NEW_NOTE
```

## Data flow

0. One `NoteRepository` per process: `NoteRepositoryHolder.get(application)` is the default constructor
   argument of both `NotesViewModel` and `EditorViewModel`, so the list screen and the editor observe and
   mutate the **same** `StateFlow<List<NoteMeta>>` — editor-side create/write/delete reach the list without
   waiting for an `onResume` refresh (issue #57). Tests inject a shared instance via the constructor
   parameter.
1. `NoteRepository.refresh()` resolves the active `NoteStore` (SAF tree if a persisted writable URI permission exists, else internal dir) and loads `List<NoteMeta>` (uri, name, mtime, size) into a `StateFlow`. Title/snippet/full content come from one `store.readNote(uri)` per changed file; the **title is always the file name** (stable across refreshes, never flipping between file name and first line), the snippet is the first non-empty line of that content. Both the derived head fields and the full content are cached repository-side keyed by `(uri, lastModified, size)` — a refresh re-reads content only for files that changed since the last scan, so SAF folders pay one `query()` plus one `openInputStream` per *changed* note per refresh. Only successfully read contents are cached: a failed read falls back to the file name (title) and empty content for that refresh only, so a later refresh (e.g. once an SD card is readable again) recovers the real title/snippet/content. The full content in the index is what makes search match the whole note body without any per-keystroke disk access. The snippet is the first non-empty line of content (it no longer relates to the title, which is the file name).
2. The list screen renders from that flow; search/sort/day-grouping/count are pure `combine()` transforms over it (`data/NoteListLogic.kt`, `data/NoteSearch.kt`) — search matches against lowercase forms computed once per list change, no disk access. Sort order and confirm-before-delete are UI prefs in `ListPrefs` (same SharedPreferences file as the folder URI).
3. Create: optimistic insert into the flow + background `store.createNote()` (empty file; `createNoteWithContent(content)` for share/duplicate — a single write, no read-modify-write). Editor opens immediately. Widget/tile taps and the launcher static shortcut send `ACTION_NEW_NOTE` to `MainActivity` (cold start via `onCreate`, warm re-delivery via `onNewIntent` — `singleTop`); `NewNoteIntentRouter.incomingNoteRequestFromIntent` maps the intent to an `IncomingNoteRequest` (new note, optionally with shared text; or open an existing note from a widget row). Share (`ACTION_SEND` → `EXTRA_TEXT`/`EXTRA_STREAM`) also creates a new note; shared text prefills the editor through the debounced autosave.
4. Edit: `EditorViewModel.onContentChange` debounces (400 ms default, configurable in Settings), then `repository.writeNote` on `Dispatchers.IO`; the flow entry's title/snippet/mtime update in place (the edited note is always the newest, so it moves to the top without a full re-sort). Undo/redo snapshots are kept in a capped in-memory `UndoStack` (snapshot per debounced save, ≤50) — never on disk; word/char count is a pure derivation over the same `content` flow.
5. Delete: remove from flow instantly, delete the document on `Dispatchers.IO`, keep the last deleted note for one-tap undo (restores the file with original content).
6. `onResume` re-runs `refresh()` — this is how externally synced changes appear (no FileObserver on SAF paths); thanks to the head cache, repeat refreshes are cheap.
7. `NoteRepository` create/edit/delete/restore broadcast a package-scoped `ACTION_REFRESH`, coalesced to at most one broadcast per 2 s so a burst of autosaves triggers a single widget rescan; `RecentNotesWidgetProvider` answers it by re-running `onUpdate` (goAsync + `Dispatchers.IO`), reading heads only for the top-N visible rows — widgets never rescan per keystroke, only on mutations and host updates.

## Storage decisions

- **Markdown files are the source of truth**; the in-memory list is a rebuildable index. Never persist derived state that can't be rebuilt from the folder.
- `NoteStore.listNotes()` returns metadata only (no file content reads); full content (from which title/snippet derive) is cached in-memory keyed by `(uri, lastModified, size)` and invalidated by mtime/size change — SAF listings stay one `query()`, plus one read per *changed* note, never one IPC per note per refresh.
- Filenames `yyyyMMdd-HHmmss-<rand36>.md` are sortable and collision-resistant across devices; the timestamp formatter is thread-safe (`DateTimeFormatter`, not `SimpleDateFormat`).
- Dotfiles are ignored (generic hygiene — keeps sync-tool marker files out of the list without naming any tool).
- Deletes are hard deletes. Recovery is the sync tool's versioning, not ours.
- SAF writes go through `ContentResolver`; they can be slow (cloud folders) — that's fine because they're off the main thread and the UI is optimistic.
- SAF `createDocument` takes the tree's **document URI** (`buildDocumentUriUsingTree(tree, getTreeDocumentId(tree))`), never the tree URI itself — removable volumes (SD cards) reject the tree URI and the write silently fails (issue #23).
- SAF error semantics (issue #59): a **null cursor from `resolver.query()` or a null stream from `openInputStream()` means the volume is unavailable — throw, never return an empty list/`""`**; only a non-null cursor with zero rows means "empty folder". `NoteRepository.refresh` keeps the last known list and shows the revoked/unavailable banner when listing fails. `COLUMN_LAST_MODIFIED` is sanitized through `safLastModified()` because AOSP omits the column (reads as 0) for files older than ~1 year, which would otherwise sort notes to the bottom under a 1970 header.

## Durability on the quick-leave path (SLO: open < 1 s, type, leave < 100 ms)

- "Leave < 100 ms" is a **UI-leave** latency; durability is guaranteed asynchronously, never by blocking the UI on storage.
- Every `NoteRepository.writeNote` journals `(uri, content)` in a dedicated SharedPreferences file (`PendingSaveJournal`) **before** the store write and clears the entry only after the write succeeds. `record` uses `Editor.commit()` (synchronous), not `apply()` — `apply()` may not have flushed when the process dies, which would defeat the journal; only `clear` stays async. On cold start `MainActivity.onCreate` → `NotesViewModel.flushPendingSaves()` → `NoteRepository.flushPendingSaves()` rewrites whatever is still journaled — a process kill inside the 400 ms debounce window (or mid-SAF-write) is recovered on next launch.
- Writes never delete the target before the new content is committed: tmp file → rename original to a dotfile `.bak` → rename tmp to the target name → delete `.bak` (rename `.bak` back if the commit rename fails). All writes to a given note URI are serialized by a per-URI mutex in `NoteRepository` (a cancelled autosave job that already entered its IO block still finishes its write; the mutex guarantees the newest write always lands last). AOSP's `renameDocument` does not overwrite when the target name already exists, so rename-over is not portable; the `.bak` step is what closes the kill-during-write window (issue #30). Leftover `.tmp`/`.bak` dotfiles from a kill are healed on the next `listNotes()` scan (restore the `.bak` if the note is gone, delete otherwise); both stores ignore dotfiles in listings.
- SAF failures must fail loudly, never return `""`: `SafNoteStore.listNotes` throws when `resolver.query` returns null, and `readNote`/`readHead` throw when `openInputStream` returns null (SD card unmounted / permission hiccup) — only a genuinely 0-byte document reads as `""`. `NoteRepository.refresh` surfaces `FolderUnavailable` when a non-internal store lists zero notes, and `NoteRepository.readNote` returns a `Result` (failure + `StorageError` banner on error); an empty read of a note the index says is non-empty (`size > 0`) is treated as unreadable, not as content. `EditorViewModel.open` leaves the editor empty and blocks autosave/save-on-leave when the load failed, so a silent-empty read can never be autosaved over the real file. `NoteRepository.deleteNote` reads the content *before* deleting and aborts on failure, so undo always restores real content.
- Cold start runs exactly one list scan: `NotesViewModel.refresh()` coalesces concurrent calls while a refresh is in flight, so `NotesListScreen`'s initial `LaunchedEffect` and `MainActivity.onResume` don't double-scan the folder.

## Performance contract

- Tap widget/tile/`+` → editable note well under 1 s (no heavy init on the quick path; DB-free).
- List scroll, search, delete feel instant at ≤ a few hundred notes.
- A refresh of a SAF folder costs one `query()` plus full-note reads only for notes changed since the last scan; widget updates read heads only for visible rows.
- No I/O on the main thread, ever (enforced by debug `StrictMode` disk penalties in `MainActivity` + convention).

## Home-screen widgets

All widgets extend `receiver/BaseWidgetProvider.kt`, which handles the `RemoteViews` plumbing: building the views for the widget's layout, applying the shared `@drawable/widget_background`, attaching `PendingIntent`s to view ids (`setActivityClick`), pushing updates to `AppWidgetManager`, and re-rendering a single widget on `onAppWidgetOptionsChanged` (resize). Widgets are resizable (`resizeMode="horizontal|vertical"` + `targetCell*`) and size-adaptive: `widgetLayoutForHeight()` in `WidgetNoteLoader.kt` maps the current height (dp, from `widgetHeightDp()` over `OPTION_APPWIDGET_MAX_HEIGHT`/`MIN_HEIGHT`) to visible rows / subtitle visibility. Row containers stay `wrap_content` — never `0dp`+weight next to GONE rows, which collapses visible rows to zero height on some hosts (issue #34) — and every row slot is always bound (text cleared on hidden rows). Shared widget text sizes/colors/paddings live in `res/values/widget_styles.xml`; all user-visible strings in `res/values/strings.xml`. Data-driven widgets load notes via `receiver/WidgetNoteLoader.kt` (`goAsync` + `Dispatchers.IO`, never disk on the main thread).

### Adding a new widget — 4 steps

1. **Provider**: create `receiver/<Name>WidgetProvider.kt` extending `BaseWidgetProvider` — implement `widgetLayout()` and override `attachClickIntents()` to map view ids to intents (e.g. `MainActivity.ACTION_NEW_NOTE`).
2. **Layout**: create `res/layout/<name>_widget.xml` with root id `@+id/widget_root`, `android:background="@drawable/widget_background"`, and shared dimens/styles from `widget_styles.xml`.
3. **Info XML**: create `res/xml/<name>_widget_info.xml` (`<appwidget-provider>` pointing at the layout).
4. **Manifest**: add a `<receiver android:exported="true">` with the `APPWIDGET_UPDATE` intent-filter and `android.appwidget.provider` meta-data pointing at the info XML.

Golden rule #1 applies to quick-add style widgets: the click intent must be `ACTION_NEW_NOTE` — always a new note.
