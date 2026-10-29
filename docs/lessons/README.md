# Lessons Log

Self-improving log for this repo. 

## Index (newest first)

| Date | Lesson | File |
|---|---|---|
| `2026-10-29` | [PR review is layered: fast static gate + AI agent review + human](2026-10-29-ai-agent-pr-review.md) |
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
