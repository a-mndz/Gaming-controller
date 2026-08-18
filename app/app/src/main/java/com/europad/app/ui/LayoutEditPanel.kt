package com.europad.app.ui

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Full-screen overlay for editing control element positions.
 * Displays all elements as draggable items with visual feedback.
 *
 * @param prefs SharedPreferences instance for tracking first-time instructions
 * @param mode Current steering mode ("wheel" or "gyro")
 * @param defaultPositions Default positions from DeckLayout
 * @param customPositions Current custom positions (if any)
 * @param onSave Callback when user saves changes
 * @param onReset Callback when user resets to default
 * @param onCancel Callback when user cancels editing
 */
@Composable
fun LayoutEditPanel(
    prefs: SharedPreferences,
    mode: String,
    defaultPositions: Map<String, DeckRect>,
    customPositions: Map<String, ElementPosition>,
    onSave: (Map<String, ElementPosition>) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
) {
    // Current editing state
    var editingPositions by remember {
        mutableStateOf(
            customPositions.ifEmpty {
                defaultPositions.mapValues { (id, rect) ->
                    ElementPosition.fromDeckRect(id, rect)
                }
            }
        )
    }

    // Track which element is currently selected
    var selectedElement by remember { mutableStateOf<String?>(null) }

    // Track if changes have been made
    var hasChanges by remember { mutableStateOf(false) }

    // Dialog state
    var showUnsavedDialog by remember { mutableStateOf(false) }

    // First-time instructions state
    val isFirstTime = remember { !LayoutPreferences.hasSeenEditModeInstructions(prefs) }
    var showFirstTimeInstructions by remember { mutableStateOf(isFirstTime) }

    // Minimum separation threshold for overlap detection (2% of canvas dimension)
    val minSeparation = 0.02f

    // Check if two elements overlap
    fun elementsOverlap(pos1: ElementPosition, pos2: ElementPosition): Boolean {
        val dx = kotlin.math.abs(pos1.cx - pos2.cx)
        val dy = kotlin.math.abs(pos1.cy - pos2.cy)
        val minDistX = (pos1.w + pos2.w) / 2f + minSeparation
        val minDistY = (pos1.h + pos2.h) / 2f + minSeparation
        return dx < minDistX && dy < minDistY
    }

    // Check if the selected element overlaps with any other element
    fun hasOverlap(id: String): Boolean {
        val pos = editingPositions[id] ?: return false
        return editingPositions.entries.any { (otherId, otherPos) ->
            otherId != id && elementsOverlap(pos, otherPos)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(PitWall.Ground.copy(alpha = 0.95f))
    ) {
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = constraints.maxHeight.toFloat()

        // Render all draggable elements
        editingPositions.forEach { (id, position) ->
            DraggableElement(
                id = id,
                position = position,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                isSelected = selectedElement == id,
                hasOverlap = hasOverlap(id),
                onPositionChange = { newPos ->
                    editingPositions = editingPositions + (id to newPos)
                    hasChanges = true
                },
                onSelected = {
                    selectedElement = id
                }
            )
        }

        // Action buttons at the bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            // Save button
            ChipLabel(
                text = "SAVE",
                color = PitWall.SignalGreen,
                onClick = {
                    onSave(editingPositions)
                }
            )

            // Reset button
            ChipLabel(
                text = "RESET TO DEFAULT",
                color = PitWall.Amber,
                onClick = {
                    editingPositions = defaultPositions.mapValues { (id, rect) ->
                        ElementPosition.fromDeckRect(id, rect)
                    }
                    hasChanges = true
                    onReset()
                }
            )

            // Cancel button
            ChipLabel(
                text = "CANCEL",
                color = PitWall.SignalRed,
                onClick = {
                    if (hasChanges) {
                        showUnsavedDialog = true
                    } else {
                        onCancel()
                    }
                }
            )
        }

        // Instructions overlay at the top
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(PitWall.Panel.copy(alpha = 0.9f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "EDIT LAYOUT - ${mode.uppercase()} MODE",
                color = PitWall.Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Drag elements to reposition • Tap to select • Save when done",
                color = PitWall.ButtonLabel,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    // First-time instructions overlay
    if (showFirstTimeInstructions) {
        FirstTimeInstructionsDialog(
            onDismiss = {
                showFirstTimeInstructions = false
                LayoutPreferences.markEditModeInstructionsSeen(prefs)
            }
        )
    }

    // Unsaved changes dialog
    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            onSave = {
                onSave(editingPositions)
                showUnsavedDialog = false
            },
            onDiscard = {
                showUnsavedDialog = false
                onCancel()
            },
            onCancel = {
                showUnsavedDialog = false
            }
        )
    }
}

/**
 * Individual draggable control element in edit mode.
 *
 * @param id Element identifier
 * @param position Current element position
 * @param canvasWidth Canvas width in pixels
 * @param canvasHeight Canvas height in pixels
 * @param isSelected Whether this element is currently selected
 * @param hasOverlap Whether this element overlaps with another
 * @param onPositionChange Callback when position changes
 * @param onSelected Callback when element is selected
 */
@Composable
private fun DraggableElement(
    id: String,
    position: ElementPosition,
    canvasWidth: Float,
    canvasHeight: Float,
    isSelected: Boolean,
    hasOverlap: Boolean,
    onPositionChange: (ElementPosition) -> Unit,
    onSelected: () -> Unit,
) {
    // Track drag offset
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // Calculate pixel positions
    val centerX = (position.cx * canvasWidth) + dragOffset.x
    val centerY = (position.cy * canvasHeight) + dragOffset.y
    val width = position.w * canvasWidth
    val height = position.h * canvasHeight

    // Calculate top-left position for offset
    val offsetX = (centerX - width / 2f).roundToInt()
    val offsetY = (centerY - height / 2f).roundToInt()

    // Get display name
    val displayName = ElementId.entries.find { it.name == id }?.displayName ?: id

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX, offsetY) }
            .size(width.dp, height.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    hasOverlap -> PitWall.SignalRed.copy(alpha = 0.3f)
                    isSelected -> PitWall.WheelGlow.copy(alpha = 0.5f)
                    else -> PitWall.Panel.copy(alpha = 0.7f)
                }
            )
            .border(
                width = if (isSelected) 3.dp else 2.dp,
                color = when {
                    hasOverlap -> PitWall.SignalRed
                    isSelected -> PitWall.WheelGlow
                    else -> PitWall.PanelBorder
                },
                shape = RoundedCornerShape(8.dp)
            )
            .pointerInput(id) {
                detectDragGestures(
                    onDragStart = {
                        onSelected()
                        dragOffset = Offset.Zero
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                    },
                    onDragEnd = {
                        // Calculate new fractional position with drag offset applied
                        val newCx = ((position.cx * canvasWidth + dragOffset.x) / canvasWidth)
                            .coerceIn(position.w / 2f, 1f - position.w / 2f)
                        val newCy = ((position.cy * canvasHeight + dragOffset.y) / canvasHeight)
                            .coerceIn(position.h / 2f, 1f - position.h / 2f)

                        // Snap to valid position
                        val newPosition = ElementPosition.create(
                            id = id,
                            cx = newCx,
                            cy = newCy,
                            w = position.w,
                            h = position.h
                        )

                        onPositionChange(newPosition)
                        dragOffset = Offset.Zero
                    }
                )
            }
            .alpha(if (dragOffset != Offset.Zero) 0.7f else 1f),
        contentAlignment = Alignment.Center
    ) {
        // Element label
        Text(
            text = displayName,
            color = PitWall.Ink,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(4.dp)
        )

        // Overlap warning indicator
        if (hasOverlap) {
            Text(
                text = "⚠",
                color = PitWall.SignalRed,
                fontSize = 20.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )
        }
    }
}

