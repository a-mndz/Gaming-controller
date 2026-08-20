package com.europad.app.ui

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.europad.app.input.SteerReturn
import com.europad.app.net.FrameEncoder
import com.europad.app.net.UdpTransport

/**
 * Range of the RETURN TO CENTRE slider, in ms — the same bounds the return profile itself clamps to,
 * so the slider cannot offer a value that gets silently coerced. The floor moved up from 60 ms: a
 * return that fast is why the truck used to keep its turn (see [SteerReturn]).
 */
private val RETURN_MS_MIN = SteerReturn.MIN_MS.toFloat()
private val RETURN_MS_MAX = SteerReturn.MAX_MS.toFloat()

/**
 * On-phone remapping: every action shows its current key; tapping it opens the picker. A change
 * updates local storage immediately AND sends a config frame so the PC server rewrites the active
 * profile JSON — remapping lives entirely on the phone, no PC edits needed.
 *
 * @param pendingKeys actions saved on the phone but not yet pushed over a live link (shown amber)
 * @param sentKeys actions confirmed pushed to the server (shown green)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeymapPanel(
    transport: UdpTransport,
    prefs: SharedPreferences,
    keymap: Map<String, String>,
    mode: String = "wheel",
    wheelRangeDeg: Int = 360,
    gyroRangeDeg: Int = 180,
    returnMs: Int = 420,
    pendingKeys: Set<String> = emptySet(),
    sentKeys: Set<String> = emptySet(),
    onToggleMode: () -> Unit = {},
    onCycleWheelRange: () -> Unit = {},
    onCycleGyroRange: () -> Unit = {},
    onReturnMs: (Int) -> Unit = {},
    onRemap: (String, String) -> Unit,
    onClose: () -> Unit,
    onDisconnect: () -> Unit = {},
    onEditLayout: () -> Unit = {},
) {
    var picking by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(PitWall.Ground)
            .padding(12.dp),
    ) {
        // FlowRow, not Row: five chips plus the title need ~680 dp, so on a 640 dp-wide landscape
        // phone a plain Row pushed DONE and DISCONNECT off the right edge with no way to reach them.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "SETTINGS & SENSITIVITY",
                color = PitWall.Ink,
                fontSize = 15.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            ChipLabel(
                if (mode == "gyro") "MODE: GYRO" else "MODE: WHEEL",
                if (mode == "gyro") PitWall.SignalGreen else PitWall.Indigo,
            ) { onToggleMode() }

            if (mode == "gyro") {
                ChipLabel("TILT: $gyroRangeDeg°", PitWall.WheelGlow) { onCycleGyroRange() }
            } else {
                ChipLabel("WHEEL: $wheelRangeDeg°", PitWall.WheelGlow) { onCycleWheelRange() }
            }

            ChipLabel("EDIT LAYOUT", PitWall.Indigo) { onEditLayout() }
            ChipLabel("DISCONNECT", PitWall.SignalRed) { onDisconnect() }
            ChipLabel("DONE", PitWall.TowerGray) { onClose() }
        }
        Spacer(Modifier.padding(4.dp))

        val target = picking
        if (target != null) {
            KeyPicker(
                action = target,
                current = keymap[target] ?: "",
                onPick = { key ->
                    keyForIndex(target)?.let { idx ->
                        transport.sendConfig(FrameEncoder.encodeSetBitKey(idx, key))
                    }
                    prefs.edit().putString(prefKeyFor(target), key).apply()
                    onRemap(target, key)
                    picking = null
                },
                onCancel = { picking = null },
            )
        } else {
            if (mode != "gyro") ReturnToCenterRow(returnMs, onReturnMs)
            KeyList(keymap, pendingKeys, sentKeys, onOpen = { picking = it })
        }
    }
}

/** How fast the wheel snaps back to centre once the finger lifts. */
@Composable
private fun ReturnToCenterRow(returnMs: Int, onReturnMs: (Int) -> Unit) {
    // Local float so dragging stays smooth; the pref is written on release.
    var ms by remember(returnMs) { mutableFloatStateOf(returnMs.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("RETURN TO CENTRE", color = PitWall.TowerGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("${ms.toInt()} ms", color = PitWall.WheelGlow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = ms,
            valueRange = RETURN_MS_MIN..RETURN_MS_MAX,
            onValueChange = { ms = it },
            onValueChangeFinished = { onReturnMs(ms.toInt()) },
            colors = SliderDefaults.colors(
                thumbColor = PitWall.WheelGlow,
                activeTrackColor = PitWall.WheelGlow,
                inactiveTrackColor = Color(0xFF232330),
            ),
            modifier = Modifier.height(24.dp),
        )
    }
}

@Composable
private fun KeyList(
    keymap: Map<String, String>,
    pendingKeys: Set<String>,
    sentKeys: Set<String>,
    onOpen: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (name in TruckKeys.names) {
            val label = TruckKeys.labels[name] ?: name
            // Amber = the phone saved it but no live link has carried it yet; green = the PC has it.
            val keyColor = when (name) {
                in pendingKeys -> PitWall.Amber
                in sentKeys -> PitWall.SignalGreen
                else -> PitWall.Indigo
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(PitWall.Panel)
                    .border(1.dp, PitWall.TowerGray.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                    .clickable { onOpen(name) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, color = PitWall.Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .background(PitWall.Ground, RoundedCornerShape(4.dp))
                        .border(1.dp, keyColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(keymap[name] ?: "—", color = keyColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun KeyPicker(action: String, current: String, onPick: (String) -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Pick key for ${TruckKeys.labels[action] ?: action} (current: $current)",
                color = PitWall.Ink,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            ChipLabel("BACK", PitWall.TowerGray) { onCancel() }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (chunk in TruckKeys.keyChoices.chunked(8)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    chunk.forEachIndexed { i, key ->
                        if (i > 0) Spacer(Modifier.padding(3.dp))
                        Box(
                            modifier = Modifier
                                .width(52.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (key == current) PitWall.Indigo else PitWall.Panel,
                                    RoundedCornerShape(6.dp),
                                )
                                .border(1.dp, PitWall.TowerGray.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .clickable { onPick(key) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                key,
                                color = if (key == current) PitWall.Ink else PitWall.TowerGray,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChipLabel(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            // 12sp text plus 6 dp of padding is a 26 dp-tall tap target. The layout editor refuses to
            // let a deck control get that small, so the settings chips do not get to either.
            .heightIn(min = LayoutEdit.MIN_TOUCH_DP.dp)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, fontSize = 12.sp, letterSpacing = 1.sp) }
}

internal fun prefKeyFor(action: String): String = "key.$action"

internal fun keyForIndex(action: String): Int? = TruckKeys.names.indexOf(action).takeIf { it >= 0 }

internal fun loadKeymap(prefs: SharedPreferences): MutableMap<String, String> {
    val m = mutableMapOf<String, String>()
    for (name in TruckKeys.names) {
        m[name] = prefs.getString(prefKeyFor(name), null) ?: TruckKeys.defaults[name] ?: ""
    }
    return m
}
