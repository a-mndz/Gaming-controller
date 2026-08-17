# EuroPad

**One phone. Every controller.** Turn your Android phone into a low-latency, all-in-one game controller for your Windows PC — over USB, Wi-Fi, or Bluetooth — with swappable mode decks for truck sims, racing, gamepad, gyro steering, FPS aiming, keyboards, flight, retro emulators, media, and **up to 4 phones at once** — plus **game rumble haptics** fed back to your phone's vibration motor.

The PC runs a tiny server that creates **real virtual Xbox 360 controllers** (ViGEmBus, XInput slots 0–3 — shown as P1–P4) plus **virtual keyboard** presses, so Steam, ETS2/ATS, and any game accept each phone as a normal controller with zero per-game configuration.

| | |
|---|---|
| Status | Phase 1 complete on real hardware (connect → play a Steam game end-to-end) · Phase 2 (truck sim / gyro / USB / haptics) underway |
| Targets | Windows 10/11 x64 · Android 10+ |
| Latency | ~1–4 ms USB · ~5–10 ms Wi-Fi 5 GHz · ~15–50 ms BT (not recommended for racing) |
| Multi-device | Up to 4 phones → XInput slots 0–3 / P1–P4 (API hard limit), each independent |
| Haptics | Game rumble → ViGEm notification → phone vibration (amplitude-mapped) |
| Docs | [PROJECT_BIBLE](PROJECT_BIBLE.md) · [PRD](PRD.md) · [ARCHITECTURE](ARCHITECTURE.md) |

## Repo layout

Repo root: `C:\Users\amand\Downloads\controller euro` (folder name has a space — quote paths in shells)

```
├── server/           ← C# / .NET 8 PC server (ViGEmBus pad + SendInput keyboard, UDP + mDNS)
│   ├── EuroPad.Server/
│   └── EuroPad.Server.Tests/   ← xUnit, run with `dotnet test`
├── app/              ← Kotlin + Jetpack Compose Android app (120 Hz UDP sender, decks, HUD)
├── profiles/         ← per-game keymap JSONs (default.json, ets2.json; hot-reload in T2.1)
└── tools/echotest/   ← latency/loss test harness (planned, T2.9d)
```

## How it works

```
Phone (deck UI + gyro) ──UDP/BT, 30-byte snapshots──► PC Server ──► Virtual X360 pad + Keyboard ──► Steam/ETS2/games
```

- **Decks**: home screen shows mode cards like swappable faceplates — tap one, it loads full-screen, switch anytime mid-game.
- **Transports**: switch USB / Wi-Fi / Bluetooth from inside the app; all three carry the same protocol. Wi-Fi is live today (Phase 1); USB and BT arrive in later phases.
- **Failsafe**: link drops → throttle to zero, buttons release, wheel centers (verified <850 ms end-to-end on real Wi-Fi). No stuck inputs, ever.

## Quick start

1. Install the [ViGEmBus 1.22.0](https://github.com/nefarius/vigembus) driver on the PC.
2. Run `EuroPadServer.exe` (server/) — virtual pad appears immediately and the server announces itself over mDNS (`_europad._udp`).
3. Install the APK (app/), open it, tap your PC in the discovered list (or add its IP manually — manual IP is a first-class path). From Phase 3 (T3.6), you can also scan the pairing QR.
4. Pick a deck. Play.

USB mode (Phase 2): enable USB tethering on the phone, pick the USB transport in-app.

## Development

- Server: `dotnet build server/EuroPad.Server.sln -c Release`, tests: `dotnet test server/EuroPad.Server.sln` (30 xUnit tests, all green)
- Phone: `gradlew assembleDebug` in `app/` (or Android Studio)
- Verified on: OPPO CPH1911 (Android 11) ↔ Windows 10/11 over Wi-Fi

## License

Personal project (undecided; will be Apache-2.0 if ever published). See PROJECT_BIBLE §10.
