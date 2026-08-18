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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
    var statusText by remember { mutableStateOf("Scanning LAN for PC...") }

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
        statusText = "Connecting to $host:$port..."
        scope.launch {
            val st = withContext(Dispatchers.IO) {
                transport.connect(host, port, 0)
            }
            connecting = false
            if (st == ConnState.Connected) {
                connected = true
                statusText = "Connected to $host"
                prefs.edit().putString("lastIp", host).apply()
                showingDeck = true
            } else {
                statusText = "Connection failed. Please retry."
            }
        }
    }

    if (showingDeck) {
        TruckDeck2(
            transport = transport,
            prefs = prefs,
            onOpenConnection = { showingDeck = false },
        )
        return
    }

    // Modern High-Tech Dashboard
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF07070A), Color(0xFF0D0D14), Color(0xFF060609)),
                ),
            )
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
                            .border(1.5.dp, PitWall.WheelGlow.copy(alpha = 0.6f), CircleShape),
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
                                    .border(1.dp, PitWall.Indigo.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text("PRO ETS2 TELEMETRY", color = PitWall.WheelGlow, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("YOUR PHONE. YOUR VIRTUAL TRUCK CONTROLLER.", color = PitWall.TowerGray, fontSize = 9.sp, letterSpacing = 0.5.sp)
                    }
                }

                // Direct Offline Drive Button
                Button(
                    onClick = { showingDeck = true },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF191924)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PitWall.Indigo.copy(alpha = 0.6f)),
                    modifier = Modifier.height(38.dp),
                ) {
                    Text(
                        if (connected) "➔ ENTER ACTIVE COCKPIT" else "➔ DRIVE / TEST OFFLINE",
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
                        .background(Color(0xFF0F0F16))
                        .border(1.dp, Color(0xFF232330), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("CONNECTION METHOD", color = PitWall.TowerGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConnectionTile(
                            title = "WI-FI / HOTSPOT",
                            subtitle = "Auto-Discovery on LAN",
                            selected = connectionMethod == ConnectionMethod.Wifi,
                            modifier = Modifier.weight(1f),
                        ) { connectionMethod = ConnectionMethod.Wifi }

                        ConnectionTile(
                            title = "USB TETHERING",
                            subtitle = "Ultra-Low Latency (<1ms)",
                            selected = connectionMethod == ConnectionMethod.Usb,
                            modifier = Modifier.weight(1f),
                        ) { connectionMethod = ConnectionMethod.Usb }
                    }

                    // Mode Instructions
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF151520))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Text(
                            if (connectionMethod == ConnectionMethod.Usb) {
                                "⚡ Connect phone via USB cable and enable 'USB Tethering' in Android settings."
                            } else {
                                "📶 Connect phone and PC to the same Wi-Fi or mobile hotspot. Discovery connects automatically."
                            },
                            color = PitWall.TowerGray,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
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
                                val next = when (wheelRange) { 180 -> 270; 270 -> 360; 360 -> 540; 540 -> 900; else -> 180 }
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
                                val next = when (gyroRange) { 90 -> 180; 180 -> 270; 270 -> 360; else -> 90 }
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
                        Text(if (hapticIntensity > 0.05f) "${(hapticIntensity * 100).toInt()}%" else "OFF", color = PitWall.WheelGlow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = hapticIntensity,
                        onValueChange = {
                            hapticIntensity = it
                            prefs.edit().putFloat("hapticIntensity", it).apply()
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = PitWall.WheelGlow,
                            activeTrackColor = PitWall.WheelGlow,
                            inactiveTrackColor = Color(0xFF232330),
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
                        .background(Color(0xFF0F0F16))
                        .border(1.dp, Color(0xFF232330), RoundedCornerShape(14.dp))
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
                                    .background(if (connected) PitWall.SignalGreen else PitWall.WheelGlow)
                                    .alpha(if (connecting || !connected) pulseAlpha else 1f),
                            )
                            Text("PC SERVER RADAR", color = PitWall.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                        Text(
                            if (connecting) "CONNECTING..." else statusText,
                            color = if (connected) PitWall.SignalGreen else PitWall.TowerGray,
                            fontSize = 9.5.sp,
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
                                .background(Color(0xFF08080C))
                                .border(1.dp, Color(0xFF1C1C26), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Searching for EuroPad PC Server on LAN...", color = PitWall.TowerGray, fontSize = 11.5.sp)
                                Text("Launch EuroPad server on PC or connect via direct IP below", color = PitWall.TowerGray.copy(alpha = 0.5f), fontSize = 9.sp)
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
                                        .background(Color(0xFF181822))
                                        .border(1.dp, PitWall.Indigo.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                        .clickable { connectTo(info.host, info.port) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(info.name, color = PitWall.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Box(
                                                Modifier
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(PitWall.SignalGreen.copy(alpha = 0.2f))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                            ) {
                                                Text("ONLINE", color = PitWall.SignalGreen, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Text("${info.host}:${info.port}", color = PitWall.TowerGray, fontSize = 10.sp)
                                    }

                                    Button(
                                        onClick = { connectTo(info.host, info.port) },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PitWall.SignalGreen),
                                        modifier = Modifier.height(34.dp),
                                    ) {
                                        Text("CONNECT ➔", color = PitWall.Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Direct IP Connect Bar
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0A0A10))
                                .border(1.dp, Color(0xFF282838), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (manualIp.isEmpty()) {
                                Text("Enter PC IP (e.g. 192.168.1.50)", color = PitWall.TowerGray.copy(alpha = 0.5f), fontSize = 11.sp)
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = manualIp,
                                onValueChange = { manualIp = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = PitWall.Ink,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(PitWall.WheelGlow),
                            )
                        }
                        Button(
                            onClick = { connectTo(manualIp.trim(), Proto.DEFAULT_PORT) },
                            modifier = Modifier.height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PitWall.Indigo),
                        ) {
                            Text("CONNECT", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
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
    val bg = if (selected) Color(0xFF1E1E2E) else Color(0xFF14141C)
    val border = if (selected) PitWall.WheelGlow else Color(0xFF242430)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = if (selected) PitWall.Ink else PitWall.TowerGray, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = PitWall.TowerGray.copy(alpha = 0.75f), fontSize = 8.sp, maxLines = 1)
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
    val bg = if (selected) Color(0xFF1E1E2E) else Color(0xFF14141C)
    val border = if (selected) PitWall.SignalGreen else Color(0xFF242430)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = if (selected) PitWall.SignalGreen else PitWall.TowerGray, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = PitWall.TowerGray.copy(alpha = 0.75f), fontSize = 8.sp, maxLines = 1)
        }
    }
}

