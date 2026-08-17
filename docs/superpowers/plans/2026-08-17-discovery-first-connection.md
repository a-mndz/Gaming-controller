# EuroPad Discovery-First Connection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace manual IP/port/PIN setup with a discovery-first connection screen.

**Architecture:** Model the three user-facing connection methods as pure data, then render that model in `DeckPickerScreen`. Keep mDNS discovery and `UdpTransport.connect` unchanged; discovered servers still supply the hidden host and port to the existing connection call.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit 4, Android Gradle Plugin 8.4.2.

## Global Constraints

- Android 10+ (minSdk 29); no new dependencies.
- Do not display IP addresses, ports, PIN fields, or QR payload fields in the normal app UI.
- Wi-Fi/hotspot and USB use existing mDNS discovery and UDP connection code.
- Bluetooth remains unavailable until RFCOMM transport is implemented.

---

### Task 1: Model Visible Connection Methods

**Files:**
- Create: `app/app/src/main/java/com/europad/app/ui/ConnectionMethod.kt`
- Create: `app/app/src/test/java/com/europad/app/ConnectionMethodTest.kt`

**Interfaces:**
- Produces: `ConnectionMethod.entries`, each with `label`, `detail`, and `available` properties.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun connectionMethods_onlyExposeImplementedLinks() {
    assertTrue(ConnectionMethod.Wifi.available)
    assertTrue(ConnectionMethod.Usb.available)
    assertFalse(ConnectionMethod.Bluetooth.available)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle.bat :app:testDebugUnitTest --tests com.europad.app.ConnectionMethodTest`

Expected: FAIL because `ConnectionMethod` is unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
enum class ConnectionMethod(val label: String, val detail: String, val available: Boolean) {
    Wifi("WI-FI / HOTSPOT", "Find your PC automatically", true),
    Usb("USB TETHERING", "Enable tethering, then connect", true),
    Bluetooth("BLUETOOTH", "Available in a future update", false),
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle.bat :app:testDebugUnitTest --tests com.europad.app.ConnectionMethodTest`

Expected: PASS.

### Task 2: Render Discovery-First Setup

**Files:**
- Modify: `app/app/src/main/java/com/europad/app/ui/DeckPickerScreen.kt`

**Interfaces:**
- Consumes: `ConnectionMethod.entries`.
- Preserves: `doConnect(host: String, port: Int, pin: Int)` and `EuroPadDiscovery.hosts`.

- [ ] **Step 1: Render connection methods**

```kotlin
ConnectionMethod.entries.forEach { method ->
    ConnectionMethodCard(method = method, selected = method == selectedMethod) {
        if (method.available) selectedMethod = method
    }
}
```

- [ ] **Step 2: Replace the manual fields**

Remove the IP, port, PIN, and QR-payload rows. Render discovered PCs by `info.name` only and connect through `doConnect(info.host, info.port, 0)` when tapped.

- [ ] **Step 3: Add empty and USB states**

```kotlin
Text(if (selectedMethod == ConnectionMethod.Usb)
    "Enable USB tethering, then connect your phone to the PC."
else
    "Looking for EuroPad on the same network…")
```

- [ ] **Step 4: Verify UI compiles**

Run: `gradle.bat :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

### Task 3: Verify Android Deliverable

**Files:**
- Verify: `app/app/src/main/java/com/europad/app/ui/ConnectionMethod.kt`
- Verify: `app/app/src/main/java/com/europad/app/ui/DeckPickerScreen.kt`

- [ ] **Step 1: Run all Android unit tests**

Run: `gradle.bat :app:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 2: Build the debug APK**

Run: `gradle.bat :app:assembleDebug`

Expected: BUILD SUCCESSFUL.
