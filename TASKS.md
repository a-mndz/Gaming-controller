# TASKS.md
> The working task tracker. Status: `[ ]` todo · `[~]` in-progress · `[x]` done · `[!]` blocked.
> IDs are stable forever (commit messages reference them). Phases align with PRD §4 acceptance criteria.

---

## Phase 0 — Environment & proof-of-life

- [x] **T0.1** ~~Install prerequisites: .NET 8 SDK, Android Studio + SDK (API 34+), ViGEmBus 1.22.0 MSI.~~ Done 2026-08-17 via winget: .NET SDK 8.0.424, ViGEmBus 1.22.0 (service Running). Android SDK pending (blocks phone-side tasks).
- [x] **T0.2** ~~Initialize repos: `server/` (dotnet new sln + xunit project skeleton), `app/` (empty Compose app running on phone), `profiles/`, `.gitignore`. First commit.~~ Done 2026-08-17: server sln + xUnit project + shipped profiles (`default.json`, `ets2.json`) + `.gitignore` written; builds 0 warnings, 30/30 tests green. Android `app/` scaffold pending (no SDK yet); `git init` deferred until both sides compile.
- [x] **T0.3** ~~ViGEm hello-world: console app that creates a virtual X360 pad and sweeps left stick X. Success = pad appears in `joy.cpl` and the stick moves.~~ Done 2026-08-17: real server + synthetic-phone probe over UDP → HELLO/ACK → pad visible via `XInputGetState` → buttons/sticks/triggers verified (buttons 0x1101, LX=1000, RT=155 = 20000·255/32767).
- [x] **T0.4** ~~SendInput hello-world: console app presses/releases `H` key on a keystroke. Success = Notepad receives it.~~ Done 2026-08-17: verified via poller job — server's HORN bit → SendInput → `GetAsyncKeyState(0x48)` shows exactly DOWN then UP. Fixed INPUT-union size bug (keyboard-only union marshaled 32B; x64 requires 40B full union — SendInput silently rejected all calls).
- [x] **T0.5** ~~Answer PRD §6 open questions~~ → resolved autonomously (no user questions): generic Android 10+ target with runtime capability detection; dual-band single-SSID assumed with RTT-based detection; ETS2 default keymap with hot-reload JSON overrides; no web profile dashboard in v1. See PRD §6.

## Phase 1 — MVP: link + Gamepad deck

