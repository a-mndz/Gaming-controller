# ARCHITECTURE.md
> Technical architecture for EuroPad. Requirements live in PRD.md; principles in PROJECT_BIBLE.md.

## 1. System overview

```
┌────────────────────────────┐                    ┌──────────────────────────────┐
│      Android phone app     │                    │     Windows PC server        │
│        (Kotlin/Compose)    │                    │         (C# / .NET 8)        │
│                            │                    │                              │
│  Deck UI (10 modes)        │   USB tethering    │  UDP listener (per iface)    │
│  └─ touch/sensor capture   │═══════════════════►│  RFCOMM listener (BT, ph4)   │
│  Gyro engine (rotation vec)│   Wi-Fi UDP        │      │                       │
│  Encoder (30B snapshot)    │───────────────────►│      ▼                       │
│  Transport switcher        │   BT RFCOMM        │  PacketDispatcher            │
│  RTT / HUD                 │───────────────────►│      │ diff → button edges   │
│  mDNS discovery            │                    │      ▼                       │
│  HapticEngine (vibrator)   │                    │  SlotManager (XInput 0..3)   │
│                            │◄── RTT echo ───────│  InputState store            │
│                            │◄── RUMBLE frames ──│      │                       │
└────────────────────────────┘                    │      ├──► ViGEmClient ──► [Virtual X360 pad, slots 0..3]
                                                  │      │    └─ rumble notify callback
                                                  │      ├──► KeyboardEmulator (SendInput)
                                                  │      └──► MediaKeyEmulator (SendInput, ph4)
                                                  │                              │
                                                  │  ProfileManager (hot-reload JSON)
                                                  │  FailsafeTimer (300ms)       │
                                                  │  Tray icon (WinForms-lite)   │
                                                  └──────────┬───────────────────┘
                                                             ▼
                                              Windows Input ──► Steam / ETS2 / any game
```

## 2. Repos & projects

```
controller euro/               (repo root = C:\Users\amand\Downloads\controller euro — space in name, quote in shells)
├── README.md  PROJECT_BIBLE.md  PRD.md  ARCHITECTURE.md   ← 8 core docs,
├── RULES.md   MEMORY.md         TASKS.md  DECISIONS.md       at repo root
├── server/                    # C# solution
│   ├── EuroPad.Server/        # main exe (tray app)
│   │   ├── Net/               # UDP + RFCOMM listeners, handshake
│   │   ├── Protocol/          # packet codec, seq dedupe
│   │   ├── Emulation/         # ViGEm pad, keyboard, media keys, failsafe, RumbleRelay
│   │   ├── Profiles/          # JSON load/hot-reload/validate
│   │   └── Tray/              # tray UI, logging
│   └── EuroPad.Server.Tests/  # xUnit: codec, diff, failsafe, profiles
├── app/                       # Android app (Kotlin, Gradle Kotlin DSL)
│   └── src/main/
│       ├── ui/                # deck picker + 10 deck screens (Compose)
│       ├── input/             # touch capture, gyro engine, encoder
│       ├── haptics/           # HapticEngine + watchdog
│       ├── net/               # UDP/USB/BT transports, discovery, RTT
│       └── settings/          # persistence (DataStore)
├── tools/echotest/            # loopback latency/loss test harness
└── profiles/                  # shipped JSON profiles (ets2.json, default.json)
```

## 3. Snapshot packet (wire format)

Little-endian binary, fixed 30 bytes, over UDP datagrams or RFCOMM-delimited frames.

| Offset | Size | Field | Notes |
|---|---|---|---|
| 0 | 2 | magic | `0xE0 0x01` ("EP v1") |
| 2 | 1 | version | protocol version = 1 |
| 3 | 1 | flags | bit0=ping-request, bit1=ping-reply, bit2=hello, bit3=rumble feedback (server→phone), **bit4=ack**, **bit5=reject** |
| 4 | 2 | seq | u16, increments per send |
| 6 | 4 | timestamp_ms | u32, phone uptime ms (for RTT) |
| 10 | 2 | buttons_lo | u16 bitmask: ABXY, LB/RB, Start/Back, Dpad×4, misc |
| 12 | 2 | buttons_hi | u16 bitmask: deck-specific actions (indicators, horn, lights…) |
| 14 | 16 | axes[8] | i16 ×8, order: LX, LY, RX, RY, LT, RT, steer, aux0 |