/**
 * Dialog shown when user attempts to exit edit mode with unsaved changes.
 *
 * @param onSave Callback to save changes and exit
 * @param onDiscard Callback to discard changes and exit
 * @param onCancel Callback to cancel exit and continue editing
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
                letterSpacing = 1.2.sp
            )
        },
        text = {
            Text(
                text = "You have unsaved layout changes. What would you like to do?",
                color = PitWall.ButtonLabel,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChipLabel(
                    text = "SAVE",
                    color = PitWall.SignalGreen,
                    onClick = onSave
                )
                ChipLabel(
                    text = "DISCARD",
                    color = PitWall.SignalRed,
                    onClick = onDiscard
                )
                ChipLabel(
                    text = "CANCEL",
                    color = PitWall.TowerGray,
                    onClick = onCancel
                )
            }
        }
    )
}

/**
 * Dialog shown when user enters edit mode for the first time.
 * Provides brief instructions on how to use the layout editor.
 *
 * @param onDismiss Callback when user dismisses the instructions
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
                text = "WELCOME TO LAYOUT EDITOR",
                color = PitWall.Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Customize your controller layout with drag-and-drop!",
                    color = PitWall.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                
                InstructionItem(
                    icon = "👆",
                    text = "Tap any element to select it"
                )
                
                InstructionItem(
                    icon = "✋",
                    text = "Drag elements to reposition them"
                )
                
                InstructionItem(
                    icon = "⚠",
                    text = "Watch for overlap warnings"
                )
                
                InstructionItem(
                    icon = "💾",
                    text = "Tap SAVE to keep your changes"
                )
                
                Text(
                    text = "Tip: You can reset to default layout anytime!",
                    color = PitWall.Amber,
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            ChipLabel(
                text = "GOT IT",
                color = PitWall.SignalGreen,
                onClick = onDismiss
            )
        }
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 20.sp
        )
        Text(
            text = text,
            color = PitWall.ButtonLabel,
            fontSize = 13.sp
        )
    }
}

/**
 * Reusable chip-style button label used throughout the UI.
 * Extracted here to match the KeymapPanel style.
 */
@Composable
private fun ChipLabel(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}
