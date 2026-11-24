# ThumbStitch Architectural Design Document

**Status:** Draft / Proposed
**Target:** Android 8.0+ (API 26+)
**Primary stack:** Kotlin, Jetpack Compose, AndroidX Ink
**Repository role:** Initial architecture and issue-planning document

## 1. Purpose

ThumbStitch is an Android cross-stitch game in which the player makes stitches by physically drawing each diagonal leg of an X with a finger or stylus. Existing cross-stitch games commonly reduce stitching to tapping or coloring a grid cell. This project treats the gesture itself as the core mechanic.

The first product question is deliberately narrow: **does drawing `/` and `\` across a fabric cell with a thumb feel enough like stitching to be satisfying?**

The architecture therefore optimizes the first release for interaction quality, deterministic game rules, offline operation, and easy iteration rather than accounts, commerce, large pattern catalogs, or social features.

## 2. Goals

### 2.1 MVP goals

1. Display a cross-stitch pattern on a square fabric grid.
2. Allow one-finger or stylus input to draw a diagonal stitch.
3. Recognize a valid `/` or `\` gesture within a single grid cell.
4. Snap an accepted gesture to a clean rendered thread segment.
5. Combine two opposite diagonal legs into a completed `X` stitch.
6. Reject or gracefully undo invalid gestures.
7. Support thread-color selection.
8. Support two-finger pan and pinch-to-zoom without accidentally stitching.
9. Save progress locally and restore it after app restart.
10. Provide subtle haptic feedback for accepted and rejected actions.
11. Ship with at least one small built-in test pattern, initially a roughly 20x20 heart.

### 2.2 Non-goals for MVP

- User accounts or cloud synchronization
- Multiplayer/social features
- Advertising or in-app purchases
- Photo-to-pattern conversion
- Pattern marketplace/catalog service
- DMC catalog completeness or inventory management
- Backstitch, French knots, quarter stitches, or specialty stitches
- Cross-platform support
- Network dependency
- Sophisticated game progression or achievements

These are possible later features but must not complicate validation of the stitching interaction.

## 3. User experience principles

### 3.1 The stitch is the game

The player should not merely tap a cell. A normal full cross stitch requires two deliberate diagonal gestures.

Example sequence:

1. Touch near the top-left corner of a cell.
2. Drag toward the bottom-right corner.
3. Release near that corner.
4. The app accepts and snaps the gesture to a clean `\` thread segment.
5. Touch near the bottom-left corner.
6. Drag toward the top-right corner.
7. The app accepts and renders `/` over the first leg.
8. The cell becomes complete and produces a subtle haptic tick.

The opposite drawing direction for either diagonal should also be valid.

### 3.2 Forgiving, not sloppy

The user is drawing with a thumb, which obscures the target. Recognition should be tolerant while preserving intentionality.

Initial tunable values:

- Start/end corner tolerance: ~30% of cell width
- Required diagonal traversal: >=70% of corner-to-corner distance
- Dominant movement direction must match one of the two diagonals
- A stroke may begin/end slightly outside the cell
- Gesture parameters must be constants/configuration, not scattered magic numbers

These thresholds are hypotheses to be tested on real phones.

### 3.3 Gesture ownership

- One pointer: stitch input
- Two or more pointers: viewport navigation
- Pinch: zoom
- Two-finger drag: pan

Once a second pointer appears, the active stitch candidate should be cancelled rather than accidentally committed.

## 4. Technology choices

### 4.1 Android application

Use **Kotlin + Jetpack Compose** rather than a game engine for the initial implementation.

Reasons:

- The application is primarily a 2D touch/drawing UI rather than a physics/simulation game.
- Compose provides the surrounding application UI and state model.
- Native Android input and haptics are directly available.
- It keeps the project conventional for Android tooling and GitHub Copilot-assisted development.

### 4.2 Ink input/rendering

Use **AndroidX Ink (`androidx.ink`)** for low-latency in-progress stroke capture/rendering where it materially improves the feel of the finger-following thread preview.

Architectural rule: game rules must not depend directly on AndroidX Ink types. Input samples are converted at a boundary into application-domain coordinates/events. This keeps gesture recognition independently testable and permits replacing the rendering/input implementation if necessary.

### 4.3 Minimum Android version

Initial `minSdk`: **26 (Android 8.0)**, matching the current requirements of the Android Ink API considered for the project.

## 5. High-level architecture

Use a pragmatic layered architecture rather than a highly abstract framework.

```text
UI / Compose
  |-- PatternScreen
  |-- Palette
  |-- HUD / controls
  |-- Navigation gestures
  |
  v
