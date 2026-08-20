package com.europad.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.europad.app.R
import com.europad.app.input.HapticEngine
import com.europad.app.net.ConnState
import com.europad.app.net.EuroPadDiscovery
import com.europad.app.net.Proto
import com.europad.app.net.UdpTransport
import com.europad.app.net.isTetherAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DeckPickerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("europad", android.content.Context.MODE_PRIVATE) }

    var showingDeck by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var connectionMethod by remember { mutableStateOf(ConnectionMethod.Wifi) }
    var manualIp by remember { mutableStateOf(prefs.getString("lastIp", "192.168.1.50") ?: "192.168.1.50") }
    // Blank unless the server was started with --pin. Kept as text so a leading zero survives.
    var pin by remember { mutableStateOf(prefs.getString("pin", "") ?: "") }
    var connectError by remember { mutableStateOf<String?>(null) }

    var truckMode by remember { mutableStateOf(prefs.getString("truckMode", "wheel") ?: "wheel") }

    val transport = remember { UdpTransport { localIp -> isTetherAddress(localIp) } }
    val discovery = remember { EuroPadDiscovery(context) }
    val discovered by discovery.hosts.collectAsState()
    val haptics = remember { HapticEngine(context) }
    var hapticIntensity by remember { mutableStateOf(prefs.getFloat("hapticIntensity", 1f)) }
    haptics.intensity = hapticIntensity

    // Pulse animation for radar status
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    DisposableEffect(Unit) {
        discovery.start()
        transport.onRumble = { large, small -> haptics.onRumble(large, small) }
        onDispose {
            discovery.stop()
            transport.onRumble = null
            haptics.stop()
            transport.close()
        }
    }

    fun connectTo(host: String, port: Int) {
        if (connecting) return
        connecting = true
        connectError = null
        // Blank PIN sends 0, which is the server's own "no --pin configured" value.
        val pinCode = pin.trim().toIntOrNull() ?: 0
        scope.launch {
            val st = withContext(Dispatchers.IO) {
                transport.connect(host, port, pinCode)
            }
            connecting = false
            if (st == ConnState.Connected) {
                connected = true
                prefs.edit().putString("lastIp", host).putString("pin", pin.trim()).apply()
                showingDeck = true
            } else {
                connectError = PitWall.failureText(st, transport.rejectReason)
            }
        }
    }

    val statusText = when {
        connecting -> "CONNECTING"
        connected -> "PAIRED"
        connectError != null -> connectError!!
        discovered.isEmpty() -> "SCANNING"
        discovered.size == 1 -> "1 SERVER"
        else -> "${discovered.size} SERVERS"
    }

    if (showingDeck) {
        TruckDeck2(
            transport = transport,
            prefs = prefs,
            onOpenConnection = { showingDeck = false },
        )
        return
    }

    // Ink-black ground, hairline panels, indigo only where something is selected (DESIGN.md).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PitWall.Ground)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Top Navigation / Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.europad_mark),
                        contentDescription = "EuroPad Mark",
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, PitWall.Indigo, CircleShape),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "EUROPAD",
                                color = PitWall.Ink,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.5.sp,
                            )
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(PitWall.Indigo.copy(alpha = 0.25f))
                                    .border(1.dp, PitWall.Indigo, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                // The port, not a tagline: it is the one number you need when the PC's
                                // firewall prompt shows up.
                                Text("UDP ${Proto.DEFAULT_PORT}", color = PitWall.Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("Virtual Xbox 360 pad and ETS2 keys, over your LAN", color = PitWall.TowerGray, fontSize = 10.sp)
                    }
                }

                // Straight to the deck. Without a paired PC it still runs — the deck's own link strip
                // is what says the frames are going nowhere, so this button does not have to.
                Button(
                    onClick = { showingDeck = true },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PitWall.Panel),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PitWall.PanelBorder),
                    modifier = Modifier.height(38.dp),
                ) {
                    Text(
                        if (connected) "OPEN DECK" else "DRIVE OFFLINE",
                        color = if (connected) PitWall.SignalGreen else PitWall.Ink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                }
            }

            // Main Split Panel: (Left: Connection Settings & Mode) | (Right: Discovered Servers Radar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Left Panel: Connection Method & Steering Mode
                Column(
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PitWall.Panel)
                        .border(1.dp, PitWall.PanelBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("CONNECTION METHOD", color = PitWall.TowerGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConnectionTile(
                            title = "WI-FI / HOTSPOT",
                            subtitle = "Found over mDNS",
                            selected = connectionMethod == ConnectionMethod.Wifi,
                            modifier = Modifier.weight(1f),
                        ) { connectionMethod = ConnectionMethod.Wifi }

                        ConnectionTile(
                            title = "USB TETHERING",
                            // No published number: the wire cost is the phone's own send loop plus USB,
                            // and claiming "<1 ms" was a figure nothing in the build measures.
                            subtitle = "No Wi-Fi jitter",
                            selected = connectionMethod == ConnectionMethod.Usb,
                            modifier = Modifier.weight(1f),
                        ) { connectionMethod = ConnectionMethod.Usb }
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(PitWall.Ground)
                            .border(1.dp, PitWall.PanelBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Text(
                            if (connectionMethod == ConnectionMethod.Usb) {
                                "Plug in the cable, then turn on USB tethering in Android settings."
                            } else {
                                // It never connected automatically: discovery only lists servers.
                                "Put the phone and PC on the same Wi-Fi or hotspot, then tap a server."
                            },
                            color = PitWall.TowerGray,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                        )
                    }

                    // Steering Preference & Sensitivity Selector
                    var wheelRange by remember { mutableStateOf(prefs.getInt("wheelRange", 360)) }
                    var gyroRange by remember { mutableStateOf(prefs.getInt("gyroRange", 180)) }

                    Text("STEERING MODE & SENSITIVITY", color = PitWall.TowerGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SteeringChoiceCard(
                            label = "TOUCH WHEEL",
                            detail = if (truckMode == "wheel") "Sensitivity: $wheelRange°" else "360° Rotational Wheel",
                            selected = truckMode == "wheel",
                            modifier = Modifier.weight(1f),
                        ) {
                            if (truckMode == "wheel") {
                                // Shared preset list: a hand-written cycle here would send a value the
                                // deck's fine stepper produced (e.g. 460°) back to 180° on one tap.
                                val next = nextWheelRange(wheelRange)
                                wheelRange = next
                                prefs.edit().putInt("wheelRange", next).apply()
                            } else {
                                truckMode = "wheel"
                                prefs.edit().putString("truckMode", "wheel").apply()
                            }
                        }
                        SteeringChoiceCard(
                            label = "GYROSCOPE",
                            detail = if (truckMode == "gyro") "Lock to lock: $gyroRange°" else "Rotate Phone Like a Wheel",
                            selected = truckMode == "gyro",
                            modifier = Modifier.weight(1f),
                        ) {
                            if (truckMode == "gyro") {
                                val next = nextGyroRange(gyroRange)
                                gyroRange = next
                                prefs.edit().putInt("gyroRange", next).apply()
                            } else {
                                truckMode = "gyro"
                                prefs.edit().putString("truckMode", "gyro").apply()
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // Haptic Slider
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("HAPTIC FEEDBACK", color = PitWall.TowerGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(if (hapticIntensity > 0.05f) "${(hapticIntensity * 100).toInt()}%" else "OFF", color = PitWall.Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = hapticIntensity,
                        onValueChange = {
                            hapticIntensity = it
                            prefs.edit().putFloat("hapticIntensity", it).apply()
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = PitWall.Ink,
                            activeTrackColor = PitWall.Indigo,
                            inactiveTrackColor = PitWall.PanelBorder,
                        ),
                        modifier = Modifier.height(24.dp),
                    )
                }

                // Right Panel: PC Server Radar & Direct Connect
                Column(
                    modifier = Modifier
                        .weight(0.52f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PitWall.Panel)
                        .border(1.dp, PitWall.PanelBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            connected -> PitWall.SignalGreen
                                            connectError != null -> PitWall.SignalRed
                                            else -> PitWall.Indigo
                                        },
                                    )
                                    .alpha(if (connecting || (!connected && connectError == null)) pulseAlpha else 1f),
                            )
                            Text("PC SERVERS", color = PitWall.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                        Text(
                            statusText,
                            color = when {
                                connected -> PitWall.SignalGreen
                                connectError != null -> PitWall.SignalRed
                                else -> PitWall.TowerGray
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }

                    // Server List Box
                    if (discovered.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PitWall.Ground)
                                .border(1.dp, PitWall.PanelBorder, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("No servers found yet", color = PitWall.Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Start EuroPad on the PC, or type its IP below", color = PitWall.TowerGray, fontSize = 10.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(discovered.values.toList()) { info ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(PitWall.Ground)
                                        .border(1.dp, PitWall.PanelBorder, RoundedCornerShape(10.dp))
                                        .clickable { connectTo(info.host, info.port) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(info.name, color = PitWall.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("${info.host}:${info.port}", color = PitWall.TowerGray, fontSize = 10.sp)
                                    }

                                    Button(
                                        onClick = { connectTo(info.host, info.port) },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PitWall.Indigo),
                                        modifier = Modifier.height(34.dp),
                                    ) {
                                        Text("PAIR", color = PitWall.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Direct connect. The PIN is blank unless the PC was started with --pin: the server
                    // rejects a HELLO whose PIN does not match, and this app had no way to send one, so
                    // a PIN-gated server was unpairable from the phone.
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FieldBox(
                            value = manualIp,
                            placeholder = "PC IP (e.g. 192.168.1.50)",
                            keyboard = KeyboardType.Uri,
                            modifier = Modifier.weight(1f),
                        ) { manualIp = it.trim() }

                        FieldBox(
                            value = pin,
                            placeholder = "PIN",
                            keyboard = KeyboardType.NumberPassword,
                            modifier = Modifier.width(74.dp),
                        ) { typed -> pin = typed.filter { it.isDigit() }.take(4) }

                        Button(
                            onClick = { connectTo(manualIp.trim(), Proto.DEFAULT_PORT) },
                            enabled = !connecting && manualIp.isNotBlank(),
                            modifier = Modifier.height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PitWall.Indigo,
                                disabledContainerColor = PitWall.PanelBorder,
                            ),
                        ) {
                            Text("PAIR", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) PitWall.Indigo.copy(alpha = 0.22f) else PitWall.Ground)
            .border(1.dp, if (selected) PitWall.Indigo else PitWall.PanelBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = if (selected) PitWall.Ink else PitWall.TowerGray, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = PitWall.TowerGray, fontSize = 9.5.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SteeringChoiceCard(
    label: String,
    detail: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // Indigo, not green: green is reserved for a healthy link, and two different "selected" colours on
    // one screen read as two different kinds of state.
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) PitWall.Indigo.copy(alpha = 0.22f) else PitWall.Ground)
            .border(1.dp, if (selected) PitWall.Indigo else PitWall.PanelBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = if (selected) PitWall.Ink else PitWall.TowerGray, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = PitWall.TowerGray, fontSize = 9.5.sp, maxLines = 1)
        }
    }
}

/** Hairline single-line field. Shared by the IP and PIN boxes so they cannot drift apart. */
@Composable
private fun FieldBox(
    value: String,
    placeholder: String,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PitWall.Ground)
            .border(1.dp, PitWall.PanelBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = PitWall.TowerGray, fontSize = 11.sp, maxLines = 1)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            textStyle = TextStyle(color = PitWall.Ink, fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(PitWall.Ink),
        )
    }
}

