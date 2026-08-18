package com.europad.app

import com.europad.app.input.GyroMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tilt steering, validated against simulated gravity readings.
 *
 * The rig below is the whole point of these tests: it derives what TYPE_GRAVITY *would* report for a
 * given grip and wheel rotation, independently of the production code, so the sign convention and the
 * lean-back tolerance are checked against physics rather than against themselves.
 *
 * Android frame: +x right, +y toward the top edge, +z out of the screen; the vector points *away*
 * from the Earth (flat on a table, screen up, reads +9.81 on z).
 */
class GyroTiltTest {

    private val g = 9.81f

    /** Device rotated counter-clockwise (as the driver sees it) by [phiRad] about the screen normal. */
    private fun roll(gx: Float, gy: Float, phiRad: Float): Pair<Float, Float> {
        // A frame rotating CCW by φ makes a fixed world vector's coordinates rotate CW by φ.
        val c = cos(phiRad)
        val s = sin(phiRad)
        return (gx * c + gy * s) to (-gx * s + gy * c)
    }

    private fun deg(d: Double) = Math.toRadians(d).toFloat()

    /** Landscape grip, phone's top edge to the left: world up lies along device +x. */
    private val neutralLandscape = g to 0f

    /** The other landscape grip, top edge to the right. */
    private val neutralLandscapeFlipped = -g to 0f

    private fun deltaFor(neutral: Pair<Float, Float>, phiRad: Float): Float {
        val center = GyroMath.rollFromGravity(neutral.first, neutral.second)
        val (gx, gy) = roll(neutral.first, neutral.second, phiRad)
        return GyroMath.shortestDelta(center, GyroMath.rollFromGravity(gx, gy))
    }

    @Test
    fun `neutral grip reads zero delta`() {
        assertEquals(0f, deltaFor(neutralLandscape, 0f), 1e-5f)
        assertEquals(0f, deltaFor(neutralLandscapeFlipped, 0f), 1e-5f)
    }

    @Test
    fun `turning the wheel left gives a negative delta`() {
        // Counter-clockwise is left on a real wheel, and negative is left on the steering axis.
        assertTrue(deltaFor(neutralLandscape, deg(30.0)) < 0f)
        assertEquals(-deg(30.0), deltaFor(neutralLandscape, deg(30.0)), 1e-4f)
    }

    @Test
    fun `turning the wheel right gives a positive delta`() {
        assertEquals(deg(30.0), deltaFor(neutralLandscape, deg(-30.0)), 1e-4f)
    }

    @Test
    fun `sign is the same in either landscape grip`() {
        // The old yaw implementation flipped sign depending on how the phone was held.
        val a = deltaFor(neutralLandscape, deg(40.0))
        val b = deltaFor(neutralLandscapeFlipped, deg(40.0))
        assertEquals(a, b, 1e-4f)
        assertTrue(a < 0f)
    }

    @Test
    fun `leaning the phone back does not change the measured angle`() {
        // Relaxed driving posture: top edge tilted away by 45 degrees. The roll being measured is
        // about the screen normal, so lean must cancel out entirely.
        val lean = deg(45.0)
        val gxNeutral = g * cos(lean)
        val gzNeutral = g * sin(lean)
        val center = GyroMath.rollFromGravity(gxNeutral, 0f)
        val (gx, gy) = roll(gxNeutral, 0f, deg(25.0))

        assertTrue(GyroMath.isGripUsable(gx, gy, gzNeutral))
        assertEquals(-deg(25.0), GyroMath.shortestDelta(center, GyroMath.rollFromGravity(gx, gy)), 1e-4f)
    }

    @Test
    fun `grip is usable through a comfortable lean and lost near horizontal`() {
        fun usableAtLean(leanDeg: Double): Boolean {
            val lean = deg(leanDeg)
            return GyroMath.isGripUsable(g * cos(lean), 0f, g * sin(lean))
        }
        assertTrue("upright", usableAtLean(0.0))
        assertTrue("30 degrees back", usableAtLean(30.0))
        assertTrue("60 degrees back", usableAtLean(60.0))
        assertFalse("flat on a table", usableAtLean(88.0))
    }

    @Test
    fun `phone flat on a table is rejected instead of spinning on noise`() {
        // In-plane part is pure sensor noise here; atan2 of noise wanders over the full circle.
        assertFalse(GyroMath.isGripUsable(0.08f, -0.11f, 9.79f))
    }

    @Test
    fun `full lock at half the configured range`() {
        val range = deg(180.0) // GYRO_RANGES default
        val delta = deltaFor(neutralLandscape, deg(90.0))
        assertEquals(-1f, GyroMath.steerFromDelta(delta, range, 0.05f), 1e-4f)
        assertEquals(1f, GyroMath.steerFromDelta(-delta, range, 0.05f), 1e-4f)
    }

    @Test
    fun `mid travel is proportional`() {
        val range = deg(180.0)
        val delta = deltaFor(neutralLandscape, deg(-45.0)) // half of the 90 degree half-range, right
        val steer = GyroMath.steerFromDelta(delta, range, 0f)
        assertEquals(0.5f, steer, 1e-3f)
    }

    @Test
    fun `unwrap crosses the pi seam without flipping`() {
        // Raw roll jumps 3.10 -> -3.10 rad; the real motion is 0.083 rad, not -6.2.
        val before = 3.10f
        val after = -3.10f
        val unwrapped = GyroMath.unwrapStep(before, before, after)
        assertEquals(before + 0.0832f, unwrapped, 1e-3f)
    }

    @Test
    fun `unwrap accumulates a full turn monotonically`() {
        // Sweep 350 degrees in 5 degree steps: the unwrapped angle must rise the whole way, with no
        // sign flip when the raw angle wraps.
        var raw = GyroMath.rollFromGravity(neutralLandscape.first, neutralLandscape.second)
        var unwrapped = raw
        var prevUnwrapped = unwrapped
        for (step in 1..70) {
            val phi = deg(-5.0 * step) // clockwise, so the angle should climb
            val (gx, gy) = roll(neutralLandscape.first, neutralLandscape.second, phi)
            val next = GyroMath.rollFromGravity(gx, gy)
            unwrapped = GyroMath.unwrapStep(unwrapped, raw, next)
            raw = next
            assertTrue("monotonic at step $step", unwrapped > prevUnwrapped)
            prevUnwrapped = unwrapped
        }
        assertEquals(deg(350.0), unwrapped, 1e-2f)
    }

    @Test
    fun `unwrap has no drift over repeated sweeps`() {
        // Telescoping differences, so returning to the start must land back on the start value —
        // this is what makes the drift-trim hack from the yaw implementation unnecessary.
        var raw = GyroMath.rollFromGravity(neutralLandscape.first, neutralLandscape.second)
        val start = raw
        var unwrapped = raw
        repeat(20) {
            for (step in 0..36) {
                val phi = deg(sin(step / 36.0 * 2 * PI) * 60.0)
                val (gx, gy) = roll(neutralLandscape.first, neutralLandscape.second, phi)
                val next = GyroMath.rollFromGravity(gx, gy)
                unwrapped = GyroMath.unwrapStep(unwrapped, raw, next)
                raw = next
            }
        }
        assertEquals(start, unwrapped, 1e-3f)
    }

    @Test
    fun `deadzone keeps a steady grip at dead centre`() {
        val range = deg(180.0)
        // A 2 degree hand wobble inside a 5 percent deadzone of a 90 degree half-range (4.5 degrees).
        val delta = deltaFor(neutralLandscape, deg(2.0))
        assertEquals(0f, GyroMath.steerFromDelta(delta, range, 0.05f), 1e-6f)
    }
}
