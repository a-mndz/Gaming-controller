package com.europad.app.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Tilt steering: hold the phone upright in landscape and rotate it like a steering wheel.
 *
 * Built on the gravity vector, not on compass yaw. The previous implementation read
 * TYPE_GAME_ROTATION_VECTOR and extracted yaw, which failed in three separate ways in the driving
 * grip: that sensor has no magnetometer so its yaw drifts without bound (hence the old drift-trim
 * hack, which then fought the driver's own inputs), yaw is degenerate when the screen is near
 * vertical, and the sign was inverted so rotating left steered right.
 *
 * Gravity is an absolute reference that fusion never loses, and its in-plane component is *largest*
 * exactly when the phone is held upright — the same posture that broke yaw. No drift, no trim, no
 * calibration beyond an optional [recenter] to declare a comfortable neutral grip.
 */
class GyroSteering(context: Context) {

    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /**
     * TYPE_GRAVITY is fused (accelerometer + gyro) so hand tremor and road-bump acceleration are
     * already rejected. Raw accelerometer is the fallback and needs much heavier smoothing, because
     * every jolt of the phone reads as a change in "down".
     */
    private val gravitySensor: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val sensor: Sensor? = gravitySensor ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val usingRawAccelerometer = gravitySensor == null

    var rangeDeg = 180f
    var deadzone = 0.05f

    /** Output smoothing per sample. The wheel should feel damped, not laggy. */
    var alpha = 0.35f

    private companion object {
        const val VECTOR_ALPHA_GRAVITY = 0.55f
        const val VECTOR_ALPHA_ACCEL = 0.15f

        /** How fast steering bleeds back to centre once the phone is laid flat. ~1 s at 50 Hz. */
        const val FLAT_RECOVER_ALPHA = 0.06f
    }

    // Low-passed gravity vector, device frame.
    private var fx = 0f
    private var fy = 0f
    private var fz = 0f
    private var haveVector = false

    private var haveAngle = false
    private var lastRawAngle = 0f

    @Volatile private var unwrapped = 0f
    @Volatile private var centerAngle = 0f
    @Volatile private var smooth = 0f
    @Volatile private var flat = false

    /** True while the phone is too close to horizontal for tilt to mean anything. */
    val isFlat: Boolean get() = flat

    /** True when the device has no fused gravity sensor and the noisier fallback is in use. */
    val isFallbackSensor: Boolean get() = usingRawAccelerometer

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val v = event.values
            if (v.size < 3) return

            val a = if (usingRawAccelerometer) VECTOR_ALPHA_ACCEL else VECTOR_ALPHA_GRAVITY
            if (!haveVector) {
                fx = v[0]; fy = v[1]; fz = v[2]
                haveVector = true
            } else {
                fx += a * (v[0] - fx)
                fy += a * (v[1] - fy)
                fz += a * (v[2] - fz)
            }

            if (!GyroMath.isGripUsable(fx, fy, fz)) {
                // Flat on a table or a desk mount. Do not freeze at the last angle: a phone set down
                // mid-corner would hold that lock indefinitely. Bleed to centre instead, and drop the
                // angle reference so picking it back up re-seeds cleanly rather than jumping.
                flat = true
                haveAngle = false
                smooth = GyroMath.complementaryStep(smooth, 0f, FLAT_RECOVER_ALPHA)
                return
            }
            flat = false

            val raw = GyroMath.rollFromGravity(fx, fy)
            if (!haveAngle) {
                // First usable sample of this grip: adopt it as neutral so the wheel starts centred
                // wherever the driver happens to be holding the phone.
                lastRawAngle = raw
                unwrapped = raw
                centerAngle = raw
                haveAngle = true
            } else {
                unwrapped = GyroMath.unwrapStep(unwrapped, lastRawAngle, raw)
                lastRawAngle = raw
            }

            val delta = unwrapped - centerAngle
            val rangeRad = Math.toRadians(rangeDeg.toDouble()).toFloat()
            val target = GyroMath.steerFromDelta(delta, rangeRad, deadzone)
            smooth = GyroMath.complementaryStep(smooth, target, alpha)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start(): Boolean {
        val s = sensor ?: return false
        reset()
        sensors.registerListener(listener, s, SensorManager.SENSOR_DELAY_GAME)
        return true
    }

    fun stop() {
        sensors.unregisterListener(listener)
        reset()
    }

    /** Declare the current grip as straight-ahead. */
    fun recenter() {
        centerAngle = unwrapped
        smooth = 0f
    }

    fun currentSteer(): Float = smooth

    private fun reset() {
        haveVector = false
        haveAngle = false
        lastRawAngle = 0f
        unwrapped = 0f
        centerAngle = 0f
        smooth = 0f
        flat = false
    }
}
