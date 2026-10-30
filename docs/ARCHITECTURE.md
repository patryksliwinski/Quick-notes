# Architecture

## Layout

```
app/src/main/java/com/patryksliwinski/quicknotes/
├── data/
│   ├── NoteStore.kt        # NoteStore interface + InternalNoteStore + SafNoteStore, folder prefs, filename/title helpers; ShareIntentText.kt extracts shared text from ACTION_SEND
│   └── NoteRepository.kt   # single source of truth: in-memory StateFlow<List<NoteMeta>>, optimistic mutations
├── ui/
│   ├── MainActivity.kt     # single activity, screen state machine (List / Editor / Settings), ACTION_NEW_NOTE / ACTION_OPEN_NOTE / ACTION_SEND (share into new note)
│   ├── NotesListScreen.kt  # search field + LazyColumn + swipe-to-delete + undo snackbar
│   ├── NotesViewModel.kt
│   ├── EditorScreen.kt     # monospace TextField, debounced autosave
│   ├── EditorViewModel.kt  # 400 ms debounce, saveNow() on dispose/back
│   └── SettingsScreen.kt   # SAF folder picker (ACTION_OPEN_DOCUMENT_TREE + persisted URI permission)
├── receiver/
│   ├── BaseWidgetProvider.kt   # abstract AppWidgetProvider: builds RemoteViews, applies shared widget_background, attaches PendingIntents, pushes updates
│   ├── NewNoteWidgetProvider.kt     # home-screen widget -> ACTION_NEW_NOTE (extends the base)
│   ├── RecentNotesWidgetProvider.kt # home-screen widget: 5 most recent notes -> ACTION_OPEN_NOTE per row (goAsync + WidgetNoteLoader)
│   └── WidgetNoteLoader.kt         # off-main-thread helper: active NoteStore (goAsync + Dispatchers.IO) -> recent List<NoteMeta> + widgetRows() mapping for RemoteViews
└── tile/QuickNoteTileService.kt       # QS tile -> ACTION_NEW_NOTE
```

## Data flow

1. `NoteRepository.refresh()` resolves the active `NoteStore` (SAF tree if a persisted writable URI permission exists, else internal dir) and loads `List<NoteMeta>` (uri, name, mtime, title, snippet) into a `StateFlow`.
2. The list screen renders from that flow; search/sort/day-grouping/count are pure `combine()` transforms over it (`data/NoteListLogic.kt`) — no disk access. Sort order and confirm-before-delete are UI prefs in `ListPrefs` (same SharedPreferences file as the folder URI).
3. Create: optimistic insert into the flow + background `store.createNote()` (empty file; `createNoteWithContent(content)` for share/duplicate — a single write, no read-modify-write). Editor opens immediately. Share (`ACTION_SEND` → `EXTRA_TEXT`/`EXTRA_STREAM`) and the launcher static shortcut (`res/xml/shortcuts.xml` → `ACTION_NEW_NOTE`) both create a new note; shared text prefills the editor through the debounced autosave.
4. Edit: `EditorViewModel.onContentChange` debounces 400 ms, then `repository.writeNote` on `Dispatchers.IO`; the flow entry's title/snippet/mtime update optimistically in-memory.
5. Delete: remove from flow instantly, delete the document on `Dispatchers.IO`, keep the last deleted note for one-tap undo (restores the file with original content).
6. `onResume` re-runs `refresh()` — this is how externally synced changes appear (no FileObserver on SAF paths).
7. `NoteRepository` create/edit/delete/restore broadcast a package-scoped `ACTION_REFRESH`, which `RecentNotesWidgetProvider` answers by re-running `onUpdate` (goAsync + `Dispatchers.IO`) — widgets never rescan per keystroke, only on mutations and host updates.

## Storage decisions

- **Markdown files are the source of truth**; the in-memory list is a rebuildable index. Never persist derived state that can't be rebuilt from the folder.
- Filenames `yyyyMMdd-HHmmss-<rand36>.md` are sortable and collision-resistant across devices.
- Dotfiles are ignored (generic hygiene — keeps sync-tool marker files out of the list without naming any tool).
- Deletes are hard deletes. Recovery is the sync tool's versioning, not ours.
- SAF writes go through `ContentResolver`; they can be slow (cloud folders) — that's fine because they're off the main thread and the UI is optimistic.
- SAF `createDocument` takes the tree's **document URI** (`buildDocumentUriUsingTree(tree, getTreeDocumentId(tree))`), never the tree URI itself — removable volumes (SD cards) reject the tree URI and the write silently fails (issue #23).

## Performance contract

- Tap widget/tile/`+` → editable note well under 1 s (no heavy init on the quick path; DB-free).
- List scroll, search, delete feel instant at ≤ a few hundred notes.
- No I/O on the main thread, ever (enforced by convention + debug `StrictMode`).

## Home-screen widgets

All widgets extend `receiver/BaseWidgetProvider.kt`, which handles the `RemoteViews` plumbing: building the views for the widget's layout, applying the shared `@drawable/widget_background`, attaching `PendingIntent`s to view ids (`setActivityClick`), pushing updates to `AppWidgetManager`, and re-rendering a single widget on `onAppWidgetOptionsChanged` (resize). Widgets are resizable (`resizeMode="horizontal|vertical"` + `targetCell*`) and size-adaptive: `widgetLayoutForHeight()` in `WidgetNoteLoader.kt` maps the current height (dp, from `widgetHeightDp()` over `OPTION_APPWIDGET_MAX_HEIGHT`/`MIN_HEIGHT`) to visible rows / subtitle visibility. Row containers stay `wrap_content` — never `0dp`+weight next to GONE rows, which collapses visible rows to zero height on some hosts (issue #34) — and every row slot is always bound (text cleared on hidden rows). Shared widget text sizes/colors/paddings live in `res/values/widget_styles.xml`; all user-visible strings in `res/values/strings.xml`. Data-driven widgets load notes via `receiver/WidgetNoteLoader.kt` (`goAsync` + `Dispatchers.IO`, never disk on the main thread).

### Adding a new widget — 4 steps

1. **Provider**: create `receiver/<Name>WidgetProvider.kt` extending `BaseWidgetProvider` — implement `widgetLayout()` and override `attachClickIntents()` to map view ids to intents (e.g. `MainActivity.ACTION_NEW_NOTE`).
2. **Layout**: create `res/layout/<name>_widget.xml` with root id `@+id/widget_root`, `android:background="@drawable/widget_background"`, and shared dimens/styles from `widget_styles.xml`.
3. **Info XML**: create `res/xml/<name>_widget_info.xml` (`<appwidget-provider>` pointing at the layout).
4. **Manifest**: add a `<receiver android:exported="true">` with the `APPWIDGET_UPDATE` intent-filter and `android.appwidget.provider` meta-data pointing at the info XML.

Golden rule #1 applies to quick-add style widgets: the click intent must be `ACTION_NEW_NOTE` — always a new note.