Interaction Controller
  |-- pointer ownership
  |-- screen -> board coordinate transform
  |-- live stroke preview
  |
  v
Stitch Recognizer ---------> Viewport Controller
  |                            |-- pan
  |                            `-- zoom
  v
Game Domain
  |-- Pattern
  |-- Grid / StitchCell
  |-- ThreadColor
  |-- StitchLeg
  |-- GameSession
  |-- validation/rules
  |
  +----------> Rendering
  |             |-- fabric/grid
  |             |-- committed thread
  |             `-- transient stroke
  |
  `----------> Persistence
                |-- pattern assets
                `-- saved progress
```

### 5.1 Dependency rule

The domain layer must contain no Android UI classes. Recognition should operate on normalized/domain points rather than `MotionEvent`, `Canvas`, or Compose-specific objects.

This is important because gesture classification should have dense unit tests without an emulator.

## 6. Domain model

Suggested starting model:

```kotlin
data class GridPoint(val x: Float, val y: Float)

enum class Diagonal {
    SLASH,      // /
    BACKSLASH   // \
}

enum class StitchState {
    EMPTY,
    SLASH_ONLY,
    BACKSLASH_ONLY,
    COMPLETE
}

data class ThreadColor(
    val id: String,
    val argb: Long,
    val displayName: String
)

data class PatternCell(
    val targetColorId: String?
)

data class StitchCellProgress(
    val row: Int,
    val column: Int,
    val colorId: String?,
    val state: StitchState
)

data class Pattern(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val palette: List<ThreadColor>,
    val cells: List<PatternCell>
)
```

Do not store the user's raw freehand geometry as authoritative stitch state. Once accepted, a stroke becomes a semantic `SLASH` or `BACKSLASH`. This makes saves compact, rendering consistent, and progress deterministic.

## 7. Coordinate systems

Explicitly separate coordinate spaces:

1. **Screen coordinates**: physical touch/display pixels.
2. **Board coordinates**: continuous coordinates after inverse pan/zoom transform.
3. **Cell-local coordinates**: normalized `0.0..1.0` space inside one cell.

The recognizer should primarily consume cell-local coordinates. A 12 mm physical cell and a heavily zoomed cell should therefore behave equivalently.

A dedicated transform component should own conversions. Do not duplicate transformation math in gesture recognition and rendering.

## 8. Stitch recognition

### 8.1 Input

A candidate stroke contains:

- pointer ID
- ordered input points
- timestamps
- board/cell coordinates
- target cell determined from the gesture

Pressure may be captured when available, but it must not affect MVP correctness because normal finger input cannot be assumed to provide useful pressure information.

### 8.2 Recognition algorithm

A first implementation should intentionally be geometric rather than ML-based.

1. On pointer down, identify the candidate cell and nearest corner.
2. Record movement while rendering a transient thread preview.
3. If a second pointer appears, cancel the candidate.
4. On pointer up, determine nearest ending corner.
5. Reject if start/end are not opposite corners.
6. Reject if traversal distance is below threshold.
7. Classify opposite corners as `/` or `\`.
8. Optionally calculate path deviation from the ideal diagonal and reject extreme detours.
9. Ask the game domain whether the classified leg is currently legal.
10. If accepted, commit the semantic stitch leg and animate/snap rendering to canonical geometry.

### 8.3 Recognition result

```kotlin
sealed interface StrokeRecognitionResult {
    data class Accepted(
        val row: Int,
        val column: Int,
        val diagonal: Diagonal
    ) : StrokeRecognitionResult

    data class Rejected(val reason: RejectReason) : StrokeRecognitionResult

