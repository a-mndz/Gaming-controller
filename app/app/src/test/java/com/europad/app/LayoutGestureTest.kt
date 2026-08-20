package com.europad.app

import com.europad.app.ui.DeckLayout
import com.europad.app.ui.DeckRect
import com.europad.app.ui.ElementPosition
import com.europad.app.ui.LayoutEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gesture semantics for the layout editor.
 *
 * These lock in *how* the editor is allowed to turn a stream of pointer deltas into a position. The
 * editor used to recompute each event from the last committed position, which it read through
 * `rememberUpdatedState` — a value that only refreshes on recomposition. Pointer events arrive far
 * faster than the deck recomposes, so a run of events all saw the same base and the last write won:
 * the control crawled behind the finger and lost most of the drag. The fix is to accumulate in the
 * pointer scope, and these tests describe the accumulator the UI is expected to implement.
 *
 * [dragged] and [cornerDragged] mirror `ElementHandle`'s gesture loops exactly, so if the UI ever
 * drifts back to per-event re-reads, the numbers here stop matching.
 */
class LayoutGestureTest {

    private val canvasW = 758f
    private val canvasH = 338f

    /** One move gesture: seed once from [start], accumulate every delta, clamp only on output. */
    private fun dragged(
        start: ElementPosition,
        deltas: List<Pair<Float, Float>>,
    ): ElementPosition {
        var liveCx = start.cx
        var liveCy = start.cy
        var out = start
        deltas.forEach { (dx, dy) ->
            liveCx += dx
            liveCy += dy
            out = LayoutEdit.moved(start, liveCx, liveCy)
        }
        return out
    }

    /** One corner gesture: [start] stays the anchor for every event, size accumulates. */
    private fun cornerDragged(
        start: ElementPosition,
        deltas: List<Pair<Float, Float>>,
    ): ElementPosition {
        // Seeded from the *enforced* size rather than the stored one, exactly as ElementHandle does
        // — see `a control that starts below the touch floor grows on the first event`.
        val shown = LayoutEdit.resized(start, start.w, start.h, canvasW, canvasH)
        var liveW = shown.w
        var liveH = shown.h
        var out = shown
        deltas.forEach { (dx, dy) ->
            liveW += dx
            liveH += dy
            out = LayoutEdit.resized(start, liveW, liveH, canvasW, canvasH)
        }
        return out
    }

    // ========================================================================
    // No delta may be dropped, however many events a gesture is split into
    // ========================================================================

    /** 40 small events must land exactly where one big event lands — nothing swallowed. */
    @Test
    fun `a drag split across many events lands where a single event would`() {
        val start = ElementPosition("BRAKE", cx = 0.40f, cy = 0.50f, w = 0.11f, h = 0.20f)

        val stepped = dragged(start, List(40) { 0.005f to 0.0025f })
        val single = LayoutEdit.moved(start, start.cx + 0.20f, start.cy + 0.10f)

        assertEquals("x travel lost", single.cx, stepped.cx, 0.0001f)
        assertEquals("y travel lost", single.cy, stepped.cy, 0.0001f)
    }

    /**
     * The regression itself, stated numerically: re-reading a base that lags behind the events
     * keeps only the last delta of each stale run. Nothing in the app should behave like this.
     */
    @Test
    fun `re-reading a stale base is what used to swallow the travel`() {
        val start = ElementPosition("WHEEL", cx = 0.40f, cy = 0.50f, w = 0.25f, h = 0.50f)
        val deltas = List(40) { 0.005f }

        // Recomposition only lands every 8th event, so 7 of every 8 deltas overwrite each other.
        var stale = start
        deltas.forEachIndexed { i, dx ->
            val next = LayoutEdit.moved(stale, stale.cx + dx, stale.cy)
            if (i % 8 == 7) stale = next
        }

        val accumulated = dragged(start, deltas.map { it to 0f })
        assertEquals(0.60f, accumulated.cx, 0.0001f)
        assertEquals("stale base kept 5 of 40 deltas", 0.425f, stale.cx, 0.0001f)
        assertTrue("stale base must undershoot — that was the bug", stale.cx < accumulated.cx)
    }

