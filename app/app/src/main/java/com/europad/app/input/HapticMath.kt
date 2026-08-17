package com.europad.app.input

import kotlin.math.roundToLong

/**
 * Pure rumble→vibration mapping (D-015). The server's RumbleRelay coalesces ViGEm callbacks to
 * ~33ms frames, so each arriving frame becomes one chunk here; ERM phone motors cannot follow
 * faster modulation, keeping the waveform period in the 30–40ms band.
 */
object HapticMath {
    const val WATCHDOG_MS = 500L
    const val BURST_MS = 50L
    const val DUTY_PERIOD_MS = 40L
    const val MIN_ON_MS = 6L

    /** Large motor dominates (body shake), small motor adds buzz; both 0..255 → 0f..1f. */
    fun amplitude(large: Int, small: Int, intensity: Float): Float {
        if (large <= 0 && small <= 0) return 0f
        val l = large.coerceIn(0, 255) / 255f
        val s = small.coerceIn(0, 255) / 255f
        val combined = (0.75f * l + 0.45f * s).coerceAtMost(1f)
        return combined * intensity.coerceIn(0f, 1f)
    }

    /**
     * [on, off] timings for devices without `hasAmplitudeControl()`: the duty cycle carries the
     * intensity instead of the amplitude channel. Null when there is nothing to play.
     */
    fun dutyTimings(amp: Float): LongArray? {
        if (amp <= 0f) return null
        val on = (DUTY_PERIOD_MS * amp.coerceIn(0f, 1f)).roundToLong().coerceIn(MIN_ON_MS, DUTY_PERIOD_MS)
        return longArrayOf(on, DUTY_PERIOD_MS - on)
    }
}
