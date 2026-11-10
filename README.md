# Zip2Git

Android app that compares a ZIP archive with a GitHub branch and pushes the detected changes as **one Git commit**.

## Features
- Kotlin + Jetpack Compose dark UI, English and Thai (`values/`, `values-th/`)
- GitHub PAT encrypted with an AES-256-GCM key in the Android Keystore (no deprecated `security-crypto`)
- All repositories (push access) and branches, with pagination; the repo's default branch is preselected
- ZIP import via Storage Access Framework; files are extracted to the app cache one by one (no whole-archive-in-RAM), size limits enforced *while* reading
- Path safety: rejects `..`, absolute paths, drive letters, control characters; detects duplicates ignoring case; skips `__MACOSX/`, `.DS_Store`, `Thumbs.db`, `.git/`
- Root-folder handling for GitHub "Download ZIP" archives (`repo-main/…`): auto-detected, toggle to strip
- Smart comparison by Git blob SHA-1: ADD / MODIFY / DELETE / UNCHANGED; existing file modes (e.g. executable `100755`) are preserved
- Safety: mass-delete warning, explicit delete confirmation, and the branch head is re-checked before pushing (aborts if someone committed in between)
- Push: parallel blob upload with progress, retry with backoff on rate limits / 5xx, then tree → commit → fast-forward ref update
- State lives in a `ViewModel`: rotation no longer cancels a running push or loses the comparison
- Unit tests for Git SHA-1, ZIP scanning/limits/path rules and diffing; CI runs them before building

## Build
Requires JDK 17, Android SDK 35 and Gradle 8.9 (AGP 8.7.3).

```bash
gradle wrapper --gradle-version 8.9   # once, then commit gradlew + gradle/wrapper/*
./gradlew testDebugUnitTest assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

### Release build
`assembleRelease` enables R8. It is signed when these environment variables are set (use GitHub Secrets in CI): `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## GitHub token
Create a fine-grained Personal Access Token limited to the target repositories with **Contents: Read and write**. The token is only ever sent to `api.github.com`.

## Known limits
- Individual files ≤ 25 MB, whole ZIP ≤ 100 MB after extraction, ≤ 20 000 files.
- Empty repositories (no commit yet) and creating new branches are not supported.
- Git LFS pointers and submodules are not handled specially; renames appear as delete + add.
- Before publishing to Google Play, check the target API level Play currently requires and update Compose BOM / AGP / Kotlin.
