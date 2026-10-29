# 2026-10-29 — Releasing a new version requires a version-bump PR on main before tagging

- **Context:** Cutting v1.1.0 — main still had `versionName = "1.0.1"` / `versionCode = 2` because past releases (v1.0.0, v1.0.1) were tagged without bumping the version first.
- **Lesson:** `release.yml` publishes whatever the tagged commit builds, so the tag must point at a commit whose `app/build.gradle.kts` already carries the new `versionCode`/`versionName`. Bumping on a short-lived branch, merging to `main` via PR, then tagging `main` keeps the invariant "every tag's APK reports the tag's version" without any push to `main`.
- **Rule now:** New release procedure — bump `versionCode`/`versionName` in `app/build.gradle.kts` on a `vibe/release-vX.Y.Z-*` branch, open a PR to `main` and merge it, then `git tag vX.Y.Z` on `main` and push the tag; `release.yml` builds, signs, and publishes the GitHub Release with auto-generated notes.
