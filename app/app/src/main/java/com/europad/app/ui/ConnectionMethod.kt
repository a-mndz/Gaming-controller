package com.europad.app.ui

enum class ConnectionMethod(
    val label: String,
    val detail: String,
    val available: Boolean,
) {
    Wifi("WI-FI / HOTSPOT", "Find your PC automatically", true),
    Usb("USB TETHERING", "Enable tethering, then connect", true),
    Bluetooth("BLUETOOTH", "Available in a future update", false),
}
