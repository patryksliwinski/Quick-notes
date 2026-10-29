# 2026-10-29 — Release notes always generated for every release

- **Context:** The release workflow published a generic "Automated signed release" body, so releases carried no information about what changed.
- **Lesson:** `actions/checkout` needs `fetch-depth: 0` in the release job to see tags; release notes should be generated from `git log <prev-tag>..HEAD` (merge commits condensed to `(#N) title`) and passed to `gh release create --notes-file`, with an explicit fallback line when the range is empty (e.g. a re-run of the same tag). That guarantees every release has meaningful notes without manual editing.
- **Rule now:** `release.yml` always generates notes from commits since the previous tag and publishes them with the APK; pipelines table in `docs/WORKFLOW.md` updated.
