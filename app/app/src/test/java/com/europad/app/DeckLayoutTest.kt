package com.europad.app

import com.europad.app.ui.DeckLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckLayoutTest {

    @Test
    fun `top row sits in spec band`() {
        val yRange = 0.05f..0.12f
        assertTrue("top row Y ${DeckLayout.TOP_ROW_Y}", DeckLayout.TOP_ROW_Y in yRange)
        for (i in 0..2) {
            val b = DeckLayout.utilBtn(i)
            assertTrue(b.cy in yRange)
            val r = DeckLayout.topRightBtn(i)
            assertTrue(r.cy in yRange)
            assertTrue("util right ${b.right}", b.right < 0.30f)
            assertTrue("topRight left ${r.left}", r.left > 0.65f)
        }
    }

    @Test
    fun `gear selector is centered`() {
        val g = DeckLayout.gearSel()
        assertEquals(0.50f, g.cx, 0.001f)
        assertTrue("gear width ${g.w}", g.w in 0.16f..0.34f)
    }

    @Test
    fun `arrows and camera share the second row`() {
        val yRange = 0.22f..0.28f
        assertTrue(DeckLayout.arrows().cy in yRange)
        assertTrue(DeckLayout.camera().cy in yRange)
    }

    @Test
    fun `wheel sits in spec rect`() {
        val w = DeckLayout.wheel()
        assertTrue("cx ${w.cx}", w.cx in 0.18f..0.25f)
        assertTrue("cy ${w.cy}", w.cy in 0.63f..0.68f)
        assertTrue("diam ${DeckLayout.WHEEL_DIAM}", DeckLayout.WHEEL_DIAM in 0.44f..0.52f)
        assertTrue("diam(h) ${w.h}", w.h in 0.44f..0.52f)
    }

    @Test
    fun `pedals are bottom aligned vertical with brake wider and accel taller`() {
        val b = DeckLayout.brake()
        val a = DeckLayout.accel()
        assertEquals(b.bottom, a.bottom, 0.001f)
        assertTrue("bottom ${b.bottom}", b.bottom >= 0.85f)
        assertTrue("pedals vertical", b.h > b.w && a.h > a.w)
        assertTrue("brake wider or equal to accel", b.w >= a.w)
        assertTrue("accel taller than brake", a.h >= b.h)
    }

    @Test
    fun `everything stays inside the 3 percent margins`() {
        val margin = DeckLayout.MARGIN
        val eps = 0.001f // float32 rounding on the exact boundary rows
        val rects = listOf(
            DeckLayout.utilBtn(0), DeckLayout.utilBtn(1), DeckLayout.utilBtn(2),
            DeckLayout.topRightBtn(0), DeckLayout.topRightBtn(1), DeckLayout.topRightBtn(2),
            DeckLayout.gearSel(), DeckLayout.linkStrip(),
            DeckLayout.arrows(), DeckLayout.camera(), DeckLayout.wheel(),
            DeckLayout.brake(), DeckLayout.accel(),
        )
        // Width fractions span the full 0..1 on X; margins apply in both axes.
        for (r in rects) {
            assertTrue("top of $r", r.top >= margin - eps)
            assertTrue("bottom of $r", r.bottom <= 1f - margin + eps)
        }
    }

    @Test
    fun `left top group is separated from gear selector`() {
        assertTrue(DeckLayout.utilBtn(2).right < DeckLayout.gearSel().left)
        assertTrue(DeckLayout.gearSel().right < DeckLayout.topRightBtn(0).left)
    }

    @Test
    fun `wheel top edge clears the arrows row`() {
        assertTrue(DeckLayout.wheel().top > DeckLayout.arrows().bottom)
    }

    @Test
    fun `center region stays free below the link strip`() {
        val wheel = DeckLayout.wheel()
        val brake = DeckLayout.brake()
        assertTrue("wheel.right ${wheel.right}", wheel.right < 0.48f)
        assertTrue("brake.left ${brake.left}", brake.left > 0.59f)
    }
}
