package com.europad.app.ui

import androidx.compose.ui.graphics.Color
import com.europad.app.net.ConnState
import com.europad.app.net.Proto

object PitWall {
    val Ground = Color(0xFF000000)
    val Panel = Color(0xFF0D0D11)
    val PanelBorder = Color(0xFF24242A)
    val Ink = Color(0xFFF0EFF5)
    val TowerGray = Color(0xFF8E8D9A)
    val ButtonLabel = Color(0xFF9E9DAA)
    val Indigo = Color(0xFF3843D0)
    val Amber = Color(0xFFE8A92D)
    val Hazard = Color(0xFFC9B03A)
    val SignalRed = Color(0xFFFF2828)
    val SignalGreen = Color(0xFF00E600)
    val NeutralWhite = Color(0xFFE8E8EE)

    /* Steering wheel palette */
    val WheelGlow = Color(0xFF2979FF)
    val WheelRim = Color(0xFF141417)
    val WheelRimBorder = Color(0xFF2A2A30)
    val WheelSpoke = Color(0xFF1C1C21)
    val WheelCenter = Color(0xFF1550C0)
    val WheelCenterDark = Color(0xFF0E3888)

    /* Metallic pedal palette — 3D bevel/ridge look from the reference image */
    val PedalLight = Color(0xFF5E5E68)   // highlight edge / ridge top
    val PedalMid = Color(0xFF25252A)     // main metallic body
    val PedalDark = Color(0xFF121215)    // shadow edge / ridge bottom
    val PedalBg = Color(0xFF16161A)      // pedal background behind ridges
    val PedalBorder = Color(0xFF3A3A44)  // outer border
    val PedalSlotDark = Color(0xFF08080A) // recessed grip slots
    val PedalSlotLight = Color(0xFF42424A) // slot rim highlight

    fun rttDisplay(ms: Long): String =
        if (ms in 0..999) ms.toString().padStart(3, '0') + "ms" else "---ms"

    /** Fixed-place digits so the HUD never reflows (D-016). */
    fun lossDisplay(percent: Int): String =
        percent.coerceIn(0, 99).toString().padStart(2, '0') + "%"

    fun steerDisplay(value: Float): String =
        "%+04d".format((value.coerceIn(-1f, 1f) * 100).toInt())

    /** Median RTT above this reads degraded — FR-1.9's budget for a 120 Hz deck. */
    const val RTT_WARN_MS = 20L
    const val LOSS_WARN_PERCENT = 5

    /**
     * The deck's link readout: one fixed-place line plus the colour that states it.
     *
     * Pure, so the state grammar is unit-testable and lives in exactly one place — the MENU sheet
     * used to be the only surface that said anything about the link, and it said it differently.
     * Indigo is reserved for active controls and amber for warnings (DESIGN.md), so a healthy link
     * reads green, a live-but-degraded one amber, and no link at all red.
     */
    fun linkReadout(state: ConnState, transport: String, rttMs: Long, lossPercent: Int): Pair<String, Color> =
        when (state) {
            ConnState.Connected -> {
                val degraded = rttMs < 0 || rttMs > RTT_WARN_MS || lossPercent >= LOSS_WARN_PERCENT
                "$transport  ${rttDisplay(rttMs)}  LOSS ${lossDisplay(lossPercent)}" to
                    if (degraded) Amber else SignalGreen
            }
            ConnState.Reconnecting -> "RECONNECTING" to Amber
            ConnState.Connecting -> "CONNECTING" to Amber
            ConnState.Rejected -> "PAIRING REJECTED" to SignalRed
            ConnState.Idle -> "NO LINK" to SignalRed
        }

    /**
     * Why the last connect attempt failed, in the words that name the fix.
     *
     * The server already distinguishes a wrong PIN from a full lobby from a protocol mismatch, and
     * sends the reason in the REJECT frame. Collapsing all of it into "connection failed" hid the one
     * cause the user can actually act on.
     */
    fun failureText(state: ConnState, rejectReason: Int): String =
        if (state != ConnState.Rejected) {
            "NO REPLY"
        } else when (rejectReason) {
            Proto.REJECT_WRONG_PIN -> "WRONG PIN"
            Proto.REJECT_LOBBY_FULL -> "PC FULL: 4 PADS"
            Proto.REJECT_VERSION_MISMATCH -> "UPDATE PC SERVER"
            else -> "REJECTED BY PC"
        }
}
