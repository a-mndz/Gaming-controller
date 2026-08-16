# RULES.md
> Working rules for anyone (human or AI agent) touching this repo. Follow strictly; change deliberately (edit this file with a dated reason).

## 1. Prime rules (never break)

1. **Latency before cleanness on the hot path.** No allocations, logging, or locks per-packet on phone sender or server dispatch. Measure before you "optimize."
2. **Snapshot semantics.** Never build event-diff packets on the wire. The packet is always full state; edges are computed at the server.
3. **Failsafe always on.** No feature may disable the 300ms neutral-input failsafe. Applies per-slot (all 4).
4. **No network egress.** The app and server must never contact anything outside the LAN. No analytics, no update pings, no third-party SDKs that phone home.
5. **No secrets in the repo.** PINs are runtime-generated; nothing credentials-like is ever written to disk outside the server's local config.
6. **Docs over memory.** Every significant choice gets an entry in DECISIONS.md the same day it's made.
7. **Haptics never block.** Rumble callback → queue only; vibrator calls must never sit on the receive loop.
8. **4 slots is a hard ceiling.** SlotManager refuses the 5th client with LOBBY_FULL — do not "fix" this without a DECISIONS.md entry overturning D-014.

## 2. Stack & tooling

| Layer | Rule |
|---|---|
| Server | C# / .NET 8, console + tray (no WPF, no ASP.NET, no WinForms *forms*; the single `NotifyIcon` from System.Windows.Forms for the tray is the one sanctioned exception — see D-005). NuGet: `Nefarius.ViGEm.Client` (pinned to 1.21.256 — last release Feb 2023, verified existing; ViGEm is archived so no future versions are expected). Test framework: xUnit. |
| Phone | Kotlin, minSdk 29, Jetpack Compose UI, coroutines + Flow. No Jetpack bloat beyond Compose/DataStore/NSD. |
| Build | Server: `dotnet build`. Phone: Gradle Kotlin DSL. Both must build from CLI without Android Studio GUI. |
| Formatting | Server: default .editorconfig, 4 spaces, file-scoped namespaces. Phone: ktlint-friendly, 4 spaces. |
| No comments | Code explains itself via names; comments only for the *why* of a non-obvious decision (and that why belongs in DECISIONS.md anyway). |

## 3. Coding conventions

- **Naming**: `PascalCase` types/members (C#), `camelCase` (Kotlin), `CONSTANT_CASE` for protocol constants. Deck names use the canonical IDs `GAMEPAD, TRUCK, GYRO_WHEEL, ARCADE, FPS_AIM, KEYBOARD, FLIGHT, RETRO, MEDIA, MULTI`.
- **Protocol changes**: bump `PROTOCOL_VERSION` + update §3 of ARCHITECTURE.md in the same commit. Never ship a client/server with silent wire-format drift.
- **Error handling**: network layer never throws past the transport boundary; parse errors → drop + counter. Emulation layer may log but must not crash on bad input.
- **Threading**: single writer per slot on the server; phone sender coroutine is the only socket writer. Never touch a socket from the UI thread.
- **Pure functions** for all codec + gyro math; unit-test them.

## 4. Git & workflow

- Branch: `main` is releasable; work in `dev` or feature branches `feat/<scope>-<thing>`.
- Commits: conventional style — `feat:`, `fix:`, `docs:`, `test:`, `chore:`; subject ≤ 72 chars, imperative mood.
- Never commit: `bin/`, `obj/`, `build/`, `.gradle/`, local settings, APKs, logs.
- No force-push, no history rewrites after push.
- Commit message references TASKS.md IDs when applicable (`feat: truck deck indicators (T2.3)`).

## 5. Task hygiene

- TASKS.md is the tracker. Before starting work, mark the task `in-progress`; on completion, mark `done` with date and a one-line result.
- One task = one commit-sized unit (≤ ~300 lines of diff preferred).
- Blocked task → add a `blocked:` note with the exact blocker; don't silently skip.

## 6. Definition of done (per task)

1. Code compiles (`dotnet build` / `gradlew assembleDebug`) with zero new warnings.
2. Unit tests added for new logic; `dotnet test` green.
3. If protocol/profile schema touched: ARCHITECTURE.md updated.
4. If user-visible: acceptance criterion from PRD §4 verified (or noted as manual-test pending).

## 7. Performance budget (regressions = bugs)

| Metric | Budget |
|---|---|
| Server dispatch per packet | < 500 µs (median; parse + diff + emulation submit — matches ARCHITECTURE §5 budget), no allocations |
| Phone encode per frame | < 200 µs |
| Packet size | 30 bytes fixed (v1) |
| Idle CPU (server, connected, no input) | < 0.5% |
| Active CPU (server, 240 Hz, 4 slots) | < 4% |
| Rumble feedback rate | ≤ 30 frames/s per slot (coalesced) |
| Simultaneous phones | max 4 (XInput hard limit — never raise silently, see D-014) |
| Haptic round-trip (rumble → vibration) | ≤ 50 ms |

## 8. Safety

- Never run the server with elevated privileges; ViGEmBus is installed once as admin, the server itself is user-level.
- Windows firewall: allow UDP inbound on the server port (private networks only). Never "public" scope.
- If a future feature needs internet access, it requires an explicit DECISIONS.md entry + user consent.

## 9. Documentation rules

- The 8 core files are canonical. Order of precedence on conflict: PROJECT_BIBLE > PRD > ARCHITECTURE > RULES > TASKS/DECISIONS/MEMORY > README.
- Precedence exception: a dated DECISIONS.md entry outranks stale text in any of the files above it — the decision is law until the affected docs are updated to match (DECISIONS.md header rule). Update docs the same day as the decision.
- Keep README.md ≤ one screen.
- MEMORY.md is updated at the end of every work session (what's done, what's next, gotchas).
