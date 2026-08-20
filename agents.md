# Smart-AutoClicker → Macrorify-like: Master Plan

## Project Overview

Transform Smart-AutoClicker (Klick'r) into a Macrorify-like app with 6 major features:
Record & Replay, Multi-finger Gestures, Abstract/Flowchart Mode, Scripting Engine, Custom UI Builder, Macro Store.

**Base repo:** Fork of [Nain57/Smart-AutoClicker](https://github.com/Nain57/Smart-AutoClicker)
**Package:** `com.buzbuz.smartautoclicker`
**License:** GPL-3.0
**Stack:** Kotlin, Gradle KTS, Hilt, Room, OpenCV (JNI), ncnn (OCR), Coroutines, DataStore
**Min SDK:** 24 | **Compile SDK:** 37 | **NDK:** 28.2.13676358
**Build:** GitHub Actions (no local PC)

---

## Existing Architecture

```
smartautoclicker/              ← App module (AccessibilityService, Activities)
core/
  common/
    accessibility/             ← LocalAccessibilityService interface + connection
    actions/                   ← AndroidActionExecutor (dispatchGesture, intents, text)
    android/                   ← Intent helpers
    base/                      ← Identifiers, Dumpable, extensions
    bitmaps/                   ← BitmapRepository
    display/                   ← DisplayRecorder, DisplayConfigManager (MediaProjection)
    navigation/                ← Navigation helpers
    overlays/                  ← OverlayManager (floating windows, SYSTEM_ALERT_WINDOW)
    permissions/               ← Runtime permissions
    quality/                   ← Quality metrics
    settings/                  ← DataStore SettingsRepository
    tutorial/                  ← Tutorial framework
    ui/                        ← Shared UI components
  dumb/                        ← "Regular mode" (simple clicks, own Room DB)
  smart/
    database/                  ← ClickDatabase (Room v23, 8 entities, 5 DAOs)
    debugging/                 ← Live debug overlay
    detection/                 ← NativeDetector (OpenCV + ncnn via JNI)
    detection-models/          ← OCR model assets
    domain/                    ← Domain models (Scenario, Event, Action, Condition)
    processing/                ← DetectorEngine + ScenarioProcessor loop
feature/
  backup/                      ← ZIP import/export (BackupEngine)
  dumb-config/                 ← Regular mode config UI
  notifications/               ← Notification management
  quick-settings-tile/         ← Android QS tile
  revenue/                     ← Ads/billing (playStore only)
  review/                      ← Play Store review prompt
  smart-config/                ← Smart scenario config UI
  smart-debugging/             ← Debug overlay UI
  tutorial/                    ← Tutorial UI
build-logic/
  convention/                  ← 13 Gradle convention plugins (buzbuz.*)
  obfuscation/                 ← Package/class name randomizer
  source-download/             ← OpenCV source downloader
```

### Key Processing Pipeline

```
AccessibilityService
  └→ LocalService (LocalAccessibilityService interface)
       ├→ DumbEngine (simple clicks, no detection)
       │    └→ DumbActionExecutor → AndroidActionExecutor.dispatchGesture()
       └→ DetectorEngine (state: CREATED → RECORDING → DETECTING)
            ├→ DisplayRecorder (MediaProjection → Bitmap)
            ├→ NativeDetector (libsmartautoclicker.so: OpenCV + ncnn)
            └→ ScenarioProcessor
                 ├→ ConditionsVerifier (image/color/text/number/timer/counter/broadcast)
                 └→ ActionExecutor (click/swipe/pause/intent/toggle/counter/notification/system/text)
                      └→ AndroidActionExecutor.dispatchGesture()
```

### Key Patterns

- **DI:** All `@Singleton` + `@Inject constructor`, Hilt modules in `di/Hilt.kt` per module
- **Domain ↔ Data:** Domain models in `core/smart/domain/`, Room entities in `core/smart/database/`, never leak annotations
- **Interface-first:** `AndroidActionExecutor`, `LocalAccessibilityService`, `IDumbRepository`, `ImageDetector`
- **Coroutines:** `limitedParallelism(1)` for processing, `suspendCancellableCoroutine` for gesture callbacks, `StateFlow` for state
- **Single-table polymorphism:** `ActionEntity` and `ConditionEntity` use nullable columns per type (9 action types, 7 condition types)
- **Build flavors:** `fDroid` (FOSS) and `playStore` (Firebase/GMS/billing)
- **Obfuscation:** Build plugin randomizes applicationId, class names via manifest placeholders
- **Backup:** ZIP with kotlinx.serialization JSON + bitmap entries

### Existing DB Schema (v23)

| Entity | Table | Key Columns |
|---|---|---|
| ScenarioEntity | scenario_table | id, name, detectionQuality, computeRate, randomize |
| EventEntity | event_table | FK→scenario, type (IMAGE/TRIGGER), conditionOperator, priority |
| ConditionEntity | condition_table | FK→event, 7 types (image/color/text/number/counter/timer/broadcast) |
| ActionEntity | action_table | FK→event, 9 types (click/swipe/pause/intent/toggle/counter/notification/system/text) |
| IntentExtraEntity | intent_extra_table | FK→action, key/value |
| EventToggleEntity | event_toggle_table | FK→action, target event + toggle type |
| CountersEntity | counters_table | FK→scenario, name + initial value |
| ScenarioStatsEntity | scenario_stats_table | Usage tracking |

Composite queries: `CompleteScenario` → `CompleteEventEntity` → `CompleteActionEntity`

### Existing GitHub Actions

| Workflow | Trigger | Purpose |
|---|---|---|
| execute-tests.yml | push/PR to master | `testFDroidDebugUnitTest` |
| release.yml | manual | Build fDroid release APK |
| release-playstore.yml | manual | Build playStore release |
| release-ocr-models.yml | manual | Build OCR model assets |
| nightly-obfuscation.yml | nightly | Obfuscated build |

---

## FASE 0: Setup CI/CD for Development

**Goal:** Build APK via GitHub Actions since no local PC available.

### 0.1 Add dev-build workflow

**File:** `.github/workflows/dev-build.yml`

```yaml
name: Dev Build
on:
  push:
    branches: [ dev, 'feature/**' ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'zulu'
      - uses: gradle/actions/setup-gradle@v4
        with:
          cache-read-only: false
      - run: ./gradlew assembleFdroidDebug
      - uses: actions/upload-artifact@v4
        with:
          name: debug-apk-${{ github.sha }}
          path: smartautoclicker/build/outputs/apk/fDroid/debug/*.apk
          retention-days: 14
```

### 0.2 Development workflow

1. Edit code via GitHub.dev (press `.` in repo) or Codespaces
2. Push to `dev` or `feature/*` branch
3. GitHub Actions builds APK automatically
4. Download APK from Actions → Artifacts
5. Install on device to test

### 0.3 Branch strategy

```
main              ← stable releases
dev               ← integration branch
feature/recording ← Fase 1
feature/gestures  ← Fase 2
feature/flowchart ← Fase 3
feature/scripting ← Fase 4
feature/ui-builder ← Fase 5
feature/store     ← Fase 6
```

---

## FASE 1: Record & Replay (2-3 weeks)

**Priority:** #1 — Foundation feature, all other features depend on this.

### 1.1 New module: `core/recording/`

#### build.gradle.kts
```kotlin
plugins {
    id("buzbuz.androidLibrary")
    id("buzbuz.hilt")
    id("buzbuz.androidRoom")
}
```

#### Room Entities (DB migration v23 → v24)

```kotlin
@Entity(tableName = "recording_table")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "scenario_id") val scenarioId: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "finger_count") val fingerCount: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "recorded_touch_table",
    foreignKeys = [ForeignKey(
        entity = RecordingEntity::class,
        parentColumns = ["id"],
        childColumns = ["recording_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recording_id")]
)
data class RecordedTouchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recording_id") val recordingId: Long,
    @ColumnInfo(name = "pointer_index") val pointerIndex: Int,    // 0-9
    @ColumnInfo(name = "touch_action") val touchAction: Int,       // DOWN=0, MOVE=1, UP=2
    @ColumnInfo(name = "x") val x: Float,
    @ColumnInfo(name = "y") val y: Float,
    @ColumnInfo(name = "pressure") val pressure: Float,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long,      // relative to recording start
)
```

#### Domain Models

```kotlin
data class Recording(
    val id: Identifier,
    val name: String,
    val touchEvents: List<TouchEvent>,
    val durationMs: Long,
    val fingerCount: Int,
)

data class TouchEvent(
    val pointerIndex: Int,
    val action: TouchAction,  // DOWN, MOVE, UP
    val position: PointF,
    val pressure: Float,
    val timestampMs: Long,
)

enum class TouchAction { DOWN, MOVE, UP }
```

#### DAO

```kotlin
@Dao
interface RecordingDao {
    @Insert fun insertRecording(recording: RecordingEntity): Long
    @Insert fun insertTouchEvents(events: List<RecordedTouchEntity>)
    @Query("SELECT * FROM recording_table WHERE scenario_id = :scenarioId")
    fun getRecordingsForScenario(scenarioId: Long): Flow<List<RecordingEntity>>
    @Query("SELECT * FROM recorded_touch_table WHERE recording_id = :recordingId ORDER BY timestamp_ms")
    suspend fun getTouchEvents(recordingId: Long): List<RecordedTouchEntity>
    @Query("DELETE FROM recording_table WHERE id = :id")
    suspend fun deleteRecording(id: Long)
}
```

### 1.2 Touch Recorder

**File:** `core/recording/src/.../recorder/TouchRecorder.kt`

**Strategy:** Transparent overlay window (reuse `core/common/overlays/` infra) that intercepts touch events via `onTouchEvent()` then passes them through with `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`. Records MotionEvent data without blocking user interaction.

```kotlin
@Singleton
class TouchRecorder @Inject constructor(
    private val displayConfigManager: DisplayConfigManager,
) {
    sealed class State {
        data object Idle : State()
        data object Recording : State()
        data object Paused : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val events = mutableListOf<TouchEvent>()
    private var startTimeNanos: Long = 0L

    fun startRecording() {
        events.clear()
        startTimeNanos = System.nanoTime()
        _state.value = State.Recording
    }

    fun pauseRecording() { _state.value = State.Paused }
    fun resumeRecording() { _state.value = State.Recording }

    fun stopRecording(): Recording {
        _state.value = State.Idle
        val duration = (System.nanoTime() - startTimeNanos) / 1_000_000
        val fingerCount = events.maxOfOrNull { it.pointerIndex }?.plus(1) ?: 0
        return Recording(
            id = Identifier(databaseId = 0),
            name = "Recording ${System.currentTimeMillis()}",
            touchEvents = events.toList(),
            durationMs = duration,
            fingerCount = fingerCount,
        )
    }

    fun onTouchEvent(event: MotionEvent) {
        if (_state.value != State.Recording) return
        val relativeMs = (System.nanoTime() - startTimeNanos) / 1_000_000
        for (i in 0 until event.pointerCount) {
            val action = when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> TouchAction.DOWN
                MotionEvent.ACTION_MOVE -> TouchAction.MOVE
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> TouchAction.UP
                else -> return
            }
            events.add(TouchEvent(
                pointerIndex = event.getPointerId(i),
                action = action,
                position = PointF(event.getX(i), event.getY(i)),
                pressure = event.getPressure(i),
                timestampMs = relativeMs,
            ))
        }
    }
}
```

### 1.3 Replay Engine

**File:** `core/recording/src/.../replay/ReplayEngine.kt`

Builds `GestureDescription` from recorded touch events and dispatches via existing `AndroidActionExecutor.dispatchGesture()`.

```kotlin
@Singleton
class ReplayEngine @Inject constructor(
    private val actionExecutor: AndroidActionExecutor,
) {
    data class ReplayParams(
        val speedMultiplier: Float = 1.0f,
        val repeatCount: Int = 1,
        val delayBetweenRepeatMs: Long = 0L,
        val randomizePositionPx: Int = 0,
        val randomizeTimingMs: Int = 0,
    )

    suspend fun replay(recording: Recording, params: ReplayParams) {
        repeat(params.repeatCount) { iteration ->
            replayOnce(recording, params)
            if (iteration < params.repeatCount - 1) {
                delay(params.delayBetweenRepeatMs)
            }
        }
    }

    private suspend fun replayOnce(recording: Recording, params: ReplayParams) {
        // Group events by gesture (DOWN...UP per pointer)
        // Build GestureDescription with one StrokeDescription per pointer
        // Apply speed multiplier to timing
        // Apply randomization to positions
        // Dispatch via actionExecutor.dispatchGesture()
    }
}
```

**Multi-finger replay:** `GestureDescription.Builder` accepts multiple `StrokeDescription` objects. Group touch events by `pointerIndex`, build one `Path` per pointer, create parallel strokes with synced timing.

### 1.4 Extend ActionEntity

Add new action type `PLAY_RECORDING` to existing single-table polymorphism:

```kotlin
// In ActionType enum (core/smart/domain)
PLAY_RECORDING

// New nullable columns in ActionEntity (migration v23→v24)
@ColumnInfo(name = "recording_id") val recordingId: Long? = null,
@ColumnInfo(name = "replay_speed") val replaySpeed: Float? = null,
@ColumnInfo(name = "replay_repeat") val replayRepeat: Int? = null,
@ColumnInfo(name = "replay_delay_ms") val replayDelayMs: Long? = null,
@ColumnInfo(name = "replay_randomize") val replayRandomize: Int? = null,
```

Extend `ActionExecutor` in `core/smart/processing/` to handle `PLAY_RECORDING`.

### 1.5 Extend DumbAction

Add `DumbPlayRecording` to `core/dumb/` sealed class for simple recording playback without detection.

### 1.6 Extend LocalAccessibilityService interface

```kotlin
interface LocalAccessibilityService {
    // existing...
    fun startRecording()
    fun stopRecording(): Recording
    fun pauseRecording()
    fun resumeRecording()
}
```

### 1.7 Recording overlay

Transparent full-screen overlay window using existing `core/common/overlays/` infrastructure:
- Green border to indicate recording active (like Macrorify)
- Red pause/resume button in lower-left corner
- Stop button

### 1.8 New feature module: `feature/recording-config/`

Recording editor UI:
- Timeline view of recorded events
- Edit individual touch points (position, timing)
- Speed/repeat/delay/randomize parameter controls
- Preview path drawing on overlay
- Delete/reorder events
- Rename recording

### 1.9 Extend backup

Update `feature/backup/` to include recording entities in ZIP export/import.

### 1.10 Integration with scenario toolbar

Add record button to existing overlay toolbar (where play/pause/stop already exist).

### Checklist

- [ ] Create `core/recording/` module with build.gradle.kts
- [ ] Register module in settings.gradle.kts
- [ ] Add Room entities (RecordingEntity, RecordedTouchEntity)
- [ ] Write DB migration v23 → v24
- [ ] Add domain models (Recording, TouchEvent)
- [ ] Add RecordingDao
- [ ] Add RecordingRepository (interface + impl)
- [ ] Implement TouchRecorder
- [ ] Implement recording overlay (green border, pause button)
- [ ] Implement ReplayEngine
- [ ] Add PLAY_RECORDING action type to ActionEntity
- [ ] Write ActionEntity migration
- [ ] Extend ActionExecutor for PLAY_RECORDING
- [ ] Add DumbPlayRecording to dumb mode
- [ ] Extend LocalAccessibilityService interface
- [ ] Create `feature/recording-config/` module
- [ ] Build recording editor UI (timeline, edit, params)
- [ ] Add record button to scenario toolbar
- [ ] Update BackupEngine for recording entities
- [ ] Write unit tests for TouchRecorder
- [ ] Write unit tests for ReplayEngine
- [ ] Test on device via GitHub Actions build

---

## FASE 2: Multi-finger Gestures (1-2 weeks)

**Priority:** #2 — Extends recording, closely related to Fase 1.

### 2.1 New table: swipe_path_table

```kotlin
@Entity(
    tableName = "swipe_path_table",
    foreignKeys = [ForeignKey(
        entity = ActionEntity::class,
        parentColumns = ["id"],
        childColumns = ["action_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("action_id")]
)
data class SwipePathEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "action_id") val actionId: Long,
    @ColumnInfo(name = "finger_index") val fingerIndex: Int,   // 0-9
    @ColumnInfo(name = "point_index") val pointIndex: Int,     // order in path
    @ColumnInfo(name = "x") val x: Int,
    @ColumnInfo(name = "y") val y: Int,
    @ColumnInfo(name = "hold_ms") val holdMs: Long,            // hold at this point
    @ColumnInfo(name = "speed_preset") val speedPreset: Int,   // 0=very slow, 5=flick
)
```

### 2.2 Domain model

```kotlin
data class MultiFingerSwipe(
    val paths: List<SwipePath>,         // one per finger (up to 10)
    val repeatCount: Int = 1,
    val delayBetweenRepeatMs: Long = 0,
    val randomizePositionPx: Int = 0,
)

data class SwipePath(
    val fingerIndex: Int,
    val points: List<SwipePoint>,
)

data class SwipePoint(
    val x: Int,
    val y: Int,
    val holdMs: Long,
    val speedPreset: SpeedPreset,
)

enum class SpeedPreset(val durationFactor: Float) {
    VERY_SLOW(3.0f),
    SLOW(2.0f),
    NORMAL(1.0f),
    FAST(0.5f),
    VERY_FAST(0.25f),
    FLICK(0.1f),
}
```

### 2.3 Gesture styles

| Style | Implementation |
|---|---|
| Normal | Single path, straight segments |
| Pinch | 2 paths converging to center |
| Zoom | 2 paths diverging from center |
| Drag & Drop | 1 path, long hold on first point, then move |
| Joystick | 1 path, hold on last point (`continueStroke`, API 26+) |

### 2.4 GestureDescription builder

```kotlin
fun buildMultiFingerGesture(swipe: MultiFingerSwipe): GestureDescription {
    val builder = GestureDescription.Builder()
    swipe.paths.forEach { path ->
        val androidPath = Path().apply {
            moveTo(path.points[0].x.toFloat(), path.points[0].y.toFloat())
            path.points.drop(1).forEach { lineTo(it.x.toFloat(), it.y.toFloat()) }
        }
        val totalDuration = path.points.sumOf { it.holdMs } +
            path.points.zipWithNext().sumOf { (a, b) ->
                computeSegmentDuration(a, b) // based on distance + speed preset
            }
        builder.addStroke(StrokeDescription(androidPath, 0, totalDuration))
    }
    return builder.build()
}
```

### 2.5 Swipe Builder UI

**Extend `feature/smart-config/` or new `feature/swipe-builder/`**

Canvas overlay for drawing multi-finger paths:
- Draw mode: tap to place waypoints, each tap = new point in path
- Finger selector: switch between finger 0-9
- Per-point config: hold time, speed preset
- Preview animation: shows all fingers moving simultaneously
- Gesture style presets (pinch/zoom/drag templates)

### Checklist

- [ ] Add SwipePathEntity + DAO
- [ ] Write DB migration
- [ ] Add MultiFingerSwipe domain model
- [ ] Build GestureDescription from multi-path data
- [ ] Extend ActionExecutor for multi-finger swipe
- [ ] Build Swipe Builder canvas UI
- [ ] Add gesture style presets
- [ ] Add preview animation
- [ ] Update backup for SwipePathEntity
- [ ] Test multi-finger replay on device

---

## FASE 3: Abstract / Flowchart Mode (3-4 weeks)

**Priority:** #3 — Core UX feature, visual macro builder.

### 3.1 New module: `core/flowchart/`

#### Data model

```kotlin
// --- Nodes ---
sealed class FlowNode {
    abstract val id: Identifier
    abstract val position: Point  // canvas position

    data class ActionNode(
        override val id: Identifier,
        override val position: Point,
        val actionType: FlowActionType,
        val config: ActionConfig,
        val conditions: List<FlowCondition>,  // when to fire
        val conditionOperator: ConditionOperator,  // ALL_TRUE or ONE_TRUE
    ) : FlowNode()

    data class GroupNode(  // Custom Action (sequential composite)
        override val id: Identifier,
        override val position: Point,
        val name: String,
        val children: List<FlowNode>,
    ) : FlowNode()
}

// --- Action types ---
enum class FlowActionType {
    // Gesture
    CLICK, CLICK_IMAGE, CLICK_TEXT, CLICK_COLOR, SWIPE,
    // Job control
    RUN_JOB, RESTART_JOB, STOP_JOB,
    // Variable
    SET_VARIABLE,
    // Device
    TOAST, OPEN_APP, PRESS_BACK, PRESS_HOME, OPEN_RECENT,
    OPEN_NOTIFICATION, OPEN_QUICK_SETTING, LOCK_SCREEN, SCREENSHOT,
    // Macro control
    EMPTY, WAIT, PAUSE_MACRO, STOP_MACRO,
    // Recording
    PLAY_RECORDING,
    // Script
    RUN_SCRIPT,
}

// --- Condition types ---
sealed class FlowCondition {
    data class ImageAppear(val templateId: Long, val appear: Boolean, val similarity: Float, val region: Rect?) : FlowCondition()
    data class ImageCount(val templateId: Long, val count: Int, val operator: ComparisonOp) : FlowCondition()
    data class TextAppear(val text: String, val appear: Boolean, val region: Rect?) : FlowCondition()
    data class TextCount(val text: String, val count: Int, val operator: ComparisonOp) : FlowCondition()
    data class ColorAppear(val color: Int, val appear: Boolean, val region: Rect?) : FlowCondition()
    data class ActionResult(val targetActionId: Identifier, val expectedResult: Boolean) : FlowCondition()
    data class ActionCount(val targetActionId: Identifier, val count: Int, val operator: ComparisonOp) : FlowCondition()
    data class RandomTrue(val percentage: Int) : FlowCondition()
    data class OnceTrue(val scope: Scope) : FlowCondition()  // MACRO or JOB
    data class VariableCondition(val variableName: String, val operator: ComparisonOp, val value: String) : FlowCondition()
    data class IntervalTrue(val intervalMs: Long, val scope: Scope) : FlowCondition()
}

// --- Jobs ---
data class FlowJob(
    val id: Identifier,
    val name: String,
    val scenarioId: Long,
    val actions: List<FlowNode.ActionNode>,
    val variables: List<FlowVariable>,
    val performanceProfile: PerformanceProfile,
)

// --- Variables ---
data class FlowVariable(
    val id: Identifier,
    val name: String,
    val type: VariableType,  // BOOL, NUMBER, STRING
    val defaultValue: String,
    val scope: Scope,  // MACRO or JOB
)

enum class Scope { MACRO, JOB }
enum class PerformanceProfile { BATTERY_SAVING, BALANCED, HIGH_PERFORMANCE }
```

### 3.2 Room entities

```kotlin
@Entity(tableName = "flow_job_table")
data class FlowJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scenarioId: Long,
    val name: String,
    val performanceProfile: Int,
    val orderIndex: Int,
)

@Entity(tableName = "flow_action_table")
data class FlowActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val actionType: String,
    val canvasX: Int,
    val canvasY: Int,
    val conditionOperator: String,  // ALL_TRUE, ONE_TRUE
    val configJson: String,         // serialized ActionConfig
    val orderIndex: Int,
)

@Entity(tableName = "flow_condition_table")
data class FlowConditionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionId: Long,
    val conditionType: String,
    val configJson: String,
)

@Entity(tableName = "flow_variable_table")
data class FlowVariableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long?,       // null = macro scope
    val scenarioId: Long,
    val name: String,
    val type: String,
    val defaultValue: String,
)

@Entity(tableName = "flow_group_table")
data class FlowGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val name: String,
    val parentGroupId: Long?,  // null = top-level
    val orderIndex: Int,
)
```

### 3.3 Execution engine

**Reactive model (Macrorify-style):**

```kotlin
@Singleton
class FlowchartExecutor @Inject constructor(
    private val actionExecutor: AndroidActionExecutor,
    private val detectorEngine: DetectorEngine,
) {
    suspend fun executeJob(job: FlowJob) {
        val state = JobExecutionState(job)

        while (state.isRunning) {
            // Scan all actions
            for (action in job.actions) {
                if (!state.isRunning) break
                if (evaluateConditions(action.conditions, action.conditionOperator, state)) {
                    executeAction(action, state)
                }
            }
            // Sleep based on performance profile
            delay(job.performanceProfile.scanIntervalMs)
        }
    }

    private suspend fun evaluateConditions(
        conditions: List<FlowCondition>,
        operator: ConditionOperator,
        state: JobExecutionState,
    ): Boolean {
        // Use existing ConditionsVerifier for image/text/color
        // Custom eval for ActionResult, Variable, Timer, Random
    }

    private suspend fun executeAction(action: FlowNode.ActionNode, state: JobExecutionState) {
        when (action.actionType) {
            FlowActionType.CLICK -> { /* use existing click dispatch */ }
            FlowActionType.CLICK_IMAGE -> { /* click at detected image position */ }
            FlowActionType.RUN_JOB -> { /* pause current, start target job */ }
            FlowActionType.SET_VARIABLE -> { /* update variable in state */ }
            FlowActionType.PLAY_RECORDING -> { /* delegate to ReplayEngine */ }
            FlowActionType.RUN_SCRIPT -> { /* delegate to ScriptEngine */ }
            // ... etc
        }
        state.recordActionResult(action.id, success = true)
    }
}
```

### 3.4 Flowchart canvas UI

**New module: `feature/flowchart-editor/`**

Custom `View` using `Canvas`:

```
┌─────────────────────────────────────────┐
│  [Job Tabs: Home | Battle | Collect]    │
├─────────────────────────────────────────┤
│                                         │
│   ┌──────────┐     ┌──────────┐         │
│   │ Image    │────→│ Click    │         │
│   │ Appear   │     │ (300,400)│         │
│   └──────────┘     └──────────┘         │
│                                         │
│   ┌──────────┐     ┌──────────┐         │
│   │ Timer    │────→│ Swipe    │         │
│   │ 5000ms   │     │ Pinch    │         │
│   └──────────┘     └──────────┘         │
│                                         │
│   ┌──────────┐     ┌──────────┐         │
│   │ Text     │────→│ Run Job  │         │
│   │ "OK"     │     │ Battle   │         │
│   └──────────┘     └──────────┘         │
│                                         │
├─────────────────────────────────────────┤
│  [+ Action] [+ Condition] [Variables]   │
└─────────────────────────────────────────┘
```

Features:
- Pan & zoom (Matrix transform on Canvas)
- Drag nodes to reposition
- Tap node → open config bottom sheet
- Long press → context menu (delete, duplicate, copy)
- Action palette (bottom sheet with all action types)
- Condition palette (attach to action)
- Edge drawing (bezier curves from condition to action)
- Mini-map in corner
- Job tab bar at top
- Variable manager dialog

### Checklist

- [ ] Create `core/flowchart/` module
- [ ] Define all domain models (FlowNode, FlowCondition, FlowJob, FlowVariable)
- [ ] Add Room entities + DAOs
- [ ] Write DB migration
- [ ] Implement FlowchartExecutor (reactive scan loop)
- [ ] Integrate with existing DetectorEngine for image/text/color conditions
- [ ] Integrate with existing ActionExecutor for gesture dispatch
- [ ] Implement Job switching (run/restart/stop)
- [ ] Implement variable system (get/set, scoping)
- [ ] Implement Custom Action (sequential composite)
- [ ] Create `feature/flowchart-editor/` module
- [ ] Build flowchart Canvas View (pan, zoom, drag)
- [ ] Build node rendering (action nodes, condition badges)
- [ ] Build edge rendering (bezier curves)
- [ ] Build action config bottom sheets (per action type)
- [ ] Build condition config bottom sheets (per condition type)
- [ ] Build job tab bar + job management
- [ ] Build variable manager dialog
- [ ] Add flowchart mode entry point to ScenarioActivity
- [ ] Extend LocalAccessibilityService for flowchart execution
- [ ] Update backup for flowchart entities
- [ ] Test on device

---

## FASE 4: Scripting Engine (3-4 weeks)

**Priority:** #4 — Power user feature.

### 4.1 Engine choice: Duktape JavaScript

**Why JS over custom language:**
- Familiar syntax for more users
- Battle-tested engine
- No parser/lexer to build
- Smart-AutoClicker already uses JNI (OpenCV) — JNI bridge pattern exists
- MIT license (GPL-3.0 compatible)

**Library:** [nickaknudson/duktape-android](https://github.com/nickaknudson/duktape-android) or build Duktape from source (small C library, ~300KB).

**Alternative:** QuickJS (ES2020 support, also small).

### 4.2 New module: `core/scripting/`

#### Script API

```javascript
// === Touch ===
click(x, y)
click(x, y, { duration: 500, repeat: 3, delay: 100, random: 5 })
swipe(fromX, fromY, toX, toY)
swipe(fromX, fromY, toX, toY, { duration: 300 })
longClick(x, y, durationMs)
doubleClick(x, y)

// Multi-finger
touch.multi()
touch.down(x, y, fingerIndex)
touch.move(x, y, fingerIndex)
touch.up(fingerIndex)
touch.dispatch()

// === Timing ===
wait(ms)
sleep(ms)  // alias

// === Detection ===
var region = Region(x, y, width, height)
var result = region.find("templateName", timeoutMs)
if (result) { result.click() }
var results = region.findAll(["tmpl1", "tmpl2"], timeoutMs)
var textResult = region.findText("search text", timeoutMs)
var colorResult = region.findColor(0xFF0000, timeoutMs)

// === Recording ===
Record.play("recording name", { speed: 1.5, repeat: 3, delay: 100, random: 5 })

// === Variables ===
getVar("variableName")
setVar("variableName", value)

// === Device ===
toast("message")
openApp("com.example.app")
pressBack()
pressHome()
openRecent()
lockScreen()
screenshot("/path/to/save.png")

// === Clipboard ===
Clipboard.get()
Clipboard.set("text")

// === System ===
log("message")             // output to console
getPixelColor(x, y)        // returns color int
getScreenSize()            // returns {width, height}

// === Math/Utility ===
Math.random()
Math.floor()
// ... standard JS Math

// === UI (Setting Builder) ===
var builder = Setting.builder()
builder.add("key", EditText({ hint: "Enter value", text: "default" }))
builder.add("toggle", Checkbox({ label: "Enable feature", checked: true }))
var dialog = builder.build()
var saved = dialog.show()   // blocking, returns true if saved
var value = Setting.get("key")
```

#### Native bridge architecture

```
Kotlin ScriptEngine (@Singleton)
  │
  ├→ Duktape/QuickJS Context (C/JNI)
  │    ├→ Register JS functions → JNI callbacks
  │    ├→ click() → JNI → Kotlin ActionExecutor.dispatchGesture()
  │    ├→ find() → JNI → Kotlin NativeDetector.detectImage()
  │    ├→ wait() → JNI → Kotlin delay()
  │    └→ ...
  │
  └→ ScriptExecutionScope (CoroutineScope)
       ├→ Timeout protection (max execution time)
       ├→ Memory limit
       └→ Cancellation support
```

#### Security constraints

```kotlin
class ScriptEngine {
    companion object {
        const val MAX_EXECUTION_TIME_MS = 24 * 60 * 60 * 1000L  // 24h max
        const val MAX_MEMORY_BYTES = 64 * 1024 * 1024L           // 64MB max
        const val MAX_LOOP_ITERATIONS = 1_000_000                 // infinite loop protection
    }
}
```

### 4.3 Room entity

```kotlin
@Entity(tableName = "script_table")
data class ScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scenarioId: Long,
    val name: String,
    val code: String,
    val language: String,  // "javascript"
    val createdAt: Long,
    val updatedAt: Long,
)
```

### 4.4 New action type: RUN_SCRIPT

Extend ActionEntity:
```kotlin
@ColumnInfo(name = "script_id") val scriptId: Long? = null,
```

### 4.5 Script Editor UI

**New module: `feature/script-editor/`**

- Monospace `EditText` with syntax highlighting (Spannable-based, no external lib)
- Line numbers gutter
- Run/stop toolbar buttons
- Console output panel (scrolling `TextView`)
- Error display with line number
- Script list (manage multiple scripts per scenario)
- Template snippets (insert common patterns)
- Undo/redo

Syntax highlighting approach:
```kotlin
class JsSyntaxHighlighter {
    private val keywords = setOf("var", "let", "const", "if", "else", "for", "while",
        "function", "return", "true", "false", "null", "new", "class", "this")
    private val builtins = setOf("click", "swipe", "wait", "find", "log", "toast",
        "Region", "Record", "Setting", "Clipboard", "Math")

    fun highlight(spannable: Spannable) {
        // Regex-based token coloring
        // Keywords → blue
        // Builtins → purple
        // Strings → green
        // Numbers → orange
        // Comments → gray
    }
}
```

### Checklist

- [ ] Create `core/scripting/` module
- [ ] Integrate Duktape/QuickJS (JNI, .so library)
- [ ] Implement ScriptEngine (context management, execution, cancellation)
- [ ] Register all API functions as native bindings
- [ ] Implement touch API (click, swipe, multi-finger)
- [ ] Implement detection API (find, findText, findColor)
- [ ] Implement recording API (Record.play)
- [ ] Implement variable API (getVar, setVar)
- [ ] Implement device API (toast, openApp, pressBack, etc.)
- [ ] Implement clipboard API
- [ ] Implement system API (log, getPixelColor, getScreenSize)
- [ ] Add security: timeout, memory limit, loop protection
- [ ] Add ScriptEntity + DAO
- [ ] Write DB migration
- [ ] Add RUN_SCRIPT action type to ActionEntity
- [ ] Extend ActionExecutor for RUN_SCRIPT
- [ ] Create `feature/script-editor/` module
- [ ] Build syntax-highlighted editor
- [ ] Build line number gutter
- [ ] Build console output panel
- [ ] Build script management (list, create, rename, delete)
- [ ] Add script templates/snippets
- [ ] Integrate script editor into scenario config
- [ ] Update backup for ScriptEntity
- [ ] Test scripts on device

---

## FASE 5: Custom UI Builder (3-4 weeks)

**Priority:** #5 — Advanced feature for macro UX customization.

### 5.1 New module: `core/custom-ui/`

#### Component model

```kotlin
sealed class SettingComponent {
    abstract val id: Identifier
    abstract val key: String
    abstract val orderIndex: Int

    data class TextLabel(
        override val id: Identifier,
        override val key: String,
        override val orderIndex: Int,
        val text: String,
        val textSize: Float,
    ) : SettingComponent()

    data class TextInput(
        override val id: Identifier,
        override val key: String,
        override val orderIndex: Int,
        val hint: String,
        val defaultText: String,
        val inputType: InputType,  // TEXT, NUMBER, DECIMAL
    ) : SettingComponent()

    data class Checkbox(
        override val id: Identifier,
        override val key: String,
        override val orderIndex: Int,
        val label: String,
        val defaultChecked: Boolean,
    ) : SettingComponent()

    data class RadioGroup(
        override val id: Identifier,
        override val key: String,
        override val orderIndex: Int,
        val label: String,
        val options: List<String>,
        val defaultIndex: Int,
    ) : SettingComponent()

    data class ImagePicker(
        override val id: Identifier,
        override val key: String,
        override val orderIndex: Int,
        val label: String,
    ) : SettingComponent()

    data class RecordSelector(
        override val id: Identifier,
        override val key: String,
        override val orderIndex: Int,
        val label: String,
    ) : SettingComponent()

    data class TabLayout(
        override val id: Identifier,
        override val key: String,
        override val orderIndex: Int,
        val tabs: List<SettingTab>,
    ) : SettingComponent()
}

data class SettingTab(
    val title: String,
    val components: List<SettingComponent>,
)

data class SettingLayout(
    val id: Identifier,
    val scenarioId: Long,
    val name: String,
    val components: List<SettingComponent>,
    val showAtStartup: Boolean,
)
```

### 5.2 Room entities

```kotlin
@Entity(tableName = "setting_layout_table")
data class SettingLayoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scenarioId: Long,
    val name: String,
    val showAtStartup: Boolean,
)

@Entity(tableName = "setting_component_table")
data class SettingComponentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val layoutId: Long,
    val parentTabId: Long?,   // null = root level
    val componentType: String,
    val key: String,
    val configJson: String,   // serialized component-specific config
    val orderIndex: Int,
)
```

### 5.3 Runtime renderer

Renders `SettingLayout` as `AlertDialog` or floating overlay:
- Dynamically builds Android Views from component list
- Binds values to FlowVariable system
- On save → writes all component values to variables
- Accessible from macro toolbar, script API, and flowchart actions

### 5.4 Builder UI

**New module: `feature/ui-builder/`**

- Vertical component list (drag to reorder)
- Add component button → type selector
- Tap component → config dialog (label, hint, default, key binding)
- Tab management (add/rename/delete tabs)
- Live preview button
- Link to variable system (auto-create variable from key)

### Checklist

- [ ] Create `core/custom-ui/` module
- [ ] Define SettingComponent sealed class hierarchy
- [ ] Add Room entities (SettingLayoutEntity, SettingComponentEntity)
- [ ] Write DB migration
- [ ] Add SettingLayoutDao + Repository
- [ ] Build runtime dialog renderer (components → Android Views)
- [ ] Integrate with FlowVariable system (bind keys → variables)
- [ ] Create `feature/ui-builder/` module
- [ ] Build component list UI (drag-to-reorder)
- [ ] Build component type selector
- [ ] Build per-component config dialogs
- [ ] Build tab management UI
- [ ] Build live preview
- [ ] Add "show at startup" option
- [ ] Expose to scripting API (Setting.builder(), Setting.show())
- [ ] Add Setting Builder entry in scenario config
- [ ] Update backup for setting entities
- [ ] Test on device

---

## FASE 6: Macro Store (4-6 weeks)

**Priority:** #6 — Needs backend, build last.

### 6.1 Backend: Firebase (reuse existing Play Store flavor infra)

```
Firebase Services:
├── Authentication     ← Email + Google Sign-in
├── Cloud Firestore    ← Macro metadata, reviews, user profiles
├── Cloud Storage      ← Macro ZIP packages, icons, screenshots
└── Cloud Functions    ← Review queue, validation, moderation
```

**Why Firebase:**
- Smart-AutoClicker's playStore flavor already uses Firebase Crashlytics
- google-services.json infrastructure exists
- Auth + Firestore + Storage = complete backend with no server maintenance
- Free tier: 1GB Firestore, 5GB Storage, 50K reads/day

**Alternative:** Supabase (PostgreSQL, open-source, generous free tier).

### 6.2 Macro package format

Extend existing backup ZIP:
```
macro-package.zip
├── manifest.json              // metadata
│   {
│     "name": "Auto Farm",
│     "version": "1.0.0",
│     "author": "username",
│     "description": "Auto farm resources",
│     "minAppVersion": "4.1.0",
│     "targetGame": "com.game.example",
│     "tags": ["farming", "auto"],
│     "createdAt": 1234567890
│   }
├── scenario.json              // serialized scenario (existing format)
├── recordings/                // recorded touch data files
├── templates/                 // image template bitmaps
├── scripts/                   // script source files
├── flowchart/                 // flowchart job data
├── settings-layout.json       // custom UI layout
├── icon.png                   // store listing icon (512x512 max)
└── screenshots/               // optional screenshots
```

### 6.3 Firestore schema

```
users/
  {userId}/
    displayName: string
    email: string
    uploadCount: int
    downloadCount: int
    reputation: int
    createdAt: timestamp

macros/
  {macroId}/
    name: string
    description: string
    version: string
    authorId: string
    authorName: string
    packageUrl: string       // Cloud Storage URL
    iconUrl: string
    screenshotUrls: string[]
    tags: string[]
    targetGame: string
    minAppVersion: string
    downloadCount: int
    ratingSum: float
    ratingCount: int
    ratingAvg: float
    status: string           // PENDING_REVIEW, APPROVED, REJECTED
    createdAt: timestamp
    updatedAt: timestamp

reviews/
  {reviewId}/
    macroId: string
    userId: string
    userName: string
    rating: int              // 1-5
    comment: string
    createdAt: timestamp
```

### 6.4 New module: `core/store/`

```kotlin
interface MacroStoreRepository {
    fun browseMacros(query: String?, tags: List<String>?, page: Int): Flow<List<MacroListing>>
    suspend fun getMacroDetail(macroId: String): MacroDetail
    suspend fun downloadMacro(macroId: String): File  // downloads ZIP
    suspend fun uploadMacro(packageFile: File, metadata: MacroMetadata): String  // returns macroId
    suspend fun rateMacro(macroId: String, rating: Int, comment: String)
    fun getUserProfile(): Flow<UserProfile>
    fun getUserMacros(): Flow<List<MacroListing>>
}

data class MacroListing(
    val id: String,
    val name: String,
    val authorName: String,
    val iconUrl: String,
    val downloadCount: Int,
    val ratingAvg: Float,
    val tags: List<String>,
)

data class MacroDetail(
    val listing: MacroListing,
    val description: String,
    val screenshotUrls: List<String>,
    val version: String,
    val reviews: List<Review>,
)
```

### 6.5 Store UI

**New module: `feature/macro-store/`**

Screens:
1. **Browse** — Grid/list of macros, search bar, tag filters, sort (popular/new/rating)
2. **Detail** — Icon, name, author, description, screenshots carousel, reviews, download button
3. **Upload** — Select scenario → fill metadata → package → upload
4. **Profile** — User's uploaded macros, download history

### 6.6 Import pipeline

Download ZIP → `BackupEngine.loadBackup()` (existing) → import to local DB.
Extend BackupEngine to handle new entity types (recordings, flowchart, scripts, settings).

### 6.7 Upload pipeline

1. User selects scenario
2. App packages: scenario + recordings + templates + scripts + flowchart + settings → ZIP
3. User fills metadata (name, description, tags, icon)
4. Upload ZIP to Cloud Storage
5. Create Firestore document with metadata
6. Status = PENDING_REVIEW

### 6.8 Review/moderation (future)

- Cloud Function validates ZIP structure on upload
- Admin panel (simple web app or Discord bot) for manual review
- Auto-approve after N approved uploads (trusted publisher)

### Checklist

- [ ] Set up Firebase project (Auth, Firestore, Storage)
- [ ] Create `core/store/` module
- [ ] Implement MacroStoreRepository (Firebase SDK)
- [ ] Implement macro packaging (extend BackupEngine)
- [ ] Implement upload flow
- [ ] Implement download + import flow
- [ ] Create `feature/macro-store/` module
- [ ] Build browse screen (grid, search, filters)
- [ ] Build detail screen (description, screenshots, reviews)
- [ ] Build upload screen (metadata form)
- [ ] Build profile screen
- [ ] Implement rating/review system
- [ ] Set up Cloud Functions for validation
- [ ] Add store entry point to main navigation
- [ ] Handle Firebase only in playStore flavor (fDroid gets stub)
- [ ] Test full upload/download cycle

---

## Module Dependency Graph

```
smartautoclicker (app)
├── feature/recording-config    → core/recording
├── feature/flowchart-editor    → core/flowchart
├── feature/script-editor       → core/scripting
├── feature/ui-builder          → core/custom-ui
├── feature/macro-store         → core/store
├── feature/backup              → core/* (all)
├── feature/smart-config        → core/smart/*
├── feature/dumb-config         → core/dumb
└── feature/*                   → core/common/*

core/recording
├── core/common/actions         (replay via dispatchGesture)
├── core/common/overlays        (recording overlay)
├── core/common/display         (screen coordinates)
└── core/smart/database         (recording entities in ClickDatabase)

core/flowchart
├── core/common/actions         (execute gestures)
├── core/smart/processing       (reuse ConditionsVerifier)
├── core/smart/detection        (image/text/color detection)
├── core/recording              (play recordings)
├── core/scripting              (run scripts)
└── core/smart/database         (flowchart entities)

core/scripting
├── core/common/actions         (touch API → dispatchGesture)
├── core/smart/detection        (find API → NativeDetector)
├── core/recording              (Record.play API)
├── core/custom-ui              (Setting API)
└── core/smart/database         (script entities)

core/custom-ui
├── core/common/overlays        (render as overlay dialog)
└── core/smart/database         (setting entities)

core/store
├── core/smart/database         (scenario access)
├── feature/backup              (package/unpackage via BackupEngine)
└── Firebase SDK                (playStore flavor only)
```

---

## Settings.gradle.kts Additions

```kotlin
// New core modules
include(":core:recording")
include(":core:flowchart")
include(":core:scripting")
include(":core:custom-ui")
include(":core:store")

// New feature modules
include(":feature:recording-config")
include(":feature:flowchart-editor")
include(":feature:script-editor")
include(":feature:ui-builder")
include(":feature:macro-store")
```

---

## Risk Register

| Risk | Impact | Mitigation |
|---|---|---|
| Touch recording via overlay blocked by OEM | High | Fallback: AccessibilityService.onAccessibilityEvent() for gesture recording (limited) |
| Multi-finger limit varies per device | Medium | Check GestureDescription.getMaxGesturePoints(), degrade gracefully |
| Duktape/QuickJS JNI integration complexity | Medium | Start with QuickJS (better maintained), use existing JNI patterns from OpenCV |
| Infinite loops in user scripts | High | Instruction counter in Duktape, timeout via coroutine withTimeout() |
| DB migration complexity (v23 → v24+) | Medium | One migration per fase, test with Room testing library |
| APK size increase | Low | Duktape ~300KB, QuickJS ~500KB; flowchart UI is pure Canvas (no lib) |
| Firebase costs at scale (macro store) | Medium | Firestore indexes, Storage lifecycle rules, CDN caching, pagination |
| GPL-3.0 compatibility | Low | Duktape=MIT, QuickJS=MIT, Firebase=proprietary client lib (OK for app distribution) |

---

## Development Environment

Since no local PC is available:

### Primary: GitHub Codespaces / GitHub.dev
- Press `.` in repo → browser-based editor (github.dev)
- Create Codespace → full dev environment with terminal
- Free: 120 core-hours/month

### Build: GitHub Actions
- Push to `dev` or `feature/*` → auto-build debug APK
- Download APK from Actions → Artifacts tab
- Install on device

### Testing: Device
- Install debug APK
- Test features manually
- Logcat via ADB (if available) or in-app debug overlay

### Code review: GitHub PR
- Feature branch → PR to dev
- CI runs tests automatically
- Merge when green

---

## Timeline Summary

| Fase | Duration | Depends On | Branch |
|---|---|---|---|
| 0: CI Setup | 1 day | — | `dev` |
| 1: Record & Replay | 2-3 weeks | Fase 0 | `feature/recording` |
| 2: Multi-finger | 1-2 weeks | Fase 1 | `feature/gestures` |
| 3: Flowchart Mode | 3-4 weeks | Fase 0 | `feature/flowchart` |
| 4: Scripting | 3-4 weeks | Fase 1,2,3 | `feature/scripting` |
| 5: UI Builder | 3-4 weeks | Fase 3,4 | `feature/ui-builder` |
| 6: Macro Store | 4-6 weeks | Fase 5 | `feature/store` |
| **Total** | **~16-23 weeks** | | |

Fase 1+2 and Fase 3 can run in parallel (different modules, no dependency).

---

## Reference: Macrorify Feature Mapping

| Macrorify Feature | Our Implementation | Status |
|---|---|---|
| Record & Replay (10 fingers) | TouchRecorder + ReplayEngine + recording overlay | Fase 1 |
| Playback speed/repeat/delay/random | ReplayParams in ReplayEngine | Fase 1 |
| Multi-finger gestures (pinch/zoom/drag) | SwipePath + GestureDescription builder | Fase 2 |
| 10-finger swipe builder | Swipe Builder canvas UI | Fase 2 |
| Abstract Mode (reactive actions) | FlowchartExecutor reactive scan loop | Fase 3 |
| Jobs (state machines) | FlowJob with job switching | Fase 3 |
| Variables (bool/number/string, scoped) | FlowVariable with MACRO/JOB scope | Fase 3 |
| Conditions (image/text/color/timer/etc) | FlowCondition sealed class | Fase 3 |
| Custom Actions (sequential composite) | FlowNode.GroupNode | Fase 3 |
| Performance Profiles | PerformanceProfile enum per job | Fase 3 |
| EMScript | JavaScript via Duktape/QuickJS | Fase 4 |
| Script API (click, find, Record.play, etc) | JNI bridge to existing engines | Fase 4 |
| Touch class (multi-finger low-level) | touch.multi/down/move/up/dispatch | Fase 4 |
| Setting Builder (UI components) | SettingComponent + runtime renderer | Fase 5 |
| Setting.show() in script | Exposed via scripting API | Fase 5 |
| Macro Store (browse/download/upload) | Firebase backend + store UI | Fase 6 |
| Developer accounts + review | Cloud Functions + admin panel | Fase 6 |
| Reputation system | Download count + ratings | Fase 6 |
| Flavor system (multi-variant macros) | Future enhancement | — |
| Pro/premium access tiers | Future enhancement | — |