    data object Cancelled : StrokeRecognitionResult
}
```

Possible rejection reasons should be explicit and testable: `NOT_OPPOSITE_CORNERS`, `TOO_SHORT`, `TOO_FAR_FROM_CORNER`, `CROSSES_MULTIPLE_CELLS`, `WRONG_COLOR`, etc.

### 8.4 Questions to test with Amanda

- Should a wrong-color stitch be rejected, accepted as a mistake, or allowed with an optional assist mode?
- Should the player always draw the traditional lower leg before the upper leg, or should either order be accepted?
- Should repeating an existing diagonal do nothing, replace it, or count as an error?
- How much corner tolerance feels natural under a thumb?
- Should the live thread exactly follow the finger or magnetically move toward its target corner?

These should remain policy/configuration choices rather than assumptions embedded in the recognizer.

## 9. Rendering

Render three conceptual layers:

1. **Fabric layer**: background, grid/fabric texture, pattern symbols or hints.
2. **Committed-stitch layer**: canonical thread geometry for accepted stitches.
3. **Transient-ink layer**: low-latency finger-following preview for the current stroke.

Committed stitches should be rendered from the semantic game state, not from freehand input samples.

For MVP, visual thread can be a high-quality stylized line. More realistic floss can follow later using custom brushes/textures once interaction has been validated.

## 10. Viewport behavior

Maintain viewport state independently of game state:

```kotlin
data class ViewportState(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
)
```

Initial constraints:

- Minimum zoom: full pattern approximately fits screen
- Maximum zoom: enough for a cell to be comfortably thumb-sized, initially perhaps 4x-8x fit scale
- Pan is bounded so the board cannot be lost entirely off-screen
- Transition from one-finger stitching to multi-touch navigation cancels the stitch candidate

## 11. Haptics and feedback

Feedback should communicate state without becoming annoying.

Proposed MVP signals:

- accepted half-stitch: light tick
- completed X: slightly stronger tick
- rejected gesture/wrong color: short distinct bump
- visual rejection: transient fade/red highlight, not an intrusive dialog

Haptics should be user-disableable and centralized behind an interface so unit/domain code does not depend on Android vibration APIs.

## 12. Persistence

MVP should be offline-first.

Persist:

- pattern ID/version
- cell stitch states and colors
- selected color
- optional viewport state
- last-played timestamp

A simple local persistence implementation is sufficient initially. Prefer a repository interface so the first implementation can use DataStore or a small serialized representation without committing the domain model to a storage mechanism. Room is unnecessary until data relationships/querying justify it.

Pattern definitions should initially ship as versioned app assets, preferably JSON with a documented schema.

## 13. Pattern asset format

Example conceptual JSON:

```json
{
  "schemaVersion": 1,
  "id": "heart-001",
  "name": "Heart",
  "width": 20,
  "height": 20,
  "palette": [
    { "id": "red", "argb": "#FFE53935", "name": "Red" }
  ],
  "rows": [
    "....................",
    ".....RR....RR......."
  ]
}
```

The actual schema should favor easy hand-authoring and validation. Add a JSON Schema later if external/imported patterns become a feature.

## 14. State management

Use a screen-level ViewModel exposing immutable UI state, for example:

```kotlin
data class PatternUiState(
    val pattern: Pattern,
    val progress: GameProgress,
    val selectedColorId: String,
    val viewport: ViewportState,
    val activeStroke: ActiveStrokeUiState?
)
```

User actions become events/intents handled by the ViewModel or delegated controller. Avoid letting individual Composables mutate domain objects.

## 15. Testing strategy

### 15.1 Unit tests: highest priority

Gesture recognition must have tests for:

- all four valid corner-to-opposite-corner directions
- start/end just inside tolerance
- start/end just outside tolerance
- strokes too short
- mostly horizontal/vertical strokes
- multi-cell strokes
- noisy but valid diagonal movement
- cancellation when another pointer appears
- zoom-independent classification using normalized coordinates

Game-domain tests:

- first leg changes EMPTY to corresponding half state
- complementary leg completes stitch
- duplicate leg behavior
- color validation policy
- completion calculation
- serialization round trip

### 15.2 Compose/instrumentation tests

Cover:

- palette selection
- loading/restoring sessions
- navigation between screens
- visible state after accepted/rejected input where practical

### 15.3 Physical-device testing

This is essential. Emulator mouse input cannot validate the core UX.

Test at minimum:

- small phone
- large phone
- tablet if available
- finger input
- stylus input when available

Amanda's subjective evaluation is the primary acceptance test for the prototype.

## 16. Accessibility and ergonomics

- Do not rely on color alone to identify thread; include symbols/labels.
- Maintain adequate UI-target sizes for palette/control buttons.
- Support landscape and portrait eventually, but MVP can prefer portrait if needed to reduce scope.
- Allow haptics to be disabled.
- Consider left-handed use; controls should not assume a right-handed thumb.
- Avoid tiny required corner targets by performing tolerance/snap logic in normalized cells.

## 17. Security and privacy

MVP requires no network permissions and no account data. Keep it that way unless a feature requires otherwise.

When photo import is introduced, use Android's system photo picker rather than broad storage permissions where possible.

If telemetry is added later, make it explicit and evaluate whether interaction traces can remain local or be aggregated without retaining user content.

## 18. Repository structure

A simple starting structure:

```text
/
|-- README.md
|-- docs/
|   `-- ARCHITECTURE.md
|-- app/
|   `-- src/
|       |-- main/
|       |   |-- java/.../
|       |   |   |-- domain/
|       |   |   |-- input/
|       |   |   |-- rendering/
|       |   |   |-- persistence/
|       |   |   `-- ui/
|       |   `-- assets/patterns/
|       `-- test/
|-- gradle/
`-- .github/
    |-- copilot-instructions.md
    `-- ISSUE_TEMPLATE/
```

