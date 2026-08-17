# Product

## Register

product

## Users

Single user now (the author), designed to survive public release later. Uses EuroPad from a couch or desk: phone in hand, eyes on the PC screen across the room, one thumb doing all the work on touch controls, or the phone tilted for gyro steering. Sessions run from quick ETS2 hauls to long racing stints, in dim rooms where screen brightness matters.

## Product Purpose

Turn an Android phone into a low-latency Xbox 360 controller + keyboard for a Windows PC, over USB / Wi-Fi / Bluetooth, with zero per-game configuration. Success: launch ETS2, tap the Truck deck, steer by tilting, flick indicators with the thumb — and the input chain feels wired despite being wireless. Up to 4 phones at once (P1–P4) and game rumble fed back to the phone's vibration motor.

## Brand Personality

Precise. Tactile. Motorsport.

- Precise: this is an instrument — HUD readouts (RTT, slot, transport) are telemetry, large and legible at a glance like racing cockpit gauges.
- Tactile: controls should feel physical — chunky touch targets sized for a thumb, haptic ticks on press, like holding a real pad.
- Motorsport: the tone is racing-telemetry confidence, DS4Windows-functional directness, Steam Input's no-nonsense utility. Fun through feel, not decoration.

## Anti-references

- Generic default-Material Android app — stock theme with no identity.
- SaaS dashboard look — card grids, hero-metric tiles, startup-cream palettes.
- Heavy brand-gloss controller apps (Backbone/Razer style marketing sheen) — keep their polish, drop the gloss.

## Design Principles

1. **Precision is the product.** Latency and reliability are the whole reason to exist; every UI decision reinforces fast, trustworthy input — live numbers over decorative chrome.
2. **One thumb to drive it.** Every control reachable and hittable by a single thumb; oversized targets, glove-proof spacing, no fiddly gestures mid-game.
3. **Eyes on the other screen.** The phone UI is glanced at, not studied. Big type, instant state reads, zero reading required during play.
4. **Hardware, not website.** Decks should feel like physical faceplates swapped onto a controller — not web layouts rendered in Compose.
5. **Practice what you preach.** A tool whose pitch is "feels wired" cannot afford sluggish, floaty UI motion or laggy press feedback.

## Accessibility & Inclusion

- Strict WCAG AAA: ≥7:1 contrast for body text, scaled type, focus states on everything interactive.
- Min touch targets generously exceed Android's 48dp guidance — sized for one-thumb, eyes-away use.
- Respect prefers-reduced-motion / system animation scales; haptics have intensity control and can be disabled.
- Color is never the only signal: transport state, connection, failsafe all double-encode with icons/text.
