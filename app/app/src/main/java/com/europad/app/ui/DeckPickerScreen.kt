package com.europad.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.europad.app.net.ConnState
import com.europad.app.net.EuroPadDiscovery
import com.europad.app.net.UdpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DeckPickerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("47910") }
    var pin by remember { mutableStateOf("0") }
    var status by remember { mutableStateOf("idle") }
    var connected by remember { mutableStateOf(false) }
    var rttText by remember { mutableStateOf("-- ms") }
    var connecting by remember { mutableStateOf(false) }

    val transport = remember { UdpTransport() }
    val discovery = remember { EuroPadDiscovery(context) }
    val discovered by discovery.hosts.collectAsState()

    DisposableEffect(Unit) {
        discovery.start()
        onDispose { discovery.stop(); transport.close() }
    }

    fun doConnect(targetHost: String, targetPort: Int, targetPin: Int) {
        if (connecting) return
        connecting = true
        status = "connecting…"
        scope.launch {
            val st = withContext(Dispatchers.IO) {
                transport.connect(targetHost, targetPort, targetPin)
            }
            connecting = false
            when (st) {
                ConnState.Connected -> {
                    connected = true
                    status = "connected P${transport.slot + 1}"
                    host = targetHost
                    port = targetPort.toString()
                }
                ConnState.Rejected -> status = "rejected"
                else -> status = "failed"
            }
        }
    }

    LaunchedEffect(connected) {
        while (connected) {
            val rtt = withContext(Dispatchers.IO) { transport.sendPingAndAwait() }
            rttText = if (rtt >= 0) "$rtt ms" else "timeout"
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("EuroPad", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (discovered.isNotEmpty()) {
                    Text("${discovered.size} server(s) found", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7FDB8A))
                }
                if (connected) {
                    Text("RTT: $rttText", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7FDB8A))
                }
                Text(status, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }

        if (!connected) {
            if (discovered.isNotEmpty()) {
                Text("Discovered on LAN (tap to fill in)", style = MaterialTheme.typography.bodySmall)
                discovered.values.forEach { info ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF20203A), RoundedCornerShape(8.dp))
                            .clickable { doConnect(info.host, info.port, pin.toIntOrNull() ?: 0) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text("${info.name}  ${info.host}:${info.port}", color = Color.White)
                    }
                }
            } else {
                Text("Scanning for EuroPad servers… (or enter the IP below)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Server IP") },
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("PIN") },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    enabled = !connecting && host.isNotBlank(),
                    onClick = {
                        doConnect(host.trim(), port.toIntOrNull() ?: 47910, pin.toIntOrNull() ?: 0)
                    },
                ) {
                    Text(if (connecting) "…" else "Connect")
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1A1A2E), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (connected) {
                GamepadDeck(transport)
            } else {
                Text("Connect to a server to load a deck", color = Color.Gray)
            }
        }
    }
}
