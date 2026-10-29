# 2026-10-29 — Empty repo edge cases: default branch and PR creation order

- **Context:** PR #1 creation failed with "No commits between main and head" because `main` didn't exist yet.
- **Lesson:** When the repo is empty, push the scaffold to `main` first, then create a feature branch with at least one commit ahead before calling `gh pr create`.
- **Rule now:** WORKFLOW.md branch rules.
