# 2026-10-29 — SwipeToDismissBox background must be state-gated

- **Context:** Issue #14 — a newly created note rendered with the red "Note deleted" swipe background visible.
- **Lesson:** `SwipeToDismissBox` always composes `backgroundContent` in a full-size `Row` behind the content, regardless of swipe state. Because note rows are transparent, the background shows through on any settled row (a freshly prepended note included). The background must be gated on `state.currentValue != Settled` inside `backgroundContent`, not assumed to be hidden when idle.
- **Rule now:** In `NotesListScreen.kt`, `backgroundContent` renders only when `dismissState.dismissDirection != SwipeToDismissBoxValue.Settled` (any non-zero offset shows the background; idle offset 0 hides it).
