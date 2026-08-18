package com.europad.app

import com.europad.app.ui.GearShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ETS2 shifting is relative — one Shift/Ctrl press per step over R-N-D. The old stateless mapping
 * sent one Ctrl for R and Shift+Ctrl together for N, which desynced the moment the truck was not
 * in neutral. These 9 pairs are the whole contract.
 */
class GearShiftTest {

    @Test
    fun `same gear sends nothing`() {
        assertNull(GearShift.plan("R", "R"))
        assertNull(GearShift.plan("N", "N"))
        assertNull(GearShift.plan("D", "D"))
    }

    @Test
    fun `neighbours are one press`() {
        assertEquals(true to 1, GearShift.plan("R", "N"))
        assertEquals(true to 1, GearShift.plan("N", "D"))
        assertEquals(false to 1, GearShift.plan("D", "N"))
        assertEquals(false to 1, GearShift.plan("N", "R"))
    }

    @Test
    fun `R to D and back are two presses`() {
        assertEquals(true to 2, GearShift.plan("R", "D"))
        assertEquals(false to 2, GearShift.plan("D", "R"))
    }

    @Test
    fun `unknown gear names send nothing`() {
        assertNull(GearShift.plan("D", "P"))
        assertNull(GearShift.plan("", "D"))
        assertNull(GearShift.plan("d", "r")) // case matters: the deck uses upper case
    }

    @Test
    fun `order is R N D`() {
        assertEquals(listOf("R", "N", "D"), GearShift.order)
    }
}
