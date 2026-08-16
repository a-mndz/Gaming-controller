# DECISIONS.md
> Decision log. Format: ID · date · status · context · options · decision · consequences.
> Newest first. A decision is only overturned by a newer decision entry (never silent edits elsewhere).

---

### D-015 · 2026-08-17 · accepted · **Haptics: translate game rumble to phone vibration (v1 scope = rumble only)**
- **Context:** Owner requested haptics. Games rumble the virtual pad via XInput; ViGEmClient exposes a rumble notification callback (`vigem_target_x360_register_notification`) carrying left/right motor 0–255.
- **Options:** (a) full rumble translation over the existing feedback channel, (b) HD/trigger haptics emulation, (c) local-only vibration (button ticks, no game rumble).
- **Decision:** (a). Server registers per-pad rumble callbacks, RumbleRelay coalesces to ≤30 feedback frames/s (flags bit3), phone HapticEngine drives `VibrationEffect.createWaveform` repeating chunks with amplitude tracking. Devices without `hasAmplitudeControl()` fall back to duty-cycle bursts. Local UI ticks (c) included as a separate small feature (FR-2b.4).
- **Consequences:** Phone ERM/LRA motors can't reproduce high-frequency modulation — amplitude-tracking at ≤30 updates/s is the honest fidelity ceiling; HD haptics parked (BIBLE §9, ARCHITECTURE §11). 500 ms rumble watchdog prevents stuck buzzers.

