package com.europad.app.net

/**
 * Link quality over a rolling window: median-5 RTT (spike-resistant, PRD FR-1.7) plus loss %
 * across the last [window] pings. Pure so it unit-tests without a device.
 */
class LinkStats(private val window: Int = 20) {

    private val rtts = ArrayDeque<Long>()
    private val outcomes = ArrayDeque<Boolean>()

    @Volatile
    var medianRttMs: Long = -1
        private set

    @Volatile
    var lossPercent: Int = 0
        private set

    @Volatile
    var lastRttMs: Long = -1
        private set

    fun onReply(rttMs: Long) {
        if (rttMs < 0) return
        lastRttMs = rttMs
        synchronized(this) {
            rtts.addLast(rttMs)
            while (rtts.size > 5) rtts.removeFirst()
            record(true)
            medianRttMs = rtts.sorted()[rtts.size / 2]
        }
    }

    fun onTimeout() {
        lastRttMs = -1
        synchronized(this) {
            record(false)
            if (outcomes.none { it }) medianRttMs = -1
        }
    }

    fun reset() {
        synchronized(this) {
            rtts.clear()
            outcomes.clear()
            medianRttMs = -1
            lastRttMs = -1
            lossPercent = 0
        }
    }

    private fun record(ok: Boolean) {
        outcomes.addLast(ok)
        while (outcomes.size > window) outcomes.removeFirst()
        lossPercent = if (outcomes.isEmpty()) 0 else outcomes.count { !it } * 100 / outcomes.size
    }
}

object NetLabels {
    /**
     * USB tethering: AOSP hands the PC 192.168.42.x (D-012), but OEM skins use their own subnet
     * (seen on ColorOS: 10.156.34.x), so the caller can also pass a ConnectivityManager check that
     * proved the local socket address sits on the tether (TYPE_ETHERNET) interface itself.
     */
    fun transportFor(localIp: String?, isTetherLocalIp: Boolean = false): String =
        if (isTetherLocalIp || (localIp != null && localIp.startsWith("192.168.42."))) "USB" else "WI-FI"
}
