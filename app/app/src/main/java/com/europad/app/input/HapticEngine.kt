package com.europad.app.input

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.content.pm.ApplicationInfo
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Game rumble → phone vibration (D-015, T2.9b). A server rumble frame (~30/s) restarts the
 * vibration only when its amplitude genuinely changes; a 500ms watchdog cancels the motor if
 * frames stop arriving (game paused, relay died, or the rumble packet was lost).
 *
 * Devices with `hasAmplitudeControl()` get amplitude-mapped bursts; devices without it (mostly
 * ERM motors) fall back to duty-cycle bursts, since they cannot modulate amplitude anyway.
 * Cancelling and restarting on every ~33 ms frame is exactly wrong for those: an ERM mass needs
 * tens of ms to spin up, so a perpetual restart never reaches full deflection and the rumble
 * reads weak and choppy. The restart policy lives in [HapticMath.shouldRestart].
 */
class HapticEngine(context: Context) {

    private val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private val handler = Handler(Looper.getMainLooper())

    // Written from the reader thread, cleared by the watchdog on the main looper.
    @Volatile private var lastAmp: Float? = null

    private val watchdog = Runnable {
        try { vibrator.cancel() } catch (_: Exception) { }
        lastAmp = null
    }
    private val amplitudeCapable: Boolean by lazy { vibrator.hasAmplitudeControl() }

    /** User slider value 0f..1f; applied on every incoming frame. */
    @Volatile
    var intensity: Float = 1f

    fun onRumble(large: Int, small: Int) {
        if (debug) {
            android.util.Log.i(
                "EuroPadHaptic",
                "rumble large=$large small=$small t=${android.os.SystemClock.elapsedRealtime()}",
            )
        }
        handler.removeCallbacks(watchdog)
        val amp = HapticMath.amplitude(large, small, intensity)
        if (amp <= 0f) {
            lastAmp = null
            try { vibrator.cancel() } catch (_: Exception) { }
            return
        }
        handler.postDelayed(watchdog, HapticMath.WATCHDOG_MS)
        if (!HapticMath.shouldRestart(lastAmp, amp)) return // steady rumble: let the motor run
        try {
            vibrator.cancel() // replace the previous chunk — no build-up across 30/s frames
            if (amplitudeCapable) {
                val a = (amp * 255f).toInt().coerceIn(1, 255)
                vibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(HapticMath.BURST_MS), intArrayOf(a), -1),
                )
            } else {
                val timings = HapticMath.dutyTimings(amp) ?: return
                vibrator.vibrate(VibrationEffect.createWaveform(timings, 0))
            }
        } catch (_: Exception) {
            lastAmp = null
            return // vibrate can throw on some OEM stacks; the game must never die for haptics
        }
        lastAmp = amp
    }

    fun stop() {
        handler.removeCallbacks(watchdog)
        try { vibrator.cancel() } catch (_: Exception) { }
        lastAmp = null
    }
}
