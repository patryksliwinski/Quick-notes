# 2026-10-29 — Widgets share a base provider; adding one is a 4-step checklist

- **Context:** Issue #10 — prepare shared home-screen widget infrastructure so the planned quick-add and recent-5-notes widgets are not copy-pasted from `NewNoteWidgetProvider`.
- **Lesson:** One abstract `BaseWidgetProvider` (RemoteViews + shared `widget_background` + `PendingIntent` attachment + `AppWidgetManager` push) plus shared dimens/colors/styles means a new widget is exactly 4 steps: provider extending the base, layout, `res/xml/<name>_widget_info.xml`, manifest `<receiver>`. Data-driven widgets must load notes off the main thread (`goAsync` + `Dispatchers.IO` via `WidgetNoteLoader`), never inline in `onUpdate`.
- **Rule now:** The 4-step checklist lives in `docs/ARCHITECTURE.md` ("Adding a new widget"); quick-add widgets must keep golden rule #1 (`ACTION_NEW_NOTE`, always a new note).
