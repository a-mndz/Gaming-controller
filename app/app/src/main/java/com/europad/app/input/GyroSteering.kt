package com.europad.app.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread

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
 *
 * ## Latency
 *
 * Three things used to sit between a wrist movement and a UDP frame, and all three are gone:
 * SENSOR_DELAY_GAME asks for ~20 ms sampling (and permits batching on top), delivery defaulted to the
 * **main thread** where it queued behind Compose, and the deck then *polled* the value every 8 ms.
 * Now the rate is requested explicitly with batching disabled, samples land on a private
 * [HandlerThread], and each one is pushed straight to [onSteer] — the sender wakes on the change
 * instead of discovering it on the next tick.
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

    /** Lock-to-lock tilt, degrees. Smaller is more sensitive. */
    @Volatile var rangeDeg = 180f

    /** Fraction of travel around centre that reads as straight ahead. */
    @Volatile var deadzone = 0.05f

    /**
     * Precision curve, 0 (linear) to 1 (very soft near centre). See [GyroMath.applyCurve]: this is
     * what makes holding a lane possible without giving up full lock.
     */
    @Volatile var curve = 0.35f

    /**
     * Output smoothing as a time constant in milliseconds. 0 is raw and instant; ~25 ms is damped but
     * still tight; past ~60 ms it starts to feel like lag. Specified in time, not as a per-sample
     * weight, so it means the same thing whatever rate the sensor actually delivers.
     */
    @Volatile var smoothingMs = 22f

    /**
     * Called on the sensor thread for every accepted sample, with the steering value in -1..1.
     *
     * Deliberately *not* a Compose state write: the axis path must not wait for, or run on, the UI
     * thread. Set this to push straight into the frame; sample [currentSteer] separately for anything
     * that only needs to be redrawn.
     */
    @Volatile var onSteer: ((Float) -> Unit)? = null

    private companion object {
        /**
         * 5 ms (200 Hz) requested, batching explicitly disabled. This is a *hint* — the driver rounds
         * to what it supports — but asking for 200 Hz and getting 100 still halves the old 50 Hz
         * sampling delay. maxReportLatencyUs = 0 is the important half: a batching sensor hub is
         * allowed to hold samples for tens of milliseconds and deliver them in a burst, which is
         * indistinguishable from lag no matter how fast the sampling is.
         */
        const val SAMPLING_PERIOD_US = 5_000
        const val MAX_REPORT_LATENCY_US = 0

        /** Vector low-pass time constants. Gravity is pre-fused; raw accelerometer needs far more. */
        const val VECTOR_TAU_GRAVITY_MS = 28f
        const val VECTOR_TAU_ACCEL_MS = 110f

        /** How fast steering bleeds back to centre once the phone is laid flat (~1 s to settle). */
        const val FLAT_RECOVER_TAU_MS = 300f

        /** Guard against a stale timestamp gap after a pause producing one huge filter step. */
        const val MAX_DT_MS = 100f
    }

    // Low-passed gravity vector, device frame.
    private var fx = 0f
    private var fy = 0f
    private var fz = 0f
    private var haveVector = false

    private var haveAngle = false
    private var lastRawAngle = 0f
    private var lastEventNs = 0L

    @Volatile private var unwrapped = 0f
    @Volatile private var centerAngle = 0f
    @Volatile private var smooth = 0f
    @Volatile private var flat = false

    private val gripGate = GripGate()

    private var sensorThread: HandlerThread? = null

    /** True while the phone is too close to horizontal for tilt to mean anything. */
    val isFlat: Boolean get() = flat

    /** True when the device has no fused gravity sensor and the noisier fallback is in use. */
    val isFallbackSensor: Boolean get() = usingRawAccelerometer

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val v = event.values
            if (v.size < 3) return

            // Real interval between samples, from the event clock rather than an assumed rate.
            val dtMs = if (lastEventNs == 0L) 0f
            else ((event.timestamp - lastEventNs) / 1_000_000.0).toFloat().coerceIn(0f, MAX_DT_MS)
            lastEventNs = event.timestamp

            val tau = if (usingRawAccelerometer) VECTOR_TAU_ACCEL_MS else VECTOR_TAU_GRAVITY_MS
            val a = GyroMath.emaAlpha(dtMs, tau)
            if (!haveVector) {
                fx = v[0]; fy = v[1]; fz = v[2]
                haveVector = true
            } else {
                fx += a * (v[0] - fx)
                fy += a * (v[1] - fy)
                fz += a * (v[2] - fz)
            }

            // Hysteresis gate: a grip hovering near the flat threshold used to flip flat on single
            // samples, and every flip dropped the declared centre — the wheel let go mid-corner.
            // A brief dip now freezes the axis (what a held wheel does); only a dip that outlasts
            // the dwell is a phone set down, and only then does the centre drop and the axis bleed.
            when (gripGate.sample(GyroMath.gripRatio(fx, fy, fz), dtMs)) {
                GripGate.State.Dip -> return
                GripGate.State.Flat -> {
                    // Flat on a table or a desk mount. Do not freeze at the last angle: a phone set
                    // down mid-corner would hold that lock indefinitely. Bleed to centre instead,
                    // and drop the angle reference so picking it back up re-seeds cleanly.
                    flat = true
                    haveAngle = false
                    smooth = GyroMath.complementaryStep(smooth, 0f, GyroMath.emaAlpha(dtMs, FLAT_RECOVER_TAU_MS))
                    onSteer?.invoke(smooth)
                    return
                }
                GripGate.State.Usable -> flat = false
            }

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
            val linear = GyroMath.steerFromDelta(delta, rangeRad, deadzone)
            val target = GyroMath.applyCurve(linear, curve)
            smooth = GyroMath.complementaryStep(smooth, target, GyroMath.emaAlpha(dtMs, smoothingMs))
            onSteer?.invoke(smooth)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start(): Boolean {
        val s = sensor ?: return false
        stopThread()
        reset()
        // Own thread: the default is the main looper, so every sample used to queue behind whatever
        // Compose was doing — the one place in the pipeline guaranteed to be busy during a drive.
        val thread = HandlerThread("europad-gyro", android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
        thread.start()
        sensorThread = thread
        return sensors.registerListener(
            listener,
            s,
            SAMPLING_PERIOD_US,
            MAX_REPORT_LATENCY_US,
            Handler(thread.looper),
        )
    }

    fun stop() {
        sensors.unregisterListener(listener)
        stopThread()
        reset()
    }

    private fun stopThread() {
        sensorThread?.quitSafely()
        sensorThread = null
    }

    /** Declare the current grip as straight-ahead. */
    fun recenter() {
        centerAngle = unwrapped
        smooth = 0f
        onSteer?.invoke(0f)
    }

    fun currentSteer(): Float = smooth

    private fun reset() {
        haveVector = false
        haveAngle = false
        lastRawAngle = 0f
        lastEventNs = 0L
        unwrapped = 0f
        centerAngle = 0f
        smooth = 0f
        flat = false
        gripGate.reset()
    }
}