    /** Same guarantee for the corner: a stepped resize equals the one-shot resize. */
    @Test
    fun `a resize split across many events lands where a single event would`() {
        val start = ElementPosition("ACCEL", cx = 0.50f, cy = 0.50f, w = 0.10f, h = 0.20f)

        val stepped = cornerDragged(start, List(25) { 0.004f to 0.008f })
        val single = LayoutEdit.resized(start, start.w + 0.10f, start.h + 0.20f, canvasW, canvasH)

        assertEquals("width travel lost", single.w, stepped.w, 0.0001f)
        assertEquals("height travel lost", single.h, stepped.h, 0.0001f)
    }

    // ========================================================================
    // Clamping the output must not clamp the accumulator
    // ========================================================================

    /** Shove a control into the right edge, then pull back: the pull back is honoured in full. */
    @Test
    fun `dragging past an edge and back does not lose travel`() {
        val start = ElementPosition("MENU", cx = 0.90f, cy = 0.50f, w = 0.10f, h = 0.13f)

        val pinned = dragged(start, List(10) { 0.02f to 0f })
        assertEquals("should sit against the right edge", 0.95f, pinned.cx, 0.0001f)

        // 0.20 out then 0.30 back = 0.10 left of where it started, not 0.30 left of the edge.
        val returned = dragged(start, List(10) { 0.02f to 0f } + List(10) { -0.03f to 0f })
        assertEquals(0.80f, returned.cx, 0.0001f)
    }

    /** Same at the 44 dp floor: squash the corner flat, then grow, and it grows immediately. */
    @Test
    fun `shrinking to the touch floor and growing back does not lose travel`() {
        val start = ElementPosition("CAMERA", cx = 0.50f, cy = 0.50f, w = 0.20f, h = 0.30f)

        val floored = cornerDragged(start, List(10) { -0.05f to -0.05f })
        assertEquals("width floor", LayoutEdit.MIN_TOUCH_DP, floored.w * canvasW, 0.01f)

        // -0.50 then +0.55 leaves the accumulator 0.05 above the starting width.
        val regrown = cornerDragged(
            start,
            List(10) { -0.05f to -0.05f } + List(11) { 0.05f to 0.05f },
        )
        assertEquals(0.25f, regrown.w, 0.0001f)
        assertEquals(0.35f, regrown.h, 0.0001f)
    }

    // ========================================================================
    // The corner anchors the top-left edge for the whole gesture
    // ========================================================================

    /** Every intermediate frame of a corner drag keeps the top-left edge exactly where it was. */
    @Test
    fun `the resize anchor stays pinned for every event of a gesture`() {
        val start = ElementPosition("GEAR", cx = 0.50f, cy = 0.30f, w = 0.165f, h = 0.085f)
        val left = start.cx - start.w / 2f
        val top = start.cy - start.h / 2f

        // Seeded the way the UI seeds: from the enforced size. GEAR's stored height is below the
        // floor, so this is the case where raw seeding and enforced seeding differ.
        val shown = LayoutEdit.resized(start, start.w, start.h, canvasW, canvasH)
        var liveW = shown.w
        var liveH = shown.h
        repeat(30) { i ->
            // Wander in and out so the clamps get exercised mid-gesture, not just at the end.
            val dx = if (i % 3 == 0) -0.02f else 0.015f
            liveW += dx
            liveH += dx
            val out = LayoutEdit.resized(start, liveW, liveH, canvasW, canvasH)
            assertEquals("left edge drifted on event $i", left, out.cx - out.w / 2f, 0.0005f)
            assertEquals("top edge drifted on event $i", top, out.cy - out.h / 2f, 0.0005f)
        }
    }

    /**
     * `GEAR` and `INDICATORS` ship shorter than the 44 dp floor, so the first corner event snaps
     * them up to it. Seeding the accumulator from the raw stored height rather than the enforced one
     * left a dead zone: the next ~12 dp of drag was spent climbing back to a size already on screen,
     * which reads as the handle ignoring you.
     */
    @Test
    fun `a control that starts below the touch floor grows on the first event`() {
        val start = ElementPosition("GEAR", cx = 0.50f, cy = 0.30f, w = 0.165f, h = 0.085f)
        val floorH = LayoutEdit.MIN_TOUCH_DP / canvasH
        assertTrue("fixture must start below the floor to be worth testing", start.h < floorH)

        val onePush = cornerDragged(start, listOf(0f to 0.02f))

        assertEquals("first event must move the bottom edge", floorH + 0.02f, onePush.h, 0.0001f)
        assertEquals("width must not drift", start.w, onePush.w, 0.0001f)
    }

