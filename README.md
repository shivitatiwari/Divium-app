# Divium

Divium is a mobile-first Android IDE: editor, workspace, runtimes, terminal, Git, extensions and project intelligence in one application.

## Current milestone

**Part 1 — Android foundation**

- Kotlin + Jetpack Compose application targeting Android API 36
- Edge-to-edge, touch-first onboarding shell for Moto Edge 60 Pro
- Language-pack selection and workspace-location choice modeled in the UI
- Foundation theme, app structure, unit tests, and GitHub Actions debug-build CI

This is deliberately not presented as a working runtime, terminal, editor, or Git client yet. Those are subsequent milestones and will be implemented behind stable core APIs rather than mocked.

## Android baseline

- Application ID: `com.divium.ide`
- minSdk: 28
- targetSdk / compileSdk: 36
- Java: 17
- Kotlin: 2.0.21
- Android Gradle Plugin: 8.9.2

## Build

The GitHub Actions workflow runs:

```bash
gradle test
gradle lint
gradle :app:assembleDebug
```

and retains the generated debug APK as an Actions artifact.

## Planned milestones

1. Android foundation and build CI
2. Workspace/filesystem model and project persistence
3. Embedded execution runtime + PTY terminal proof
4. File manager and code editor
5. Project intelligence, runtime packs, preview and process manager
6. Git, GitHub, themes and extension/Codex integration
7. Hardening, device validation and release pipeline

## Storage model

Divium will support two workspace modes:

1. Divium-managed private storage for maximum POSIX/runtime compatibility.
2. A user-selected Android document-tree folder, persisted with the Storage Access Framework.

The external-folder synchronization/runtime bridge is part of the workspace milestone.

## Project status

The canonical source repository is `shivitatiwari/Divium-app`.
