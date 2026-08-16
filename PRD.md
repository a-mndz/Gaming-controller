# PRD.md — Product Requirements Document
> Feature-level spec for EuroPad. For mission/principles see PROJECT_BIBLE.md; for how see ARCHITECTURE.md.

## 0. Scope statement

Phone app (Android) + PC server (Windows). Server emulates virtual Xbox 360 controllers (up to **4 simultaneous devices** — the XInput API hard limit) and virtual keyboard so any PC game / Steam / ETS2 accepts each phone as a normal controller. Three switchable transports, ten mode decks, **haptic feedback (game rumble → phone vibration)**, latency-first.

---

## 1. Functional requirements

### FR-1 Connection & transport

| ID | Requirement | Priority |
|---|---|---|
| FR-1.1 | Phone discovers PC server automatically on the LAN (mDNS/NSD) and shows it in a list. | M |
| FR-1.2 | Manual host entry (IP:port) with persistence. | M |
| FR-1.3 | QR code pairing: server displays QR containing endpoint + PIN; phone scans it. | C |
| FR-1.4 | Transport switcher UI: USB / Wi-Fi / Bluetooth selectable from any mode. | M |
| FR-1.5 | USB transport: phone enables USB tethering; server binds UDP to the tether interface; same protocol. Documented setup steps in-app. | M |
| FR-1.6 | Bluetooth transport: classic BT pairing + RFCOMM socket carrying the same snapshot protocol. | C (phase 4) |
| FR-1.7 | Optional PIN gate on first connection (4-digit, server-generated). | C |
| FR-1.8 | Live connection HUD on phone: RTT ms, packet loss %, transport icon, current mode. | M |
| FR-1.9 | Auto transport suggestion: if RTT stays above the deck's budget (120 Hz decks: >20 ms; 240 Hz decks: >15 ms) for 3 s, banner suggests USB or 5 GHz. | C |
| FR-1.10 | Liveness: phone sends the RTT ping frame from FR-3.4 at 100 ms cadence when idle (dual purpose: keepalive + RTT sample). Phone: >1 s silence from server → full reconnect. Server: **>300 ms silence from phone → failsafe neutral inputs** (FR-2.6); received pings refresh this timer, so failsafe fires only on genuine link loss or app death. | M |

### FR-2 Emulation (server)

| ID | Requirement | Priority |
|---|---|---|
| FR-2.1 | Create a virtual Xbox 360 controller via ViGEmBus; visible as a real pad to Steam/games. | M |
| FR-2.2 | Full X360 surface: 2 analog sticks, LT/RT triggers, ABXY, D-pad, LB/RB, Start/Back, guide button. | M |
| FR-2.3 | Keyboard output via SendInput for keys not on a pad (indicators, horn, hazards, any mapped key). | M |
| FR-2.4 | Button edge detection: server computes pressed/released/held from snapshot diffs → keys are pressed once, released once. | M |
| FR-2.5 | Supports up to **4 simultaneous phones → pads 1–4** (XInput slot 0–3). Server assigns the lowest free slot on HELLO; slot shown in phone HUD. Slots beyond 4 are refused with a clear "lobby full" error. | M |
| FR-2.6 | Failsafe: no packet for 300ms → all buttons up, triggers 0, sticks centered, pad stays connected. | M |
| FR-2.7 | Tray icon on PC: status, connected phones, latency, open profiles folder, **copy pairing QR**, exit. | C |
| FR-2.8 | Profiles folder of JSON files; server hot-reloads on change (no restart needed). | M |
| FR-2.9 | Per-slot independence: each phone runs its own deck, profile binding, RTT, failsafe timer; one phone disconnecting never disturbs the others. | M |

### FR-2b Haptics (phone vibration feedback)

