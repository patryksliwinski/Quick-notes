# Quick Notes

A minimal, fast Android app for quick markdown notes.

- Notes are plain `.md` files in a folder you choose (Storage Access Framework), or app-private storage by default — sync the folder with Syncthing, git, Dropbox, or anything else; the app doesn't care.
- One tap creates a **new** note: home-screen widget, quick-settings tile, or the `+` button. Never reopens the same note.
- List is instant: in-memory index of at most a few hundred files, search as you type, swipe to delete with undo.
- Editor is plain monospace text with debounced autosave (400 ms) off the main thread.
- Writes are atomic-ish: files are written directly via SAF; deletes are hard deletes.

## Build

Requires JDK 17 and Android SDK (compileSdk 35).

```bash
./gradlew assembleDebug
```

## Architecture

```
ui/        Compose screens: list, editor, settings, MainActivity
receiver/  Home-screen widgets: NewNoteWidgetProvider -> ACTION_NEW_NOTE, RecentNotesWidgetProvider (5 most recent notes) -> ACTION_OPEN_NOTE
tile/      Quick-settings tile -> ACTION_NEW_NOTE
data/      NoteStore (internal dir or SAF tree), NoteRepository, folder prefs
```
