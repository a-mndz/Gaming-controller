package com.europad.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.europad.app.input.ButtonLo
import com.europad.app.input.DeckEngine
import com.europad.app.input.InputFrame
import com.europad.app.net.UdpTransport

@Composable
fun GamepadDeck(transport: UdpTransport) {
    val scope = rememberCoroutineScope()
    val deck = remember(transport) {
        DeckEngine(transport, scope).also { it.startSender(120) }
    }
    DisposableEffect(transport) { onDispose { deck.stopSender() } }

    Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Stick("L", deck)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HoldButton("BACK", ButtonLo.BACK, deck)
                HoldButton("START", ButtonLo.START, deck)
            }
        }

        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            DPad(deck)
        }

        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Row { HoldButton("LB", ButtonLo.LB, deck) }
            Spacer(Modifier.height(8.dp))
            FaceCluster(deck)
            Spacer(Modifier.height(8.dp))
            Row { HoldButton("RB", ButtonLo.RB, deck) }
            Spacer(Modifier.height(8.dp))
            Stick("R", deck)
        }
    }
}

private fun setLo(f: InputFrame, mask: Int, on: Boolean) {
    f.buttonsLo = if (on) f.buttonsLo or mask else f.buttonsLo and mask.inv()
}

@Composable
private fun HoldButton(label: String, bit: Int, deck: DeckEngine, w: Int = 76, h: Int = 48) {
    Box(
        modifier = Modifier
            .size(w.dp, h.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF2A2A4E))
            .pointerInput(bit) {
                detectTapGestures(
                    onPress = {
                        deck.setInput { setLo(it, bit, true) }
                        tryAwaitRelease()
                        deck.setInput { setLo(it, bit, false) }
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 13.sp) }
}

@Composable
private fun FaceCluster(deck: DeckEngine) {
    val items = listOf(
        "A" to ButtonLo.A,
        "B" to ButtonLo.B,
        "X" to ButtonLo.X,
        "Y" to ButtonLo.Y,
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RoundHold(items[1], deck)
            RoundHold(items[3], deck)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RoundHold(items[2], deck)
            RoundHold(items[0], deck)
        }
    }
}

@Composable
private fun RoundHold(item: Pair<String, Int>, deck: DeckEngine) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(Color(0xFF3A3A6E))
            .pointerInput(item.second) {
                detectTapGestures(
                    onPress = {
                        deck.setInput { setLo(it, item.second, true) }
                        tryAwaitRelease()
                        deck.setInput { setLo(it, item.second, false) }
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) { Text(item.first, color = Color.White) }
}

@Composable
private fun DPad(deck: DeckEngine) {
    var pressed by remember { mutableStateOf(0) }
    fun dir(up: Boolean, down: Boolean, left: Boolean, right: Boolean) {
        deck.setInput {
            setLo(it, ButtonLo.DPAD_UP, up)
            setLo(it, ButtonLo.DPAD_DOWN, down)
            setLo(it, ButtonLo.DPAD_LEFT, left)
            setLo(it, ButtonLo.DPAD_RIGHT, right)
        }
    }
    val size = 150
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF20203A))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { off ->
                        val (x, y) = off
                        val cx = size / 2f; val cy = size / 2f
                        val dx = x - cx; val dy = y - cy
                        if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                            dir(false, false, dx < 0, dx > 0)
                            pressed = if (dx < 0) 3 else 2
                        } else {
                            dir(dy < 0, dy > 0, false, false)
                            pressed = if (dy < 0) 0 else 1
                        }
                        tryAwaitRelease()
                        dir(false, false, false, false)
                        pressed = -1
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) { Text("D-PAD", color = Color.Gray, fontSize = 11.sp) }
}

@Composable
private fun Stick(name: String, deck: DeckEngine) {
    val isLeft = name == "L"
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(Color(0xFF26264A))
            .pointerInput(isLeft) {
                val cx = size.width / 2f; val cy = size.height / 2f
                val radius = size.width / 2f
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        deck.setInput {
                            if (isLeft) { it.axes[0] = 0; it.axes[1] = 0 }
                            else { it.axes[2] = 0; it.axes[3] = 0 }
                        }
                    },
                    onDragCancel = {
                        deck.setInput {
                            if (isLeft) { it.axes[0] = 0; it.axes[1] = 0 }
                            else { it.axes[2] = 0; it.axes[3] = 0 }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val pos = change.position
                        var dx = pos.x - cx; var dy = pos.y - cy
                        val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        if (len > radius) { dx *= radius / len; dy *= radius / len }
                        val sx = ((dx / radius) * Short.MAX_VALUE).toInt().toShort()
                        val sy = ((-dy / radius) * Short.MAX_VALUE).toInt().toShort()
                        deck.setInput {
                            if (isLeft) { it.axes[0] = sx; it.axes[1] = sy }
                            else { it.axes[2] = sx; it.axes[3] = sy }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) { Text(name, color = Color.Gray, fontSize = 11.sp) }
}
