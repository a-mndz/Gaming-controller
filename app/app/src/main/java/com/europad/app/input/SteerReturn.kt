package com.europad.app.input

import kotlin.math.abs

/**
 * How the steering axis travels home when the finger leaves the wheel.
 *
 * ## Why this is not just "set the axis to 0"
 *
 * The app already snapped the axis to exactly 0 on release, and the Windows controller panel
 * confirmed it — yet in ETS2 the truck kept its turn. That is not a lost packet: ETS2 does not steer
 * the truck *to* the axis position, it moves an internal virtual wheel *towards* it at a limited rate
 * (that is what the game's steering sensitivity and non-linearity settings act on). Collapse the axis
 * to 0 in one frame and the game's wheel is left wherever the rate limiter had got to, with no
 * displacement left to pull it the rest of the way. The axis is centred, the truck is not.
 *
 * A human never releases a wheel like that. They roll it back through every intermediate position and
 * — critically — carry it a little past centre before letting it settle. This class reproduces that:
 *
 *  1. **Sweep** through the intermediate angles slowly enough for the game's rate limiter to track,
 *     which is the "same effect as the finger moving" the axis back by hand.
 *  2. **Counter-steer** a small amount to the opposite side ([counterFraction] of the released angle),
 *     which gives the game's virtual wheel a displacement pointing *through* centre and cancels the
 *     deflection the rate limiter was still holding.
 *  3. **Settle** from that counter-steer to exactly 0.
 *
 * All of it is pure arithmetic over elapsed time so it can be unit-tested on the JVM; the composable
 * just samples [valueAt] and streams the result.
 */
object SteerReturn {

    /** Fraction of the total return spent on the outward sweep; the rest settles the counter-steer. */
    private const val SWEEP_SHARE = 0.72f

    /** Default opposite-side overshoot, as a fraction of the angle the finger let go of. */
    const val DEFAULT_COUNTER_FRACTION = 0.10f

    /** Slowest and fastest returns offered in the UI, in milliseconds. */
    const val MIN_MS = 120
    const val MAX_MS = 1200

    /**
     * Axis value [elapsedMs] into a return that began at [from], for a return lasting [durationMs].
     *
     * Returns exactly 0 at and after [durationMs]. [counterFraction] 0 disables the counter-steer and
     * gives a plain eased sweep to centre.
     */
    fun valueAt(from: Float, elapsedMs: Long, durationMs: Int, counterFraction: Float = DEFAULT_COUNTER_FRACTION): Float {
        val start = from.coerceIn(-1f, 1f)
        val total = durationMs.coerceIn(MIN_MS, MAX_MS)
        if (elapsedMs <= 0L) return start
        if (elapsedMs >= total) return 0f
        if (start == 0f) return 0f

        val counter = -start * counterFraction.coerceIn(0f, 0.4f)
        val sweepMs = total * SWEEP_SHARE
        val t = elapsedMs.toFloat()

        return if (t <= sweepMs) {
            // Ease-out: quickest while the wheel is far from centre (where the driver expects the
            // spring to be strongest) and gentle as it arrives, so the game's rate limiter keeps up
            // through the part that actually matters.
            lerp(start, counter, easeOut(t / sweepMs))
        } else {
            lerp(counter, 0f, easeInOut((t - sweepMs) / (total - sweepMs)))
        }
    }

    /**
     * How long the return should take for a release at [from], given the user's [durationMs] setting.
     *
     * A flick of 5° should not take as long to unwind as a full lock: scaling with the released angle
     * keeps the *rate* consistent, which is both what a real wheel does and what stops small
     * corrections feeling sticky. Floored so a tiny release still sweeps rather than snaps.
     */
    fun durationFor(from: Float, durationMs: Int): Int {
        val span = abs(from).coerceIn(0f, 1f)
        val full = durationMs.coerceIn(MIN_MS, MAX_MS)
        return (full * (0.35f + 0.65f * span)).toInt().coerceAtLeast(MIN_MS)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

    /** 1 - (1-t)², fast then slow. */
    private fun easeOut(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        val inv = 1f - x
        return 1f - inv * inv
    }

    /** Smoothstep: no velocity discontinuity at either end of the settle. */
    private fun easeInOut(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}
