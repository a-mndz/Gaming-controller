# EuroPad 🎮🚚

**Your phone as the wheel.** An Android phone becomes a virtual Xbox 360 controller for Euro Truck
Simulator 2 (and anything else that reads a gamepad — ATS, Forza, Assetto Corsa), with a full-screen
truck deck, remappable keyboard actions and rumble fed back to the phone's vibrator.

Two parts: a Kotlin/Compose Android app, and a .NET 8 Windows server that presents the pad through
ViGEmBus and injects keystrokes for everything a gamepad has no button for.

---

## Requirements

| | |
|---|---|
| **PC** | Windows 10/11 x64, [ViGEmBus 1.22.0](https://github.com/nefarius/vigembus/releases), .NET 8 runtime |
| **Phone** | Android 10 (API 29) or newer, same LAN as the PC — or a USB cable |
| **Link** | UDP `47910`, discovered over mDNS `_europad._udp`. Wire protocol v2, 30-byte frames |

---

## Quick start

Every command below is PowerShell, run **from the repo root** — the folder that contains
`build-and-deploy.ps1` and `README.md`. On the machine this was built on that is:

```powershell
cd "C:\Users\amand\Downloads\controller euro"
```

**1. PC.** Install ViGEmBus, then run the server:

```powershell
dotnet run --project server/EuroPad.Server/EuroPad.Server.csproj -c Release
```

It registers a virtual Xbox 360 pad as Player 1, starts announcing itself over mDNS, and prints a
scannable pairing QR (`ip=…&port=…`) in its console window. Add `--pin 4321` if you want the link
PIN-locked, and the PIN joins the QR payload. Allow the server through the firewall on UDP 47910
(private network) the first time.

**2. Phone.** The ready-to-install build sits at the repo root as
[`EuroPad-Mobile.apk`](EuroPad-Mobile.apk). Either copy it to the phone and tap it, or push it over USB:

```powershell
adb install -r EuroPad-Mobile.apk
```

If PowerShell answers `adb : The term 'adb' is not recognized`, adb is not on your PATH — call it by
its full path instead (this is where the Android SDK puts it):

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r EuroPad-Mobile.apk
```

**3. Connect.** The PC shows up by itself in the **PC SERVER RADAR** list — tap the row rather than
typing an IP. Also available: **USB TETHERING** (plug in, enable USB tethering in Android settings,
tap the button — lowest latency there is), **manual IP**, and **DRIVE / TEST OFFLINE** to explore the
deck with no PC at all.

**4. In ETS2**, set steering to the controller axis and leave the game's own steering sensitivity and
non-linearity near default — the app's return-to-centre profile is tuned against that (see below).

---

## Steering

Two modes, switched from the **MENU** sheet or the deck picker.

**Touch wheel.** A rotational on-screen wheel. Lock-to-lock presets are 180 / 270 / 360 / 450 / 540 /
720 / 900°, and the fine stepper covers 160–900° in 20° increments. Default 360°.

**Gyro — tilt like a wheel.** Hold the phone upright in landscape and rotate it as if it *were* the
wheel. It reads the **gravity vector's roll**, not compass yaw: gravity is an absolute reference the
sensor fusion never loses, and its in-plane component is strongest exactly in the upright grip a
driver uses — where yaw is degenerate and drifts. Presets are 60 / 90 / 120 / 180 / 240 / 300 / 360°
lock-to-lock (fine: 40–360° in 10° steps), default 180°. **SET CENTRE** declares your current grip as
straight-ahead. Lay the phone flat and steering bleeds to centre instead of latching the last tilt.

### Return to centre — why "set the axis to 0" was not enough

Releasing the wheel used to centre the controller (Windows' own controller panel agreed) while the
truck kept its turn. Nothing was being dropped: **ETS2 does not steer to the axis, it moves an
internal virtual wheel *towards* the axis at a limited rate** — that is what the game's steering
sensitivity and non-linearity settings act on. Collapse the axis to 0 in one frame and the game's
wheel is left wherever its rate limiter had reached, with no displacement left to unwind it.

So the app releases the wheel the way a hand does: it **sweeps** back through every intermediate angle
slowly enough for the game to track, carries a small **counter-steer** past centre to cancel the
deflection the rate limiter is still holding, then **settles** to exactly 0. The sweep is scaled by how
far the wheel was turned, so a flick unwinds faster than a full lock.

If the truck still holds a turn after you let go, raise RETURN TIME and COUNTER-STEER a couple of
steps each — that means ETS2 is tracking more slowly than the defaults assume.

### The six tuning adjusters (MENU sheet)

Each has a hold-to-repeat stepper, so crossing 20–40 levels is one press rather than forty taps.

| Mode | Adjuster | Range (step) | Default | What it does |
|---|---|---|---|---|
| Gyro | TILT RANGE | 40–360° (10°) | 180° | How far your wrist travels for full lock |
| Gyro | PRECISION | 0–100% (5%) | 35% | Softens the middle of the travel — lane-keeping without losing full lock |
| Gyro | SMOOTHING | 0–80 ms (4 ms) | 22 ms | Damping. The only adjuster that costs latency; raise it last |
| Wheel | WHEEL RANGE | 160–900° (20°) | 360° | Lock-to-lock |
| Wheel | RETURN TIME | 120–1200 ms (40 ms) | 420 ms | How long the release sweep takes |
| Wheel | COUNTER-STEER | 0–30% (2%) | 10% | How far past centre the sweep carries |

PRECISION is exponential shaping (`|out| = |in|^(1+2·curve)`): it pins 0 at 0 and ±1 at ±1, so full
lock still arrives at the same tilt while the middle of the travel — where you actually live —
stretches out. Range alone cannot buy both.

---

## Pedals

The **accelerator is digital** on purpose: press means 100%, instantly.

The **brake is two-stage**: a press gives 50%, and holding it past **3 seconds** takes it to 100%.
Release always returns to 0. Dab for gentle retardation, lean on it to stop. (The server maps 50% to
127/255 of the trigger, so it really is half — not a rounded-up full press.)

---

## Cockpit controls

`LIGHTS` · `WIPER` · `VIPER` (washer) · `HANDBRAKE` · `R | N | D` gear selector · `← | →` indicators ·
`CAMERA` · `SETTINGS` · `MENU`. Gear selection presses the game's relative gear keys the right number
of times in the right direction, so R→D is not one press but the several ETS2 needs.

**EDIT LAYOUT** (in SETTINGS) lets you drag and resize the deck controls; the layout is saved per
mode, so the wheel and gyro decks can be arranged differently.

**Remapping** is entirely on the phone — SETTINGS lists all 16 actions with their current key, and
picking a new one both saves it locally and pushes a config frame that makes the server rewrite its
profile JSON. No PC-side editing. Bindings are re-pushed on every fresh connect, so one lost frame
during a Wi-Fi stall cannot leave the phone and the game disagreeing.

Defaults: Indicators `[` `]` · Hazards `F` · Horn `H` · Air Horn `N` · Handbrake `Space` · Lights `L` ·
High Beam `K` · Warn Beacon `O` · Wipers `P` · Exhaust Brake `B` · Diff Lock `V` · Axle Raise `U` ·
Engine `E` · Gear Up `Shift` · Gear Down `Ctrl`.

Server-side profiles live in [`profiles/`](profiles) (`default.json`, `ets2.json`) and hot-reload
while the server runs, so you can edit them in a text editor mid-session.

---

## Latency

**What the app already does.** The sender is event-driven, not a metronome: an input goes out the
moment it lands, capped at 250 Hz, with a 12 ms heartbeat when nothing is happening — lower latency
*and* fewer idle packets on a busy channel. Gyro samples are requested at 5 ms with sensor batching
disabled and delivered on a dedicated high-priority thread, then pushed straight into the frame rather
than polled by the UI. Frames are marked DSCP EF. The session holds a
`WifiLock(WIFI_MODE_FULL_LOW_LATENCY)`, which matters more than anything else on 2.4 GHz: without it
the radio parks between packets and datagrams wait for a beacon slot — invisible in the app's own UI,
because that never crosses the air. Filters are specified as time constants, so a phone that grants
200 Hz instead of 50 Hz gets finer resolution without changing the feel.

**Reading the numbers.** The deck's status strip is `transport · RTT · LOSS`:

- Single-digit to low-teens RTT, steady, ~0% loss → you're done.
- Low baseline with spikes to 50–200 ms → power-save or off-channel Wi-Fi scanning on the phone.
- Steadily high → channel congestion or a low negotiated rate.
- Loss with a decent RTT → interference; change channel.
- `FAILSAFE` on the server console means 800 ms with no frame at all. Rare is normal; bursts are the air.
- To settle whether the air is the problem at all: **USB-tether and drive.** If the lag vanishes, stop
  tuning the app.

**Getting the most out of 2.4 GHz**, in rough order of payoff:

1. **Put the PC on Ethernet.** Otherwise every frame crosses the air twice, sharing one congested
   channel. Usually worth more than everything below combined.
2. **Channel 1, 6 or 11 only, 20 MHz width.** Those three are the only non-overlapping choices; 40 MHz
   on 2.4 GHz overlaps everything and triggers coexistence back-off. Scan first and take the least
   *occupied* channel — overlap, not weak signal, is what causes 20–100 ms spikes.
3. **DTIM 1**, default 100 ms beacon interval. A higher DTIM tells clients to sleep through beacons,
   which is exactly the delay the WifiLock is fighting.
4. **WMM / QoS on.** The frames are already marked DSCP EF; WMM is what maps that to the Voice access
   category. Without it the marking does nothing.
5. **WPA2-AES only** (no WPA/WPA2 mixed, no TKIP), and raise the minimum rate if the router allows it —
   one client negotiating 1 Mbps steals airtime from everyone.
6. **Phone:** Bluetooth off while driving (shared radio), EuroPad exempt from battery optimisation,
   adaptive connectivity and Wi-Fi scanning off. Off-channel scans punch ~100 ms holes that feel
   exactly like input lag.
7. **Windows:** High performance power plan; if the PC is on Wi-Fi, adapter power management off and
   the driver's power-saving set to maximum performance. Keep backups and Windows Update off the link —
   bufferbloat on the *uplink* shows up as steering lag.

---

## Building

### Where everything lives

| What | Path (relative to the repo root) |
|---|---|
| Build + deploy script | `build-and-deploy.ps1` |
| Install-now phone build | `EuroPad-Mobile.apk` · `EuroPad-Mobile.aab` (tracked snapshots — refresh before committing) |
| Android sources | `app/app/src/main/java/com/europad/app/` |
| Gradle project root | `app/` — that is the `-p` argument; `app/app` is the module, hence `:app:` tasks |
| Freshly built APK | `app/app/build/outputs/apk/debug/app-debug.apk` |
| Freshly built AAB | `app/app/build/outputs/bundle/debug/app-debug.aab` |
| JVM tests | `app/app/src/test/java/com/europad/app/` |
| Server sources | `server/EuroPad.Server/` (solution: `server/EuroPad.Server.sln`) |
| Published server | `server-bin/EuroPadServer.exe` — produced by the publish step, **not** in the repo |
| Server keymaps | `profiles/default.json`, `profiles/ets2.json` — edit while running, they hot-reload |
| Phone settings | SharedPreferences `europad` on the device; nothing to edit by hand, it is all in the MENU sheet |

### The easy way — one command

```powershell
cd "C:\Users\amand\Downloads\controller euro"
.\build-and-deploy.ps1
```

That does everything in the only order that works: server tests, publish, restart the server, app
tests, APK, force-stop, reinstall, relaunch. The leading `.\` is not optional — PowerShell refuses to
run a script from the current directory without it. If it answers *"running scripts is disabled on
this system"*, bypass the policy for this one run:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-and-deploy.ps1
```

The script finds its own tools and prints the path it chose for each — `dotnet`, `gradle`, `adb` — in
its first few lines, so a `<not found>` there names exactly what is missing. It also locates a JDK 17
itself (Adoptium / Java / Microsoft under `Program Files`, or your `JAVA_HOME`); pass
`-JavaHome "C:\path\to\jdk-17"` if it cannot. Nothing needs to be on your PATH.

Flags: `-SkipServer`, `-SkipApp`, `-SkipTests`, `-NoInstall`, `-NoServerStart`, `-Serial <device>`,
`-ClearLog`, `-JavaHome <path>`. It pins JAVA_HOME to a JDK 17, stops the running server before
publishing (the exe locks itself), and restarts the old server if a later step fails.

### By hand

Server — from the repo root, needs the .NET 8 SDK on PATH:

```powershell
dotnet test server/EuroPad.Server.sln -c Release
dotnet publish server/EuroPad.Server/EuroPad.Server.csproj -c Release -r win-x64 --self-contained false -o server-bin
.\server-bin\EuroPadServer.exe
```

App — needs Gradle 8.9 and a JDK 17. **`gradle` is usually not on PATH**, and a bare `gradle …` then
fails with *"The term 'gradle' is not recognized"*. So point JAVA_HOME at a JDK 17 and call the Gradle
distribution by its full path. The glob below resolves it on any machine that has ever run the script,
since that is what downloaded the distribution:

```powershell
cd "C:\Users\amand\Downloads\controller euro"
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$gradle = @(Resolve-Path "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.9-bin\*\gradle-8.9\bin\gradle.bat")[0].Path
& $gradle -p app --console=plain :app:testDebugUnitTest :app:assembleDebug :app:bundleDebug
```

Adjust `JAVA_HOME` if your JDK sits elsewhere — `Get-ChildItem 'C:\Program Files\Eclipse Adoptium'`
lists what is installed. Drop the task names you do not want; `:app:assembleDebug` alone gives the APK.

Nicer, one-time: run `& $gradle -p app wrapper --gradle-version 8.9` once. That writes
`app/gradlew.bat`, which `build-and-deploy.ps1` prefers over every other candidate, and afterwards
`.\app\gradlew.bat :app:assembleDebug` works with no path juggling at all.

### Refreshing the root snapshots

`EuroPad-Mobile.apk` / `.aab` are committed so anyone can install without building. **Copy the fresh
build over them before you commit**, or people install a stale one. Note that
`build-and-deploy.ps1` builds the APK only, so add `:app:bundleDebug` above if you want the AAB current:

```powershell
cd "C:\Users\amand\Downloads\controller euro"
Copy-Item app\app\build\outputs\apk\debug\app-debug.apk EuroPad-Mobile.apk -Force
Copy-Item app\app\build\outputs\bundle\debug\app-debug.aab EuroPad-Mobile.aab -Force
```

### Tests

71 xUnit tests on the server (frame codec, axis routing, key scan, profile hot-reload, control
frames) and ~160 JVM tests on the phone. The steering and tilt math is deliberately pure so it can be
tested without a device: `SteerReturnTest` pins the return profile (sweeps through the middle rather
than snapping, counter-steer crosses centre and comes back, ends at exactly 0), `GyroTiltTest` checks
the gravity-roll physics against an independent rig, `GyroTuningTest` covers the precision curve and
proves the smoothing behaves identically at 50 Hz and 200 Hz, and `PedalStageTest` pins the brake's
half-press at 127/255.

---

## Repo layout

Source tree, top down — for build outputs and where installable files land, see
[Where everything lives](#where-everything-lives) above.

```
app/                    Android app (Kotlin, Compose). Module is app/app.
  app/src/main/java/com/europad/app/
                        input/ = frames, gyro, pedals, SteerReturn; net/ = UDP, discovery;
                        ui/ = deck, deck picker, layout editor, keymap
  app/src/test/java/com/europad/app/
                        JVM tests (SteerReturnTest, GyroTiltTest, GyroTuningTest, PedalStageTest)
server/                 .NET 8 server. Emulation/ = ViGEmBus pad + SendInput; Net/ = mDNS, pairing QR;
                        Profiles/ = JSON keymaps with hot reload; Protocol/ = wire format
profiles/               Shipped server profiles (default.json, ets2.json)
build-and-deploy.ps1    Build + deploy both sides — the one supported entry point
EuroPad-Mobile.apk      Install-now snapshot of the phone app (.aab beside it)
ARCHITECTURE.md · PRD.md · PROJECT_BIBLE.md
```

---

## Troubleshooting

| Symptom | Cause |
|---|---|
| Truck holds its turn after you let go | ETS2 tracking slower than assumed — raise RETURN TIME and COUNTER-STEER |
| Steering fine in the app, laggy in game | The link, not the app. Check the RTT/LOSS strip, then the 2.4 GHz list |
| Tilt steers the wrong way | Hold the phone upright in landscape and press SET CENTRE |
| Tilt ignores you / drifts to centre | Phone is too close to flat for roll to mean anything — sit it up |
| No PC in the radar list | mDNS blocked: enter the IP manually, or check the firewall on UDP 47910 |
| `FAILSAFE` bursts in the server console | 800 ms with no frames — Wi-Fi, not code |
| Pad shows up but nothing moves | ViGEmBus not installed, or ETS2 is still bound to keyboard steering |
| `The term 'gradle' / 'adb' is not recognized` | Neither is on PATH — use the full-path commands in [Building](#by-hand), or just run `.\build-and-deploy.ps1`, which finds both |
| `build-and-deploy.ps1 is not recognized` | Missing the leading `.\`, or you are not in the repo root |
| `running scripts is disabled on this system` | Execution policy — `powershell -ExecutionPolicy Bypass -File .\build-and-deploy.ps1` |
| `APK not found at …` from the script | The Gradle step failed further up; scroll back to its first red line |

---

## License

Personal, open simulator project. Architecture and design notes in
[`ARCHITECTURE.md`](ARCHITECTURE.md) and [`PROJECT_BIBLE.md`](PROJECT_BIBLE.md).
