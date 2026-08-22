package com.europad.app.input

import kotlin.math.PI

/**
 * Pure math for one touch-wheel drag event: the angle about the wheel's pivot, the clamped wheel
 * angle, and the steering axis it implies. Extracted from the composable so it runs on the JVM.
 *
 * The dead radius is the fix for the hub-crossing teleport: atan2 of a touch point a few pixels
 * from the pivot is pure jitter, and dragging across the exact centre flipped the angle by up to
 * 180° in one event. Inside the gate [step] reports null — the caller keeps both the last angle
 * and the wheel position untouched — and on emerging from the gate the next delta is the shortest
 * path from the *entry* angle, which is the true swept arc for any crossing faster than half a
 * turn.
 */
object WheelDrag {
    /** Dead zone as a fraction of the wheel radius; the hub graphic reaches 0.35, spokes beyond. */
    const val DEAD_RADIUS_FRACTION = 0.18f

    data class Step(val angleDeg: Float, val steer: Float, val lastAngleRad: Double)

    fun deadRadiusPx(minDimensionPx: Float): Float = minDimensionPx / 2f * DEAD_RADIUS_FRACTION

    /**
     * Advance the wheel by the rotation between two finger angles. Null when the finger is inside
     * the dead radius: apply nothing, and keep [lastAngleRad] where it was so the exit sweep
     * measures the true travel across the hub.
     */
    fun step(
        angleDeg: Float,
        maxTurnDeg: Float,
        lastAngleRad: Double,
        curAngleRad: Double,
        radiusPx: Float,
        deadRadiusPx: Float,
    ): Step? {
        if (radiusPx < deadRadiusPx) return null

        var diff = curAngleRad - lastAngleRad
        while (diff > PI) diff -= 2 * PI
        while (diff < -PI) diff += 2 * PI
        val diffDeg = (diff * 180.0 / PI).toFloat()

        val newAngle = (angleDeg + diffDeg).coerceIn(-maxTurnDeg, maxTurnDeg)
        return Step(newAngle, (newAngle / maxTurnDeg).coerceIn(-1f, 1f), curAngleRad)
    }
}
