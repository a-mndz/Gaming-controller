# EuroPad

**One phone. Every controller.** Turn your Android phone into a low-latency, all-in-one game controller for your Windows PC — over USB, Wi-Fi, or Bluetooth — with swappable mode decks for truck sims, racing, gamepad, gyro steering, FPS aiming, keyboards, flight, retro emulators, media, and **up to 4 phones at once** — plus **game rumble haptics** fed back to your phone's vibration motor.

The PC runs a tiny server that creates **real virtual Xbox 360 controllers** (ViGEmBus, XInput slots 0–3 — shown as P1–P4) plus **virtual keyboard** presses, so Steam, ETS2/ATS, and any game accept each phone as a normal controller with zero per-game configuration.

| | |
|---|---|
| Status | Phase 0 — design & scaffolding |
| Targets | Windows 10/11 x64 · Android 10+ |
| Latency | ~1–4 ms USB · ~5–10 ms Wi-Fi 5 GHz · ~15–50 ms BT (not recommended for racing) |
| Multi-device | Up to 4 phones → XInput slots 0–3 / P1–P4 (API hard limit), each independent |
| Haptics | Game rumble → ViGEm notification → phone vibration (amplitude-mapped) |
| Docs | [PROJECT_BIBLE](PROJECT_BIBLE.md) · [PRD](PRD.md) · [ARCHITECTURE](ARCHITECTURE.md) · [RULES](RULES.md) · [TASKS](TASKS.md) · [DECISIONS](DECISIONS.md) · [MEMORY](MEMORY.md) |

## Repo layout

Repo root: `C:\Users\amand\Downloads\controller euro` (folder name has a space — quote paths in shells)

```
├── (8 core docs)     ← you are here, at the repo root
├── server/           ← C# / .NET 8 PC server (planned, T0.2)
├── app/              ← Kotlin Android app (planned, T0.2)
├── profiles/         ← per-game keymap JSONs (planned, T2.1)
└── tools/echotest/   ← latency/loss test harness (planned, T2.9d)
```

## How it works

```
Phone (deck UI + gyro) ──UDP/BT, 30-byte snapshots──► PC Server ──► Virtual X360 pad + Keyboard ──► Steam/ETS2/games
```

- **Decks**: home screen shows mode cards like swappable faceplates — tap one, it loads full-screen, switch anytime mid-game.
- **Transports**: switch USB / Wi-Fi / Bluetooth from inside the app; all three carry the same protocol.
- **Failsafe**: link drops → throttle to zero, buttons release, wheel centers. No stuck inputs, ever.

## Quick start (as soon as Phase 1 lands)

1. Install the [ViGEmBus 1.22.0](https://github.com/nefarius/vigembus) driver on the PC.
2. Run `EuroPadServer.exe` (server/) — virtual pad appears immediately.
3. Install the APK (app/), open it, tap your PC in the discovered list (or add its IP manually — manual IP is a first-class path). From Phase 3 (T3.6), you can also scan the pairing QR.
4. Pick a deck. Play.

USB mode: enable USB tethering on the phone, pick the USB transport in-app.

## Development

- Server: `dotnet build server/EuroPad.Server.sln -c Release`, tests: `dotnet test`
- Phone: `gradlew assembleDebug` (or Android Studio)
- Workflow & conventions: **RULES.md** · current work: **TASKS.md** · where we left off: **MEMORY.md**

## License

Personal project (undecided; will be Apache-2.0 if ever published). See PROJECT_BIBLE §10.
