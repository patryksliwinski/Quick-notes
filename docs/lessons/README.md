# Lessons Log

Self-improving log for this repo. High-signal only: a lesson goes here **only when it is non-obvious and would have changed the outcome if known earlier**. Never one per PR to tick a box — noise buries the valuable entries.

## Index (grouped by topic, newest first within each group)

### Storage, SAF & durability

| Date | Lesson | File |
|---|---|---|
| `2026-10-30` | [The coverage gate must not exclude the buggy surface; fakes must model silent empties, not only throws — narrow Kover exclusions to device-only UI, extract entry-point logic into pure testable functions, add an empty-result fake (issue #60)](2026-10-30-coverage-gate-must-not-exclude-the-buggy-surface.md) |
| `2026-10-30` | [A journaled-but-not-yet-written save is durable, not an error — `writeNote` returns `SaveOutcome` and only a failed cold-start flush raises `StorageError`, so leaving the editor no longer shows a phantom "could not save"](2026-10-30-journaled-save-is-not-a-save-error.md) || `2026-10-30` | [A null SAF cursor/stream means "volume unavailable", never "empty folder" — throw so the list falls back with a banner and keeps the last known notes; sanitize non-positive `COLUMN_LAST_MODIFIED` (issue #59)](2026-10-30-saf-null-cursor-means-unavailable-not-empty.md) |
| `2026-10-30` | [Durability path must be synchronous (`commit()`, not `apply()`); SAF failures must throw, never return `""` — silent empty reads get autosaved over real files (issue #58)](2026-10-30-journal-record-must-commit-saf-failures-must-throw.md) |
| `2026-10-30` | [One NoteRepository per process, shared by all ViewModels via an app-scoped holder — a repository owning a mutable index is a single source of truth only if there is one instance (issue #57)](2026-10-30-one-note-repository-per-process-shared-by-viewmodels.md) |
| `2026-10-30` | [Serialized note writes: per-URI mutex in `NoteRepository` because cancelled jobs still finish their IO write; `saveNow` commits via `saveJob`; tests quiesce before `resetMain` and survive the tmp→rename window (PR #39)](2026-10-30-serialized-note-writes-mutex-and-noncancelable-save.md) |
| `2026-10-30` | [Flaky tests traced to source: repository-started broadcasts outlive the Robolectric sandbox and poison unrelated `runTest` tests (`isActive` guard + contained send), and the editor's `loadFailed` flag raced the generation guard — decide flags from the loaded value before acting (PR #84)](2026-10-30-flaky-tests-leaked-broadcast-and-load-flag-race.md) |
| `2026-10-30` | [SLO quick-leave: journal pending saves and flush on cold start; SAF writes never delete-before-rename (`.bak` rollback, orphan dotfiles healed on scan); one refresh in flight at a time (issue #30)](2026-10-30-slo-quick-leave-durability-and-saf-write-ordering.md) |
| `2026-10-30` | [SAF head reads are cached keyed by `(uri, lastModified, size)` — `listNotes()` returns metadata only, refresh re-reads only changed files; autosave updates the list in place and coalesces widget refreshes (issue #29)](2026-10-30-saf-head-reads-cached-keyed-by-mtime-size.md) |
| `2026-10-30` | [One-line notes must have an empty snippet (no title duplication); failed/empty SAF head reads must not be cached, so SD-card rows recover on the next refresh (issue #43)](2026-10-30-one-line-note-snippet-empty-failed-head-not-cached.md) |
| `2026-10-30` | [SAF `createDocument` needs the tree's document URI, not the tree URI — wrong parent URI silently falls back to app-private storage on SD cards (issue #23)](2026-10-30-saf-create-document-needs-document-uri.md) |
| `2026-10-29` | [Every SAF store call can fail at runtime on removable volumes — the repository wraps all calls, surfaces `NoteError` as a snackbar and falls back to app-private storage; `createDocument` needs the document URI, persisted write permission must be re-checked, and null cursors must throw, never return `""` (issues #16, #23 follow-ups)](2026-10-29-saf-failures-never-crash-fail-loudly.md) |
| `2026-10-29` | [SAF I/O failures must never crash the app — repository wraps store calls, surfaces `NoteError` as a snackbar, falls back to app-private storage](2026-10-29-saf-io-failures-must-never-crash.md) |

### App behavior & quick path

| Date | Lesson | File |
|---|---|---|
| `2026-10-30` | [Inbound quick capture (share/shortcut/duplicate) reuses optimistic create + debounced prefill; never pair prefill with an async read of the new empty file (issue #31)](2026-10-30-inbound-quick-capture-share-shortcut-duplicate.md) |

### Widgets & home screen

| Date | Lesson | File |
|---|---|---|
| `2026-10-30` | [Widget rows must not rely on `0dp`+`layout_weight` when sibling rows are GONE — visible rows collapse to zero height and content becomes invisible; always bind every row slot and read height via both min/max options (issue #34)](2026-10-30-widget-rows-must-not-rely-on-weight.md) |
| `2026-10-29` | [Widget RemoteViews traps: never load note data on the main thread in `onUpdate` (`goAsync` + `Dispatchers.IO` + a pure `widgetRows` mapping); resizable widgets need `onAppWidgetOptionsChanged` plus a pure dp→rows function reading both min/max height options; `0dp`+`weight` rows collapse when siblings are GONE; swipe background must be gated on `dismissDirection != Settled` (issues #9, #10, #14, #25, #34)](2026-10-29-widget-remoteviews-layout-traps.md) |
| `2026-10-29` | [Recent-notes widget: `goAsync` off the main thread, fixed 5-row layout fed by a pure `widgetRows` mapping, package-scoped refresh broadcast from `NoteRepository`](2026-10-29-recent-notes-widget-goasync-collection.md) |
| `2026-10-29` | [Widgets share a base provider; adding one is a 4-step checklist](2026-10-29-shared-widget-base-4-step-pattern.md) |

### UI, list & editor

| Date | Lesson | File |
|---|---|---|
| `2026-10-30` | [Full-content search: `NoteMeta` carries the full `content`, cached per `(uri, mtime, size)` — one full read per *changed* note, title/snippet derived from the same read; search stays a pure in-memory match (issue #83)](2026-10-30-full-content-search-needs-full-read-per-changed-note.md) |
| `2026-10-30` | [List upgrades (sort/day groups/count/filename search/confirm-delete) are pure functions over the in-memory `NoteMeta` index; day grouping uses a LinkedHashMap so headers don't fragment under non-chronological sort (issue #33)](2026-10-30-list-upgrades-stay-in-memory-index.md) |
| `2026-10-30` | [Editor undo snapshots live in memory only; word count derives from the content flow; disk-touching prefs reads go through `Dispatchers.IO` (issue #32)](2026-10-30-editor-undo-stack-in-memory-only.md) |
| `2026-10-30` | [UI polish is a resource-level pass: externalize all strings + content descriptions, dark theme needs both Compose `colorScheme` and a `values-night` window theme (issue #28)](2026-10-30-ui-polish-issue-28.md) |
| `2026-10-29` | [Swipe-to-delete failures must surface as a snackbar, never a crash (issue #15)](2026-10-29-delete-failures-snackbar-never-crash.md) |

### CI, release & build

| Date | Lesson | File |
|---|---|---|
| `2026-10-30` | [`grep -q` mid-pipe is a no-op (downstream filters get empty input) and YAML block scalars need single regex backslashes — the main-thread I/O golden-rule check could never fire; fixed to `grep -E ... | grep -vq ...` and smoke-tested (issue #48)](2026-10-30-ci-grep-q-swallows-pipe-no-op.md) |
| `2026-10-30` | [CI caches Gradle via `gradle/actions/setup-gradle` in every build job — dependency + wrapper caches, no project change needed; warm runs drop from ~4–5 min to ~1–2 min (issue #47)](2026-10-30-ci-gradle-caching-setup-gradle.md) |
| `2026-10-30` | [One CI workflow per pipeline — never duplicate the Gradle build across workflows; pass the verdict via `needs.<job>.result`, never re-run (issue #46)](2026-10-30-single-ci-workflow-no-duplicate-builds.md) |
| `2026-10-29` | [Release pipeline: dedicated `release.yml` with `contents: write` scoped to the release job; the APK must be signed (ephemeral CI keystore + `apksigner`) — an unsigned APK is rejected as corrupt; notes always generated from `git log <prev-tag>..HEAD` via `--notes-file` (issue #5 follow-ups)](2026-10-29-release-pipeline-signing-and-notes.md) |
| `2026-10-29` | [Release notes always generated from commits since the previous tag and attached via `--notes-file`](2026-10-29-release-notes-always-generated.md) |
| `2026-10-29` | [Release APK must be signed — unsigned APK is rejected as corrupt; sign with an ephemeral CI keystore](2026-10-29-release-apk-must-be-signed.md) |

### Process & the lessons log itself

| Date | Lesson | File |
|---|---|---|
| `2026-10-30` | [Change every mention of a rule in the same PR (a rule softened in one doc but stale in another never takes effect); never commit conflict markers; keep the log high-signal with a topic index and fold anything fully restated in the canonical docs (issue #50 follow-ups)](2026-10-30-lessons-log-keep-it-high-signal.md) |
| `2026-10-30` | [A `docs/lessons/` change is incomplete until the index matches disk — PR #67 left 12 dead links and 4 unindexed files; the `lessons-index` CI job now fails any PR that desyncs them (issue #73)](2026-10-30-lessons-index-must-match-disk.md) |
| `2026-10-30` | [Lessons are required only when non-obvious, not on every PR — a required-per-PR artifact becomes noise and buries the valuable entries (issue #50)](2026-10-30-lessons-only-when-non-obvious.md) |
| `2026-10-30` | [Copilot review request never produced reviews (0/25 PRs) — step removed; self-review now happens only after the final head's CI is green (`gh pr checks --watch`), merge on final head only; `edited` no longer triggers CI/review on PRs (issue #49)](2026-10-30-copilot-review-request-never-produced-reviews.md) |

## How to add a lesson

Create `docs/lessons/YYYY-MM-DD-short-slug.md` with:

```markdown
# YYYY-MM-DD — Short title
- **Context:** what task/PR prompted this
- **Lesson:** the takeaway in one or two sentences
- **Rule now:** the concrete rule added to AGENTS.md / docs / code (if any)
```

Then add a row to the matching topic table in the index above (newest first within the group).

Rules:

- **Only when non-obvious**: a perf trap, an API gotcha, a UX decision, a CI failure, a review finding, a decision you'd make differently — something that would have changed the outcome if known earlier. If the PR changed no rule and taught nothing surprising, add nothing.
- Exception: a PR that adds or changes a rule in `AGENTS.md` / `docs/WORKFLOW.md` always gets a lesson explaining why.
- **The index must match disk**: every `docs/lessons/*.md` file is linked exactly once in the index, and no index row points at a file that doesn't exist. Update the index in the same PR that adds, renames, or deletes lesson files — the `lessons-index` job in `.github/workflows/pr.yml` fails the PR otherwise.
- Keep the log small: when it grows past ~40 entries, delete or fold noise entries (fold recurring ones into `AGENTS.md` / `docs/ARCHITECTURE.md` and mark the file `(folded)` in the index). Update `docs/WORKFLOW.md` if the process itself changes.
