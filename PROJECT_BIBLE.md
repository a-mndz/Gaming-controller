# PROJECT_BIBLE.md
> Single source of truth for the EuroPad project. Every other doc defers to this one.
> If anything else conflicts with this file, this file wins — then update the other file.

| Key | Value |
|---|---|
| **Product name** | EuroPad (working name — rename freely until v1.0) |
| **One-liner** | Turn your Android phone into a low-latency universal PC controller with swappable mode decks — truck sim, racing, gamepad, gyro, and more — over USB, Wi-Fi, or Bluetooth, with up to 4 phones at once and game-rumble haptic feedback. |
| **Tagline** | One phone. Every controller. |
| **Owner** | amand (solo developer) |
| **Started** | 2026-08-17 |
| **Status** | Phase 0 — design & scaffolding |

---

## 1. Mission

Build a personal-use tool that replaces physical controllers for truck sims, racing, and general gaming by turning the phone in my hand into the controller for my Windows PC, with **zero per-game configuration** in Steam or any game.

Success looks like: launch ETS2, tap the Truck deck (remembered from last session), steer by tilting the phone, flick indicators and the horn with my thumb, and the whole input chain feels wired despite being wireless.

## 2. Why this exists (the problem)

- I play **truck sims (ETS2/ATS)** and want full truck controls — steering, pedals, handbrake, **indicators, horn, hazard** — but real truck-sim wheels + button boxes cost $200–600 and only do one job.
- I also play **racing, shooters (gyro aim), emulators, and general games** — each has a different ideal controller layout.
- Existing phone-as-gamepad apps exist (Remote Gamepad, DroidJoy, RojX, PhantomPad) but **none is open, none does all modes**, and none combines truck-sim keyboard extras with a swappable deck UI.

## 3. Core product principles (non-negotiable)

| # | Principle | Meaning in practice |
|---|---|---|
| P1 | **Latency is sacred** | Every design decision is checked against latency. Favors UDP snapshots, 240 Hz in racing/gyro modes, USB mode. |
| P2 | **Works with Steam & everything, out of the box** | Emulate a *real* Xbox 360 controller + keyboard via OS-level drivers. No game patching, no proxy DLLs, no hooking. |
| P3 | **Modes are interchangeable decks** | Home screen = deck picker. Each mode is a self-contained "faceplate". Switching is instant, one tap, during play. |
| P4 | **Failsafe beats feature** | If the link drops, throttle to zero, buttons release, wheel centers. A stuck throttle is a bug; a lost packet is not. |
| P5 | **Snapshot, not event** | Every packet is full current state. Dropped packets self-heal in one send interval. |
| P6 | **Three transports, one pipeline** | USB, Wi-Fi, and Bluetooth are swappable links to the same input pipeline. No transport-specific game logic. |
| P7 | **YAGNI** | Personal tool. No user accounts, no cloud, no telemetry, no store monetization. Ship the MVP slice first. |
| P8 | **Document decisions** | Every non-obvious choice lands in DECISIONS.md with date, options, and rationale. |

## 4. Users & scenarios

**Primary user: me** (Windows 11 PC, Android phone, same home Wi-Fi, 5 GHz available).

| Scenario | Transport | Mode | Latency need |
|---|---|---|---|
| ETS2 highway job, long session | Wi-Fi 5 GHz (cable-free convenience) | Truck Sim + Gyro Wheel | < 15 ms |
| Racing / drifting, precision steering | USB tethering | Gyro Wheel @240 Hz | < 5 ms |
| Couch game on Steam | USB or Wi-Fi | Gamepad deck | < 15 ms |
| Competitive shooter aiming | USB | FPS Gyro-Aim | < 5 ms |
| Emulator night | Wi-Fi | Emulator/Retro deck | < 20 ms |
| Control music from bed | Wi-Fi | Media Remote | irrelevant |
| Local co-op party (up to 4 phones) | Wi-Fi (or USB mix) | Multiplayer Lobby → each phone picks its own deck | < 20 ms |

## 5. Glossary (project vocabulary)

| Term | Meaning |
|---|---|
| **Deck** | A mode's full-screen UI + its input mapping — one swappable "faceplate" |
| **Transport** | The physical link: USB tethering, Wi-Fi, or Bluetooth |
| **Pipeline** | Phone capture → encode → transport → PC server → emulation → OS |
| **Snapshot** | One packet = full current input state (buttons + axes), not a diff |
| **Failsafe** | Server behavior when the phone goes silent: release inputs, center axes |
| **Pad** | A virtual Xbox 360 controller instance (ViGEm). **Max 4 visible to games — the XInput API hard limit.** |
| **Slot** | XInput slot 0–3 assigned to a connected phone; the server hands out the lowest free slot |
| **Haptics** | Game rumble → ViGEm notification → server feedback frame → phone vibration |
| **Keyboard emitter** | Server component that synthesizes key presses (indicators, horn, etc.) |
| **Profile** | JSON file mapping deck actions → keyboard keys for a specific game |
| **Keymap** | The concrete key assignments inside a profile (e.g., ETS2 defaults) |

## 6. The 10 mode decks (master list)

