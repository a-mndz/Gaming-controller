package com.europad.app.net

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import android.util.Log

enum class ConnState { Idle, Connecting, Connected, Rejected, Reconnecting }

/**
 * One socket, one reader thread, one housekeeping thread.
 *
 * The reader owns every receive so ACK / PING_REPLY / RUMBLE can arrive in any order — the old
 * blocking `receive()` inside the ping call swallowed rumble frames. Housekeeping doubles as the
 * keepalive (the server's 300 ms failsafe fires without traffic), the RTT sampler and the
 * reconnect watchdog.
 */
class UdpTransport(
    /** Resolves whether a given local socket address sits on the USB-tether interface. */
    private val isTetherAddress: ((String?) -> Boolean)? = null,
) {

    private companion object {
        const val TICK_MS = 150L
        const val PING_TIMEOUT_MS = 1000L
        const val LINK_LOST_MS = 1500L
        const val HELLO_RETRY_MS = 500L
        const val DSCP_EF = 0xB8 // expedited forwarding: Wi-Fi maps it to WMM voice (AC_VO)
    }

    private var socket: DatagramSocket? = null
    private var serverAddr: InetSocketAddress? = null
    private var reader: Thread? = null
    private var keeper: Thread? = null
    private var pin: Int = 0
    private var hostLabel = ""
    private var transportLabel = "WI-FI"

    private val stateRef = AtomicReference(ConnState.Idle)
    private val assignedSlot = AtomicLong(-1)
    private val pendingPingTs = AtomicLong(-1)
    private val pendingSentAt = AtomicLong(0)
    private val lastRxAt = AtomicLong(0)
    private val lastPingAt = AtomicLong(0)
    private val lastHelloAt = AtomicLong(0)
    private var ackLatch: CountDownLatch? = null

    val stats = LinkStats()

    /** Server-driven rumble (large, small) in 0..255. Set before connecting. */
    @Volatile
    var onRumble: ((Int, Int) -> Unit)? = null

    val state: ConnState get() = stateRef.get()
    val slot: Int get() = assignedSlot.get().toInt()
    val rtt: Long get() = stats.medianRttMs
    val lossPercent: Int get() = stats.lossPercent
    val serverHost: String get() = hostLabel
    val transport: String get() = transportLabel

    fun connect(host: String, port: Int, pin: Int): ConnState {
        close()
        this.pin = pin
        return try {
            val addr = InetSocketAddress(host, port)
            val sock = DatagramSocket()
            sock.soTimeout = 0
            try { sock.trafficClass = DSCP_EF } catch (_: Exception) { }
            socket = sock
            serverAddr = addr
            hostLabel = host
            val local = localAddressFor(addr)
            transportLabel = NetLabels.transportFor(local, isTetherAddress?.invoke(local) == true)
            stats.reset()
            stateRef.set(ConnState.Connecting)
            lastRxAt.set(clockMs())

            val latch = CountDownLatch(1)
            ackLatch = latch
            reader = Thread({ readLoop(sock) }, "europad-rx").apply { isDaemon = true; start() }
            Log.d("EuroPadUDP", "connect hello -> $host:$port pin=$pin local=$local")
            sendHello()
            latch.await(2000, TimeUnit.MILLISECONDS)
            ackLatch = null

            if (stateRef.get() == ConnState.Connected) {
                keeper = Thread({ keeperLoop(sock) }, "europad-keeper").apply { isDaemon = true; start() }
            } else {
                if (stateRef.compareAndSet(ConnState.Connecting, ConnState.Idle)) sock.close()
            }
            Log.d("EuroPadUDP", "connect result=${stateRef.get()} slot=${assignedSlot.get()}")
            stateRef.get()
        } catch (e: Exception) {
            Log.w("EuroPadUDP", "connect threw ${e.javaClass.simpleName}: ${e.message}", e)
            stateRef.set(ConnState.Idle)
            ConnState.Idle
        }
    }

    fun sendSnapshot(buf: ByteArray) {
        if (stateRef.get() == ConnState.Idle || stateRef.get() == ConnState.Rejected) return
        send(buf)
    }

    /**
     * Fire-and-forget control frame (key remaps) with best-effort delivery: one-shot UDP is lost
     * for good if the Wi-Fi radio is mid power-save burst (the same stall that trips the server's
     * 300 ms failsafe). Retry over ~1.6 s — idempotent on the server side (same bit+key rewrite),
     * and by the later attempts the phone has re-helloed any freed slot.
     */
    fun sendConfig(buf: ByteArray) {
        Thread({
            for ((attempt, delayMs) in intArrayOf(0, 300, 800, 1600).withIndex()) {
                if (delayMs > 0) {
                    try { Thread.sleep(delayMs.toLong()) } catch (_: InterruptedException) { return@Thread }
                }
                val st = stateRef.get()
                if (st == ConnState.Connected || st == ConnState.Reconnecting) {
                    send(buf)
                    Log.d("EuroPadUDP", "config frame sent (attempt ${attempt + 1}, state=$st)")
                } else {
                    Log.d("EuroPadUDP", "config frame skipped (state=$st)")
                }
            }
        }, "europad-cfg").apply { isDaemon = true }.start()
    }

    fun close() {
        stateRef.set(ConnState.Idle)
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        serverAddr = null
        reader = null
        keeper = null
        assignedSlot.set(-1)
        pendingPingTs.set(-1)
        stats.reset()
    }

    private fun send(buf: ByteArray) {
        val sock = socket ?: return
        val addr = serverAddr ?: return
        try { sock.send(DatagramPacket(buf, Proto.FRAME_SIZE, addr)) } catch (_: Exception) { }
    }

    private fun sendHello() {
        lastHelloAt.set(clockMs())
        send(FrameEncoder.encodeHello(pin))
    }

    private fun sendPing() {
        val ts = clockMs() and 0xFFFFFFFFL
        pendingPingTs.set(ts)
        pendingSentAt.set(clockMs())
        lastPingAt.set(clockMs())
        send(FrameEncoder.encodePing(ts))
    }

    private fun readLoop(sock: DatagramSocket) {
        val buf = ByteArray(64)
        while (!sock.isClosed) {
            val pkt = DatagramPacket(buf, buf.size)
            try {
                sock.receive(pkt)
            } catch (_: Exception) {
                if (sock.isClosed) return
                continue // ICMP port-unreachable while the server restarts, etc.
            }
            if (pkt.length < Proto.FRAME_SIZE) continue
            if (buf[Proto.OFF_MAGIC] != Proto.MAGIC_LO || buf[Proto.OFF_MAGIC + 1] != Proto.MAGIC_HI) continue
            lastRxAt.set(clockMs())
            val flags = FrameEncoder.parseFlags(buf).toInt()

            when {
                flags and Proto.FLAG_ACK.toInt() != 0 -> {
                    assignedSlot.set(buf[Proto.OFF_BUTTONS_LO].toLong() and 0xFF)
                    stateRef.set(ConnState.Connected)
                    Log.d("EuroPadUDP", "rx ACK slot=${assignedSlot.get()}")
                    ackLatch?.countDown()
                }
                flags and Proto.FLAG_REJECT.toInt() != 0 -> {
                    Log.d("EuroPadUDP", "rx REJECT reason=${buf[Proto.OFF_BUTTONS_LO].toInt() and 0xFF}")
                    stateRef.set(ConnState.Rejected)
                    ackLatch?.countDown()
                }
                flags and Proto.FLAG_PING_REPLY.toInt() != 0 -> {
                    val ts = FrameEncoder.parseTimestamp(buf)
                    if (pendingPingTs.compareAndSet(ts, -1)) stats.onReply(elapsedSince(ts))
                }
                flags and Proto.FLAG_RUMBLE.toInt() != 0 -> {
                    val large = buf[Proto.OFF_BUTTONS_LO].toInt() and 0xFF
                    val small = buf[Proto.OFF_BUTTONS_LO + 1].toInt() and 0xFF
                    onRumble?.invoke(large, small)
                }
            }
        }
    }

    private fun keeperLoop(sock: DatagramSocket) {
        while (!sock.isClosed && stateRef.get() != ConnState.Idle) {
            val now = clockMs()

            val pending = pendingPingTs.get()
            if (pending >= 0 && now - pendingSentAt.get() > PING_TIMEOUT_MS &&
                pendingPingTs.compareAndSet(pending, -1)
            ) stats.onTimeout()

            when (stateRef.get()) {
                ConnState.Connected -> {
                    if (now - lastRxAt.get() > LINK_LOST_MS) {
                        stateRef.set(ConnState.Reconnecting)
                        sendHello()
                    } else if (now - lastPingAt.get() >= TICK_MS) {
                        // Doubles as keepalive: the server refreshes the slot on any frame, so
                        // pinging faster than the 300 ms failsafe keeps an idle deck alive.
                        sendPing()
                    }
                }
                ConnState.Reconnecting -> {
                    if (now - lastHelloAt.get() >= HELLO_RETRY_MS) sendHello()
                }
                else -> { }
            }

            try { Thread.sleep(TICK_MS) } catch (_: InterruptedException) { return }
        }
    }

    /** The kernel's chosen source address for this route — 192.168.42.x means USB tether (D-012). */
    private fun localAddressFor(addr: InetSocketAddress): String? = try {
        DatagramSocket().use { probe ->
            probe.connect(addr)
            probe.localAddress?.hostAddress
        }
    } catch (_: Exception) {
        null
    }

    private fun clockMs(): Long = System.nanoTime() / 1_000_000L

    private fun elapsedSince(wireTs: Long): Long {
        var d = (clockMs() and 0xFFFFFFFFL) - wireTs
        if (d < 0) d += 1L shl 32 // u32 timestamp wrapped mid-flight
        return d
    }
}
