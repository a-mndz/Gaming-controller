package com.europad.app.ui

import androidx.compose.ui.graphics.Color

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
}
