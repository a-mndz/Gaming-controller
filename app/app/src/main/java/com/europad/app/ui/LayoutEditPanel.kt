package com.europad.app.ui

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
 * @param ids every element the deck draws. Fixed for the session; its iteration order is the
 *   handles' draw order.
 * @param positionOf one element's live position — its in-progress edit if it has one, else its
 *   saved position. A lambda rather than a map, and per element rather than for all of them: it is
 *   read in the layout and draw phases only, so a drag never recomposes this overlay or the deck
 *   (see [placeOnDeck]), and it touches only the dragged element's state, so the other thirteen
 *   controls do not re-measure and repaint on every pointer event.
 * @param onChange called with the new position of one element, live during a drag
 */
@Composable
fun LayoutEditOverlay(
    prefs: SharedPreferences,
    mode: String,
    ids: Set<String>,
    positionOf: (String) -> ElementPosition,
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
            // Blocks the live deck underneath *and* deselects on a tap on bare canvas — one handler,
            // because the two cannot be separated.
            //
            // This used to be an unconditional sink that consumed every change on the Main pass, plus
            // a `detectTapGestures` for the deselect. That combination silently broke every tap in the
            // editor. Compose runs the *whole* Main walk of an event before the Final walk, and every
            // tap detector — `detectTapGestures`, and so `clickable` and the SAVE/RESET/CANCEL chips —
            // ends its wait with `waitForUpOrCancellation`, which treats any consumption it sees on
            // Final as "someone else took this gesture, give up". So the sink's own consumption, from
            // an ancestor, cancelled the taps of its descendants: a real finger emits move events
            // between press and release, each one got consumed, and the tap was abandoned before the
            // release arrived. Tap-to-select never fired, which is why a control could never be
            // selected without nudging it (the drag detector reads the Main pass, so it was immune)
            // and why the resize corner — which only exists on a selected control — looked missing.
            //
            // A press has already passed every descendant by the time it reaches here, so consuming
            // only the gestures that nobody above claimed keeps the deck quiet without touching the
            // ones the editor needs.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var claimed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) {
                            claimed = event.changes.any { it.isConsumed }
                            if (!claimed) selected = null
                        }
                        if (!claimed) event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) claimed = false
                    }
                }
            },
    ) {
        ids.forEach { id ->
            ElementHandle(
                id = id,
                position = { positionOf(id) },
                overlaps = {
                    val r = positionOf(id).toDeckRect()
                    ids.any { other -> other != id && LayoutEdit.collides(r, positionOf(other).toDeckRect()) }
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
                    "Drag to move · pinch or hold-then-pull to resize · tap empty space to deselect",
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

/** How a press on a control is being read. Decided once per gesture, then only MOVE may change. */
private enum class EditGesture { RELEASED, MOVE, PULL, PINCH }

/**
 * One element's edit handle — the whole gesture surface for that control, move *and* resize.
 * Geometry comes from [placeOnDeck], so this is the same rectangle the deck draws the control in.
 *
 * There is no corner tab any more. It was a 44 dp box centred on the bottom-right corner: the one
 * spot you had to hit precisely, and the only way to resize anything. Resize now starts anywhere on
 * the control — pinch it with two fingers, or hold still until it ticks and then pull. The size
 * readout is gone with it; the control changing shape under the finger says the same thing.
 *
 * Nothing here reads a position during composition. [position] and [overlaps] are read in the layout
 * and draw phases, so a gesture re-measures and repaints this one handle and recomposes nothing at
 * all. The gesture also accumulates its own running total inside the pointer scope rather than
 * re-reading the committed position on each event: a position only refreshes between frames, so
 * several callbacks in a row used to see the same base and the last write won — the element crawled
 * behind the finger and lost most of the travel. A plain local `var` is immune to that: every delta
 * lands exactly once.
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
    // The gesture node never restarts (its key is just the id), so everything a gesture calls has to
    // be reached through state.
    // A captured lambda keeps merging edits into the map from the composition that installed it —
    // RESET's fresh defaults would be silently thrown away by the next drag, and SAVE would write the
    // stale ones. Same for `pos`: after RESET the baseline map is a new instance.
    val pos by rememberUpdatedState(position)
    val emit by rememberUpdatedState(onChange)
    val select by rememberUpdatedState(onSelect)
    val density = LocalDensity.current.density
    // The one cue that a hold became a resize, now that the editor draws no marks at all: the tick
    // Android gives every long press.
    val haptics = LocalHapticFeedback.current
    // Read through state rather than added as pointerInput keys, which would tear down the gesture
    // node mid-gesture on a rotation.
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
                // No outline. The controls themselves stay on screen while editing — they are their
                // own boundary — so a dashed box around each of thirteen of them was noise, not
                // information. What is left is the one thing the control cannot say by itself: a
                // red wash when it overlaps a neighbour. Draw-phase read, so an overlap flipping
                // repaints this handle and recomposes nothing.
                if (overlaps()) {
                    drawRoundRect(
                        color = PitWall.SignalRed.copy(alpha = 0.22f),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                }
            }
            // Select on press, not on tap, and run the whole gesture here — one node owns select,
            // move, and both resizes. A tap detector cannot survive in here: it confirms on the Final
            // pass and abandons the gesture if anything consumed it, and something always does — see
            // the note on the overlay's root.
            //
            // Select and drag were two `pointerInput` nodes in this chain once. The press-consumer
            // ran, the drag detector never started a single coroutine — proven on device: its first
            // line never logged while both of its neighbours logged every event. Two nodes splitting
            // one gesture in one chain is the bug; one node that awaits its own events has nothing to
            // race. That is also why resize lives in here now instead of in a tab of its own.
            //
            // Consuming every change keeps the real control underneath silent, and keeps the overlay
            // root from deselecting us — it drops the selection on any press nobody claimed.
            .pointerInput("edit-$id") {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    select()
                    val base = pos()
                    var liveCx = base.cx
                    var liveCy = base.cy

                    // What did this press mean? Movement past the slop is a move, a second finger is
                    // a pinch, and a finger that just stays put until the long-press timeout is a
                    // pull resize. Sub-slop deltas are banked into the move accumulator rather than
                    // dropped, so a move that starts slowly still lands its first millimetre.
                    var mode = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var travel = 0f
                        var decided = EditGesture.RELEASED
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            // `positionChange()` reports Offset.Zero once the change is consumed, so
                            // the delta has to be read *before* consuming. Inverting these two lines
                            // silently kills every gesture in the editor: travel stays zero, the slop
                            // is never crossed, and every press decays into a long press.
                            val drag = change?.positionChange() ?: Offset.Zero
                            val fingers = event.changes.count { it.pressed }
                            event.changes.forEach { it.consume() }
                            if (change == null || change.changedToUpIgnoreConsumed()) break
                            if (fingers > 1) {
                                decided = EditGesture.PINCH
                                break
                            }
                            travel += drag.getDistance()
                            liveCx += (drag.x / density) / canvasWDp
                            liveCy += (drag.y / density) / canvasHDp
                            if (travel > viewConfiguration.touchSlop) {
                                decided = EditGesture.MOVE
                                break
                            }
                        }
                        decided
                    } ?: EditGesture.PULL
                    if (mode == EditGesture.RELEASED) return@awaitEachGesture
                    // The travel banked while deciding, so a move starts from the finger, not from
                    // where it first landed.
                    if (mode == EditGesture.MOVE) emit(LayoutEdit.moved(base, liveCx, liveCy))
                    if (mode == EditGesture.PULL) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }

                    // Resize anchor: fixed for the rest of the gesture, and seeded from the size
                    // actually on screen rather than the stored one — GEAR and INDICATORS ship
                    // shorter than the 44 dp floor, so seeding raw leaves the accumulator below what
                    // the first event snaps to, and the next ~12 dp of pull does nothing.
                    var anchor = pos()
                    var shown = LayoutEdit.resized(anchor, anchor.w, anchor.h, canvasWDp, canvasHDp)
                    // Unclamped running totals on purpose: clamping an accumulator lets an edge or
                    // the touch floor swallow overshoot and stick.
                    var liveW = shown.w
                    var liveH = shown.h
                    var pinchSpan = 0f
                    var pinchOther: PointerId? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        // Delta before consume, as in phase 1. `position` is untouched by consuming,
                        // so the pinch span below can still be measured after the fact.
                        val drag = change?.positionChange() ?: Offset.Zero
                        val fingers = event.changes.count { it.pressed }
                        event.changes.forEach { it.consume() }
                        if (change == null || change.changedToUpIgnoreConsumed()) break
                        // A second finger turns a move into a pinch mid-gesture. Only pointers that
                        // landed on this control reach this node, so it is never someone else's.
                        if (mode == EditGesture.MOVE && fingers > 1) {
                            mode = EditGesture.PINCH
                        }

                        if (mode == EditGesture.PINCH) {
                            val other = if (pinchOther == null) {
                                event.changes.firstOrNull { it.id != down.id && it.pressed }
                            } else {
                                event.changes.firstOrNull { it.id == pinchOther }
                            }
                            // Lost the second finger: end here rather than snap to a new span.
                            if (other == null || !other.pressed) break
                            val span = (change.position - other.position).getDistance()
                            if (pinchOther == null) {
                                if (span < 1f) break
                                pinchOther = other.id
                                pinchSpan = span
                                // Re-anchor: a pinch that follows a move has to start from where the
                                // move left the control, not from where the finger first landed.
                                anchor = pos()
                                shown = LayoutEdit.resized(anchor, anchor.w, anchor.h, canvasWDp, canvasHDp)
                                continue
                            }
                            emit(LayoutEdit.scaled(shown, span / pinchSpan, canvasWDp, canvasHDp))
                            continue
                        }

                        if (drag == Offset.Zero) continue
                        if (mode == EditGesture.MOVE) {
                            liveCx += (drag.x / density) / canvasWDp
                            liveCy += (drag.y / density) / canvasHDp
                            emit(LayoutEdit.moved(base, liveCx, liveCy))
                        } else {
                            liveW += (drag.x / density) / canvasWDp
                            liveH += (drag.y / density) / canvasHDp
                            emit(LayoutEdit.resized(anchor, liveW, liveH, canvasWDp, canvasHDp))
                        }
                    }
                }
            },
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
                InstructionItem("Pinch it with two fingers to resize (44 dp minimum)")
                InstructionItem("Or hold it still until it buzzes, then pull to resize")
                InstructionItem("Tap empty space to deselect — do this if a control covers the buttons below")
                InstructionItem("A red tint means it overlaps another control")
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
