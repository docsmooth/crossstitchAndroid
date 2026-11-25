# ThumbStitch

An offline Android cross-stitch prototype centered on drawing each diagonal leg,
not tapping cells to paint. This repository currently contains the application
bootstrap, not a playable game.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the ThumbStitch architecture and milestone plan.

## Build and test

Install JDK 17 and the Android SDK with platform 35, and configure `ANDROID_HOME`
or an Android Studio `local.properties` file. From the repository root:

```sh
./gradlew assembleDebug testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/`. The minimum supported
Android version is API 26. CI runs the same build and unit tests.

The app is a single Kotlin/Compose module. Add `domain`, `input`, `rendering`,
`persistence`, and `ui` packages under `com.docsmooth.thumbstitch` as their
corresponding issues are implemented. The domain and recognition code must not
depend on Android UI or Ink types. AndroidX Ink is intentionally deferred to the
separate spike, which will select and document its version and modules.

The initial build uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, the Compose
compiler plugin, and the Compose BOM 2024.12.01. Compose provides the application
UI; AndroidX Activity hosts it; JUnit and AndroidX test libraries support unit and
instrumented tests. Update pinned versions deliberately and validate compatibility.