**Frame total = 30 bytes.** Buttons are a *state*; server diffs consecutive snapshots to synthesize press/release edges (PRD FR-2.4). Axes are absolute — `0` = neutral.

Ping packets reuse the same frame with flags bit0 set and axes/buttons zeroed; server replies with bit1 + the client's timestamp so RTT = now − timestamp. Ordinary snapshots may also carry bit0: the server echoes those as well, giving the phone RTT samples during active play at zero extra packet cost (FR-3.4).

Rumble feedback frames reuse the frame with flags bit3 set: `buttons_lo` carries left motor u8 (low byte) + right motor u8 (high byte); `slot` implicit from the connection; phone maps to vibrator amplitude.

Handshake payload overload (keeps the frame fixed at 30 bytes):
- **HELLO** (bit2): `buttons_lo` = 4-digit PIN (0 = no PIN set).
- **ACK** (bit4): `buttons_lo` = assigned XInput slot (0–3); `timestamp_ms` = server clock reference for RTT.
- **REJECT** (bit5): `buttons_lo` = reason code (1=version mismatch, 2=wrong PIN, 3=LOBBY_FULL).

Socket tuning (research-verified, both ends):
- Small send buffers (avoid queuing delay; throughput is irrelevant at 30 B × 240 Hz).
- Set DSCP EF (ToS 0xB8) on the datagram socket where the OS honors it.
- Send-immediately semantics: no Nagle-equivalent batching; phone sender never sleeps longer than the deck cadence.

Button bitmask allocation (v1):
- lo: DPAD_UP/RIGHT/DOWN/LEFT, START, BACK, LB, RB, A, B, X, Y, GUIDE, spare×3
- hi: IND_L, IND_R, HAZARD, HORN, AIR_HORN, HANDBRAKE, LIGHTS, BEAM, WARNING, WIPERS, EXH_BRAKE, DIFF_LOCK, AXLE_RAISE, ENGINE, GEAR_UP, GEAR_DN

Axis index mapping is per-deck (the phone encodes its UI into the canonical axis slots; profile decides whether "steer" goes to left-stick-X or a dedicated wheel axis).

## 4. Key components

### 4.1 Phone

- **DeckEngine**: active deck produces an `InputFrame` (buttons bitset + axes) at deck rate.
- **GyroEngine** (wheel/FPS decks):
  - Source: `Sensor.TYPE_GAME_ROTATION_VECTOR` where available, falling back to `TYPE_ROTATION_VECTOR` (D-008); registered at `SENSOR_DELAY_GAME`; request max rate via `registerListener(..., maxReportLatencyUs=0)` where supported (many phones deliver 200–400 Hz).
  - Extract yaw around the phone's vertical-in-plane axis → steering angle.
  - **Center calibration**: stores quaternion at "Center" tap; steering = angle delta from it, clamped to configured range.
  - **Smoothing**: complementary filter: `smooth = smooth + α(measured − smooth)`, α ≈ 0.25–0.5 adjustable; plus drift trim by blending toward accel-derived pitch when angular velocity is ~0 (framerate-independent via `event.timestamp` deltas).
- **Encoder**: InputFrame → 30-byte snapshot; dedupe by seq is trivially safe.
- **TransportManager**:
  - USB: same `DatagramSocket` bound/sent over the tether interface (`192.168.42.x` typical tether subnet; discover via `ConnectivityManager` routes).
  - Wi-Fi: UDP to discovered/saved endpoint.
  - BT (ph4): `BluetoothSocket` RFCOMM, length-prefixed frames, same codec.
- **HapticEngine**:
  - Receives server RUMBLE frames (left/right u8 motor values) on the receive coroutine.
  - Drives a **repeating waveform chunk** via `VibrationEffect.createWaveform(timings, amplitudes, repeat=...)` — continuous rumble emulation with amplitude = mapped motor value (scaled by user intensity slider). Incoming zeros or 500 ms timeout → `cancel()`.
  - ERM motor caveat (research-verified): spin-up/spin-down lag makes high-frequency modulation impossible; the design targets **amplitude tracking at ≤30 updates/s**, which phone motors render acceptably. On devices without `hasAmplitudeControl()`, fall back to duty-cycle on/off bursts.
  - Separate local UI ticks: `createOneShot(10–15ms, low amplitude)` on touch press — independent of game rumble.
- **HUD**: RTT (median of last 5 replies), loss % (seq gaps over window), transport icon, deck name, **slot number (P1–P4 in multiplayer)**.

