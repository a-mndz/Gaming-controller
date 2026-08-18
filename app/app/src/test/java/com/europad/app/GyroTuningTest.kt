package com.europad.app

import com.europad.app.input.GyroMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp

/**
 * The two knobs behind "I can't get a precise level" with tilt steering: the precision curve, and the
 * rate-independent smoothing that lets the sensor run at 200 Hz without changing how the wheel feels.
 */
class GyroTuningTest {

    // ---- applyCurve ---------------------------------------------------------------------------

    @Test
    fun `curve zero is the identity`() {
        for (x in listOf(-1f, -0.6f, -0.05f, 0f, 0.05f, 0.6f, 1f)) {
            assertEquals(x, GyroMath.applyCurve(x, 0f), 1e-6f)
        }
    }

    @Test
    fun `curve keeps centre and full lock reachable`() {
        // The whole point of shaping instead of widening the range: full lock still arrives at the same
        // tilt, so precision near centre costs no travel at the extremes.
        for (c in listOf(0f, 0.2f, 0.5f, 0.8f, 1f)) {
            assertEquals(0f, GyroMath.applyCurve(0f, c), 1e-6f)
            assertEquals(1f, GyroMath.applyCurve(1f, c), 1e-5f)
            assertEquals(-1f, GyroMath.applyCurve(-1f, c), 1e-5f)
        }
    }

    @Test
    fun `curve softens the middle of the travel`() {
        val half = GyroMath.applyCurve(0.5f, 0.5f)
        assertTrue("half tilt should give less than half lock, got $half", half < 0.5f)
        assertEquals(0.25f, half, 1e-5f)                              // 0.5 ^ (1 + 2*0.5) = 0.25
        assertEquals(0.125f, GyroMath.applyCurve(0.5f, 1f), 1e-5f)    // 0.5 ^ 3
    }

    @Test
    fun `stronger curve is always gentler near centre`() {
        val x = 0.35f
        val gentle = GyroMath.applyCurve(x, 0.2f)
        val strong = GyroMath.applyCurve(x, 0.9f)
        assertTrue("$strong should be nearer centre than $gentle", strong < gentle)
        assertTrue("both stay inside the linear value", gentle < x)
    }

    @Test
    fun `curve preserves direction`() {
        assertEquals(-GyroMath.applyCurve(0.7f, 0.6f), GyroMath.applyCurve(-0.7f, 0.6f), 1e-6f)
        assertTrue(GyroMath.applyCurve(-0.2f, 1f) < 0f)
        assertTrue(GyroMath.applyCurve(0.2f, 1f) > 0f)
    }

    @Test
    fun `curve is monotonic so the wheel never reverses`() {
        var prev = -1.0001f
        for (i in -100..100) {
            val out = GyroMath.applyCurve(i / 100f, 0.7f)
            assertTrue("output went backwards at $i: $prev -> $out", out >= prev)
            prev = out
        }
    }

    @Test
    fun `curve clamps out of range input and settings`() {
        assertEquals(1f, GyroMath.applyCurve(3f, 0.5f), 1e-5f)
        assertEquals(-1f, GyroMath.applyCurve(-3f, 0.5f), 1e-5f)
        // Settings outside 0..1 behave like the nearest legal curve rather than exploding.
        assertEquals(GyroMath.applyCurve(0.4f, 1f), GyroMath.applyCurve(0.4f, 5f), 1e-6f)
        assertEquals(0.4f, GyroMath.applyCurve(0.4f, -2f), 1e-6f)
    }

    // ---- emaAlpha ----------------------------------------------------------------------------

    @Test
    fun `alpha degenerates safely`() {
        assertEquals(1f, GyroMath.emaAlpha(5f, 0f), 0f)      // smoothing off: follow exactly
        assertEquals(1f, GyroMath.emaAlpha(5f, -1f), 0f)
        assertEquals(0f, GyroMath.emaAlpha(0f, 25f), 0f)     // no time passed: no blend
        assertEquals(0f, GyroMath.emaAlpha(-3f, 25f), 0f)
    }

    @Test
    fun `alpha reaches one minus one over e after one time constant`() {
        assertEquals(1f - exp(-1f), GyroMath.emaAlpha(25f, 25f), 1e-6f)
    }

    @Test
    fun `alpha grows with the gap and stays bounded`() {
        var prev = 0f
        // Stops at 150 ms (5 time constants): past that 1 - exp(-dt/tau) is 1.0 in float32 and
        // "strictly growing" stops being a meaningful thing to assert.
        for (dt in 1..150) {
            val a = GyroMath.emaAlpha(dt.toFloat(), 30f)
            assertTrue("alpha $a out of range at dt=$dt", a in 0f..1f)
            assertTrue("alpha should grow with dt at $dt", a > prev)
            prev = a
        }
    }

    @Test
    fun `smoothing feels the same at any sample rate`() {
        // This is the property the whole time-constant rewrite exists for: raising the sensor from
        // 50 Hz to 200 Hz must not change the damping, only the resolution.
        val tau = 25f
        val totalMs = 100f

        fun settle(dtMs: Float): Float {
            var v = 0f
            var t = 0f
            while (t < totalMs - 1e-4f) {
                v = GyroMath.complementaryStep(v, 1f, GyroMath.emaAlpha(dtMs, tau))
                t += dtMs
            }
            return v
        }

        val slow = settle(20f)   // 50 Hz
        val fast = settle(5f)    // 200 Hz
        val ideal = 1f - exp(-totalMs / tau)

        assertEquals("50 Hz should match the analytic response", ideal, slow, 0.01f)
        assertEquals("200 Hz should match the analytic response", ideal, fast, 0.01f)
        assertTrue("rates must agree within 1%", abs(slow - fast) < 0.01f)
    }

    @Test
    fun `a stale gap does not blow past the measurement`() {
        // After a pause the caller clamps dt, but even an unclamped gap must not overshoot: alpha 1 is
        // the worst case and lands exactly on the measurement.
        val a = GyroMath.emaAlpha(10_000f, 25f)
        assertEquals(1f, a, 1e-6f)
        assertEquals(1f, GyroMath.complementaryStep(-1f, 1f, a), 1e-6f)
    }
}
