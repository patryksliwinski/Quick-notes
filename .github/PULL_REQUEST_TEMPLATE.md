## What & why
- (2–3 bullets: what changed, why)

## Verification
- [ ] `./gradlew testDebugUnitTest` run
- [ ] `./gradlew :app:koverVerifyDebug` run — CI enforces the >= 80% line/branch gate
- [ ] `./gradlew assembleDebug` run
- [ ] `./gradlew lintDebug` adds no new warnings — CI enforces
- Deviations from the above: (one line, or "none") — no pasted outputs, CI logs are the record

## Golden rules (only the ones machines can't check)
- [ ] Quick path (widget/tile/`+` → new note → editor) untouched and still dialog-free
- [ ] Storage layer stays sync-agnostic (no Syncthing/conflict/trash logic)
- [ ] Strings externalized; no hardcoded UI text

## Lesson
- (one line + a file under `docs/lessons/` if this PR taught something non-obvious; omit otherwise — rule-changing PRs always get one)
- [ ] If this PR adds/renames/deletes files under `docs/lessons/`, the index in `docs/lessons/README.md` is updated in the same PR (CI `lessons-index` job fails otherwise)

## Self-review
- [ ] Self-reviewed the diff against `AGENTS.md`; findings fixed in this branch (post them as PR comments only if there were actual findings)

## Screenshots / demo
- Attach if UI changed