| ID | Requirement | Priority |
|---|---|---|
| FR-2b.1 | Server subscribes to ViGEm rumble notifications for each virtual pad; maps left/right motor (0–255) into a feedback frame per slot. | M |
| FR-2b.2 | Phone: `Vibrator` continuous rumble emulation — incoming motor values drive a repeating `createWaveform` chunk with amplitude = mapped value; zero values cancel. Check `hasAmplitudeControl()` at startup; on devices without it, fall back to on/off bursts (duty-cycle encoding). | M |
| FR-2b.3 | Haptic intensity slider (0–100%) + master on/off in app settings, persisted. | M |
| FR-2b.4 | Local UI haptics: short `VibrationEffect.createOneShot` tick on button press for tactile feel (separate from game rumble). | M |
| FR-2b.5 | Rumble watchdog: no feedback frame for 500 ms → vibrator cancelled (no stuck buzzer if link dies mid-rumble). | M |

### FR-3 Protocol

| ID | Requirement | Priority |
|---|---|---|
| FR-3.1 | Binary snapshot packet, little-endian: magic(2B) + version(1B) + flags(1B) + seq(u16) + timestamp(u32 ms) + buttons(2×u16 masks) + axes(8×i16). Total **30 bytes** fixed (offsets in ARCHITECTURE §3). | M |
| FR-3.2 | Axes as i16 on the wire (-32768..32767): sticks mapped 1:1 to XInput stick range; **triggers are stored as full-range i16 and scaled server-side to XInput's 0–255 u8 range**. | M |
| FR-3.3 | Send rate: on-change + 120 Hz steady cap; racing/gyro/fps-aim/flight decks → 240 Hz (D-007). | M |
| FR-3.4 | RTT measurement: the same ping frame as FR-1.10 is timestamped; server echoes the timestamp back; HUD shows median over last 5 replies. Ping cadence is 100 ms when idle (FR-1.10); during active input the phone opportunistically sets ping-request bit0 on ordinary snapshot frames and the server echoes those too — RTT samples with zero extra packets (mechanism in ARCHITECTURE §3). | M |
| FR-3.5 | Packet dedupe: server keeps last seq; stale/out-of-order snapshots dropped silently. | M |
| FR-3.6 | Protocol version negotiation at handshake; server rejects mismatched client with clear error. | C |
| FR-3.7 | Feedback channel: server → phone rumble frames (flags bit3) carrying left/right motor u8 values, rate-capped to ≤30/s per phone. | M |

### FR-4 Mode decks (phone UI)

General: home screen = grid of deck cards ("faceplates"). Tap card → deck loads full-screen. Persistent swipe-down/tab handle returns to deck picker. Deck switching is instant, allowed during gameplay.

#### Deck 1 — Gamepad 🎮 (phase 1)
- Two virtual joysticks (touch drag), ABXY diamond, D-pad (cross), LB/RB shoulder buttons, LT/RT as vertical sliders or pressure bars, Start/Select.
- Vibration toggle (phone buzz on button press for feel).

#### Deck 2 — Truck Sim 🚛 (phase 2)
- Steering: on-screen wheel bar OR full gyro (switch inside deck).
- Pedals: throttle + brake as vertical sliders or tap-hold zones (configurable).
- Handbrake: big hold button (maps to pad button, also keyboard `Space` toggle option per profile).
- **Indicator stalk buttons**: ⮜ left, ⮞ right, hazards ⚠ (toggle behavior — server presses key once per toggle state change).
- Horn: hold button (keyboard `H`; air-horn `N` optional per profile).
- Extras row (ETS2 keymap): lights `L`, high beam `K`, warning lights `O`, wipers `P`, exhaust brake `B`, differential lock `V`, raise axle `U`, engine start `E`, gear up `Shift`, gear down `Ctrl`.
- All controls re-orderable in a simple layout editor.

#### Deck 3 — Gyro Wheel 📱 (phase 2)
- Hold phone flat, tap "Center" → that orientation becomes steering zero.
- Tilt range slider: 90°–900°.
- Deadzone % and auto-center strength.
- Complementary-filtered, drift-corrected (D-013); fallback to touch-drag wheel on gyro-less phones.
- On-screen pedals always available; steering also drives pad left-stick X or a dedicated wheel axis.