    /** A move never changes the size, so the two gestures can't contaminate each other. */
    @Test
    fun `moving preserves the element size exactly`() {
        val start = ElementPosition("HANDBRAKE", cx = 0.72f, cy = 0.10f, w = 0.107f, h = 0.13f)

        val moved = dragged(start, List(15) { -0.03f to 0.04f })

        assertEquals(start.w, moved.w, 0.0f)
        assertEquals(start.h, moved.h, 0.0f)
    }

    // ========================================================================
    // Overlap warning: real collisions only
    // ========================================================================

    /** Two controls sharing an edge, or clipping a corner, are not worth a red outline. */
    @Test
    fun `collides ignores controls that merely touch or graze`() {
        val a = DeckRect(cx = 0.20f, cy = 0.20f, w = 0.20f, h = 0.20f)
        val touching = DeckRect(cx = 0.40f, cy = 0.20f, w = 0.20f, h = 0.20f)
        val grazing = DeckRect(cx = 0.39f, cy = 0.20f, w = 0.20f, h = 0.20f)

        assertFalse("edge to edge", LayoutEdit.collides(a, touching))
        assertTrue("a graze is still an AABB intersect", LayoutEdit.overlaps(a, grazing))
        assertFalse("...but not a collision", LayoutEdit.collides(a, grazing))
    }

    /** A real pile-up still warns, and is measured against the smaller of the two. */
    @Test
    fun `collides flags a real pile-up`() {
        val a = DeckRect(cx = 0.30f, cy = 0.30f, w = 0.20f, h = 0.20f)
        val half = DeckRect(cx = 0.40f, cy = 0.30f, w = 0.20f, h = 0.20f)
        val big = DeckRect(cx = 0.32f, cy = 0.32f, w = 0.60f, h = 0.60f)
        val small = DeckRect(cx = 0.30f, cy = 0.30f, w = 0.05f, h = 0.05f)

        assertEquals(0.5f, LayoutEdit.overlapFraction(a, half), 0.0001f)
        assertTrue(LayoutEdit.collides(a, half))
        // The little one is swallowed whole: measuring against the big one's area would hide it.
        assertEquals(1.0f, LayoutEdit.overlapFraction(big, small), 0.0001f)
        assertTrue(LayoutEdit.collides(big, small))
    }

    /**
     * The stock deck must open clean. The three utility buttons really do intersect on a 16:9-ish
     * inner canvas, so the bare AABB test painted them red — and their ⚠ — the instant the editor
     * opened, before the user had touched anything.
     */
    @Test
    fun `the default layout raises no collision warning at any realistic aspect`() {
        // 16:9 and 18:9 handsets, plus the inner-canvas aspects those become after the 3% margin.
        listOf(16f / 9f, 1.827f, 2.0f, 2.24f, 2.4f).forEach { aspect ->
            listOf("wheel", "gyro").forEach { mode ->
                val els = defaults(mode, aspect)
                els.forEach { (idA, a) ->
                    els.forEach { (idB, b) ->
                        if (idA < idB) {
                            assertFalse(
                                "$idA collides with $idB in $mode at aspect $aspect " +
                                    "(${LayoutEdit.overlapFraction(a, b)})",
                                LayoutEdit.collides(a, b),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun defaults(mode: String, aspect: Float): Map<String, DeckRect> = buildMap {
        put("LIGHTS", DeckLayout.utilBtn(0, aspect))
        put("WIPER", DeckLayout.utilBtn(1, aspect))
        put("VIPER", DeckLayout.utilBtn(2, aspect))
        put("HANDBRAKE", DeckLayout.topRightBtn(0, aspect))
        put("SETTINGS", DeckLayout.topRightBtn(1, aspect))
        put("MENU", DeckLayout.topRightBtn(2, aspect))
        put("CAMERA", DeckLayout.camera(aspect))
        put("GEAR", DeckLayout.gearSel())
        put("INDICATORS", DeckLayout.arrows())
        if (mode == "gyro") {
            put("GYRO_ACCEL", DeckLayout.gyroAccel())
            put("GYRO_BRAKE", DeckLayout.gyroBrake())
        } else {
            put("WHEEL", DeckLayout.wheel(aspect))
            put("BRAKE", DeckLayout.brake())
            put("ACCEL", DeckLayout.accel())
        }
    }
}
