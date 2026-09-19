# Divium

Divium is a mobile-first Android IDE: editor, workspace, runtimes, terminal, Git, extensions and project intelligence in one application.

## Status

Milestone 1 establishes the Android foundation, branding, first-run setup, language-pack selection, workspace selection, persistence, and CI. Runtime execution, the full workspace explorer and PTY terminal are the next milestone.

## Android baseline

- Application ID: `com.divium.ide`
- minSdk: 28
- targetSdk: 36 (Android 16)
- compileSdk: 37
- AGP: 9.4.0
- Gradle: 9.6.x in CI
- Java: 17
- Jetpack Compose BOM: 2026.09.00

## Build

CI installs the pinned Gradle version and runs:

```bash
gradle test lint :app:assembleDebug
```

An official Gradle wrapper will be committed as part of the runtime/toolchain foundation milestone, once generated and verified by CI.

## Storage model

Divium supports two workspace modes:

1. Divium-managed private storage for maximum POSIX/runtime compatibility.
2. A user-selected Android document-tree folder, persisted with the Storage Access Framework.

The external-folder synchronization/runtime bridge is implemented in the workspace milestone.
