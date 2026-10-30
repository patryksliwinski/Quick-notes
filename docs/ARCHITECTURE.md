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
│   ├── NotesListScreen.kt  # search field + LazyColumn + swipe-to-delete + undo snackbar
│   ├── NotesViewModel.kt
│   ├── EditorScreen.kt     # monospace TextField, debounced autosave, undo/redo + overflow (timestamp/share/copy), word-count status line
│   ├── EditorViewModel.kt  # configurable debounce, in-memory UndoStack (≤50), stats derived from content, saveNow() on dispose/back
│   ├── UndoStack.kt        # pure in-memory undo/redo history (unit-tested)
│   ├── EditorText.kt       # pure TextStats + timestamp-line helpers (unit-tested)
│   └── SettingsScreen.kt   # SAF folder picker (ACTION_OPEN_DOCUMENT_TREE + persisted URI permission) + font size / debounce / theme rows
├── receiver/
│   ├── BaseWidgetProvider.kt   # abstract AppWidgetProvider: builds RemoteViews, applies shared widget_background, attaches PendingIntents, pushes updates
│   ├── NewNoteWidgetProvider.kt     # home-screen widget -> ACTION_NEW_NOTE (extends the base)
│   ├── RecentNotesWidgetProvider.kt # home-screen widget: 5 most recent notes -> ACTION_OPEN_NOTE per row (goAsync + WidgetNoteLoader)
│   └── WidgetNoteLoader.kt         # off-main-thread helper: active NoteStore (goAsync + Dispatchers.IO) -> recent List<NoteMeta> + widgetRows() mapping for RemoteViews
└── tile/QuickNoteTileService.kt       # QS tile -> ACTION_NEW_NOTE
```

## Data flow

1. `NoteRepository.refresh()` resolves the active `NoteStore` (SAF tree if a persisted writable URI permission exists, else internal dir) and loads `List<NoteMeta>` (uri, name, mtime, size) into a `StateFlow`. Title/snippet come from `store.readHead(uri)` (first 4 KiB) via a repository-side cache keyed by `(uri, lastModified, size)` — a refresh re-reads heads only for files that changed since the last scan, so SAF folders pay one `query()` per refresh instead of one `openInputStream` per note. Only successfully read non-empty heads are cached: a failed or empty head read falls back to the file name for that refresh only, so a later refresh (e.g. once an SD card is readable again) recovers the real title/snippet. Snippet derivation never duplicates the title — a note with no body line distinct from its title gets an empty snippet.
2. The list screen renders from that flow; search/sort/day-grouping/count are pure `combine()` transforms over it (`data/NoteListLogic.kt`, `data/NoteSearch.kt`) — search matches against lowercase forms computed once per list change, no disk access. Sort order and confirm-before-delete are UI prefs in `ListPrefs` (same SharedPreferences file as the folder URI).
3. Create: optimistic insert into the flow + background `store.createNote()` (empty file; `createNoteWithContent(content)` for share/duplicate — a single write, no read-modify-write). Editor opens immediately. Share (`ACTION_SEND` → `EXTRA_TEXT`/`EXTRA_STREAM`) and the launcher static shortcut (`res/xml/shortcuts.xml` → `ACTION_NEW_NOTE`) both create a new note; shared text prefills the editor through the debounced autosave.
4. Edit: `EditorViewModel.onContentChange` debounces (400 ms default, configurable in Settings), then `repository.writeNote` on `Dispatchers.IO`; the flow entry's title/snippet/mtime update in place (the edited note is always the newest, so it moves to the top without a full re-sort). Undo/redo snapshots are kept in a capped in-memory `UndoStack` (snapshot per debounced save, ≤50) — never on disk; word/char count is a pure derivation over the same `content` flow.
5. Delete: remove from flow instantly, delete the document on `Dispatchers.IO`, keep the last deleted note for one-tap undo (restores the file with original content).
6. `onResume` re-runs `refresh()` — this is how externally synced changes appear (no FileObserver on SAF paths); thanks to the head cache, repeat refreshes are cheap.
7. `NoteRepository` create/edit/delete/restore broadcast a package-scoped `ACTION_REFRESH`, coalesced to at most one broadcast per 2 s so a burst of autosaves triggers a single widget rescan; `RecentNotesWidgetProvider` answers it by re-running `onUpdate` (goAsync + `Dispatchers.IO`), reading heads only for the top-N visible rows — widgets never rescan per keystroke, only on mutations and host updates.

## Storage decisions

- **Markdown files are the source of truth**; the in-memory list is a rebuildable index. Never persist derived state that can't be rebuilt from the folder.
- `NoteStore.listNotes()` returns metadata only (no file content reads); head-derived fields are cached in-memory keyed by `(uri, lastModified, size)` and invalidated by mtime/size change — SAF listings stay one `query()`, never one IPC per note.
- Filenames `yyyyMMdd-HHmmss-<rand36>.md` are sortable and collision-resistant across devices; the timestamp formatter is thread-safe (`DateTimeFormatter`, not `SimpleDateFormat`).
- Dotfiles are ignored (generic hygiene — keeps sync-tool marker files out of the list without naming any tool).
- Deletes are hard deletes. Recovery is the sync tool's versioning, not ours.
- SAF writes go through `ContentResolver`; they can be slow (cloud folders) — that's fine because they're off the main thread and the UI is optimistic.
- SAF `createDocument` takes the tree's **document URI** (`buildDocumentUriUsingTree(tree, getTreeDocumentId(tree))`), never the tree URI itself — removable volumes (SD cards) reject the tree URI and the write silently fails (issue #23).

## Durability on the quick-leave path (SLO: open < 1 s, type, leave < 100 ms)

- "Leave < 100 ms" is a **UI-leave** latency; durability is guaranteed asynchronously, never by blocking the UI on storage.
- Every `NoteRepository.writeNote` journals `(uri, content)` in a dedicated SharedPreferences file (`PendingSaveJournal`) **before** the store write and clears the entry only after the write succeeds. On cold start `MainActivity.onCreate` → `NotesViewModel.flushPendingSaves()` → `NoteRepository.flushPendingSaves()` rewrites whatever is still journaled — a process kill inside the 400 ms debounce window (or mid-SAF-write) is recovered on next launch.
- Writes never delete the target before the new content is committed: tmp file → rename original to a dotfile `.bak` → rename tmp to the target name → delete `.bak` (rename `.bak` back if the commit rename fails). All writes to a given note URI are serialized by a per-URI mutex in `NoteRepository` (a cancelled autosave job that already entered its IO block still finishes its write; the mutex guarantees the newest write always lands last). AOSP's `renameDocument` does not overwrite when the target name already exists, so rename-over is not portable; the `.bak` step is what closes the kill-during-write window (issue #30). Leftover `.tmp`/`.bak` dotfiles from a kill are healed on the next `listNotes()` scan (restore the `.bak` if the note is gone, delete otherwise); both stores ignore dotfiles in listings.
- Cold start runs exactly one list scan: `NotesViewModel.refresh()` coalesces concurrent calls while a refresh is in flight, so `NotesListScreen`'s initial `LaunchedEffect` and `MainActivity.onResume` don't double-scan the folder.

## Performance contract

- Tap widget/tile/`+` → editable note well under 1 s (no heavy init on the quick path; DB-free).
- List scroll, search, delete feel instant at ≤ a few hundred notes.
- A refresh of a SAF folder costs one `query()` plus head reads only for notes changed since the last scan; widget updates read heads only for visible rows.
- No I/O on the main thread, ever (enforced by debug `StrictMode` disk penalties in `MainActivity` + convention).

## Home-screen widgets

All widgets extend `receiver/BaseWidgetProvider.kt`, which handles the `RemoteViews` plumbing: building the views for the widget's layout, applying the shared `@drawable/widget_background`, attaching `PendingIntent`s to view ids (`setActivityClick`), pushing updates to `AppWidgetManager`, and re-rendering a single widget on `onAppWidgetOptionsChanged` (resize). Widgets are resizable (`resizeMode="horizontal|vertical"` + `targetCell*`) and size-adaptive: `widgetLayoutForHeight()` in `WidgetNoteLoader.kt` maps the current height (dp, from `widgetHeightDp()` over `OPTION_APPWIDGET_MAX_HEIGHT`/`MIN_HEIGHT`) to visible rows / subtitle visibility. Row containers stay `wrap_content` — never `0dp`+weight next to GONE rows, which collapses visible rows to zero height on some hosts (issue #34) — and every row slot is always bound (text cleared on hidden rows). Shared widget text sizes/colors/paddings live in `res/values/widget_styles.xml`; all user-visible strings in `res/values/strings.xml`. Data-driven widgets load notes via `receiver/WidgetNoteLoader.kt` (`goAsync` + `Dispatchers.IO`, never disk on the main thread).

### Adding a new widget — 4 steps

1. **Provider**: create `receiver/<Name>WidgetProvider.kt` extending `BaseWidgetProvider` — implement `widgetLayout()` and override `attachClickIntents()` to map view ids to intents (e.g. `MainActivity.ACTION_NEW_NOTE`).
2. **Layout**: create `res/layout/<name>_widget.xml` with root id `@+id/widget_root`, `android:background="@drawable/widget_background"`, and shared dimens/styles from `widget_styles.xml`.
3. **Info XML**: create `res/xml/<name>_widget_info.xml` (`<appwidget-provider>` pointing at the layout).
4. **Manifest**: add a `<receiver android:exported="true">` with the `APPWIDGET_UPDATE` intent-filter and `android.appwidget.provider` meta-data pointing at the info XML.

Golden rule #1 applies to quick-add style widgets: the click intent must be `ACTION_NEW_NOTE` — always a new note.