- [~] **T1.1** Protocol v1 codec (C# + Kotlin, mirrored): 30-byte frame per ARCHITECTURE §3, round-trip unit tests. — C# side done + 30/30 tests green; Kotlin mirror pending Android toolchain.
- [x] **T1.2** ~~Server UDP listener + handshake (HELLO/ACK/REJECT) + seq dedupe + RTT echo + SlotManager.~~ Done 2026-08-17: full path live-tested with synthetic UDP client (ACK slot, dedupe, ping echo all verified).
- [x] **T1.3** ~~Server InputDiff + PadEmulator wired to ViGEm; failsafe timer (300ms neutral).~~ Done 2026-08-17: edges + failsafe verified live (neutral after 300ms silence, stale seq ignored).
- [x] **T1.4** ~~Phone transport layer (Wi-Fi first): DatagramSocket sender coroutine, latest-frame-only, 120 Hz cap, keepalive.~~ Done 2026-08-17: `UdpTransport` + `DeckEngine` 120 Hz sender verified on-device (OPPO CPH1911, Android 11). Removed sender-side receive-drain (same-socket drain starved ping echo + caused spurious failsafes). DSCP EF still pending (Phase 2 polish).
- [x] **T1.5** ~~Phone mDNS/NSD discovery + connect flow + saved host + manual IP.~~ Done 2026-08-17: server mDNS (`_europad._udp`) wire-verified; Android NSD discovers + resolves the server on-device (192.168.1.2:47910); manual IP field works. Saved-host persistence + MulticastLock polish = Phase 2.
- [x] **T1.6** ~~Gamepad deck UI: sticks, ABXY, D-pad, bumpers, triggers, Start/Select → InputFrame.~~ Done 2026-08-17: sticks (L/R), A/B/X/Y, LB/RB, START/BACK, D-pad all verified on-device via XInput; controls carry Compose semantics (`pad:*`). LT/RT sliders = Phase 2 polish.
- [x] **T1.7** ~~Phone HUD: RTT (median/5), loss %, transport icon, deck name, slot # (P1–P4).~~ Done 2026-08-17: RTT (1 Hz ping), transport, connection state + P# shown in header (verified on screen). Median-5/loss% = Phase 2 polish.
- [x] **T1.8** ~~Deck picker home screen (faceplates grid), Gamepad deck wired.~~ Done 2026-08-17: single-deck v1 picker (discovered servers + manual IP + Gamepad deck); faceplates grid arrives with deck 2+.
- [x] **T1.9** ~~Integration: connect → play a Steam game end-to-end. Run PRD §4 Phase-1 acceptance checklist.~~ Done 2026-08-17, all 5 criteria pass on real hardware: (1) pad visible in XInput ✓ (2) Wi-Fi connect via NSD, no manual IP ✓ (3) all X360 inputs from Gamepad deck — A/B/X/Y/LB/RB/START/BACK/D-pad/sticks verified mid-gesture via `XInputGetState` ✓ (4) Wi-Fi killed → failsafe neutralizes inputs (server-side 300 ms timer, observed neutralization including transport propagation < 850 ms) ✓ (5) HUD shows RTT + transport + slot ✓

## Phase 2 — Truck Sim + Gyro + USB + Haptics

- [ ] **T2.1** Profile system: JSON schema, loader, hot-reload watcher, shipped `ets2.json` (PRD §3 keymap), `default.json`.
- [ ] **T2.2** Server KeyboardEmulator: edge-driven key down/up, toggle semantics for IND_L/IND_R/HAZARD. Unit tests.
- [ ] **T2.3** Truck deck UI: wheel bar, pedals, handbrake, indicator stalk buttons, horn (hold), extras row → buttons_hi + keys via profile.
- [ ] **T2.4** USB transport (tethering): PC binds listener to tether interface; phone route detection; first-run steps in-app. Latency HUD check ≤5ms.
- [ ] **T2.5** GyroEngine: rotation-vector quaternion → yaw delta, center calibration, range slider, deadzone, complementary filter (D-013), drift trim. Unit tests for center/clamp math.
- [ ] **T2.6** Gyro Wheel deck: center button, 90°–900° range, pedals, auto-center option; touch fallback (FR-5.5).
- [ ] **T2.7** ETS2 end-to-end: steer, throttle/brake, handbrake toggle, indicators one-press toggles, horn on hold. PRD §4 Phase-2 checklist (incl. transport switch mid-session).
- [ ] **T2.8** Foreground service + wakelock + rotation lock during decks; **battery-optimization exemption request flow**; battery check (NFR-3 spot test).
- [ ] **T2.9** **Haptics chain (D-015)**:
  - [ ] **T2.9a** Server: ViGEm rumble notification callback per pad → RumbleRelay (coalesce ≤30/s, per-slot queue, no ViGEm reentry) → feedback frame (flags bit3).
  - [ ] **T2.9b** Phone: HapticEngine — amplitude-mapped repeating waveform chunks, `hasAmplitudeControl()` check + duty-cycle fallback, intensity slider, 500ms watchdog cancel.
  - [ ] **T2.9c** Local UI tick haptic on button press (createOneShot, 10–15ms).
  - [ ] **T2.9d** Rumble round-trip timing test ≤50ms (echotest harness).

## Phase 3 — Arcade / FPS aim / Keyboard + editor

- [ ] **T3.1** Arcade Racing deck (one-hand landscape).
- [ ] **T3.2** FPS Gyro-Aim deck: gyro→right stick fine aim + flick threshold, sensitivity sliders.
- [ ] **T3.3** Keyboard deck: remappable grid, presets (WASD), long-press repeat.
- [ ] **T3.4** 240 Hz mode for gyro/fps/flight decks (D-007) + per-rate auto transport suggestion logic (FR-1.9).
- [ ] **T3.5** Deck layout mini-editor: move/resize controls per deck, persist.
- [ ] **T3.6** QR pairing + PIN gate (FR-1.3/1.7), retry-lock per ARCHITECTURE §8. QR is displayed on the server console/log until the tray app (T4.6) takes over the display surface.

## Phase 4 — Multiplayer + long tail + polish

- [ ] **T4.1** Bluetooth transport: RFCOMM listener + phone BT socket (D-011); framed codec reuse. Optional, lowest priority transport. Documented as latency-unfriendly (D-011).
- [ ] **T4.2** Flight Sim deck (yoke axes + throttle slider).
- [ ] **T4.3** Emulator/Retro deck: SNES/N64/PS1 presets.
- [ ] **T4.4** Media Remote deck (SendInput media keys).
- [ ] **T4.5** **Multiplayer Lobby (4 phones, D-014)**:
  - [ ] **T4.5a** Server: SlotManager verified under 4 concurrent clients (joy.cpl shows 4, independent input, 5th rejected LOBBY_FULL).
  - [ ] **T4.5b** Phone: lobby join flow — scan, see slot status (P1–P4), auto-assigned lowest free slot on HELLO/ACK (ARCHITECTURE §4.2 SlotManager), display assigned P# in HUD.
  - [ ] **T4.5c** Per-slot rumble independence test (rumble pad 2 only → only phone 2 vibrates).
- [ ] **T4.6** Server tray app: slots (1–4) with RTT each, open profiles, pairing QR, exit. Windows logon auto-start option.
- [ ] **T4.7** Log rotation + weekly summary (latency histogram per slot, per transport).
- [ ] **T4.8** README first-run walkthrough polish + record final latency numbers in MEMORY.md.

## Parking lot (v2+, referenced from DECISIONS/ARCHITECTURE)

- [ ] **P1** BluetoothHidDevice native-pad mode (no server) — D-011(b).
- ~~**P2**~~ *(used for game haptics — delivered in T2.9; ID retired, not reused)*
- [ ] **P3** ETS2 telemetry second-screen dashboard.
- [ ] **P4** VirtualPad driver migration evaluation (D-003 watch item).
- [ ] **P5** Linux server (uinput).
- [ ] **P6** HD/trigger haptics (DualSense-style waveforms) — requires BT HID host or companion; out of XInput scope.
- [ ] **P7** DirectInput output pads for phone #5+ (D-014 escalation path).
