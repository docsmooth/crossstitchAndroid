# Copilot instructions

ThumbStitch is an offline Android cross-stitch prototype where users draw each diagonal leg of a stitch (not tap-to-paint). See `docs/ARCHITECTURE.md` for the architecture and milestones.

- Single Kotlin/Jetpack Compose module `:app`, package `com.docsmooth.thumbstitch`, minSdk 26, JDK 17.
- Build and test: `./gradlew assembleDebug testDebugUnitTest` (requires the Android SDK, platform 35).
- Organize code into `domain`, `input`, `rendering`, `persistence`, and `ui` packages. Domain and recognition code must not depend on Android UI or AndroidX Ink types.
- AndroidX Ink is deferred to a separate spike; do not add it otherwise.
- Pinned versions (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01) are changed deliberately and validated.
- Add unit tests for domain logic under `app/src/test`.
