# 2026-10-29 — Release APK via GitHub Releases workflow

- **Context:** Issue #5 — publish the built APK to GitHub Releases so users can install without building from source.
- **Lesson:** A separate `release.yml` (tag `v*` push or `workflow_dispatch`) keeps release publishing out of the PR CI path; `contents: write` is scoped to the release job only, and `gh release create` with `GITHUB_TOKEN` needs no extra secrets for unsigned builds. Releases are unsigned here (`app-release-unsigned.apk`) — signing would need keystore secrets, which is a deliberate follow-up, not a blocker.
- **Rule now:** Release pipeline documented in the `docs/WORKFLOW.md` pipelines table; tag pushes publish the APK automatically.
