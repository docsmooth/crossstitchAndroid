# AndroidX Ink spike

Renders an in-progress finger stroke on a blank Compose screen. No game logic.

- **Ink version:** `1.0.0` (all modules).
- **Modules:** `androidx.ink:ink-authoring`, `ink-brush`, `ink-strokes`, `ink-rendering`.
- **Boundary:** Ink types are confined to `rendering/InkStrokeView`; it reports input as
  Ink-independent `StrokeSample`s through `input/StrokeListener`.
- **Run:** `./gradlew assembleDebug`, install `app/build/outputs/apk/debug/app-debug.apk`
  (also uploaded as a CI artifact) and draw with a finger.
- **Latency on a physical device:** _to be recorded here after manual testing_ (device, Android
  version, subjective/measured result).
- Note: the code could not be compiled in the authoring sandbox (no access to Google Maven); verify
  the build in CI and adjust API names if the Ink 1.0.0 surface differs.