| # | Deck | Emoji | Inputs | Emulation target | Phase |
|---|---|---|---|---|---|
| 1 | Gamepad | 🎮 | 2 sticks, ABXY, D-pad, LB/RB, LT/RT, Start/Select | Xbox 360 pad | 1 |
| 2 | Truck Sim | 🚛 | wheel/pedals/handbrake/indicators/hazard/horn/lights/wipers/exhaust brake/gears | pad + keyboard (ETS2 keymap) | 2 |
| 3 | Gyro Wheel | 📱 | tilt-steer up to 900° + on-screen pedals | pad axes | 2 |
| 4 | Arcade Racing | 🏎️ | simplified wheel + pedals + handbrake | pad axes | 3 |
| 5 | FPS Gyro-Aim | 🔫 | tilt = right-stick fine aim + standard gamepad inputs | pad axes | 3 |
| 6 | Keyboard | ⌨️ | remappable touch key grid | keyboard | 3 |
| 7 | Flight Sim | ✈️ | yoke pitch/roll axes + throttle slider | pad axes | 4 |
| 8 | Emulator/Retro | 👾 | SNES/N64/PS1 layout presets | pad + keyboard | 4 |
| 9 | Media Remote | 🎵 | play/pause/volume/skip | keyboard media keys | 4 |
| 10 | Multiplayer Lobby | 👥 | up to 4 phones join → XInput slots 0–3 (shown as P1–P4), each runs any deck 1–9 | pads P1–P4 | 4 |

## 7. Transport master table

| Transport | Mechanism | Expected latency | When to use |
|---|---|---|---|
| 🔌 USB | Android USB tethering → phone becomes a virtual Ethernet NIC on PC; the same UDP socket code runs over it | ~1–4 ms | racing, gyro-aim, anything competitive, multi-phone sessions |
| 📶 Wi-Fi | UDP over LAN (same router), mDNS auto-discovery (with manual IP fallback — first-class, not just a fallback; QR scan from Phase 3 — NSD is unreliable on some OEM skins) | ~5–10 ms on 5 GHz; 15–30 ms on 2.4 GHz | convenience play, couch, media, 2–4 player casual |
| 🅱️ Bluetooth | Classic BT pairing, RFCOMM serial pipe (SPP) to server listener | **~15–50 ms best case, up to 100 ms under load** (research-verified; RFCOMM is slow for real-time input) | last resort only; not recommended for racing/gyro |

All three feed the **same** server pipeline. Transport picking is a UI choice on the phone; server does not care.

## 8. Success metrics

| Metric | Target | How measured |
|---|---|---|
| End-to-end input latency (UDP/Wi-Fi 5 GHz) | ≤ 10 ms median | phone injects timestamp; server echoes RTT |
| End-to-end latency (USB) | ≤ 5 ms median | same |
| Input send rate (racing/gyro/aim/flight decks) | 240 Hz (D-007) | counters in HUD |
| Failsafe trigger time | ≤ 300 ms after last packet | server log + on-screen |
| Packet loss impact | zero visible hitch at 5% loss on LAN | synthetic loss test |
| Steam recognition | virtual pad shows in Steam controller settings, works in-game with no per-game config | manual checklist |
| Multi-device | 4 phones connected → 4 distinct XInput pads, independent input + rumble per phone | joy.cpl shows 4 devices; per-slot input test |
| Haptics round-trip | rumble notification → phone vibration begins ≤ 50 ms | timed test harness |
| Battery drain (phone, gyro wheel playing) | ≤ 15%/hour | Android battery stats |

## 9. Explicit non-goals

- ❌ iOS / iPhone support
- ❌ Console targets (PS5/Xbox) — Windows PC only
- ❌ Internet/remote play (LAN only)
- ❌ Anti-cheat bypass (we comply with normal virtual-input behavior; if a game blocks it, that game is out)
- ❌ Video streaming / screen mirroring (not a Steam Link replacement)
- ❌ Multi-user accounts, cloud sync, payments, ads
- ❌ Haptics beyond rumble translation (trigger motors, HD haptic waveforms, audio-to-haptics)
- ❌ More than 4 simultaneous phones — XInput API cannot address a 5th slot; physically impossible without a DirectInput fallback

## 10. Legal & license posture

- **Personal project**, no distribution planned for v1. If ever published: Apache-2.0.
- ViGEmBus itself is retired (Nov 2023, trademark conflict — see DECISIONS.md) but **still functional on Win10/11** and is the same mechanism commercial apps use. The architect's successor line ("VirtualPad") is tracked in DECISIONS.md as a migration watch-item.
- Emulating an Xbox controller via ViGEmBus is standard practice (DS4Windows, Sunshine, Phone2Pad and similar tools all build on it).

## 11. File map (this repo)

| File | Purpose |
|---|---|
| README.md | Quick orientation + how to run |
| PROJECT_BIBLE.md | This file — mission, principles, masters |
| PRD.md | Product requirements & feature spec |
| ARCHITECTURE.md | Technical architecture & protocols |
| RULES.md | Working rules, conventions, do/don't |
| MEMORY.md | Session memory — where we left off, gotchas |
| TASKS.md | Phased task breakdown & status |
| DECISIONS.md | Decision log (ADR style) |
