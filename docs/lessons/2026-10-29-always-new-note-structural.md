# 2026-10-29 — "Always a new note" must be structural, not a preference

- **Context:** Requirement that quick-note entry points never reopen the same note.
- **Lesson:** Implement it as a hard-coded intent action (`ACTION_NEW_NOTE`) whose handler unconditionally creates a note and opens the editor — no "current note" concept anywhere in the quick path.
- **Rule now:** AGENTS.md golden rule #1.
