package com.europad.app.ui

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Places one control on the deck's inner canvas — the single expression both the live deck and the
 * editor's handles use, so a handle is always exactly the rectangle of the control under it.
 *
 * [rect] is read in the layout phase and never during composition, and that is the whole point. A
 * drag writes a new position on every pointer event, ~120–240 times a second. While those positions
 * were read during composition, one finger movement recomposed the entire deck — the wheel and its
 * canvas, both pedals, six utility buttons, the gear selector, the link strip, and all thirteen edit
 * handles — plus a fresh 13-entry map each time. Nothing could keep up, so a drag arrived in visible
 * steps: the control sat still, then jumped, and the resize corner appeared to ignore whole stretches
 * of the gesture. Reading in the layout phase costs one re-measure of one node instead.
 */
internal fun Modifier.placeOnDeck(pad: Dp, innerW: Dp, innerH: Dp, rect: () -> DeckRect): Modifier =
    this
        .offset {
            val r = rect()
            IntOffset(
                (pad.toPx() + innerW.toPx() * r.left).roundToInt(),
                (pad.toPx() + innerH.toPx() * r.top).roundToInt(),
            )
        }
        .layout { measurable, _ ->
            val r = rect()
            val w = (innerW.toPx() * r.w).roundToInt().coerceAtLeast(0)
            val h = (innerH.toPx() * r.h).roundToInt().coerceAtLeast(0)
            val placeable = measurable.measure(Constraints.fixed(w, h))
            layout(w, h) { placeable.place(0, 0) }
        }

/**
 * Edit-mode chrome, drawn as a transparent overlay *on top of the live deck*.
 *
 * It deliberately owns no geometry of its own: [pad]/[innerW]/[innerH] are the exact Dp values the
 * deck lays its controls out with, so a handle is always the same rectangle as the control under it
 * and a saved position lands where the editor showed it. Because the overlay sits above the deck it
 * also swallows every pointer event, so the real controls never fire while editing — no per-control
 * edit flag needed.
 *
 * @param baseline every element the deck draws, at its saved position. Supplies the id set (which
 *   never changes mid-session) and the fallback for an id [live] has no entry for.
 * @param live the in-progress edit map. A lambda, not a value: it is read in the layout and draw
 *   phases only, so a drag never recomposes this overlay or the deck (see [placeOnDeck]).
 * @param onChange called with the new position of one element, live during a drag
 */
