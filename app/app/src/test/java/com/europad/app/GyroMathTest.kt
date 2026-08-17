package com.europad.app

import com.europad.app.input.GyroMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class GyroMathTest {

    @Test
    fun `yaw of identity quaternion is zero`() {
        assertEquals(0f, GyroMath.yawFromQuaternion(0f, 0f, 0f, 1f), 1e-5f)
    }

    @Test
    fun `yaw of 180 degree rotation around z is pi`() {
        val yaw = GyroMath.yawFromQuaternion(0f, 0f, 1f, 0f)
        assertEquals(PI.toFloat(), kotlin.math.abs(yaw), 1e-4f)
    }

    @Test
    fun `shortest delta wraps across the 180 boundary`() {
        val from = PI.toFloat() - 0.1f
        val to = -PI.toFloat() + 0.1f
        val d = GyroMath.shortestDelta(from, to)
        assertEquals(0.2f, d, 1e-4f)
    }

    @Test
    fun `shortest delta is direct when both on same side`() {
        assertEquals(0.5f, GyroMath.shortestDelta(0.25f, 0.75f), 1e-5f)
    }

    @Test
    fun `steer clamps beyond half range`() {
        val range = PI.toFloat()
        assertEquals(1f, GyroMath.steerFromDelta(range, range, 0f), 1e-5f)
        assertEquals(-1f, GyroMath.steerFromDelta(-range, range, 0f), 1e-5f)
    }

    @Test
    fun `steer is zero inside deadzone and continuous at its edge`() {
        val range = PI.toFloat()
        val dz = 0.1f
        assertEquals(0f, GyroMath.steerFromDelta(0f, range, dz), 1e-6f)
        assertEquals(0f, GyroMath.steerFromDelta(range * 0.04f, range, dz), 1e-6f)
        val atEdge = GyroMath.steerFromDelta(range * 0.05f + 0.001f, range, dz)
        assertTrue("just past deadzone edge yields tiny positive steer", atEdge > 0f)
        assertEquals(0f, atEdge, 0.05f)
    }

    @Test
    fun `steer at full range maps to 1`() {
        val range = PI.toFloat()
        assertEquals(1f, GyroMath.steerFromDelta(range / 2f, range, 0.1f), 1e-4f)
    }

    @Test
    fun `complementary filter moves toward measured by alpha`() {
        assertEquals(0.3f, GyroMath.complementaryStep(0f, 1f, 0.3f), 1e-6f)
        assertEquals(0.5f, GyroMath.complementaryStep(0.4f, 0.6f, 0.5f), 1e-6f)
        assertEquals(1f, GyroMath.complementaryStep(1f, 5f, 0f), 1e-6f)
    }

    @Test
    fun `complementary alpha is clamped`() {
        assertEquals(0f, GyroMath.complementaryStep(0f, 0.1f, -5f), 1e-6f)
        assertEquals(0.1f, GyroMath.complementaryStep(0f, 0.1f, 9f), 1e-6f)
    }
}
