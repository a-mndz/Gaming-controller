# EuroPad Steering UI Design

## Goal

Make the Truck, Gyro Wheel, and Arcade decks feel like purposeful driving controls: the steering state is legible at a glance, all in-play controls sit in a comfortable thumb arc, and the connection telemetry stays available without competing with steering.

## Visual Direction

Use EuroPad's existing **Pit Wall** language: instrument black ground, low indigo panel surfaces, ice-white labels, and amber only for safety or link warnings. Keep the interface flat and mechanical—hairline borders, compact uppercase labels, no decorative motion, gradients, or card chrome.

## Interaction Model

- The steering field is the primary visual element in each driving deck. It has left/right end stops, a persistent center datum, a live indigo position marker, and a signed steering readout.
- Touch steering maps horizontal drag to the full steering range and returns to center when released. When gyro is active, the field becomes read-only feedback and a deliberate tap recenters the phone.
- Brake and throttle remain separate vertical bars at opposite ends of the layout so they can be found without looking. Their fills show the current analogue pressure.
- Low-frequency controls (indicators, lights, engine, wipers) stay in a compact utility bank above the steering field. High-frequency controls (horn and handbrake) remain central and oversized.
- The HUD is reduced to a fixed, single-row timing strip: slot, transport, host, RTT, loss, and connection warning. Gyro settings appear in a small dedicated settings row rather than expanding the telemetry strip.

## Scope

This pass only improves the driving decks' in-play Compose UI. It does not change packet protocol, input routing, server mappings, gyro maths, transport, or deck selection.

## Validation

Pure steering presentation math is unit-tested. The Android unit-test suite and debug APK build provide regression coverage for the Compose changes.