@Composable
fun LayoutEditOverlay(
    prefs: SharedPreferences,
    mode: String,
    baseline: Map<String, ElementPosition>,
    live: () -> Map<String, ElementPosition>,
    pad: Dp,
    innerW: Dp,
    innerH: Dp,
    hasChanges: Boolean,
    onChange: (String, ElementPosition) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
) {
    var selected by remember { mutableStateOf<String?>(null) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showInstructions by remember { mutableStateOf(!LayoutPreferences.hasSeenEditModeInstructions(prefs)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(PitWall.Ground.copy(alpha = 0.2f))
            // Pointer sink: children (handles, chips) hit-test above this, everything else — the
            // live deck underneath — gets nothing.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            // Declared after the sink so it is the inner node and sees the Main pass first. A tap a
            // handle already claimed never reaches it; a tap on bare canvas does, and clears the
            // selection. That is the way back to the toolbar when a selected control is sitting on
            // top of it — see the zIndex note in ElementHandle.
            .pointerInput(Unit) { detectTapGestures { selected = null } },
    ) {
        baseline.forEach { (id, fallback) ->
            ElementHandle(
                id = id,
                position = { live()[id] ?: fallback },
                overlaps = {
                    val all = live()
                    val r = (all[id] ?: fallback).toDeckRect()
                    all.any { (other, p) -> other != id && LayoutEdit.collides(r, p.toDeckRect()) }
                },
                pad = pad,
                innerW = innerW,
                innerH = innerH,
                isSelected = selected == id,
                onSelect = { selected = id },
                onChange = { onChange(id, it) },
            )
        }

        Row(
            Modifier
                // Above every unselected handle, below the selected one (zIndex 3): a control the
                // user is working on has to stay grabbable even when it is parked over the chips,
                // and tapping bare canvas deselects it to get the chips back.
                .zIndex(2f)
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(PitWall.Panel.copy(alpha = 0.9f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "EDIT LAYOUT · ${mode.uppercase()}",
                    color = PitWall.Ink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Text(
                    "Drag to move · corner to resize · tap empty space to deselect",
                    color = PitWall.ButtonLabel,
                    fontSize = 9.sp,
                )
            }
            ChipLabel("SAVE", PitWall.SignalGreen, onSave)
            ChipLabel("RESET", PitWall.Amber, onReset)
            ChipLabel("CANCEL", PitWall.SignalRed) {
                if (hasChanges) showUnsavedDialog = true else onCancel()
            }
        }
    }

    if (showInstructions) {
        FirstTimeInstructionsDialog(
            onDismiss = {
                showInstructions = false
                LayoutPreferences.markEditModeInstructionsSeen(prefs)
            },
        )
    }

    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            onSave = { showUnsavedDialog = false; onSave() },
            onDiscard = { showUnsavedDialog = false; onCancel() },
            onCancel = { showUnsavedDialog = false },
        )
    }
}

/** Visible corner tab. */
private val HANDLE_SIZE = 22.dp

/**
 * Grab area for the corner tab, centred on the element's bottom-right corner. The tab used to *be*
 * the target: a 22 dp square wholly inside the element, half the app's own touch floor
 * ([LayoutEdit.MIN_TOUCH_DP]) on the one control you have to hit precisely. Missing it started a
 * move instead of a resize, which is what "resizing doesn't work" looks like from the outside.
 */
private val HANDLE_HIT = LayoutEdit.MIN_TOUCH_DP.dp

/** Pushes the hit box out until its centre lands on the corner, leaving the tab where it always was. */
private val HANDLE_OUT = (HANDLE_HIT - HANDLE_SIZE) / 2

/**
 * One element's drag box + resize corner. Geometry comes from [placeOnDeck], so it is the same
 * rectangle the deck draws the control in.
 *
 * Nothing here reads a position during composition. [position] and [overlaps] are read in the layout
 * and draw phases, so a drag re-measures and repaints this one handle and recomposes nothing at all —
 * the live size readout is its own composable for exactly that reason. Both gestures also accumulate
 * their own running total inside the pointer scope rather than re-reading the committed position on
 * each event: a position only refreshes between frames, so several `onDrag` callbacks in a row used to
 * see the same base and the last write won — the element crawled behind the finger and lost most of
 * the travel. A plain local `var` is immune to that: every delta lands exactly once.
 */
@Composable
private fun ElementHandle(
    id: String,
    position: () -> ElementPosition,
    overlaps: () -> Boolean,
    pad: Dp,
    innerW: Dp,
    innerH: Dp,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onChange: (ElementPosition) -> Unit,
) {
    // pointerInput(id) never restarts, so everything a gesture calls has to be reached through state.
    // A captured lambda keeps merging edits into the map from the composition that installed it —
    // RESET's fresh defaults would be silently thrown away by the next drag, and SAVE would write the
    // stale ones. Same for `pos`: after RESET the baseline map is a new instance.
    val pos by rememberUpdatedState(position)
    val emit by rememberUpdatedState(onChange)
    val select by rememberUpdatedState(onSelect)
    val density = LocalDensity.current.density
    // Read through state rather than added as pointerInput keys, which would tear down the drag
    // detector mid-gesture on a rotation.
    val canvasWDp by rememberUpdatedState(innerW.value)
    val canvasHDp by rememberUpdatedState(innerH.value)

    Box(
        Modifier
            // Selecting raises the handle above its neighbours *and* above the editor toolbar
            // (zIndex 2). Drawing order is map order, so a neighbour used to bury the selected
            // outline and — worse — its resize corner; and a control parked over the chips could not
            // be grabbed back at all. zIndex reorders draw *and* hit-test without touching
            // composition, so raising an element from its own onDragStart can't cancel the gesture
            // that raised it. Tapping bare canvas deselects, which hands the chips back.
            .zIndex(if (isSelected) 3f else 0f)
            .placeOnDeck(pad, innerW, innerH) { pos().toDeckRect() }
            .drawBehind {
                // Draw-phase read: an overlap flipping repaints the outline without recomposing.
                val bad = overlaps()
                drawRoundRect(
                    color = when {
                        bad -> PitWall.SignalRed
                        isSelected -> PitWall.WheelGlow
                        else -> PitWall.TowerGray
                    },
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(
                        width = if (isSelected || bad) 2.dp.toPx() else 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                        ),
                    ),
                )
            }
            .pointerInput(id) { detectTapGestures(onTap = { select() }) }
            .pointerInput(id) {
                // Anchor + running total for one move gesture. Unclamped on purpose: clamping the
                // accumulator would make an edge swallow overshoot and stick.
                var base = pos()
                var liveCx = base.cx
                var liveCy = base.cy
                detectDragGestures(
                    onDragStart = {
                        select()
                        base = pos()
                        liveCx = base.cx
                        liveCy = base.cy
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        liveCx += (drag.x / density) / canvasWDp
                        liveCy += (drag.y / density) / canvasHDp
                        emit(LayoutEdit.moved(base, liveCx, liveCy))
                    },
                )
            },
    ) {
        if (isSelected) {
            SizeReadout(id, pos, innerW, innerH)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = HANDLE_OUT, y = HANDLE_OUT)
                    .size(HANDLE_HIT)
                    .pointerInput(id) {
                        // Snapshot at drag start and never re-read: `base` is both the fixed
                        // top-left anchor for the whole gesture and the seed for the running size.
                        var base = pos()
                        var liveW = base.w
                        var liveH = base.h
                        detectDragGestures(
                            onDragStart = {
                                base = pos()
                                // Seed from the size actually on screen, not the stored one. GEAR
                                // and INDICATORS are shorter than the 44 dp floor, so seeding raw
                                // left the accumulator below what the first event snapped to and
                                // the next ~12 dp of drag did nothing.
                                val shown = LayoutEdit.resized(base, base.w, base.h, canvasWDp, canvasHDp)
                                liveW = shown.w
                                liveH = shown.h
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                liveW += (drag.x / density) / canvasWDp
                                liveH += (drag.y / density) / canvasHDp
                                emit(LayoutEdit.resized(base, liveW, liveH, canvasWDp, canvasHDp))
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(HANDLE_SIZE)
                        .clip(RoundedCornerShape(50))
                        .background(PitWall.WheelGlow.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) { Text("⤡", color = PitWall.Ink, fontSize = 12.sp) }
            }
        }
    }
}

/**
 * Live size in dp, so the 44 dp floor is something you can see yourself hit.
 *
 * Its own composable because it is the one thing in the editor that genuinely has to read the
 * position during composition. Keeping it in here means a drag recomposes this label and nothing
 * else — inline in [ElementHandle] it dragged the whole handle, its outline and its corner along
 * with it, once per pointer event.
 */
@Composable
private fun BoxScope.SizeReadout(id: String, position: () -> ElementPosition, innerW: Dp, innerH: Dp) {
    val p = position()
    Text(
        "$id  ${(innerW.value * p.w).toInt()}×${(innerH.value * p.h).toInt()}",
        color = PitWall.Ink,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        // A utility button is only ~56 dp wide; without this the readout wraps to three lines and
        // the box clips it.
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(3.dp)
            .background(PitWall.Panel.copy(alpha = 0.75f), RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp),
    )
}

/**
 * Dialog shown when user attempts to exit edit mode with unsaved changes.
 */
@Composable
private fun UnsavedChangesDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = PitWall.Panel,
        title = {
            Text(
                text = "UNSAVED CHANGES",
                color = PitWall.Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
        },
        text = {
            Text(
                text = "You have unsaved layout changes. What would you like to do?",
                color = PitWall.ButtonLabel,
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipLabel("SAVE", PitWall.SignalGreen, onSave)
                ChipLabel("DISCARD", PitWall.SignalRed, onDiscard)
                ChipLabel("CANCEL", PitWall.TowerGray, onCancel)
            }
        },
    )
}