#### Deck 4 — Arcade Racing 🏎️ (phase 3)
- Big left/right steer zones OR gyro tilt, big throttle/brake, handbrake flick.
- One-hand landscape orientation.

#### Deck 5 — FPS Gyro-Aim 🔫 (phase 3)
- Left stick: touch drag (movement). Right stick: **gyro aiming** — small tilts = fine aim, flick threshold for quick turns.
- ABXY + triggers as usual. Sensitivity sliders.

#### Deck 6 — Keyboard ⌨️ (phase 3)
- Grid of remappable keys; drag to place; long-press to configure repeat.
- Presets: WASD + space/shift layout.

#### Deck 7 — Flight Sim ✈️ (phase 4)
- Yoke: two vertical sliders (pitch/roll) or gyro both axes.
- Throttle slider, trim buttons.

#### Deck 8 — Emulator/Retro 👾 (phase 4)
- Preset layouts: SNES (d-pad + A/B/X/Y/L/R), GBA, N64 analog, PS1.
- Button size/position adjustable.

#### Deck 9 — Media Remote 🎵 (phase 4)
- Big play/pause, prev/next, volume slider, mute.
- Uses keyboard media key codes via same SendInput path.

#### Deck 10 — Multiplayer Lobby 👥 (phase 4)
- Join flow: phones scan/discover the PC; server assigns the lowest free XInput slot (up to 4); HUD shows P1–P4; 5th phone → LOBBY_FULL message.
- Each phone independently picks any deck 1–9; per-slot independence for input, profile, rumble, failsafe (FR-2.9).

### FR-5 Gyro subsystem

| ID | Requirement | Priority |
|---|---|---|
| FR-5.1 | Use `TYPE_GAME_ROTATION_VECTOR` where available, fall back to `TYPE_ROTATION_VECTOR`; registered at `SENSOR_DELAY_GAME` or faster (D-008). | M |
| FR-5.2 | Center calibration on demand (tap button). | M |
| FR-5.3 | Drift compensation: low-pass correction every N seconds when velocity ~0. | M |
| FR-5.4 | Configurable range, deadzone, response curve (linear/expo). | M |
| FR-5.5 | Graceful fallback: no gyroscope → touch-drag steering with same output axis. | M |
| FR-5.6 | Phone rotation lock + wakelock while a deck is active. | M |

### FR-6 Settings & persistence

| ID | Requirement | Priority |
|---|---|---|
| FR-6.1 | Phone: last transport, last deck, gyro sensitivity, layout edits — persistent across app restarts. | M |
| FR-6.2 | Server: profile JSONs in `%APPDATA%\EuroPad\profiles\`; default profile for ETS2 ships. | M |
| FR-6.3 | Server log file with latency stats, rotate weekly. | C |

---

## 2. Non-functional requirements

| ID | Requirement |
|---|---|
| NFR-1 | Median latency ≤ 10 ms Wi-Fi 5GHz, ≤ 5 ms USB (see BIBLE §8). |
| NFR-2 | Server CPU < 2% on a modern dual-core while streaming 240 Hz from one phone; < 4% at 4-slot worst case (RULES §7). |
| NFR-3 | Phone must stay >4h battery in gyro mode (screen on, brightness 40%). |
| NFR-4 | Windows 10 22H2 / Windows 11; x64. |
| NFR-5 | Android 10+ (API 29+), multi-touch required; gyro optional — wheel decks fall back to touch steering on gyro-less phones (FR-5.5). |
| NFR-6 | No data leaves the LAN. No telemetry, no crash reporter, no network calls except discovery/transport. |
| NFR-7 | Server boots at login (optional auto-start entry). |

## 3. Default ETS2/ATS keymap (ships in profile)

Verified default keys (ETS2 keyboard defaults):

| Deck control | Key | Notes |
|---|---|---|
| Left indicator | `[` | toggles indicator arm left |
| Right indicator | `]` | |
| Hazard | `F` | warning flashers toggle |
| Horn | `H` | held = horn sounds |
| Air horn | `N` | requires cabin air-horn accessory |
| Light signal | `J` | flash high beams |
| Lights mode | `L` | cycle off/parking/low beam |
| Main beam | `K` | |
| Warning lights | `O` | |
| Wipers | `P` | |
| Exhaust brake | `B` | |
| Retarder + | `;` | |
| Retarder − | `'` | |
| Diff lock | `V` | |
| Raise axle | `U` | |
| Engine start/stop | `E` | |
| Gear up | `Shift` | |
| Gear down | `Ctrl` | |
| Handbrake | `Space` | toggle |

