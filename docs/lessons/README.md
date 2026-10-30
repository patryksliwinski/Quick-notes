# Lessons Log

Self-improving log for this repo. 

## Index (newest first)

| Date | Lesson | File |
|---|---|---|
| `2026-10-30` | [One CI workflow per pipeline — never duplicate the Gradle build across workflows; pass the verdict via `needs.<job>.result`, never re-run (issue #46)](2026-10-30-single-ci-workflow-no-duplicate-builds.md) |
| `2026-10-30` | [Lessons log cleaned after the rule change: noise entries from the per-PR era removed, high-signal entries kept (issue #50 follow-up)](2026-10-30-lessons-log-cleanup-after-rule-change.md) |
| `2026-10-30` | [Lessons are required only when non-obvious, not on every PR — a required-per-PR artifact becomes noise and buries the valuable entries (issue #50)](2026-10-30-lessons-only-when-non-obvious.md) |
| `2026-10-30` | [One-line notes must have an empty snippet (no title duplication); failed/empty SAF head reads must not be cached, so SD-card rows recover on the next refresh (issue #43)](2026-10-30-one-line-note-snippet-empty-failed-head-not-cached.md) |
| `2026-10-30` | [SLO quick-leave: journal pending saves and flush on cold start; SAF writes never delete-before-rename (`.bak` rollback, orphan dotfiles healed on scan); one refresh in flight at a time (issue #30)](2026-10-30-slo-quick-leave-durability-and-saf-write-ordering.md) |
| `2026-10-30` | [SAF head reads are cached keyed by `(uri, lastModified, size)` — `listNotes()` returns metadata only, refresh re-reads only changed files; autosave updates the list in place and coalesces widget refreshes (issue #29)](2026-10-30-saf-head-reads-cached-keyed-by-mtime-size.md) |
| `2026-10-30` | [Inbound quick capture (share/shortcut/duplicate) reuses optimistic create + debounced prefill; never pair prefill with an async read of the new empty file (issue #31)](2026-10-30-inbound-quick-capture-share-shortcut-duplicate.md) |
| `2026-10-30` | [List upgrades (sort/day groups/count/filename search/confirm-delete) are pure functions over the in-memory `NoteMeta` index; day grouping uses a LinkedHashMap so headers don't fragment under non-chronological sort (issue #33)](2026-10-30-list-upgrades-stay-in-memory-index.md) |
| `2026-10-30` | [UI polish is a resource-level pass: externalize all strings + content descriptions, dark theme needs both Compose `colorScheme` and a `values-night` window theme (issue #28)](2026-10-30-ui-polish-issue-28.md) |
| `2026-10-30` | [Widget rows must not rely on `0dp`+`layout_weight` when sibling rows are GONE — visible rows collapse to zero height and content becomes invisible; always bind every row slot and read height via both min/max options (issue #34)](2026-10-30-widget-rows-must-not-rely-on-weight.md) |
| `2026-10-30` | [Resizable widgets must override `onAppWidgetOptionsChanged` and derive layout from a pure dp→rows function; weighted `0dp` rows stretch content to fill the widget (issue #25)](2026-10-30-resizable-widgets-options-changed.md) |
| `2026-10-30` | [SAF `createDocument` needs the tree's document URI, not the tree URI — wrong parent URI silently falls back to app-private storage on SD cards (issue #23)](2026-10-30-saf-create-document-needs-document-uri.md) |
| `2026-10-29` | [SAF I/O failures must never crash the app — repository wraps store calls, surfaces `NoteError` as a snackbar, falls back to app-private storage](2026-10-29-saf-io-failures-must-never-crash.md) |
| `2026-10-29` | [Recent-notes widget: `goAsync` off the main thread, fixed 5-row layout fed by a pure `widgetRows` mapping, package-scoped refresh broadcast from `NoteRepository`](2026-10-29-recent-notes-widget-goasync-collection.md) |
| `2026-10-29` | [SwipeToDismissBox background is always composed — gate it on `dismissDirection != Settled` or it shows through transparent rows](2026-10-29-swipe-background-must-be-state-gated.md) |
| `2026-10-29` | [Widgets share a base provider; adding one is a 4-step checklist](2026-10-29-shared-widget-base-4-step-pattern.md) |
| `2026-10-29` | [Release notes always generated from commits since the previous tag and attached via `--notes-file`](2026-10-29-release-notes-always-generated.md) |
| `2026-10-29` | [Swipe-to-delete failures must surface as a snackbar, never a crash (issue #15)](2026-10-29-delete-failures-snackbar-never-crash.md) |
| `2026-10-29` | [80% line+branch coverage is enforced with Kover (`koverVerifyDebug`), not JaCoCo; device-only classes are excluded from the gate](2026-10-29-kover-80-percent-coverage-gate.md) |
| `2026-10-29` | [Release APK must be signed — unsigned APK is rejected as corrupt; sign with an ephemeral CI keystore](2026-10-29-release-apk-must-be-signed.md) |
| `2026-10-29` | [Release APK published to GitHub Releases via a dedicated `release.yml` (unsigned for now)](2026-10-29-release-apk-github-releases-workflow.md) |
| `2026-10-29` | [CI lint caught minSdk bugs: `readNBytes` needs API 33; tile API deprecated](2026-10-29-lint-newapi-and-deprecated-tile-api.md) |
| `2026-10-29` | [Kotlin 2.0 requires the Compose Compiler Gradle plugin, not composeOptions](2026-10-29-kotlin2-compose-compiler-plugin.md) |
| `2026-10-29` | [SAF folders need persisted write permission checks, not just the URI](2026-10-29-saf-persisted-permission-check.md) |
| `2026-10-29` | [FileObserver doesn't work on SAF paths; rescan on resume instead](2026-10-29-no-fileobserver-on-saf.md) |
| `2026-10-29` | ["Always a new note" must be structural, not a preference](2026-10-29-always-new-note-structural.md) |

## How to add a lesson

Create `docs/lessons/YYYY-MM-DD-short-slug.md` with:

```markdown
# YYYY-MM-DD — Short title
- **Context:** what task/PR prompted this
- **Lesson:** the takeaway in one or two sentences
- **Rule now:** the concrete rule added to AGENTS.md / docs / code (if any)
```

Then add a row to the index above. Update `docs/WORKFLOW.md` if the process itself changes. Periodically fold recurring lessons into `AGENTS.md` / `docs/ARCHITECTURE.md` and mark the file `(folded)` in the index.
