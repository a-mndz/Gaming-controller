package com.europad.app

import com.europad.app.input.GripGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hysteresis over the gravity in-plane ratio, independently of the production code.
 *
 * The failure this gate exists for: a grip hovering near the flat threshold used to flip the
 * flat flag on single samples, and every flip discarded the driver's declared centre — felt as
 * the wheel letting go and pulling straight mid-corner.
 */
class GripGateTest {

    private fun gate() = GripGate(tripRatio = 0.26f, recoverRatio = 0.35f, dwellMs = 350f)

    @Test
    fun `upright grip is usable`() {
        val g = gate()
        assertTrue(g.sample(0.9f, 16f) == GripGate.State.Usable)
        assertFalse(g.isFlat)
    }

    @Test
    fun `brief dip below trip stays in Dip and never goes Flat`() {
        val g = gate()
        // 200 ms of a too-flat grip, in sensor samples.
        assertTrue(g.sample(0.2f, 100f) == GripGate.State.Dip)
        assertTrue(g.sample(0.2f, 100f) == GripGate.State.Dip)
        assertFalse(g.isFlat)
        // Grip firms back up past the trip ratio: back to normal, centre never dropped.
        assertTrue(g.sample(0.9f, 100f) == GripGate.State.Usable)
        assertFalse(g.isFlat)
    }

    @Test
    fun `dip held past dwell commits to Flat`() {
        val g = gate()
        assertTrue(g.sample(0.2f, 100f) == GripGate.State.Dip)
        assertTrue(g.sample(0.2f, 100f) == GripGate.State.Dip)
        assertTrue(g.sample(0.2f, 100f) == GripGate.State.Dip)
        // 400 ms cumulative >= 350 ms dwell: this is a phone set down, not a stir.
        assertTrue(g.sample(0.2f, 100f) == GripGate.State.Flat)
        assertTrue(g.isFlat)
    }

    @Test
    fun `recovery from Flat needs the recover ratio not the trip ratio`() {
        val g = gate()
        g.sample(0.0f, 10_000f)
        assertTrue(g.isFlat)
        // Above trip (0.26) but below recover (0.35): still flat — no flapping at the seam.
        assertTrue(g.sample(0.30f, 100f) == GripGate.State.Flat)
        assertTrue(g.sample(0.35f, 100f) == GripGate.State.Usable)
        assertFalse(g.isFlat)
    }

    @Test
    fun `usable sample resets the dwell accumulator`() {
        val g = gate()
        g.sample(0.2f, 300f)                    // Dip, 300 ms banked
        assertTrue(g.sample(0.9f, 16f) == GripGate.State.Usable)
        // Fresh dip must start counting from zero, not inherit the earlier 300 ms.
        assertTrue(g.sample(0.2f, 100f) == GripGate.State.Dip)
        assertTrue(g.sample(0.2f, 100f) == GripGate.State.Dip)
        assertFalse(g.isFlat)
    }

    @Test
    fun `zero dt cannot run out the dwell`() {
        val g = gate()
        repeat(1000) { assertTrue(g.sample(0.2f, 0f) == GripGate.State.Dip) }
        assertFalse(g.isFlat)
    }

    @Test
    fun `reset clears a committed flat`() {
        val g = gate()
        g.sample(0.0f, 10_000f)
        assertTrue(g.isFlat)
        g.reset()
        assertFalse(g.isFlat)
        assertTrue(g.sample(0.9f, 16f) == GripGate.State.Usable)
    }
}
