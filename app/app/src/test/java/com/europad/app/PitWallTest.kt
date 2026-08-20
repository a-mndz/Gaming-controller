package com.europad.app

import com.europad.app.net.ConnState
import com.europad.app.net.Proto
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

    @Test
    fun `link reads green only when connected and inside budget`() {
        val (line, color) = PitWall.linkReadout(ConnState.Connected, "USB", 8, 0)
        assertEquals("USB  008ms  LOSS 00%", line)
        assertEquals(PitWall.SignalGreen, color)
    }

    @Test
    fun `link reads amber when connected but degraded`() {
        assertEquals(PitWall.Amber, PitWall.linkReadout(ConnState.Connected, "WI-FI", 21, 0).second)
        assertEquals(PitWall.Amber, PitWall.linkReadout(ConnState.Connected, "WI-FI", 5, 5).second)
        // No RTT sample yet is not the same as a good one.
        assertEquals(PitWall.Amber, PitWall.linkReadout(ConnState.Connected, "WI-FI", -1, 0).second)
    }

    @Test
    fun `link reads red when there is no link`() {
        assertEquals("NO LINK" to PitWall.SignalRed, PitWall.linkReadout(ConnState.Idle, "WI-FI", 5, 0))
        assertEquals(PitWall.SignalRed, PitWall.linkReadout(ConnState.Rejected, "WI-FI", 5, 0).second)
    }

    @Test
    fun `failure text names the reject reason`() {
        assertEquals("WRONG PIN", PitWall.failureText(ConnState.Rejected, Proto.REJECT_WRONG_PIN))
        assertEquals("PC FULL: 4 PADS", PitWall.failureText(ConnState.Rejected, Proto.REJECT_LOBBY_FULL))
        assertEquals("UPDATE PC SERVER", PitWall.failureText(ConnState.Rejected, Proto.REJECT_VERSION_MISMATCH))
        assertEquals("REJECTED BY PC", PitWall.failureText(ConnState.Rejected, 99))
        // Timeout: no REJECT arrived, so the stale reason byte must not be read as one.
        assertEquals("NO REPLY", PitWall.failureText(ConnState.Idle, Proto.REJECT_WRONG_PIN))
    }
}