Keep the first project single-module unless build times or reusable boundaries demonstrate a reason to split modules. Package boundaries give enough architectural separation initially.

## 19. GitHub Copilot working conventions

Add `.github/copilot-instructions.md` early. Suggested project rules:

- Kotlin is the implementation language.
- Prefer Jetpack Compose over XML layouts.
- Domain and gesture-recognition code must remain Android-framework-independent where practical.
- Do not introduce a new dependency without documenting why it is needed.
- Every gesture-recognition behavior change requires unit tests.
- Do not silently change pattern/save schemas.
- Keep constants such as gesture tolerances centralized and named.
- Favor straightforward code over generalized frameworks until there are at least two concrete use cases.
- MVP is offline-only; do not add analytics, ads, accounts, or network services.
- Build and test before declaring an issue complete.

## 20. Proposed milestones and GitHub issues

### Milestone 0: Repository/bootstrap

**Issue 1 - Bootstrap Android project**
- Kotlin
- Jetpack Compose
- minSdk 26
- basic CI build/test workflow
- package structure
- initial README

**Issue 2 - Add AndroidX Ink spike**
- Render an in-progress finger stroke on a blank Compose screen.
- Confirm acceptable latency on a physical Android device.
- Record chosen Ink version and required modules.
- No game logic yet.

**Issue 3 - Establish Copilot/project contribution instructions**
- Add `.github/copilot-instructions.md`.
- Add issue/PR templates if useful.
- Document build/test commands.

### Milestone 1: Domain and pattern

**Issue 4 - Implement core stitch domain model**
- Pattern, cells, palette, stitch state, progress.
- Unit tests.

**Issue 5 - Define/load built-in pattern JSON**
- Define schema v1.
- Parser/validation.
- Add 20x20 heart.
- Failure behavior for invalid assets.

**Issue 6 - Render static pattern grid**
- Fabric/grid.
- Pattern hints.
- Existing stitch states.
- No touch interaction required.

### Milestone 2: Core interaction

**Issue 7 - Implement coordinate transformation service**
- screen -> board -> cell-local
- inverse transform tests
- handling boundaries

