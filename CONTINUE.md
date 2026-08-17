# CONTINUE.md — EuroPad rewrite status (updated 2026-08-17 ~20:10)

> The ETS2 rewrite (two-mode truck deck + on-phone key remapping) is functionally
> complete and built. The **minimal-deck interface redesign is DONE** (see §3).
> One wire-path issue remains open: the remap config frame was not reaching the
> server reliably (see §2).

---

## 1. Done this session (rewrite complete, all builds green)

- **DeckPickerScreen rewired** to the single `TruckDeck2(transport, prefs)` — old 6-tab deck row,
  `DeckTabs`/`DeckTab`, the `deck` state, the SIZE/controlScale slider (Gamepad-only), and the
  240 Hz budget logic are all gone. 120 Hz only, budget = 20 ms.
- **Old decks deleted**: GamepadDeck, TruckDeck, GyroWheelDeck, ArcadeRacingDeck, FpsAimDeck,
  KeyboardDeck, input/GyroAim.kt, input/AimMath.kt, test/AimMathTest.kt. Grep for dangling
  references came back clean.
- **Server tests: 47/47 pass** — added `ConfigFrame_MirrorsPhoneEncoder` (4 theories + 17-char max)
  in FrameCodecTests and `SetBitKey_UpdatesActiveProfileAndPersists` in ProfileTests.
- **APK builds + unit tests pass** (Gradle 8.9 dist, JDK 17). Only pre-existing warnings.
- **Server republished Release, running** as background process (EuroPadServer.exe, protocol v2,
  Wi-Fi 192.168.1.3:47910, PIN 4321).
- **Phone verified live** (serial CUONZPQORO8HWW5P): new UI launches, discovery finds server,
  connects P1, deck shows STEER left / BRAKE+THRTL right / KEYS / GYRO / MAP KEYS; KEY BINDINGS
  panel opens; picker works; on-phone prefs save (`key.HORN` persisted via SharedPreferences).

## 2. OPEN BUG at interrupt — config frame not reaching server

Rebind HORN → the phone saved the pref, but `%APPDATA%\EuroPad\profiles\default.json` stayed at
the old key and the server printed no `remap bitN` line.

**Diagnosis so far**
- Wire layout, offsets (0/2/3/4/6/10/12/14), flags and VERSION=2 match on both Protos. Verified.
- Server silently dropped config frames arriving with no slot (HandlePacket line ~197 `slot is
  null → return`) — and the server log showed chronic FAILSAFE/link-recovered churn: Wi-Fi
  stalls pause the phone's >300 ms snapshot stream. A one-shot config frame sent during a stall
  or after a slot-free is lost forever.

**Fixes already applied + built + deployed (NOT yet re-verified end-to-end)**
1. `UdpTransport.sendConfig`: now retries 4× over ~1.6 s (delays 0/300/800/1600 ms) on a daemon
   thread, sends in both Connected and Reconnecting states, logs `config frame sent/skipped`.
2. Server: logs `Config frame from <remote> dropped (no slot — connect first; phone retries)`.
3. Both rebuilt: server republished Release, APK rebuilt + installed on phone, server restarted.

**Verify when resuming**
1. Server is running as a persistent background process (EuroPadServer.exe). If not:
   `& "server\EuroPad.Server\bin\Release\net8.0\EuroPadServer.exe"` from its dir.
2. App is on the deck (connected P1). MAP KEYS → Horn → pick a new key (e.g. J).
3. Server console must print `Slot 0 (P1) remap bit3 (HORN) -> 'J' : ok`.
4. `%APPDATA%\EuroPad\profiles\default.json` must show `"HORN": "J"`.
5. If it still fails, `adb logcat -d -t 300 tag EuroPadUDP:* *:S` shows `config frame sent/skipped`
   lines per attempt — correlate with the server's FAILSAFE churn timestamps.
6. Optional sanity: hold HORN button on the deck — the remapped key should press in ETS2/Notepad.

## 3. DONE 2026-08-17 ~21:20 — controller interface redesign (minimal ETS2 deck)

Plan: `.kilo\plans\1786973406460-ets2-minimal-deck-layout.md` — percentage-grid redesign of
`TruckDeck2`. Phone-only UI change, **no wire protocol changes**.

**Delivered:**
- `ui\DeckLayout.kt` (new) — pure fraction-rect math (`DeckRect` + `DeckLayout` object),
  aspect-ratio-aware sizes, unit-testable on the JVM.