### D-014 · 2026-08-17 · accepted · **Max 4 simultaneous phones — the XInput hard limit, not our choice**
- **Context:** Owner requested multi-device connectivity, up to 4 phones. Research confirmed: the XInput API supports **exactly four controllers** (dwUserIndex 0–3); ViGEmBus can create more virtual pads, but games using XInput cannot see past slot 3 (Steam can work around this for up to ~16 via its own input layer, but not generically).
- **Options:** (a) cap at 4 XInput phones (matches owner's request exactly), (b) allow 8 ViGEm pads anyway (5th+ invisible to most games), (c) mixed XInput+DirectInput output for phone #5+ (complex, breaks "works everywhere" promise).
- **Decision:** (a). SlotManager allocates the lowest free XInput slot; 5th phone gets REJECT `LOBBY_FULL`. (c) parked in ARCHITECTURE §11 if ever needed.
- **Consequences:** Simple mental model (4 players, like a real console), zero compatibility surprises, each phone fully independent (input, profile, rumble, failsafe). If owner ever wants phone #5, the only clean path is BluetoothHidDevice-style DirectInput (parked, D-011).

### D-013 · 2026-08-17 · accepted · **Gyro smoothing: complementary filter, not Kalman**
- **Context:** Gyro Wheel and FPS-Aim decks need jitter-free steering/aim without adding lag.
- **Options:** (a) raw gyro (jittery), (b) simple low-pass (adds lag), (c) complementary filter with velocity-gated accel blend, (d) full Kalman (complex, CPU per sample).
- **Decision:** (c). `smooth += α(measured − smooth)`, α tunable 0.25–0.5; drift trim only when angular velocity ≈ 0.
- **Consequences:** Tunable per-user, ~zero lag cost, simple to unit-test. If quality is insufficient on cheap phones, revisit Kalman (re-open this entry).

### D-012 · 2026-08-17 · accepted · **USB transport = Android USB tethering, not adb reverse**
- **Context:** Want wired ~1ms path without dev-setup friction.
- **Options:** (a) USB tethering makes phone a virtual NIC → same UDP code runs over it, needs zero setup beyond toggling tethering; (b) `adb reverse` — needs USB debugging + adb on PC; (c) Android Open Accessory.
- **Decision:** (a). Server just binds the UDP listener to the tether interface (subnet typically 192.168.42.x).
- **Consequences:** Tethering briefly reroutes phone internet through PC — acceptable for local play; document the toggle + firewall allowance in the first-run guide. adb kept as documented fallback.

### D-011 · 2026-08-17 · accepted · **Bluetooth transport = classic RFCOMM pipe; BluetoothHidDevice parked** *(latency figure corrected 2026-08-17 by research loop)*
- **Context:** Third transport requested. Two ways to use BT.
- **Options:** (a) RFCOMM serial socket to our server — keeps the unified pipeline incl. keyboard output; (b) Android `BluetoothHidDevice` API makes phone a native BT gamepad — no server needed but DirectInput-only pad, no keyboard for indicators/horn, finicky pairing.
- **Decision:** (a) for v1. (b) explicitly parked as v2 candidate (see ARCHITECTURE §11).
- **Consequences:** BT latency **~15–50 ms best case, up to ~100 ms under load** (research-verified; RFCOMM is slow for real-time input) — the weakest transport; UI positions it as last resort, never auto-default.

### D-010 · 2026-08-17 · accepted · **Indicators / horn / hazards → virtual keyboard via SendInput**
- **Context:** A gamepad has no "indicator" input; ETS2 binds these to keyboard by default.
- **Options:** (a) map to unused gamepad buttons (games ignore / conflicts), (b) SendInput virtual keys driven by a per-game JSON keymap, (c) reWASD-style user remapping inside games.
- **Decision:** (b), with the verified default ETS2 keymap shipped in `profiles/ets2.json` (see PRD §3).
- **Consequences:** Works for any game, fully reconfigurable without rebuild. Edge case: if user rebinds ETS2 controls, they edit the JSON (hot-reloaded).

### D-009 · 2026-08-17 · accepted · **10 mode decks with "faceplate" deck-picker UX** *(multi-device part superseded by D-014)*
- **Context:** Owner wants "all-in-one interchangeable" with switchable modes incl. truck sim, racing, gyro, gamepad, media, 2-player.
- **Decision:** Home screen = grid of swappable deck cards (tap → full-screen deck; persistent handle returns to picker). Decks switchable mid-game. 2-player is a connection state that enables pad slot 2, not a separate screen. → *Superseded: now the Multiplayer Lobby connection state supporting up to 4 phones/slots (D-014).*
- **Consequences:** Each deck is self-contained (input map + UI) which maps cleanly to the snapshot protocol's buttons_hi bitmask per deck.

### D-008 · 2026-08-17 · accepted · **Sensor capture: TYPE_GAME_ROTATION_VECTOR (fallback TYPE_ROTATION_VECTOR) @ SENSOR_DELAY_GAME**
- **Context:** Steering quality depends on gyro sample rate and fusion; budget phones vary (200–400Hz typical).
- **Options:** raw `TYPE_GYROSCOPE` (drifts), `TYPE_GAME_ROTATION_VECTOR` (fused, no magnetometer), `TYPE_ROTATION_VECTOR` (fused incl. mag).
- **Decision:** Use `TYPE_GAME_ROTATION_VECTOR` where available, fall back to `TYPE_ROTATION_VECTOR`; request fastest continuous rate (`maxReportLatencyUs = 0`); use `event.timestamp` deltas, never frame time.
- **Consequences:** Fusion handled by OS (battery-friendly); our code only extracts yaw-delta. Gyro-less phones fall back to touch-drag steering (PRD FR-5.5).

### D-007 · 2026-08-17 · accepted · **Adaptive send rate: 240 Hz on latency-critical decks**
- **Context:** 120 Hz is plenty for gamepad; gyro steering at 120 Hz can feel stepped on 900° range.
- **Decision:** Decks declare their rate: GAMEPAD/TRUCK/ARCADE/KEYBOARD/MEDIA/RETRO = 120 Hz; GYRO_WHEEL/FPS_AIM/FLIGHT = 240 Hz. Phone caps to the deck rate even if sensors run hotter.
- **Consequences:** ~2× packets (still ~7 KB/s — trivial). Auto transport suggestion thresholds are per-rate.

### D-006 · 2026-08-17 · accepted · **UDP snapshot protocol, binary, 30 bytes**
- **Context:** Latency goal <10ms; commercial rivals use TCP/WebSocket (10–30ms with retransmit stalls).
- **Options:** (a) WebSocket/TCP — head-of-line blocking under loss, (b) raw UDP with full-state snapshots — loss self-heals in one interval, (c) WebRTC DataChannel — good but signaling complexity for a LAN tool.
- **Decision:** (b). 30-byte fixed frame (see ARCHITECTURE §3): magic+ver+flags+seq+timestamp+2×u16 buttons+8×i16 axes. On-change + steady cadence, capped per D-007. RTT via timestamped ping frames echoed by server.
- **Consequences:** No delivery guarantees — fine because every packet is absolute truth. Stale seq dedupe at server.

### D-005 · 2026-08-17 · accepted · **Server language: C# / .NET 8**
- **Context:** Owner is comfortable with desktop dev; ViGEm has first-class C# bindings.
- **Options:** (a) C# + `Nefarius.ViGEm.Client` NuGet (verified package, v1.21.256), (b) Python + vgamepad (prototypes fast, packaging weaker), (c) C++ fork of PhantomPad.
- **Decision:** (a). WinForms-free tray via minimal notifyicon (System.Windows.Forms.NotifyIcon is acceptable for tray only).
- **Consequences:** Clean ViGEm integration, easy Windows service/tray packaging, xUnit tests. Python path kept as reference only.

### D-004 · 2026-08-17 · accepted · **Phone platform: Android only, native Kotlin**
- **Context:** Android phone confirmed; Android gives raw UDP sockets, full sensor control, sideload install.
- **Decision:** Kotlin + Jetpack Compose, minSdk 29. iOS and browser-based versions are out of scope (BIBLE §9).
- **Consequences:** Single codebase, best possible sensor latency, no store dependency.

### D-003 · 2026-08-17 · accepted · **Controller emulation: Xbox 360 via ViGEmBus 1.22.0**
- **Context:** Must appear as a real controller to Steam/ETS2/all games with zero per-game config.
- **Options:** (a) ViGEmBus X360 (industry standard), (b) HID-composer DirectInput pad (no driver needed but weaker game support), (c) DS4 emulation via ViGEm (works but fewer games support DS4 natively).
- **Decision:** (a). Known risk: ViGEmBus was retired/archived Nov 2023 (trademark conflict with ViGEM GmbH) — **still works on Win10/11**, and every commercial phone-as-gamepad app uses it. Nefarius's successor line is "VirtualPad".
- **Consequences:** Watch-item: track VirtualPad GA; abstraction layer isolates ViGEm calls so a driver swap is contained. Install step documented in README.

### D-002 · 2026-08-17 · accepted · **Transport set: USB + Wi-Fi + Bluetooth, all swappable, one pipeline**
- **Context:** Owner wants wired + wireless + BT, switchable.
- **Decision:** All three links carry the identical snapshot protocol into the same server pipeline. Transport is a UI choice; game logic is link-agnostic (D-012 for USB mechanism, D-011 for BT).
- **Consequences:** One codebase for input handling; latency differences are transport properties, not behavioral differences.

### D-001 · 2026-08-17 · accepted · **Product form: phone app + PC server over same Wi-Fi**
- **Context:** Original ask: control PC games from the phone, wireless, same network, low latency, works with Steam.
- **Decision:** Build EuroPad from scratch as owner's personal tool. Not adapting PhantomPad/RojX: none are open enough, none support the full deck set, and building our own gives full control over latency and mappings.
- **Consequences:** Full control, full effort. Competitive research (PhantomPad <5ms UDP claim, RojX 900°+gyro, SimWheel) validates feasibility and sets the target bar.
