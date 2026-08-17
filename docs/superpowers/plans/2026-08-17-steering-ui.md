# EuroPad Steering UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refine the Android driving decks into clear, one-thumb steering surfaces that match the established Pit Wall design language.

**Architecture:** Add a small, testable steering-display formatter to `PitWall`, then use it from a shared visual treatment in the Truck and Arcade steering fields. Keep all input behaviour inside the existing deck composables: layout changes must not alter `DeckEngine`, gyro calculation, or packet axes.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit 4, Android Gradle Plugin 8.4.2.

## Global Constraints

- Android 10+ (minSdk 29); no new dependencies.
- Preserve existing `AX_STEER`, trigger axes, and button bit mappings.
- Use Pit Wall colors: indigo for live state, amber only for warnings.
- Controls remain large, landscape-first, and reachable by one thumb.
- No gradients, shadows, blur, or state-transition animation.

---

### Task 1: Add Fixed-Width Steering Readout

**Files:**
- Modify: `app/app/src/main/java/com/europad/app/ui/PitWall.kt`
- Modify: `app/app/src/test/java/com/europad/app/PitWallTest.kt`

**Interfaces:**
- Produces: `PitWall.steerDisplay(value: Float): String`, returning a signed, three-digit percentage within `-100` to `+100`.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun steerDisplay_isSignedAndFixedWidth() {
    assertEquals("-100", PitWall.steerDisplay(-1f))
    assertEquals("+000", PitWall.steerDisplay(0f))
    assertEquals("+038", PitWall.steerDisplay(0.375f))
    assertEquals("+100", PitWall.steerDisplay(2f))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle.bat :app:testDebugUnitTest --tests com.europad.app.PitWallTest.steerDisplay_isSignedAndFixedWidth`

Expected: FAIL because `steerDisplay` is unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
fun steerDisplay(value: Float): String {
    val percentage = (value.coerceIn(-1f, 1f) * 100).toInt()
    return "%+04d".format(percentage)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle.bat :app:testDebugUnitTest --tests com.europad.app.PitWallTest.steerDisplay_isSignedAndFixedWidth`

Expected: PASS.

### Task 2: Redesign Truck Steering Surface

**Files:**
- Modify: `app/app/src/main/java/com/europad/app/ui/TruckDeck.kt`

**Interfaces:**
- Consumes: `PitWall.steerDisplay(value: Float)`.
- Preserves: `WheelBar` pointer behaviour and `onSteer` output range of `-1f..1f`.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun steerDisplay_clampsInputBeforeRendering() {
    assertEquals("-100", PitWall.steerDisplay(-1.5f))
    assertEquals("+100", PitWall.steerDisplay(1.5f))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle.bat :app:testDebugUnitTest --tests com.europad.app.PitWallTest.steerDisplay_clampsInputBeforeRendering`

Expected: FAIL until Task 1 clamps the input.

- [ ] **Step 3: Write minimal implementation**

```kotlin
Text(PitWall.steerDisplay(steer), color = PitWall.Ink, fontSize = 18.sp)
Box(Modifier.width(1.dp).fillMaxHeight().background(PitWall.Ink.copy(alpha = 0.55f)))
```

Place the fixed center datum behind the live indigo marker, add `L` and `R` end-stop labels, and move gyro controls to a compact row beneath the HUD. Keep the existing touch and gyro callbacks unchanged.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle.bat :app:testDebugUnitTest --tests com.europad.app.PitWallTest`

Expected: PASS.

### Task 3: Align Arcade Steering Surface

**Files:**
- Modify: `app/app/src/main/java/com/europad/app/ui/ArcadeRacingDeck.kt`

**Interfaces:**
- Consumes: `PitWall.steerDisplay(value: Float)`.
- Preserves: `ArcadeWheel` touch and gyro callbacks and its `-1f..1f` steer range.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun steerDisplay_roundsTowardZeroForStableReadouts() {
    assertEquals("+037", PitWall.steerDisplay(0.379f))
    assertEquals("-037", PitWall.steerDisplay(-0.379f))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle.bat :app:testDebugUnitTest --tests com.europad.app.PitWallTest.steerDisplay_roundsTowardZeroForStableReadouts`

Expected: FAIL until the formatter uses integer conversion.

- [ ] **Step 3: Write minimal implementation**

```kotlin
Text("STEER ${PitWall.steerDisplay(steer)}", color = PitWall.Ink, fontSize = 13.sp)
```

Add the same center datum, end-stop labels, and indigo position marker used by Truck. Keep the large throttle and brake plates untouched.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle.bat :app:testDebugUnitTest --tests com.europad.app.PitWallTest`

Expected: PASS.

### Task 4: Verify Android Deliverable

**Files:**
- Verify: `app/app/src/main/java/com/europad/app/ui/PitWall.kt`
- Verify: `app/app/src/main/java/com/europad/app/ui/TruckDeck.kt`
- Verify: `app/app/src/main/java/com/europad/app/ui/ArcadeRacingDeck.kt`

**Interfaces:**
- Verifies the existing public deck composables still compile and preserve their transport parameter.

- [ ] **Step 1: Run the focused unit tests**

Run: `gradle.bat :app:testDebugUnitTest --tests com.europad.app.PitWallTest`

Expected: PASS.

- [ ] **Step 2: Run all Android unit tests**

Run: `gradle.bat :app:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 3: Build the debug APK**

Run: `gradle.bat :app:assembleDebug`

Expected: BUILD SUCCESSFUL and APK at `app/app/build/outputs/apk/debug/app-debug.apk`.
