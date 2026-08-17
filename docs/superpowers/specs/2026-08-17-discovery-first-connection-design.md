# EuroPad Discovery-First Connection Design

## Goal

Replace the developer-facing manual connection form with a controller-like connection screen that only asks the user to choose a real-world link: Wi-Fi/hotspot or USB tethering.

## Experience

- The app automatically scans for EuroPad PCs from launch.
- Users see three link choices: Wi-Fi or hotspot, USB tethering, and Bluetooth.
- Wi-Fi/hotspot and USB use the existing mDNS discovery path. A detected PC is presented by server name only; tapping it connects immediately with the discovered endpoint.
- USB includes one short instruction to enable Android USB tethering before connecting the cable. It does not show IP addresses.
- Bluetooth is visibly marked as unavailable until the RFCOMM transport exists; it is not selectable.
- The IP address, port, PIN, and QR-text fields are removed from the app UI. Existing saved-endpoint preferences may remain unused for compatibility.

## Scope

This changes only the pre-connection Android UI. The existing UDP connection call, discovery implementation, PIN argument (default zero), and connection state handling remain unchanged.

## Validation

Add a pure model for user-visible connection methods, then unit-test that Wi-Fi and USB are selectable while Bluetooth is unavailable. Run Android unit tests and assemble the debug APK.
