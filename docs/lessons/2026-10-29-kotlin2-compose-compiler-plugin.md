# 2026-10-29 — Kotlin 2.0 requires the Compose Compiler Gradle plugin, not composeOptions

- **Context:** First build failed with "Starting in Kotlin 2.0, the Compose Compiler Gradle plugin is required".
- **Lesson:** With Kotlin ≥ 2.0, `composeOptions { kotlinCompilerExtensionVersion }` is invalid; apply `org.jetbrains.kotlin.plugin.compose` and drop the extension version. Also pin a known-good Kotlin/Compose-BOM pairing (here Kotlin 2.0.20 + compose-bom 2024.10.01).
- **Rule now:** AGENTS.md build section lists the verified toolchain versions.