- `ui\Ets2Deck.kt` (rewritten) — single `BoxWithConstraints` root, absolutely-positioned
  controls per `DeckLayout`:
  - Top utility row (y≈0.08): LIGHTS / WIPER (120 ms tap) / WASHER (700 ms hold pulse on WIPERS
    bit, separate busy guard) with hand-drawn Canvas icons; R|N|D segmented gear selector (gear
    taps: R=GEAR_DN 200 ms, N=GEAR_UP+GEAR_DN together 250 ms, D=GEAR_UP 200 ms; guard flag
    ignores taps during a pulse); P-BRK (red) / SET (opens KeymapPanel) / MENU (opens OptionsSheet).
  - Link strip (y≈0.155): "P1 · USB · RTT · LOSS" 9 sp text, amber when degraded.
  - Second row (y≈0.25): ←|→ signal arrows (IND_L/IND_R holds, chevron icons) + CAMERA button
    (holds `ButtonLo.BACK` on the virtual Xbox pad — no protocol change).
  - Wheel (cx 0.20, cy 0.74, diam 0.22h): Canvas disc + spokes + blue accent ring (`WheelAccent`,
    4 dp ring / 3 dp spokes, Y-down spoke angles from 90° base), rotates steer×90°, "EURO TRUCK"
    two-line hub text; keeps the old WheelPad drag/gyro semantics verbatim.
  - Pedals: BRAKE (0.11w×0.24h) + ACCEL (0.13w×0.30h), bottom-aligned at y=0.90, fill-from-bottom,
    % readout centered, label below; horizontal slot grip lines (5× Canvas lines, 10%–90% width);
    same AX_LT/AX_RT drag math as before.
  - Center zone (X 0.30–0.62) intentionally empty.
  - `OptionsSheet` (MENU popup): steer-mode toggle, gyro range cycle, center, MAP KEYS,
    quick-keys row (HORN/HAZARD/BEAM/ENGINE). Old QuickKeysRow/top status bar deleted.
- `ui\PitWall.kt`: added `SignalRed`, `SignalGreen`, `WheelAccent` tokens.
- `DeckLayoutTest.kt`: 10 unit tests asserting spec bounds — all pass.
- **33/33 app unit tests pass**, APK built, installed on phone (CUONZPQORO8HWW5P),
  connected P1 over Wi-Fi, verified via uiautomator dump: all `pad:*` semantic nodes
  present (LIGHTS/WIPER/WASHER/GEAR/HANDBRAKE/SETTINGS/MENU, IND_L/IND_R, CAMERA,
  WHEEL with EURO/TRUCK text, BRAKE, ACCEL) with center region free.
- All plan §1 mappings implemented as specified; no new dependencies; `KeymapPanel`,
  `TruckKeys`, `DeckPickerScreen`, server, and encoders untouched.

**Not yet verified (manual):** WIPER/WASHER pulse behavior in ETS2, gear tap feel,
CAMERA (Back button) bind in-game.

## 4. Config-frame wire layout (both sides MUST stay in sync)

```
flags      = FLAG_CONFIG (0x40)
buttons_lo = kind (=1 CfgSetBitKey) | (hiBitIndex << 8)
buttons_hi = charCount | (firstChar << 8)          // ASCII, ≤17 chars total
axes[0..7] = remaining chars, two per axis word (low byte first)
```
- Server decode: `InputFrame.PayloadText`, bit index `ButtonsLoRaw >> 8`.
- Phone encode: `FrameEncoder.encodeSetBitKey`.
- VERSION = 2 on both sides — mixed versions reject (BadVersion).

## 5. Gotchas (already paid for — don't re-learn)

- **Do not use PowerShell `>` redirect for binary output (screencap etc.) — it corrupts the file
  via CRLF conversion.** Use `adb shell screencap -p /sdcard/x.png` then `adb pull`, or `-d` for
  XML.
- This model cannot read images — use `uiautomator dump` + XML text nodes to verify phone UI.
- `adb install -r` does NOT kill the running app: an old activity kept showing the old deck after
  install. Always `adb shell am force-stop com.europad.app` + relaunch after installing.
- Server exe must be stopped before `dotnet publish` (file lock) and restarted after — kill via
  the background process manager or `Stop-Process -Name EuroPadServer -Force`.
- adb full path: `C:\Users\amand\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
- Gradle full path: `C:\Users\amand\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat`,
  with `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"`.
- `TruckKeys.names` order = server `ProfileManager.HiBitNames` order — drift silently remaps the
  wrong action.
- Phone has no rotation-vector sensor? `GyroSteering.start()` returns false → falls back to wheel
  mode and disables the GYRO chip.
- Wi-Fi here (2.4 GHz) stalls routinely >300 ms → FAILSAFE churn is expected noise, not a code
  bug. USB tether or 5 GHz for serious driving.
