package com.europad.app.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sqrt

class GyroSteering(context: Context) {

    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    var rangeDeg = 360f
    var deadzone = 0.05f
    var alpha = 0.35f

    /**
     * Drift trim (T2.5): while the wheel has sat inside the idle band for longer than
     * [trimIdleMs], the center reference creeps toward the actual yaw so sensor drift and a
     * slightly-tilted grip don't accumulate into a permanent steer offset.
     */
    var driftTrim = true
    private var idleSinceMs = 0L
    private var lastEventMs = 0L

    private val TRIM_IDLE_MS = 1500L
    private val TRIM_RATE_PER_S = 0.15f // rad of center shift per second of idle
    private val IDLE_BAND = 0.02f

    private var centerYaw = 0f
    private var latestYaw = 0f

    @Volatile
    private var smooth = 0f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val v = event.values
            if (v.size < 3) return
            val now = System.nanoTime() / 1_000_000L
            val x = v[0]
            val y = v[1]
            val z = v[2]
            val w = if (v.size > 3) {
                v[3]
            } else {
                sqrt(max(0f, 1f - x * x - y * y - z * z))
            }
            val yaw = GyroMath.yawFromQuaternion(x, y, z, w)
            latestYaw = yaw
            val delta = GyroMath.shortestDelta(centerYaw, yaw)
            if (driftTrim) {
                val dt = if (lastEventMs == 0L) 0f else (now - lastEventMs) / 1000f
                lastEventMs = now
                if (abs(delta) < IDLE_BAND) {
                    if (idleSinceMs == 0L) idleSinceMs = now
                    else if (now - idleSinceMs > TRIM_IDLE_MS) {
                        val step = (TRIM_RATE_PER_S * dt.coerceAtMost(0.1f)).coerceAtMost(abs(delta))
                        centerYaw += sign(delta) * step
                    }
                } else {
                    idleSinceMs = 0L
                }
            }
            val target = GyroMath.steerFromDelta(delta, Math.toRadians(rangeDeg.toDouble()).toFloat(), deadzone)
            smooth = GyroMath.complementaryStep(smooth, target, alpha)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start(): Boolean {
        val s = sensor ?: return false
        lastEventMs = 0L
        idleSinceMs = 0L
        sensors.registerListener(listener, s, SensorManager.SENSOR_DELAY_GAME)
        return true
    }

    fun stop() {
        sensors.unregisterListener(listener)
        smooth = 0f
        lastEventMs = 0L
        idleSinceMs = 0L
    }

    fun recenter() {
        centerYaw = latestYaw
        smooth = 0f
        idleSinceMs = 0L
    }

    fun currentSteer(): Float = smooth
}