**Issue 8 - Implement geometric stitch recognizer**
- classify `/` and `\`
- corner tolerance
- minimum traversal
- rejection reasons
- dense unit-test suite

**Issue 9 - Connect live stroke input to recognizer**
- input lifecycle
- transient rendering
- cancellation
- accepted/rejected callback

**Issue 10 - Snap accepted strokes to canonical thread**
- replace live freehand path with rendered diagonal
- transition/animation optional
- do not persist raw path

**Issue 11 - Combine half stitches into X completion**
- first-leg and second-leg state transition
- duplicate behavior
- completion feedback

### Milestone 3: Playable prototype

**Issue 12 - Implement thread-color palette**
- current color selection
- accessible color labels/symbols
- indication of current selection

**Issue 13 - Define wrong-color policy and implement it**
- Start with strict assist mode if no preference has yet been established.
- Make policy separable/configurable.

**Issue 14 - Add pinch zoom and two-finger pan**
- navigation claims multi-touch gestures
- active stitch is cancelled when second pointer enters
- bounded zoom/pan

**Issue 15 - Add haptic/visual feedback**
- half-stitch
- completed X
- rejected stroke
- user setting to disable haptics

**Issue 16 - Persist and restore progress**
- local repository
- save on semantic state changes
- restore after process/app restart
- version saved format

### Milestone 4: Amanda validation build

**Issue 17 - Produce first installable prototype**
Acceptance criteria:
- launches directly into the heart pattern or has a one-tap start
- user can select thread color
- user can physically draw both diagonals
- accepted stitches snap cleanly
- pinch/pan works without unwanted stitches
- progress survives restart

**Issue 18 - Conduct UX test and capture tuning decisions**
Evaluate:
- corner tolerance
- minimum traversal
- live-stroke magnetism
- cell size/zoom
- haptic strength
- wrong-color behavior
- stitch-order rules

Record decisions as architecture/config updates rather than undocumented code tweaks.

## 21. Definition of MVP done

The MVP is done when Amanda can install the APK on her Android device, open the included heart pattern, choose a thread color, zoom/pan naturally, draw both legs of individual cross stitches with her thumb, receive understandable feedback for good/bad gestures, close the app, reopen it, and continue where she stopped.

The MVP is **not** defined by app-store publication or breadth of content. Its purpose is to validate whether literal finger-drawn stitching is enjoyable.

## 22. Future architecture considerations

Only pursue these after validating the core interaction:

1. DMC floss catalog and symbols.
2. Multiple built-in patterns and browsing.
3. Photo-to-pattern conversion with palette quantization.
4. Imported pattern formats.
5. Backstitch and specialty-stitch recognizers.
6. Free-stitch/sandbox mode.
7. Difficulty/assist modes.
8. Undo/redo/history.
9. Cloud backup/sync.
10. Pattern authoring tools.
11. Achievement/progression systems.
12. Tablet-optimized workspace.

If specialty stitches appear, evolve the domain from `StitchState` into a collection of semantic stitch primitives rather than continuously adding enum states.

## 23. Architecture decisions to record as ADRs

Create Architecture Decision Records when these are resolved:

- **ADR-001:** Native Android/Compose versus game engine. Proposed: Native Android/Compose.
- **ADR-002:** AndroidX Ink versus direct MotionEvent/Compose pointer rendering. Proposed: Ink for transient drawing behind an abstraction.
- **ADR-003:** Gesture recognition strategy. Proposed: deterministic geometric recognizer, no ML.
- **ADR-004:** Persist semantic stitches, not raw stroke geometry.
- **ADR-005:** Pattern schema and versioning.
- **ADR-006:** Wrong-color and stitch-order gameplay policy after user testing.

## 24. First implementation slice

The smallest vertical slice worth building is:

> Display one 5x5 grid; let the player draw `\` and `/` in one target cell; recognize each gesture; snap it to canonical thread; mark the X complete.

No persistence, palette, pattern loader, or navigation is necessary for that spike. If this interaction does not feel good on a physical device, solve that before building the rest of the application.

Once the slice works, expand to the 20x20 heart and Milestone 3 features.

## 25. References

The initial architecture is based on current Android platform capabilities reviewed during project ideation:

- Android Developers: AndroidX Ink API for high-performance, low-latency freehand drawing and its wet-ink/dry-ink model.
- Android Developers: Android `MotionEvent`/pointer input lifecycle, including down, move, up, cancellation, multiple pointers, and stylus data.
- Google Play review of existing cross-stitch applications including Cross-Stitch World, Cross Stitch: Color by Number, Cross Stitch King, and Cross Stitch Party. These products informed the product hypothesis that physically drawing the diagonal stitch is meaningfully different from tap-to-fill interaction.

Before implementation, pin actual AndroidX/Compose dependency versions from current Android Developers/AndroidX release documentation rather than copying versions from this design document.