> Note: Light signal (`J`), Retarder + (`;`), Retarder − (`'`) are verified ETS2 defaults but have **no v1 deck control** — the buttons_hi bitmask is fully allocated (16/16, ARCHITECTURE §3). They stay in the shipped profile for a future protocol version.

## 4. Acceptance criteria (definition of done per phase)

**Phase 1 (MVP)** — all true:
1. `EuroPadServer.exe` running on PC, virtual Xbox 360 pad appears in Windows (`joy.cpl`) and in Steam controller settings.
2. Phone connects over Wi-Fi without manual IP (mDNS).
3. Gamepad deck drives all X360 inputs live in a Steam game.
4. Kill Wi-Fi → failsafe triggers ≤300ms, pad inputs neutralized.
5. HUD shows RTT and transport.

**Phase 2 (Truck + Haptics)** — all true:
1. Truck deck drives ETS2: steer, throttle/brake via sticks, handbrake, **indicators toggle with one press each**, horn while held.
2. Gyro Wheel deck: centered calibration works, 900° range, smooth (no visible jitter) on a 5GHz network.
3. USB transport connects and shows ≤5ms latency in HUD.
4. Transport switcher changes link mid-session with no pad disconnect.
5. **Haptics**: game rumble causes phone vibration within ≤50ms, intensity slider works, vibration stops ≤500ms after rumble ends, no stuck buzzer on link drop.

**Phase 3** — Arcade, FPS-aim, Keyboard decks all function per FR-4; layout editor works.

**Phase 4** — Flight, Retro, Media decks function; Multiplayer Lobby: **up to 4 phones drive 4 independent pads simultaneously**, 5th rejected; haptics verified per slot.

## 5. Risks & mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| ViGEmBus fully breaks on a future Windows update | low–med | Abstraction layer in server; successor "VirtualPad" tracked; keyboard emulation (SendInput) already provides a driver-free fallback path |
| Anti-cheat blocks virtual pads | med | Accept; document as out of scope. |
| Phone gyroscope missing/low quality | low | FR-5.5 touch fallback + gyro quality check at deck open |
| 2.4GHz Wi-Fi congestion | med | USB mode + auto-suggest (FR-1.9) |
| Phone kills app in background (Doze) | med | Foreground service with persistent notification while connected |
| BT transport flaky | low | BT is optional phase-4; Wi-Fi/USB cover primary use |

## 6. Open questions — RESOLVED (2026-08-17, autonomously; owner opted out of Q&A)

1. **Phone model/gyro quality**: resolved → app targets generic Android 10+ with **runtime capability detection**: rotation-vector sensor presence, max sample rate (measured via `event.timestamp` deltas over 1s at connect time), `hasAmplitudeControl()`. No gyro → touch-drag fallback (FR-5.5). HUD shows detected gyro Hz.
2. **PC Wi-Fi band layout**: resolved → assume typical dual-band single-SSID home router. App measures RTT per transport at connect; the auto-suggest banner (FR-1.9) fires on sustained over-budget RTT (per-deck thresholds) regardless of cause. 5 GHz recommended in README.
3. **Personal ETS2 keybind overrides**: resolved → ship `profiles/ets2.json` with verified defaults (PRD §3); owner edits JSON, server hot-reloads. No per-user branching.
4. **Web profile dashboard**: resolved → **no** (YAGNI, BIBLE P7). Direct JSON editing + README documentation is sufficient for a personal tool; revisit only if profiles become a pain point.
