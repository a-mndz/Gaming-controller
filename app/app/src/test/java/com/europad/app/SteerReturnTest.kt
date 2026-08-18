package com.europad.app

import com.europad.app.input.SteerReturn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs
import org.junit.Test

/**
 * The return profile is the fix for "the truck holds its turn even though the axis is centred", so
 * these tests guard the two properties that actually make it work in game: the axis must *travel*
 * through the intermediate positions (a snap is what broke it), and it must carry a little past centre
 * before settling to exactly 0.
 */
class SteerReturnTest {

    @Test
    fun startsWhereTheFingerLetGo() {
        assertEquals(0.8f, SteerReturn.valueAt(0.8f, 0L, 400), 0.0001f)
        assertEquals(-1f, SteerReturn.valueAt(-1f, 0L, 400), 0.0001f)
        // Negative elapsed can only come from a clock oddity; treat it as "not started".
        assertEquals(0.8f, SteerReturn.valueAt(0.8f, -5L, 400), 0.0001f)
    }

    @Test
    fun endsAtExactlyZero() {
        assertEquals(0f, SteerReturn.valueAt(0.9f, 400L, 400), 0f)
        assertEquals(0f, SteerReturn.valueAt(0.9f, 100_000L, 400), 0f)
        assertEquals(0f, SteerReturn.valueAt(-0.9f, 400L, 400), 0f)
    }

    @Test
    fun sweepsThroughTheMiddleInsteadOfSnapping() {
        // The whole point: ETS2 moves its own wheel towards the axis at a limited rate, so the axis has
        // to spend real time at intermediate deflections for the game to be able to follow it home.
        val total = 400
        val samples = (0..total step 8).map { SteerReturn.valueAt(1f, it.toLong(), total) }
        val midTravel = samples.count { it in 0.2f..0.8f }
        assertTrue("expected a gradual sweep, got $samples", midTravel >= 8)
    }

    @Test
    fun counterSteersPastCentreThenSettles() {
        val total = 500
        val from = 1f
        val samples = (0..total).map { SteerReturn.valueAt(from, it.toLong(), total, 0.12f) }
        val mostNegative = samples.min()

        // Opposite side of centre, by about the requested fraction: this is the nudge that pulls the
        // game's virtual wheel through zero instead of leaving it parked mid-corner.
        assertTrue("never crossed centre: min=$mostNegative", mostNegative < -0.05f)
        assertEquals(-0.12f, mostNegative, 0.02f)
        // ...and it comes back. No lingering counter-steer, or the truck would pull the other way.
        assertEquals(0f, samples.last(), 0f)
    }

    @Test
    fun counterSteerIsOptional() {
        val total = 400
        val samples = (0..total).map { SteerReturn.valueAt(1f, it.toLong(), total, 0f) }
        assertTrue("no sample may cross centre with counter-steer off", samples.all { it >= -1e-6f })
        // Still monotonic on the way home.
        for (i in 1 until samples.size) {
            assertTrue("sample $i rose: ${samples[i - 1]} -> ${samples[i]}", samples[i] <= samples[i - 1] + 1e-5f)
        }
    }

    @Test
    fun neverOvershootsTheReleasedAngleOrTheCounterSteer() {
        for (from in listOf(-1f, -0.4f, 0.25f, 1f)) {
            for (t in 0..600) {
                val v = SteerReturn.valueAt(from, t.toLong(), 600, 0.3f)
                assertTrue("|$v| exceeded |$from| at t=$t", abs(v) <= abs(from) + 1e-5f)
                if (from > 0f) assertTrue("counter too deep at t=$t: $v", v >= -from * 0.3f - 1e-5f)
                else assertTrue("counter too deep at t=$t: $v", v <= -from * 0.3f + 1e-5f)
            }
        }
    }

    @Test
    fun mirrorsForBothDirections() {
        for (t in 0..400 step 10) {
            val left = SteerReturn.valueAt(-0.7f, t.toLong(), 400)
            val right = SteerReturn.valueAt(0.7f, t.toLong(), 400)
            assertEquals(-right, left, 0.0001f)
        }
    }

    @Test
    fun releasingAtCentreIsAlreadyDone() {
        assertEquals(0f, SteerReturn.valueAt(0f, 0L, 400), 0f)
        assertEquals(0f, SteerReturn.valueAt(0f, 50L, 400), 0f)
    }

    @Test
    fun durationScalesWithHowFarTheWheelWasTurned() {
        val full = SteerReturn.durationFor(1f, 400)
        val half = SteerReturn.durationFor(0.5f, 400)
        val flick = SteerReturn.durationFor(0.05f, 400)

        assertEquals(400, full)
        assertTrue("a half turn should unwind faster than a full lock", half < full)
        assertTrue("a flick should unwind faster than a half turn", flick < half)
        // Never so short that it becomes the snap this class exists to avoid.
        assertTrue(flick >= SteerReturn.MIN_MS)
    }

    @Test
    fun durationRespectsTheSettingBounds() {
        assertTrue(SteerReturn.durationFor(1f, 5) >= SteerReturn.MIN_MS)
        assertEquals(SteerReturn.MAX_MS, SteerReturn.durationFor(1f, 99_999))
    }

    @Test
    fun clampsAbsurdInputs() {
        // A caller that hands over an out-of-range angle gets a legal axis value, not a wild one.
        assertEquals(1f, SteerReturn.valueAt(4f, 0L, 400), 0.0001f)
        assertEquals(-1f, SteerReturn.valueAt(-4f, 0L, 400), 0.0001f)
        // A duration below the floor is clamped up to MIN_MS, so the profile keeps its shape instead of
        // collapsing into the instant snap that left the truck turned.
        assertEquals(0f, SteerReturn.valueAt(1f, SteerReturn.MIN_MS.toLong(), 1), 0f)
        val midway = SteerReturn.valueAt(1f, (SteerReturn.MIN_MS / 3).toLong(), 1)
        assertTrue("still travelling one third in, got $midway", midway > 0.1f && midway < 1f)
    }
}
