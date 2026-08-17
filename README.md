# EuroPad 🎮🚚

**Your Phone. Your Virtual Truck Controller & Sim Deck.**

EuroPad transforms your Android phone into an ultra-low latency, full-screen virtual controller with real-time haptic feedback for PC games (Euro Truck Simulator 2, American Truck Simulator, Forza, Assetto Corsa, etc.).

---

## 📦 Ready-to-Install Mobile Builds

Pre-built binaries are placed in the root directory for immediate setup:
- **Android APK (Direct Install)**: [`EuroPad-Mobile.apk`](EuroPad-Mobile.apk)
- **Android App Bundle (AAB)**: [`EuroPad-Mobile.aab`](EuroPad-Mobile.aab)
- **Windows PC Server (Executable)**: [`server-bin/EuroPadServer.exe`](server-bin/EuroPadServer.exe)

---

## 🚀 Quick Start Guide

### 🖥️ Step 1: Launch on Windows PC

1. **Prerequisite**: Install the [ViGEmBus 1.22.0 Driver](https://github.com/nefarius/vigembus/releases) (enables Windows virtual Xbox 360 gamepad emulation).
2. **Start the PC Server**:
   - **Option A (Standalone Exe)**: Run `server-bin\EuroPadServer.exe`.
   - **Option B (From Source)**:
     ```powershell
     dotnet run --project server/EuroPad.Server/EuroPad.Server.csproj -c Release
     ```
3. The server immediately registers as a virtual Xbox 360 controller (Player 1) and begins broadcasting on your local network (UDP Port `4242` / mDNS `_europad._udp`).

---

### 📱 Step 2: Install & Launch on Android Phone

1. **Install the APK on your device**:
   - **Via ADB**:
     ```powershell
     adb install -r EuroPad-Mobile.apk
     ```
   - **Direct Transfer**: Copy `EuroPad-Mobile.apk` to your phone storage and tap to install.
2. **Open EuroPad**:
   - **Wi-Fi Mode**: Ensure your PC and phone are on the same Wi-Fi / hotspot. EuroPad will discover your PC server automatically in the **PC SERVER RADAR** list. Tap **`CONNECT ➔`**!
   - **USB Tethering Mode**: Connect via USB cable, enable *USB Tethering* in Android settings, and tap **`USB TETHERING`** in EuroPad for ultra-low latency (<1 ms).
   - **Manual IP**: Enter your PC's LAN IP directly (e.g. `192.168.1.50`) and tap **`CONNECT`**.
   - **Offline / Practice**: Tap **`➔ DRIVE / TEST OFFLINE`** in the top header to enter the cockpit immediately without connecting to a PC.

---

## 🕹️ Steering Modes & Cockpit Controls

| Feature | Touch Wheel Mode | Gyroscope Mode |
|---|---|---|
| **Steering Control** | Smooth 360° rotational on-screen wheel with spring-to-center physics | Physical phone tilting (hardware rotation vector sensor) |
| **Left Thumb** | Circular steering wheel gestures | **ACCELERATOR** pedal (immediate 100% responsive press) |
| **Right Thumb** | Dual side-by-side **BRAKE** & **ACCEL** pedals | **BRAKE** pedal (immediate 100% responsive press) |
| **Sensitivity** | Configurable: 180°, 270°, 360°, 540°, 900° lock-to-lock | Configurable: 90°, 180°, 270°, 360° range + Quick Recenter |

### Top Cockpit Functions:
- **`LIGHTS`**: Toggle truck headlights dome and beam rays.
- **`WIPER`**: Cycle wiper speed.
- **`VIPER`**: Trigger windshield washer fluid spray.
- **`R | N | D`**: Instant gear selector (Reverse, Neutral, Drive).
- **`HANDBRAKE`**: Toggle parking brake `(P)`.
- **`SETTINGS`**: Open in-game key remapping and steering mode configuration.
- **`MENU`**: Access quick steering sensitivity controls, gyro recenter, and connection switcher.
- **`← | →`**: Turn signal arrows.
- **`CAMERA`**: Change camera viewpoint.

---

## 🛠️ Building From Source

### Building PC Server (.NET 8):
```powershell
dotnet build server/EuroPad.Server.sln -c Release
dotnet test server/EuroPad.Server.sln
```

### Building Android App (Gradle):
```powershell
cd app
& 'C:\Users\amand\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat' :app:testDebugUnitTest :app:assembleDebug :app:bundleDebug
```

---

## 📄 License
Personal & open simulator project. See [`PROJECT_BIBLE.md`](PROJECT_BIBLE.md) for architectural specifications.
