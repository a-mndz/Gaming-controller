package com.europad.app

import com.europad.app.ui.ConnectionMethod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionMethodTest {

    @Test
    fun `only implemented connection methods are available`() {
        assertTrue(ConnectionMethod.Wifi.available)
        assertTrue(ConnectionMethod.Usb.available)
        assertFalse(ConnectionMethod.Bluetooth.available)
    }
}
