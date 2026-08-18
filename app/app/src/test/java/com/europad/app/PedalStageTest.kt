package com.europad.app

import com.europad.app.input.PedalStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PedalStageTest {
    @Test
    fun shortHold_isHalfBrake() {
        assertEquals(0.5f, PedalStage.levelForHold(0L), 0.0001f)
        assertEquals(0.5f, PedalStage.levelForHold(1L), 0.0001f)
        assertEquals(0.5f, PedalStage.levelForHold(2999L), 0.0001f)
    }

    @Test
    fun holdPastBoundary_isFullBrake() {
        // The boundary itself counts as "held": the coroutine fires exactly on the 3 s tick.
        assertEquals(1f, PedalStage.levelForHold(3000L), 0.0001f)
        assertEquals(1f, PedalStage.levelForHold(30_000L), 0.0001f)
    }

    @Test
    fun zeroStage_meansPlainOnOff() {
        // The accelerator keeps the old instant-100% behaviour by passing stageMs = 0.
        assertEquals(1f, PedalStage.levelForHold(0L, stageMs = 0L), 0.0001f)
        assertEquals(1f, PedalStage.levelForHold(5000L, stageMs = 0L), 0.0001f)
    }

    @Test
    fun customStage_respectsBothArgs() {
        assertEquals(0.3f, PedalStage.levelForHold(100L, stageMs = 500L, firstLevel = 0.3f), 0.0001f)
        assertEquals(1f, PedalStage.levelForHold(500L, stageMs = 500L, firstLevel = 0.3f), 0.0001f)
    }

    @Test
    fun axis_mapsLevelToTriggerWord() {
        assertEquals(0, PedalStage.axis(0f).toInt())
        assertEquals(Short.MAX_VALUE.toInt(), PedalStage.axis(1f).toInt())
        assertEquals(16383, PedalStage.axis(0.5f).toInt())
    }

    @Test
    fun axis_clampsOutOfRangeLevels() {
        assertEquals(0, PedalStage.axis(-1f).toInt())
        assertEquals(Short.MAX_VALUE.toInt(), PedalStage.axis(4f).toInt())
    }

    @Test
    fun halfLevel_reachesTheGameAsHalfTrigger() {
        // The server scales the trigger word with clamped * 255 / 32767, so mirror that here: a first
        // stage that arrived as 255 (or 0) would make this feature pointless without anyone noticing.
        val byteValue = PedalStage.axis(PedalStage.BRAKE_FIRST_LEVEL).toInt() * 255 / Short.MAX_VALUE
        assertEquals(127, byteValue)
        assertTrue(byteValue in 100..155)
    }
}
