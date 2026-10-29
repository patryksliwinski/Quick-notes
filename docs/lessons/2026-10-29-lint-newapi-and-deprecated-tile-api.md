# 2026-10-29 — CI lint caught real minSdk bugs the compiler didn't: readNBytes (API 33) and TileService deprecation
- **Context:** PR #1 CI `lintDebug` failed with 4 errors after the setup-android action was fixed.
- **Lesson:** `InputStream.readNBytes` requires API 33 but minSdk is 26 — replace with a manual `read(buf, off, len)` loop. `TileService.startActivityAndCollapse(Intent)` is deprecated in favor of the `PendingIntent` overload (API 34+); on older devices the deprecated call is unavoidable, so gate on `Build.VERSION.SDK_INT >= UPSIDE_DOWN_CAKE` and disable just that lint check (`StartActivityAndCollapseDeprecated`) rather than suppressing broadly. Always run `lintDebug` locally before pushing — it catches `NewApi` issues that compile fine.
- **Rule now:** AGENTS.md build/verify section includes `./gradlew lintDebug` as a required gate; CI runs it and fails on errors.

## Follow-up (same day)
The `pr-review.yml` static grep flagged `Dispatchers.Main` as "main-thread I/O" even though the only use is a legitimate `withContext(Dispatchers.Main)` hop for a UI callback. Refined the check to allow that pattern while still catching `runBlocking`/`Thread.sleep`/bare `Dispatchers.Main` launches.
