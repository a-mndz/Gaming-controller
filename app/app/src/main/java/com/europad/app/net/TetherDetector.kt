package com.europad.app.net

import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Finds whether a local socket address belongs to the USB-tether interface.
 *
 * The phone routes UDP to the tethered PC over rndis0; the server subnet is NOT always
 * 192.168.42.x (ColorOS hands out 10.156.34.x), and the tether link is NOT registered as a
 * ConnectivityManager network either (it's not the default route, and on-device testing showed
 * `allNetworks` only lists mobile/wifi), so we enumerate raw [NetworkInterface]s and match the
 * resolved local address against their bound addresses. The Android USB-tether NIC is named
 * `rndis*`/`usb*`, which is checked as a secondary signal.
 */
fun isTetherAddress(localIp: String?): Boolean {
    if (localIp.isNullOrBlank()) return false
    return try {
        val addr = InetAddress.getByName(localIp)
        val nic = NetworkInterface.getByInetAddress(addr) ?: return false
        val usbName = nic.name.startsWith("rndis") || nic.name.startsWith("usb")
        val tetherRange = localIp.startsWith("192.168.42.")
        usbName || tetherRange
    } catch (_: Exception) {
        false
    }
}
