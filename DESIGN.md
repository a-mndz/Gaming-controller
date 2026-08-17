<!-- SEED: established with the user before implementation; re-run /impeccable document once there's code to capture the actual tokens and components. -->
---
name: EuroPad
description: Low-latency phone-as-controller for Windows PC
---

# Design System: EuroPad

## Overview

**Creative North Star: "The Pit Wall"**

EuroPad is a pit-wall timing screen, not a gamepad picture. Every screen is a dark, hairline-ruled instrument surface where data is the furniture: connection state, latency, slot, and the control field itself. The phone is held in one hand in a dim room while the eyes stay on a PC screen across the room, so the UI is read in glances and driven by one thumb — backlit glyphs, fixed-place digit readouts, instant color states, zero reading required mid-play.

The palette is ink black, ice white, one fountain-pen indigo, and one amber signal. Indigo is the voice of *active and selected*; amber is reserved for *warning semantics* (failsafe, disconnect, packet loss, and the truck cab's warning lamps). Nothing glows for decoration; color arrives with meaning or not at all. Depth comes from hairline rules and tonal layering, not shadows. Motion grammar is the instant flat-color swap of a timing-screen update — state changes are events, not animations.

Every deck (Truck, Gyro Wheel, Gamepad, FPS, Keyboard, Media…) keeps this world's chrome — HUD strip, digit-bank readouts, state grammar — while owning its own layout, composed around the physical logic of its controls rather than a shared grid. Each deck is a different faceplate on the same instrument.

**Key Characteristics:**
- Ink-black instrument ground, ice-white ink, indigo active, amber warning-only.
- HUD strip as a timing-tower row: `P1 · 192.168.1.2 · RTT 007ms · LOSS 0.0%` — mono, tabular, top edge.
- Every numeric readout in fixed-place digit slots (odometer/timing-screen discipline).
- One-thumb-arc control placement; oversized targets; hairline-ruled modules with literal tab labels.
- State = instant color swap + brightness step + haptic tick. No easing on press feedback.
- Strict WCAG AAA; color never the only signal.

## Colors

The palette is committed: four roles on instrument-black ground, composed from a fountain-pen indigo seed (hue ~270°). Dark is forced by the scene: a single player in a dim room, evening, eyes on the far PC screen — bright surfaces fight the room.

### Primary

- **Fountain-Pen Indigo** (oklch(0.46 0.15 272)): active and selected state — current deck, pressed control fill, selected server row, gyro-center lock. Carries white text when filled. Rare: only ever appears where something is *live*.

### Secondary

- **Signal Amber** (oklch(0.77 0.14 80)): warning semantics exclusively — failsafe countdown, link loss, high-loss badge, truck-deck indicator/hazard lamps. Carries near-black text on filled badges (L 0.77 pale-fill zone).

### Neutral

- **Instrument Black** (oklch(0.07 0.000 0)): app ground. Pure near-black, chroma zero — no hue tint.
- **Panel** (oklch(0.14 0.010 270)): deck modules and HUD strip fill, bg pulled 10–15% toward indigo-ink. The only depth layer below rules.
- **Ice White** (oklch(0.93 0.005 270)): body text and control labels; ≥7:1 against ground (AAA).
- **Tower Gray** (oklch(0.60 0.008 270)): secondary labels, inactive controls, grid hairlines; ≥3.5:1 against ground.

### Named Rules

**The Amber-for-Alarm Rule.** Amber means something is wrong about the link or the cab, never "highlighted". One amber pixel during clean play is a bug.

**The Indigo-Rarity Rule.** Indigo appears only on live/selected state. If a screen is mostly indigo, the state signal has been diluted.

## Typography

**Display Font:** [condensed industrial grotesque — to be resolved during implementation; Android font resource, must ship with the APK]
**Body Font:** same family as display, regular weight
**Label/Mono Font:** [tabular-figure mono — to be resolved during implementation; must have true `tabular-nums` behavior for digit banks]

**Character:** Instrument-panel voice, not website voice. Condensed caps for module tab labels (small size, wide tracking, like engraved panel legends); regular-case body for anything instructional; mono tabular figures for every number that updates — RTT, loss, slot, pedal values. Pairing is one engineered family across weights plus one mono: contrast by function, not by decorative pairing.

**The Fixed-Place Rule.** Any updating number occupies a fixed-width digit slot with leading zeros (RTT reads `007ms`, loss reads `0.0%`). Numbers that shift width while updating read as jitter; a timing screen never jitters.

## Layout

Landscape phone, one thumb. Shared chrome across all decks: the HUD timing strip is a hairline-ruled row pinned to one edge (top in landscape decks), mono, tabular; everything else is deck territory.

**The Deck-Owns-Its-Grid Rule.** Each deck mode is composed as a separate layout around the physical logic of its controls — the Truck deck arranges wheel bar, pedal pair, and stalk toggles by reach and cadence; the Gyro Wheel deck is one centered wheel with thumb-zone pedals; the Gamepad deck mirrors twin-stick ergonomics; the Keyboard deck is a reach-mapped grid. No deck inherits another deck's layout or grid. The world shares palette, chrome, and state grammar; layouts are never shared.

**The One-Thumb-Arc Rule.** Control placement follows thumb-arc reach from the natural grip point: high-cadence controls (steering, pedals, primary buttons) inside the arc; low-cadence controls (mode switch, settings) at the arc edge. Targets generously exceed 48dp.

Spacing: hairlines (1px, Tower Gray at low alpha) separate modules; no boxy card chrome. Modules are titled zones, not cards.

## Elevation & Depth

Flat. The system uses hairline rules and the single Panel tonal step for depth — instrument surfaces don't float, they're mounted. No shadows, no blur, no glass. Pressed state sinks via brightness step (darker fill + white label), never a shadow. Focus is a 2px Ice White inset ring.

**The Instant-Swap Rule.** State changes are instant color swaps (0ms transition) with a haptic tick; continuous animation is reserved for continuous physics only (wheel auto-center return, gyro needle). Reveal animations: none — everything is visible by default; this is an instrument, not a brochure.

## Shapes

Slightly machined corners: 4–8px radii on interactive surfaces (buttons, digit banks, bars), never pill-round except for physical knobs rendered literally (gyro center button, stick caps). Control bars (wheel bar, pedals, triggers) are long rectangles with hairline borders and an indigo fill proportional to value. Indicator stalks and toggles render as labeled rocker rectangles with literal ON/OFF legends.

**The No-Stripe Rule.** Disabled state is Tower Gray fill at 40% alpha with the label struck through — never a diagonal stripe, never a red slash.

## Do's and Don'ts

### Do:
- **Do** keep the HUD strip identical across decks: slot (P1–P4) · host · RTT in fixed-place digits · loss% · transport icon, in that order.
- **Do** double-encode every state: transport/connection/failsafe each pair a color with an icon or word.
- **Do** give every updating number a fixed-width digit slot and leading zeros.
- **Do** keep one thumb in charge: all in-play controls reachable without re-gripping.
- **Do** treat each deck as a bespoke layout that reuses only the world's chrome and vocabulary.

### Don't:
- **Don't** use amber for anything that isn't a warning or a literal cab lamp.
- **Don't** add shadows, blurs, glass, or gradient fills (incl. no gradient text, ever).
- **Don't** animate state swaps; presses and selections are instant + haptic.
- **Don't** share grids or layouts between decks — that kills the faceplate model.
- **Don't** use side-stripe accent borders, numbered section scaffolds, or card grids as structure.
- **Don't** rely on color alone for any state.