### 4.2 Server

- **PacketDispatcher**: one handler per active client slot (max **4** — XInput hard limit). Parses, dedupes stale seq, invokes `InputStore.Apply(slot, frame)`.
- **InputDiff**: computes pressed/released/held sets between consecutive frames → feeds emulators.
- **SlotManager**: owns XInput slots 0–3. HELLO → lowest free slot allocated (ACK carries it); 5th phone → REJECT reason `LOBBY_FULL`. Slot freed on failsafe-expiry disconnect (2 s grace) so a flaky link can reclaim its slot.
- **PadEmulator**: wraps ViGEmClient `Xbox360Controller` per slot; pushes XINPUT_GAMEPAD state only on change. Registers the ViGEm rumble notification callback per pad (`vigem_target_x360_register_notification`).
- **RumbleRelay**: rumble callback → rate-limit (≤30 frames/s, coalescing) → feedback frame (flags bit3) to that phone's active transport. Per-slot independent: game rumbles pad 2 → only phone 2 vibrates.
- **KeyboardEmulator**: `SendInput` batched per tick; key codes from active profile; press = key-down, release = key-up; toggles (indicators/hazard) = one press per state flip.
- **FailsafeTimer**: per-slot; last-packet timestamp; on 300ms expiry → neutral frame.
- **ProfileManager**: watches `%APPDATA%\EuroPad\profiles\`; on file change → validate + swap atomically (no lock during swap).
- **Tray**: minimal WinForms tray app — connected slots (1–4), each slot's RTT, "Open profiles", "Copy pairing QR", "Exit".

### 4.3 Profiles (JSON schema, v1)

```json
{
  "name": "ets2",
  "game": "Euro Truck Simulator 2",
  "keys": {
    "IND_L": "[", "IND_R": "]", "HAZARD": "F", "HORN": "H",
    "HANDBRAKE": "Space", "ENGINE": "E", "LIGHTS": "L",
    "GEAR_UP": "Shift", "GEAR_DN": "Ctrl"
  },
  "axisMap": { "steer": "LX", "throttle": "RT", "brake": "LT" },
  "steerRange": 270
}
```

## 5. Data & control flow (one input cycle)

```
1. Phone touch/gyro event ──► DeckEngine builds InputFrame
2. Encoder → 30B snapshot, seq++ ──► UDP send (or RFCOMM write)
3. Server PacketDispatcher: magic/version check, seq dedupe
4. InputStore: merge into slot state (latest-wins)
5. InputDiff vs prev frame:
     pressed  → PadEmulator.Update(button) / KeyboardEmulator.KeyDown(profile key)
     released → PadEmulator / KeyboardEmulator.KeyUp
     axes     → PadEmulator.UpdateAxes (XInput range)
