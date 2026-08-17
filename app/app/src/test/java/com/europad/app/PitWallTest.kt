package com.europad.app

import com.europad.app.ui.PitWall
import org.junit.Assert.assertEquals
import org.junit.Test

class PitWallTest {

    @Test
    fun `rtt pads to three digits with ms suffix`() {
        assertEquals("007ms", PitWall.rttDisplay(7))
        assertEquals("000ms", PitWall.rttDisplay(0))
        assertEquals("999ms", PitWall.rttDisplay(999))
    }

    @Test
    fun `rtt shows dashes for timeout or negative`() {
        assertEquals("---ms", PitWall.rttDisplay(-1))
        assertEquals("---ms", PitWall.rttDisplay(1500))
    }

    @Test
    fun `steering display is signed fixed width and clamped`() {
        assertEquals("-100", PitWall.steerDisplay(-1f))
        assertEquals("+000", PitWall.steerDisplay(0f))
        assertEquals("+037", PitWall.steerDisplay(0.379f))
        assertEquals("+100", PitWall.steerDisplay(2f))
    }
}