/**
 * Dialog shown when user enters edit mode for the first time.
 */
@Composable
private fun FirstTimeInstructionsDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PitWall.Panel,
        title = {
            Text(
                text = "LAYOUT EDITOR",
                color = PitWall.Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Your real controls stay on screen — move and resize them in place.",
                    color = PitWall.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                InstructionItem("Tap a control to select it")
                InstructionItem("Drag it anywhere on the deck")
                InstructionItem("Drag the corner handle to resize (44 dp minimum)")
                InstructionItem("Tap empty space to deselect — do this if a control covers the buttons below")
                InstructionItem("A red dashed outline means it overlaps another control")
                InstructionItem("SAVE applies it to the deck straight away")
                Text(
                    text = "Tip: outside the editor, long-press R/N/D to tell the app which gear the truck is really in.",
                    color = PitWall.Amber,
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = { ChipLabel("GOT IT", PitWall.SignalGreen, onDismiss) },
    )
}

/**
 * One line of the editor's how-to.
 *
 * Bulleted with a rule instead of an emoji: OEM emoji fonts differ enough that ⤡ and ✋ landed as
 * tofu on some phones, and TalkBack reads each one aloud by name before the sentence it decorates.
 */
@Composable
private fun InstructionItem(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 10.dp, height = 1.dp).background(PitWall.TowerGray))
        Text(text = text, color = PitWall.ButtonLabel, fontSize = 13.sp)
    }
}
