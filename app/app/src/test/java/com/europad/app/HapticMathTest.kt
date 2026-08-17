package com.europad.app

import com.europad.app.input.HapticMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticMathTest {

    @Test
    fun `zero motors yield zero amplitude`() {
        assertEquals(0f, HapticMath.amplitude(0, 0, 1f), 1e-6f)
        assertEquals(0f, HapticMath.amplitude(0, 0, 0.5f), 1e-6f)
    }

    @Test
    fun `full large motor saturates without intensity`() {
        assertEquals(0.75f, HapticMath.amplitude(255, 0, 1f), 1e-4f) // headroom for the small motor on top
        assertEquals(0.45f, HapticMath.amplitude(0, 255, 1f), 1e-4f)
        assertEquals(1f, HapticMath.amplitude(255, 255, 1f), 1e-4f) // combined sum clamps to 1
    }

    @Test
    fun `intensity scales linearly`() {
        assertEquals(0.5f * 0.75f * 1f, HapticMath.amplitude(255, 0, 0.5f), 1e-4f)
        assertEquals(0f, HapticMath.amplitude(255, 255, 0f), 1e-6f)
    }

    @Test
    fun `motor bytes are clamped into 0 255`() {
        val fromOverflow = HapticMath.amplitude(999, 999, 1f)
        val fromNegative = HapticMath.amplitude(-5, -5, 1f)
        assertEquals(1f, fromOverflow, 1e-6f)
        assertEquals(0f, fromNegative, 1e-6f)
    }

    @Test
    fun `duty timings are null at zero`() {
        assertNull(HapticMath.dutyTimings(0f))
        assertNull(HapticMath.dutyTimings(-0.1f))
    }

    @Test
    fun `duty timings sum to the period with a minimum on time`() {
        val full = HapticMath.dutyTimings(1f)
        assertNotNull(full)
        assertEquals(HapticMath.DUTY_PERIOD_MS, full!![0] + full[1])
        assertEquals(HapticMath.DUTY_PERIOD_MS, full[0])

        val half = HapticMath.dutyTimings(0.5f)!!
        assertEquals(HapticMath.DUTY_PERIOD_MS, half[0] + half[1])
        assertEquals(20L, half[0])

        val faint = HapticMath.dutyTimings(0.01f)!!
        assertEquals(HapticMath.MIN_ON_MS, faint[0]) // floor keeps even tiny rumbles perceptible
        assertTrue(faint[1] > faint[0])
    }
}