6. ViGEmClient pushes XINPUT_GAMEPAD state to the bus driver → OS → game poll
7. FailsafeTimer refreshed on every accepted packet
```

Feedback (haptics) return path:

```
1. Game calls rumble on the virtual pad → ViGEmBus delivers output report
2. PadEmulator's notification callback fires (slot, leftMotor u8, rightMotor u8)
3. RumbleRelay coalesces → ≤30/s feedback frames (flags bit3) on that slot's transport
4. Phone HapticEngine: amplitude-mapped repeating waveform chunk on the vibrator
5. Rumble watchdog (500 ms silence) → vibrator.cancel()
```

Latency budget (verified targets): phone capture ~2ms, encode ~0.1ms, LAN hop 2–4ms (5GHz) or ~0.3ms (USB), server dispatch <0.5ms, ViGEm submit <0.5ms, game poll ≤8ms (120Hz) — **median 5–10ms Wi-Fi, ~1–4ms USB** (tmphonepad measured 1–3ms over tethering).

## 6. Threading model

- Server: 1 thread per UDP listener (one per interface: LAN + tether), 1 per BT listener (ph4), emulators write on the dispatch thread (ViGEm + SendInput calls are cheap); timer thread for failsafe + RTT echo. ViGEm rumble callbacks arrive on the driver's thread — RumbleRelay hands them to a per-slot queue (no ViGEm API reentry, no locks on input hot path).
- Phone: UI thread stays free of encoding work — a dedicated `SenderCoroutine` (Dispatchers.IO) drains the latest InputFrame at capped rate; gyro callbacks just post latest value atomically. Receive loop handles RTT echoes + rumble frames; vibrator calls are cheap and non-blocking. Foreground service + PARTIAL_WAKE_LOCK + battery-optimization exemption request keeps the process alive (Doze; OEM skins like MIUI/ColorOS kill background aggressively — exemption flow is first-run).

## 7. Discovery & pairing

1. Server announces itself via mDNS (`_europad._udp` service) on all LAN interfaces; phone listens with Android NSD.
2. **Research-verified caveat**: NSD needs `CHANGE_WIFI_MULTICAST_STATE` + a held `MulticastLock`, and discovery is flaky on some OEM skins (slow start, failed resolution). Manual IP entry is therefore a **first-class path, not just a fallback** (ships in Phase 1, FR-1.2); QR scan ships in Phase 3 (FR-1.3/T3.6) and is a convenience layer on top. Discovery list refreshes lazily; manual/saved hosts always shown.
3. Phone lists services + saved manual hosts; user taps → sends HELLO frame (flags bit2).
4. Server: if PIN is enabled, HELLO must carry it; mismatch → REJECT frame with reason code → phone shows friendly error.
5. **Slot allocation**: server ACKs with endpoint + assigned XInput slot (0–3) + RTT clock reference. All 4 slots taken → REJECT `LOBBY_FULL`.
6. QR code ships Phase 3 (T3.6) and encodes `ip=…&port=…&pin=…` as a convenience for the first connection of each phone (slot assigned dynamically, never baked into the QR). Display surface: server console/log output in Phase 3; the tray's "Show pairing QR" takes over when the tray app lands (Phase 4, T4.6).

## 8. Error handling matrix

| Condition | Behavior |
|---|---|
| Malformed / bad-magic packet | drop, increment counter, stay silent |
| Version mismatch | REJECT frame, reason 1 (client shows expected protocol version from its own frame) |
| Stale seq | drop (idempotent — snapshot is full state) |
| 300ms silence | failsafe neutral input; HUD on phone shows LINK LOST |
| Phone app killed | same as silence; server slot freed after 2s for reuse |
| PIN wrong (3 tries) | that phone's endpoint (source IP) locked out for 60 s (lockout occurs before any slot is allocated) |
| 5th phone connects | REJECT `LOBBY_FULL` (XInput max 4); phone shows friendly message |
| Rumble frames stop (link died mid-rumble) | phone watchdog cancels vibrator after 500 ms |
| ViGEm driver missing | server starts, tray shows red badge + "Install ViGEmBus" action; keyboard-only emulation still works |

## 9. Testing strategy

- **Unit (server)**: codec round-trip, seq dedupe, button-edge diff, failsafe timing, profile schema validation, keymap parsing, rumble coalescing/rate-limit.
- **Unit (phone)**: encoder bit-packing, gyro center math, axis clamping, transport selection logic, haptic amplitude mapping.
- **Integration**: loopback server↔phone test harness (`tools/echotest/`) that measures RTT distribution and simulates 10% packet loss — asserts median RTT and zero stuck inputs; rumble round-trip timing test (game rumble → phone vibration ≤50 ms).
- **Multi-device test**: 4 simulated phones → 4 XInput slots active in `joy.cpl`, independent inputs, 5th client rejected.
- **Manual checklist** per phase (in PRD §4 acceptance criteria).

## 10. Build & run

- Server: `dotnet build server/EuroPad.Server.sln -c Release`; run `EuroPadServer.exe` (tray icon). Requires ViGEmBus 1.22.0 installed.
- Phone: Android Studio → open `app/`; Run to device (or `./gradlew assembleDebug` + sideload APK).
- Profiles: edit JSON in the profiles folder → server hot-reloads.
- See README.md for first-run walkthrough.

## 11. Future / v2 (explicitly parked)

- BluetoothHidDevice mode (phone = native BT gamepad, no server needed) — direct-input only, no keyboard; park. Note: this would bypass the XInput-4-limit for casual non-XInput games, but lose keyboard output and Steam-grade compatibility — parked deliberately (DECISIONS D-011/D-014).
- HD / trigger haptics beyond rumble translation (DualSense-style waveforms need a BT HID host or companion app — out of XInput scope).
- ETS2 telemetry second-screen dashboard.
- Linux server (uinput), per-deck layout sharing/import.
- DirectInput output pads for phone #5+ (only if ever needed; most games only see XInput).
