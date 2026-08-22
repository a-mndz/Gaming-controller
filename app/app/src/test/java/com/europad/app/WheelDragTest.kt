package com.europad.app

import com.europad.app.input.WheelDrag
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The touch-wheel drag math, extracted from the composable so it can run on the JVM.
 *
 * The defect being pinned: a finger dragged across the wheel's hub produced angle jumps of up
 * to 180° in one event, because atan2 of a point a few pixels from the pivot is pure jitter.
 */
class WheelDragTest {

    private val EPS = 1e-3f
    private fun rad(deg: Double) = Math.toRadians(deg)

    @Test
    fun `counter-clockwise rotation accumulates positive angle and steer`() {
        val step = WheelDrag.step(
            angleDeg = 0f, maxTurnDeg = 180f,
            lastAngleRad = rad(0.0), curAngleRad = rad(10.0),
            radiusPx = 300f, deadRadiusPx = 60f,
        )!!
        assertEquals(10f, step.angleDeg, EPS)
        assertEquals(10f / 180f, step.steer, EPS)
        assertEquals(rad(10.0), step.lastAngleRad, 1e-6)
    }

    @Test
    fun `angle accumulates across events`() {
        var angle = 0f
        var last = rad(0.0)
        for (deg in listOf(5.0, 10.0, 15.0)) { // finger at 5°, 10°, 15°: +5° per event
            val step = WheelDrag.step(angle, 180f, last, rad(deg), 300f, 60f)!!
            angle = step.angleDeg
            last = step.lastAngleRad
        }
        assertEquals(15f, angle, EPS)
    }

    @Test
    fun `rotation clamps at full lock`() {
        val step = WheelDrag.step(175f, 180f, rad(0.0), rad(20.0), 300f, 60f)!!
        assertEquals(180f, step.angleDeg, EPS)
        assertEquals(1f, step.steer, EPS)
    }

    @Test
    fun `wrap across plus-minus 180 takes the shortest path`() {
        // From +179° to -179° is +2° of rotation, not -358°.
        val step = WheelDrag.step(100f, 180f, rad(179.0), rad(-179.0), 300f, 60f)!!
        assertEquals(102f, step.angleDeg, EPS)
    }

    @Test
    fun `finger inside the dead radius is ignored`() {
        val step = WheelDrag.step(
            0f, 180f, rad(0.0), rad(90.0),
            radiusPx = 30f, deadRadiusPx = 60f,
        )
        assertNull(step)
    }

    @Test
    fun `finger exactly at the dead radius boundary is tracked`() {
        val step = WheelDrag.step(0f, 180f, rad(0.0), rad(10.0), radiusPx = 60f, deadRadiusPx = 60f)
        assertTrue(step != null)
        assertEquals(10f, step!!.angleDeg, EPS)
    }

    @Test
    fun `dead radius is a fraction of the wheel radius`() {
        // A 1000 px cell has a 500 px wheel; the gate sits at 18% of that.
        assertEquals(90f, WheelDrag.deadRadiusPx(1000f), EPS)
    }

    @Test
    fun `crossing the hub resumes with the true swept angle`() {
        // Enter the hub at +30°, emerge at -30°: the wheel should record the 60° swept,
        // not jump by the difference of two noise angles.
        val entered = WheelDrag.step(0f, 180f, rad(0.0), rad(30.0), radiusPx = 300f, deadRadiusPx = 60f)!!
        // ...finger spirals inside the hub: every sample ignored...
        assertNull(WheelDrag.step(entered.angleDeg, 180f, entered.lastAngleRad, rad(170.0), 20f, 60f))
        // ...and emerges on the other side.
        val exit = WheelDrag.step(entered.angleDeg, 180f, entered.lastAngleRad, rad(-30.0), 300f, 60f)!!
        assertEquals(-30f, exit.angleDeg, EPS)
        assertTrue(abs(exit.angleDeg - entered.angleDeg) > 59f) // genuinely swept, not teleported
    }
}
