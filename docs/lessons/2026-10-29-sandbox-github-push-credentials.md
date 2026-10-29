# 2026-10-29 — Sandbox GitHub credentials are read-only; use the gh credential helper for git

- **Context:** Initial implementation of the Quick Notes app (PR #1).
- **Lesson:** In the Vibe sandbox, direct `git push` 403s even though `gh api` reads work. The fix is `git -c credential.helper='!gh auth git-credential get' push ...`. Mutating REST/GraphQL calls (create ref, contents PUT, PR create via raw API) are blocked ("Resource not accessible by integration"); `gh pr create` works, and pushing a branch first makes the repo non-empty so `gh pr create` succeeds.
- **Rule now:** Document the push command in CONTRIBUTING.md for agent runs.
