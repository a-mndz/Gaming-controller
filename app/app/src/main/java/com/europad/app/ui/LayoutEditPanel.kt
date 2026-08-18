package com.europad.app.ui

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Edit-mode chrome, drawn as a transparent overlay *on top of the live deck*.
 *
 * It deliberately owns no geometry of its own: [pad]/[innerW]/[innerH] are the exact Dp values the
 * deck lays its controls out with, so a handle is always the same rectangle as the control under it
 * and a saved position lands where the editor showed it. Because the overlay sits above the deck it
 * also swallows every pointer event, so the real controls never fire while editing — no per-control
 * edit flag needed.
 *
 * @param positions in-progress edit map (fractions of the inner canvas)
 * @param onChange called with the new position of one element, live during a drag
 */
@Composable
fun LayoutEditOverlay(
    prefs: SharedPreferences,
    mode: String,
    positions: Map<String, ElementPosition>,
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

    fun overlapping(id: String): Boolean {
        val r = positions[id]?.toDeckRect() ?: return false
        return positions.any { (other, p) -> other != id && LayoutEdit.overlaps(r, p.toDeckRect()) }
    }

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
            },
    ) {
        positions.forEach { (id, position) ->
            ElementHandle(
                id = id,
                position = position,
                pad = pad,
                innerW = innerW,
                innerH = innerH,
                isSelected = selected == id,
                hasOverlap = overlapping(id),
                onSelect = { selected = id },
                onChange = { onChange(id, it) },
            )
        }

        Row(
            Modifier
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
                    "Drag to move · drag the corner to resize",
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

/** Corner grab area for resizing, in dp. */
private val HANDLE_SIZE = 22.dp

/**
 * One element's drag box + resize corner. Positioned with the same expression as the deck's
 * `place()`, in Dp — the only pixel values here are the raw drag deltas, converted to fractions
 * once against the inner canvas size.
 */
@Composable
private fun ElementHandle(
    id: String,
    position: ElementPosition,
    pad: Dp,
    innerW: Dp,
    innerH: Dp,
    isSelected: Boolean,
    hasOverlap: Boolean,
    onSelect: () -> Unit,
    onChange: (ElementPosition) -> Unit,
) {
    // The gesture coroutine outlives recomposition, so it must read the *current* position.
    val cur by rememberUpdatedState(position)
    // ...and the *current* callback. pointerInput(id) never restarts, so a captured lambda keeps
    // merging edits into the map from the composition that installed it — RESET's fresh defaults
    // would be silently thrown away by the next drag, and SAVE would write the stale ones.
    val emit by rememberUpdatedState(onChange)
    val density = LocalDensity.current.density
    // Same reason as [cur]: read through state rather than adding them as pointerInput keys, which
    // would tear down the drag detector mid-gesture.
    val canvasWDp by rememberUpdatedState(innerW.value)
    val canvasHDp by rememberUpdatedState(innerH.value)
    val rect = position.toDeckRect()

    val outline = when {
        hasOverlap -> PitWall.SignalRed
        isSelected -> PitWall.WheelGlow
        else -> PitWall.TowerGray
    }

    Box(
        Modifier
            .offset(x = pad + innerW * rect.left, y = pad + innerH * rect.top)
            .size(width = innerW * rect.w, height = innerH * rect.h)
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(
                        width = if (isSelected || hasOverlap) 2.dp.toPx() else 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                        ),
                    ),
                )
            }
            .pointerInput(id) { detectTapGestures(onTap = { onSelect() }) }
            .pointerInput(id) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDrag = { change, drag ->
                        change.consume()
                        emit(
                            LayoutEdit.moved(
                                cur,
                                cur.cx + (drag.x / density) / canvasWDp,
                                cur.cy + (drag.y / density) / canvasHDp,
                            ),
                        )
                    },
                )
            },
    ) {
        if (hasOverlap) {
            Text(
                "⚠",
                color = PitWall.SignalRed,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
            )
        }
        if (isSelected) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(HANDLE_SIZE)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 0.dp, bottomEnd = 6.dp, bottomStart = 0.dp))
                    .background(PitWall.WheelGlow.copy(alpha = 0.85f))
                    .pointerInput(id) {
                        detectDragGestures(
                            onDrag = { change, drag ->
                                change.consume()
                                emit(
                                    LayoutEdit.resized(
                                        cur,
                                        cur.w + (drag.x / density) / canvasWDp,
                                        cur.h + (drag.y / density) / canvasHDp,
                                        canvasWDp,
                                        canvasHDp,
                                    ),
                                )
                            },
                        )
                    },
            ) {
                Text(
                    "⤡",
                    color = PitWall.Ink,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
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
                InstructionItem("👆", "Tap a control to select it")
                InstructionItem("✋", "Drag it anywhere on the deck")
                InstructionItem("⤡", "Drag the corner handle to resize (44 dp minimum)")
                InstructionItem("⚠", "A red dashed outline means it overlaps another control")
                InstructionItem("💾", "SAVE applies it to the deck straight away")
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
 * Helper composable for instruction items with icon and text.
 */
@Composable
private fun InstructionItem(
    icon: String,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = icon, fontSize = 18.sp)
        Text(text = text, color = PitWall.ButtonLabel, fontSize = 13.sp)
    }
}
