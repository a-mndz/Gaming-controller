# MEMORY.md
> Session continuity file. Update at the end of every work session (RULES §9). Newest entries at the top.
> Purpose: if context is ever lost, this file is the resume point.

---

## 2026-08-17 (LATEST) — PHASE 1 COMPLETE on real hardware

- **All 5 PRD §4 Phase-1 acceptance criteria PASS on-device** (OPPO CPH1911, Android 11, Wi-Fi 5 GHz → 192.168.1.6; PC server at 192.168.1.2, UDP 47910):
  1. Virtual Xbox 360 pad visible via `XInputGetState` ✓
  2. Phone discovers server via NSD (`_europad._udp`), no manual IP ✓
  3. Every Gamepad input verified on the virtual pad: A/B/X/Y/LB/RB/START/BACK/D-pad (all 4 dirs)/both sticks ✓
  4. Kill Wi-Fi → failsafe neutralizes inputs (server 300 ms timer; full transport-propagation loop measured < 850 ms) ✓
  5. HUD: RTT + transport + slot (P1) ✓
- **Dev phone facts:** serial CUONZPQORO8HWW5P, OPPO CPH1911, Android 11, screen 2340×1080 @ 408dpi. USB debugging authorized. `svc power stayon usb` set (keeps screen on while plugged in).
- **Hard-won debugging knowledge (do not re-learn):**
  - `adb input swipe x y x y <ms>` is the reliable hold/press primitive; parallel `input` calls share one touch pointer — true multi-touch cannot be composed from CLI.
  - The sender coroutine must NOT call `receive`/`drainIncoming` on the shared socket: it stole ping echoes and caused spurious failsafes. Sender = send-only; RTT loop is the sole reader (1 Hz ping). If pinging every frame is needed later, add a dedicated receiver coroutine.
  - Compose controls expose `contentDescription = "pad:*"` — use `uiautomator dump` to read exact bounds instead of guessing coordinates. Bounds from the dump are correct; a tap that reads 0x0000 almost always means the phone wasn't on the expected screen/connection (check the app state first, not the coords).
  - `XInputGetState` sampling must happen MID-gesture (hold/drag in flight); buttons/axes are zeroed on release, and `adb`-spawned gestures take ~300–500 ms to land.
  - Phone foreground can land on Developer Options after USB-auth flows — always check `uiautomator dump` package name (`com.europad.app`) before blaming the app.
- **Known gaps deferred to Phase 2 polish:** trigger LT/RT sliders, auto-reconnect after link loss, MulticastLock for flaky OEM NSD, saved-host persistence, DSCP EF, HUD median-5/loss %.
- **Run commands:** server `dotnet server\EuroPad.Server\bin\Release\net8.0\EuroPadServer.dll`; app `adb install -r app\app\build\outputs\apk\debug\app-debug.apk` (build script: `%TEMP%\kilo\europad\build-app.ps1`); acceptance test scripts live in the same temp dir (`probe.ps1` = server self-test via synthetic UDP client).
- **Next:** Phase 2 — T2.1 profile system → T2.2 KeyboardEmulator (already written + unit-tested) → T2.3 truck deck → T2.4 USB transport → T2.5/T2.6 gyro → T2.7 ETS2 e2e → T2.8 Doze exemption → T2.9 haptics.

---

## 2026-08-17 — Build session: Phase 0 done, Phase 1 server done, Android client built

