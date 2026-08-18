package com.europad.app.input

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sqrt

object GyroMath {

    private val TWO_PI = (2.0 * PI).toFloat()

    /**
     * Wheel angle from the gravity vector's projection onto the screen plane, in radians.
     *
     * This replaced yaw-from-rotation-vector steering. Yaw was wrong three ways at once: with no
     * magnetometer (TYPE_GAME_ROTATION_VECTOR) it drifts without bound, it is numerically degenerate
     * exactly in the grip a driver uses (screen near vertical), and its sign ran backwards — rotating
     * the phone left produced a positive yaw and steered the truck right.
     *
     * Gravity has none of those problems: it is an absolute reference the fusion never loses, and it
     * is at its strongest in the screen plane precisely when the phone is held upright like a wheel.
     * [gx] and [gy] are the device-frame x/y components of TYPE_GRAVITY.
     *
     * Sign: device +z points out of the screen, so a positive (counter-clockwise, as the driver sees
     * it) roll of φ moves a fixed world vector to −φ in device coordinates. The returned angle
     * therefore *decreases* as the wheel turns left, which is exactly the sign the steering axis
     * wants — negative is left.
     */
    fun rollFromGravity(gx: Float, gy: Float): Float = atan2(gy, gx)

    /** Length of the gravity component that lies in the screen plane. */
    fun inPlaneMagnitude(gx: Float, gy: Float): Float = sqrt(gx * gx + gy * gy)

    /**
     * Is the phone held steeply enough for [rollFromGravity] to mean anything?
     *
     * Laid flat, gravity is almost entirely along the screen normal and the in-plane part is sensor
     * noise, so the angle spins freely. Comparing the in-plane part against the total (rather than a
     * fixed m/s² figure) keeps this independent of sensor scaling. The 0.26 ratio trips at about 75°
     * of lean-back, so a relaxed 30-45° driving posture is comfortably inside.
     */
    fun isGripUsable(gx: Float, gy: Float, gz: Float, minRatio: Float = 0.26f): Boolean {
        val total = sqrt(gx * gx + gy * gy + gz * gz)
        if (total < 1e-3f) return false
        return inPlaneMagnitude(gx, gy) / total >= minRatio
    }

    /**
     * Continuous angle accumulator. [rollFromGravity] wraps at ±π, and a wrap mid-turn would flip the
     * steering axis from full left to full right in one sample. Adding the *shortest* step to a
     * running total unwraps that seam. Each step is the difference of two absolute measurements, so
     * unlike integrating a gyro rate this cannot accumulate drift — it is just `raw + 2πk`.
     */
    fun unwrapStep(prevUnwrapped: Float, prevRaw: Float, raw: Float): Float =
        prevUnwrapped + shortestDelta(prevRaw, raw)

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
