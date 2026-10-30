# Lessons Log

Self-improving log for this repo. 

## Index (newest first)

| Date | Lesson | File |
|---|---|---|
| `2026-10-30` | [List upgrades (sort/day groups/count/filename search/confirm-delete) are pure functions over the in-memory `NoteMeta` index; day grouping uses a LinkedHashMap so headers don't fragment under non-chronological sort (issue #33)](2026-10-30-list-upgrades-stay-in-memory-index.md) |
| `2026-10-30` | [UI polish is a resource-level pass: externalize all strings + content descriptions, dark theme needs both Compose `colorScheme` and a `values-night` window theme (issue #28)](2026-10-30-ui-polish-issue-28.md) |
| `2026-10-30` | [Widget rows must not rely on `0dp`+`layout_weight` when sibling rows are GONE — visible rows collapse to zero height and content becomes invisible; always bind every row slot and read height via both min/max options (issue #34)](2026-10-30-widget-rows-must-not-rely-on-weight.md) |
| `2026-10-30` | [Resizable widgets must override `onAppWidgetOptionsChanged` and derive layout from a pure dp→rows function; weighted `0dp` rows stretch content to fill the widget (issue #25)](2026-10-30-resizable-widgets-options-changed.md) |
| `2026-10-30` | [SAF `createDocument` needs the tree's document URI, not the tree URI — wrong parent URI silently falls back to app-private storage on SD cards (issue #23)](2026-10-30-saf-create-document-needs-document-uri.md) |
| `2026-10-29` | [SAF I/O failures must never crash the app — repository wraps store calls, surfaces `NoteError` as a snackbar, falls back to app-private storage](2026-10-29-saf-io-failures-must-never-crash.md) |
| `2026-10-29` | [Recent-notes widget: `goAsync` off the main thread, fixed 5-row layout fed by a pure `widgetRows` mapping, package-scoped refresh broadcast from `NoteRepository`](2026-10-29-recent-notes-widget-goasync-collection.md) |
| `2026-10-29` | [Releasing a new version requires a version-bump PR on main before tagging](2026-10-29-release-version-bump-needs-pr.md) |
| `2026-10-29` | [SwipeToDismissBox background is always composed — gate it on `dismissDirection != Settled` or it shows through transparent rows](2026-10-29-swipe-background-must-be-state-gated.md) |
| `2026-10-29` | [Widgets share a base provider; adding one is a 4-step checklist](2026-10-29-shared-widget-base-4-step-pattern.md) |
| `2026-10-29` | [Release notes always generated from commits since the previous tag and attached via `--notes-file`](2026-10-29-release-notes-always-generated.md) |
| `2026-10-29` | [Swipe-to-delete failures must surface as a snackbar, never a crash (issue #15)](2026-10-29-delete-failures-snackbar-never-crash.md) |
| `2026-10-29` | [80% line+branch coverage is enforced with Kover (`koverVerifyDebug`), not JaCoCo; device-only classes are excluded from the gate](2026-10-29-kover-80-percent-coverage-gate.md) |
| `2026-10-29` | [Release APK must be signed — unsigned APK is rejected as corrupt; sign with an ephemeral CI keystore](2026-10-29-release-apk-must-be-signed.md) |
| `2026-10-29` | [Release APK published to GitHub Releases via a dedicated `release.yml` (unsigned for now)](2026-10-29-release-apk-github-releases-workflow.md) |
| `2026-10-29` | [CI lint caught minSdk bugs: `readNBytes` needs API 33; tile API deprecated](2026-10-29-lint-newapi-and-deprecated-tile-api.md) |
| `2026-10-29` | [`actions/setup-android` moved to `android-actions/setup-android@v4`](2026-10-29-ci-setup-android-action-moved.md) |
| `2026-10-29` | [No human review: the agent self-reviews, comments, then fixes](2026-10-29-agent-self-review-no-human-gate.md) |
| `2026-10-29` | [PR review is layered: fast static gate + AI agent review (no human gate)](2026-10-29-ai-agent-pr-review.md) |
| `2026-10-29` | [Lessons live in a directory, not one big file](2026-10-29-lessons-as-directory.md) |
| `2026-10-29` | [Sandbox GitHub credentials are read-only; use the gh credential helper for git](2026-10-29-sandbox-github-push-credentials.md) |
| `2026-10-29` | [Kotlin 2.0 requires the Compose Compiler Gradle plugin, not composeOptions](2026-10-29-kotlin2-compose-compiler-plugin.md) |
| `2026-10-29` | [SAF folders need persisted write permission checks, not just the URI](2026-10-29-saf-persisted-permission-check.md) |
| `2026-10-29` | [FileObserver doesn't work on SAF paths; rescan on resume instead](2026-10-29-no-fileobserver-on-saf.md) |
| `2026-10-29` | ["Always a new note" must be structural, not a preference](2026-10-29-always-new-note-structural.md) |
| `2026-10-29` | [Empty repo edge cases: default branch and PR creation order](2026-10-29-empty-repo-pr-ordering.md) |

## How to add a lesson

Create `docs/lessons/YYYY-MM-DD-short-slug.md` with:

```markdown
# YYYY-MM-DD — Short title
- **Context:** what task/PR prompted this
- **Lesson:** the takeaway in one or two sentences
- **Rule now:** the concrete rule added to AGENTS.md / docs / code (if any)
```

Then add a row to the index above. Update `docs/WORKFLOW.md` if the process itself changes. Periodically fold recurring lessons into `AGENTS.md` / `docs/ARCHITECTURE.md` and mark the file `(folded)` in the index.