- **Phase 0 COMPLETE.** Toolchain: .NET SDK 8.0.424 (winget), ViGEmBus 1.22.0 (winget, service Running → virtual pads work), Temurin JDK 17 (portable zip), Gradle 8.7 (portable zip), Android SDK (cmdline-tools → platform-tools + platform-34 + build-tools 34.0.0). System `java.exe` is v25 — too new for Gradle 8.x, always set JAVA_HOME to Temurin 17.
- **Toolchain paths (outside repo, needed to rebuild):**
  - `JAVA_HOME = C:\Users\amand\AppData\Local\JDK\jdk-17.0.20+8`
  - `ANDROID_HOME = C:\Users\amand\AppData\Local\Android\Sdk` (also in `app\local.properties`)
  - Gradle: `C:\Users\amand\AppData\Local\Gradle\gradle-8.7\bin\gradle.bat`
  - Build helper scripts + probe scripts in `C:\Users\amand\AppData\Local\Temp\kilo\europad\` (temp — may be wiped; `build-app.ps1`, `probe.ps1` are the key ones)
- **Server (T0.2–T0.4, T1.1–T1.3, T1.5 server-side, mDNS) — complete & live-verified:** 30-byte codec, HELLO/ACK/REJECT, 4-slot SlotManager, ViGEm X360, SendInput keyboard (full x64 INPUT union — 40 bytes!), 300 ms failsafe + 2 s slot-release, seq dedupe, ping echo, PIN retry-lock, rumble relay, profile hot-reload, mDNS announce (`_europad._udp` via Makaretu.Dns.Multicast 0.27.0, wire-verified). 30/30 unit tests, 11/11 live probe, keyboard probe PASS (HORN bit → H key down/up).
- **Android (T1.1 Kotlin mirror, T1.4, T1.5, T1.6, T1.7 partial, T1.8) — APK builds via CLI, on-device pass still pending:** mirrored codec, UdpTransport, DeckEngine 120 Hz sender, Gamepad deck (drag sticks L/R, d-pad zones, A/B/X/Y/LB/RB/START/BACK hold-press), NSD discovery + manual IP + connect flow, RTT HUD. No phone connected yet — first on-device run is the next step.
- **Git:** repo initialized, root commit `2b7feda` (42 files, master branch). RULES §4 conventions in use.
- **API gotcha (do not re-learn):** Nefarius.ViGEm.Client 1.21.256 has NO `Xbox360Report` struct — use `IXbox360Controller.SetButtonsFull(ushort xusbMask)` / `SetAxisValue(Xbox360Axis, short)` / `SetSliderValue(Xbox360Slider, byte)` + `AutoSubmitReport = false` + `SubmitReport()`. XUSB bit mask: DpadUp=0x0001 … Guide=0x0400, A=0x1000, B=0x2000, X=0x4000, Y=0x8000. `SendInput` requires the full `INPUT` union (mouse+key+hardware members) → 40 bytes on x64 or every call fails silently.
- **Next session, in order:** (1) install APK on the phone (`adb install app\app\build\outputs\apk\debug\app-debug.apk` — USB debugging must be enabled on the phone), (2) run T1.9 Phase-1 checklist end-to-end over Wi-Fi, (3) finish T1.4/T1.5 polish (MulticastLock, saved hosts, DSCP), HUD polish (median-5, loss %), trigger controls in Gamepad deck, then Phase 2.

---

## 2026-08-17 — Audit round 6 (rating-based, phase-consistency sweep)

- 5 defects found and fixed (2 medium, 3 low): Quick-start QR claim in README conflicted with QR's Phase-3 ship date (README now offers manual IP as first-class, QR labeled Phase 3); ARCHITECTURE §7.6 implied the tray displays QR from day one, but the tray app is Phase 4 (T4.6) — now sequenced: console output for QR in P3, tray surface in P4 (synced into TASKS T3.6); BIBLE §7 transport table lumped manual IP + QR into one "fallback" phrase — split and aligned with ARCHITECTURE §7 (manual IP = first-class, QR = P3); BIBLE §8 success-metric row only said "racing/gyro decks" for 240 Hz — added aim/flight per D-007; RULES §9 precedence had DECISIONS sitting below PRD/ARCHITECTURE/RULES while DECISIONS.md's own header says a dated entry overrides stale text — explicit exception clause added so the two rules don't fight.
- Updated rating: **9.7/10 composite** (steady). Audit is at convergence — each round now yields only phase-sequencing and wording residues. Docs re-frozen; next work = TASKS T0.1.

---

## 2026-08-17 — Audit round 5 (rating-based, convergence check)

- 7 defects found and fixed (4 medium, 3 low): RULES §7 said dispatch budget <50 µs vs ARCHITECTURE §5's <0.5 ms (unified to 500 µs); FR-3.3 omitted the flight deck from the 240 Hz set (D-007 lists FLIGHT=240 Hz) — fixed; FR-3.4 claimed "opportunistic RTT from normal traffic" with no wire-level mechanism — now specified: phone sets ping bit0 on ordinary snapshots, server echoes (zero extra packets); slot numbering mixed conventions (BIBLE §6 and README said "slots 1–4" vs canonical 0–3 internal / P1–P4 display) — unified; ARCHITECTURE §8 PIN lockout said "slot locked" though lockout precedes slot allocation — now endpoint (source IP) lock; PRD §3 listed Light signal J + Retarder +/− keys but buttons_hi is 16/16 full and no deck control exists — noted as reserved for a future protocol version; MEMORY.md stale "(LATER)" marker removed.
- Updated rating: **9.7/10 composite** (DECISIONS/TASKS clean again at top; README historically most churn). Convergence confirmed: rounds found ~24 → 6 → 7 (of which 3 cosmetic). Docs re-frozen; next work = TASKS T0.1.

---

## 2026-08-17 — Audit round 4 (rating-based, re-run)

- 6 defects found and fixed: ReWASD falsely claimed as ViGEmBus user in BIBLE §10 (corrected to DS4Windows/Sunshine/Phone2Pad); `vgamepad-family` cited as server fallback in PRD §5 despite being a Python lib (fallback now = SendInput keyboard path, which is driver-free); RULES "no WinForms" contradicted ARCHITECTURE tray design (NotifyIcon explicitly sanctioned, ref D-005); QR called "first-class" in ARCHITECTURE §7 but ships Phase 3 (clarified: manual IP is first-class in P1, QR is P3 convenience); FR-1.10 vs FR-3.4 kept two different ping cadences/specs (unified: one 100ms ping frame serves both liveness + RTT; active play samples RTT from traffic, zero extra packets); FR-3.4 previously demanded a dedicated ping during input — removed as waste.
- Updated rating: **9.6/10 composite**. Remaining known weaknesses are deliberate (see previous entry). Docs considered frozen; next work = TASKS T0.1.

---

## 2026-08-17 — Audit round 3 (rating-based, initial)

- Ran 3 audit rounds (surface → deep → rating-based). ~24 defects found and fixed, including: CJK char leak, Kalman-vs-complementary contradiction (PRD vs D-013), wrong gyro sensor constant (ARCHITECTURE vs D-008), invented NuGet package name (`Nefarius.ViGEm.Net` → verified `Nefarius.ViGEm.Client` 1.21.256), unspecified handshake payload locations (PIN/slot/reason code now pinned to `buttons_lo`/`timestamp_ms` in ARCHITECTURE §3), keepalive-vs-failsafe wording conflict (FR-1.10 rewritten), undefined auto-suggest threshold (now per-deck RTT budgets), trigger-axis range mismatch (FR-3.2), orphan parking ID P2, stale cross-refs (PRD §8 → ARCHITECTURE §8), D-008 title vs body drift.
- Final rating: **9.4/10 composite** (weakest: RULES 9.4 — "no comments" vs "why-only comments" is an intentional tension; strongest: PROJECT_BIBLE/TASKS 9.7).
- Docs are frozen-design-complete. No open contradictions known. Next work = code, per TASKS T0.1.

---

## 2026-08-17 — Deep-research refine cycle: haptics + 4-device multi-controller

### What happened
- Owner requested: **haptics** + **multi-device up to 4 phones**, and asked for 10–20-result web research across 10–15 loops, then refinement into all 8 files. Owner also stated they **do not like answering questions** — so all open questions were resolved autonomously (PRD §6 now marked RESOLVED; T0.5 closed).
- Research runs (12 batches, 12–15 results each): ViGEm rumble callback mechanics, XInput 4-controller hard limit, Android VibrationEffect/amplitude APIs, UDP socket tuning, NSD/mDNS flakiness, Doze/foreground-service behavior, BT RFCOMM vs BLE latency, Steam Input detection, multi-phone-as-controller landscape (Joy2DroidX, tmphonepad, A-PC GamePad, stadia-vigem).
- Key research conclusions now baked into the docs:
  - **XInput = exactly 4 controllers** (dwUserIndex 0–3). Owner's "up to 4" maps perfectly to the API ceiling. DECISIONS **D-014**.
  - **ViGEm rumble callback** (`vigem_target_x360_register_notification`) is blocking & ordered → RumbleRelay uses per-slot queues. DECISIONS **D-015**.
  - **Android haptics** = `VibrationEffect.createWaveform` amplitude-mapped repeating chunks, gated on `hasAmplitudeControl()`; ERM motors can't do high-freq modulation → target ≤30 updates/s.
  - **BT RFCOMM** is genuinely slow (best case ~15–50 ms, up to 100 ms) → demoted to last-resort transport, never the default.
  - **USB tethering** measured ~1–3 ms by tmphonepad (independent confirmation of D-012).
  - **NSD/mDNS** needs MulticastLock and is unreliable on OEM skins → manual IP is a first-class path; QR is a Phase 3 convenience layer (clarified in round 4, see top entry).
  - **Doze** needs foreground service + PARTIAL_WAKE_LOCK + battery-optimization exemption (OEM skins kill background).

### Current state
- **Phase 0**, before T0.1. Still no code. All 8 core docs refined to include haptics + 4-device.
- Repo root: `C:\Users\amand\Downloads\controller euro` (folder name has a space — quote paths in PowerShell).

### Next session: do this in order
1. TASKS.md **T0.1**: confirm `.NET 8 SDK`, `Android Studio/SDK`, install **ViGEmBus 1.22.0**; verify bus in Device Manager.
2. **T0.2**: scaffold `server/` (dotnet sln + xUnit) and `app/` (empty Compose), `.gitignore`, `git init`, first commit.
3. **T0.3/T0.4**: ViGEm hello-pad (joy.cpl stick sweep) + SendInput hello-key (Notepad).
4. Begin Phase 1 (T1.x): codec → UDP listener + SlotManager → PadEmulator + failsafe → phone transport → discovery → Gamepad deck → HUD → deck picker → end-to-end Steam test.

### Gotchas & open items
- Folder path has a space → quote in PowerShell.
- **Owner dislikes being asked questions** — resolve ambiguities via research/judgment and document the answer (this is how PRD §6 was handled). Only ask if truly unverifiable.
- ViGEmBus retired = accepted risk (D-003); VirtualPad migration = parking P4.
- No git repo yet; `git init` happens in T0.2.
- Rules reminder: zero code comments unless asked; TASKS IDs go in commit messages.
- Max 4 phones is a **hard rule** (RULES §1.8, D-014) — not a tunable.

### Key numbers to remember
- Packet = 30 bytes fixed, UDP. Rates: 120 Hz gamepad-style decks, 240 Hz gyro/fps/flight (D-007). Failsafe 300 ms. **Max 4 XInput slots** (was "up to 8 ViGEm pads" — now aligned to the XInput 4-cap, D-014).
- Rumble feedback ≤ 30 frames/s per slot; haptic round-trip target ≤ 50 ms.
- Latency targets: USB ~1–4 ms, Wi-Fi 5 GHz ~5–10 ms, BT ~15–50 ms (median).
- ETS2 keymap lives in PRD §3 and ships as `profiles/ets2.json`.
