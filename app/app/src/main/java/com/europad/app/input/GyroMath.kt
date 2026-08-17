package com.europad.app.input

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sign

object GyroMath {

    private val TWO_PI = (2.0 * PI).toFloat()

    fun yawFromQuaternion(x: Float, y: Float, z: Float, w: Float): Float {
        return atan2(2f * (w * z + x * y), 1f - 2f * (y * y + z * z))
    }

    /** Pitch for gyro aiming (T3.2): positive when the phone's top edge tilts toward the user. */
    fun pitchFromQuaternion(x: Float, y: Float, z: Float, w: Float): Float {
        val sinp = (2f * (w * x - z * y)).coerceIn(-1f, 1f)
        return kotlin.math.asin(sinp)
    }

    fun shortestDelta(from: Float, to: Float): Float {
        var d = (to - from) % TWO_PI
        if (d > PI.toFloat()) d -= TWO_PI
        if (d < -PI.toFloat()) d += TWO_PI
        return d
    }

    fun steerFromDelta(deltaRad: Float, rangeRad: Float, deadzoneFraction: Float): Float {
        val half = max(rangeRad / 2f, 0.0001f)
        val clamped = deltaRad.coerceIn(-half, half)
        var steer = clamped / half
        val dz = deadzoneFraction.coerceIn(0f, 0.99f)
        steer = if (abs(steer) < dz) {
            0f
        } else {
            (steer - sign(steer) * dz) / (1f - dz)
        }
        return steer.coerceIn(-1f, 1f)
    }

    fun complementaryStep(smooth: Float, measured: Float, alpha: Float): Float {
        return smooth + alpha.coerceIn(0f, 1f) * (measured - smooth)
    }
}
