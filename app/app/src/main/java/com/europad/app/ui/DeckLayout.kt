package com.europad.app.ui

/**
 * Pure layout math for the minimal ETS2 deck. All coordinates are fractions of the canvas:
 * `cx`/`w` in units of width, `cy`/`h` in units of height, origin top-left. Elements that are
 * specified in "square of height" (utility buttons, camera, wheel) take an aspect ratio to
 * convert their height-based size into a width fraction. No Compose types in here so the
 * numbers can be unit-tested on the JVM.
 */
data class DeckRect(val cx: Float, val cy: Float, val w: Float, val h: Float) {
    val left get() = cx - w / 2f
    val right get() = cx + w / 2f
    val top get() = cy - h / 2f
    val bottom get() = cy + h / 2f
}

object DeckLayout {
    const val ASPECT = 16f / 9f
    const val MARGIN = 0.03f

    const val TOP_ROW_Y = 0.10f
    const val UTIL_SIDE = 0.13f
    private val UTIL_X = floatArrayOf(0.06f, 0.14f, 0.22f)

    const val GEAR_CX = 0.50f
    const val GEAR_CY = 0.09f
    const val GEAR_SEG_W = 0.055f
    const val GEAR_H = 0.085f

    private val TOP_RIGHT_X = floatArrayOf(0.72f, 0.83f, 0.93f)

    const val LINK_CX = 0.50f
    const val LINK_CY = 0.155f
    const val LINK_W = 0.30f
    const val LINK_H = 0.025f

    const val ARROWS_CX = 0.10f
    const val ARROWS_CY = 0.26f
    const val ARROWS_W = 0.11f
    const val ARROWS_H = 0.085f

    const val CAMERA_CX = 0.93f
    const val CAMERA_CY = 0.26f
    const val CAMERA_SIDE = 0.13f

    const val WHEEL_CX = 0.20f
    const val WHEEL_CY = 0.65f
    const val WHEEL_DIAM = 0.50f

    const val PEDAL_BOTTOM = 0.86f
    const val BRAKE_CX = 0.68f
    const val BRAKE_W = 0.11f
    const val BRAKE_H = 0.32f
    const val ACCEL_CX = 0.85f
    const val ACCEL_W = 0.105f
    const val ACCEL_H = 0.46f

    fun utilBtn(i: Int, aspect: Float = ASPECT) =
        DeckRect(UTIL_X[i], TOP_ROW_Y, (UTIL_SIDE * 1.15f) / aspect, UTIL_SIDE)

    fun topRightBtn(i: Int, aspect: Float = ASPECT) =
        if (i == 0) {
            // Handbrake button needs wider text width for "HANDBRAKE"
            DeckRect(TOP_RIGHT_X[i], TOP_ROW_Y, (UTIL_SIDE * 1.50f) / aspect, UTIL_SIDE)
        } else {
            DeckRect(TOP_RIGHT_X[i], TOP_ROW_Y, (UTIL_SIDE * 1.15f) / aspect, UTIL_SIDE)
        }

    fun gearSel() = DeckRect(GEAR_CX, GEAR_CY, GEAR_SEG_W * 3f, GEAR_H)

    fun linkStrip() = DeckRect(LINK_CX, LINK_CY, LINK_W, LINK_H)

    fun arrows() = DeckRect(ARROWS_CX, ARROWS_CY, ARROWS_W, ARROWS_H)

    fun camera(aspect: Float = ASPECT) =
        DeckRect(CAMERA_CX, CAMERA_CY, (CAMERA_SIDE * 1.15f) / aspect, CAMERA_SIDE)

    fun wheel(aspect: Float = ASPECT) =
        DeckRect(WHEEL_CX, WHEEL_CY, WHEEL_DIAM / aspect, WHEEL_DIAM)

    /** Bottom-aligned with [accel]; leaves room below for the label before the 3% margin. */
    fun brake() = DeckRect(BRAKE_CX, PEDAL_BOTTOM - BRAKE_H / 2f, BRAKE_W, BRAKE_H)

    fun accel() = DeckRect(ACCEL_CX, PEDAL_BOTTOM - ACCEL_H / 2f, ACCEL_W, ACCEL_H)

    /** Gyroscope Mode: Accelerator on Left, Brake on Right (no steering wheel) */
    fun gyroAccel() = DeckRect(0.18f, PEDAL_BOTTOM - ACCEL_H / 2f, 0.13f, ACCEL_H)

    fun gyroBrake() = DeckRect(0.82f, PEDAL_BOTTOM - ACCEL_H / 2f, 0.13f, ACCEL_H)
}
