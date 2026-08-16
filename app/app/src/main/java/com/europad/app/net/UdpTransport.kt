package com.europad.app.net

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.measureTimeMillis

enum class ConnState { Idle, Connecting, Connected, Rejected }

class UdpTransport {
    private var socket: DatagramSocket? = null
    private var serverAddr: InetSocketAddress? = null
    private val stateRef = AtomicReference(ConnState.Idle)
    private val assignedSlot = AtomicLong(-1)
    private val rttMs = AtomicLong(-1)
    private val rxPingTs = AtomicLong(-1)

    val state: ConnState get() = stateRef.get()
    val slot: Int get() = assignedSlot.get().toInt()
    val rtt: Long get() = rttMs.get()

    fun connect(host: String, port: Int, pin: Int): ConnState {
        try {
            socket?.close()
            val sock = DatagramSocket()
            sock.soTimeout = 0
            socket = sock
            serverAddr = InetSocketAddress(host, port)
            stateRef.set(ConnState.Connecting)

            sock.send(DatagramPacket(FrameEncoder.encodeHello(pin), Proto.FRAME_SIZE, serverAddr))
            val buf = ByteArray(64)
            sock.soTimeout = 2000
            val pkt = DatagramPacket(buf, buf.size)
            sock.receive(pkt)
            sock.soTimeout = 0

            val flags = FrameEncoder.parseFlags(pkt.data)
            when {
                flags == Proto.FLAG_ACK -> {
                    assignedSlot.set(pkt.data[Proto.OFF_BUTTONS_LO].toLong() and 0xFF)
                    stateRef.set(ConnState.Connected)
                }
                flags == Proto.FLAG_REJECT -> stateRef.set(ConnState.Rejected)
                else -> stateRef.set(ConnState.Rejected)
            }
        } catch (_: Exception) {
            stateRef.set(ConnState.Idle)
        }
        return stateRef.get()
    }

    fun sendSnapshot(buf: ByteArray) {
        val sock = socket ?: return
        val addr = serverAddr ?: return
        if (stateRef.get() != ConnState.Connected) return
        try {
            sock.send(DatagramPacket(buf, Proto.FRAME_SIZE, addr))
        } catch (_: Exception) { }
    }

    fun sendPingAndAwait(): Long {
        val sock = socket ?: return -1
        val addr = serverAddr ?: return -1
        val ts = System.nanoTime() / 1_000_000L and 0xFFFFFFFFL
        val out = FrameEncoder.encodePing(ts)
        rxPingTs.set(ts)
        try {
            sock.send(DatagramPacket(out, Proto.FRAME_SIZE, addr))
            val buf = ByteArray(64)
            sock.soTimeout = 1500
            val pkt = DatagramPacket(buf, buf.size)
            val elapsed = measureTimeMillis { sock.receive(pkt) }
            sock.soTimeout = 0
            if (FrameEncoder.parseFlags(pkt.data) == Proto.FLAG_PING_REPLY) {
                rttMs.set(elapsed.toLong())
                return elapsed.toLong()
            }
        } catch (_: Exception) { }
        return -1
    }

    fun drainIncoming() {
        val sock = socket ?: return
        val savedTimeout = sock.soTimeout
        try {
            sock.soTimeout = 1
            while (true) {
                val buf = ByteArray(64)
                val pkt = DatagramPacket(buf, buf.size)
                sock.receive(pkt)
            }
        } catch (_: Exception) {
        } finally {
            try { sock.soTimeout = savedTimeout } catch (_: Exception) { }
        }
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        stateRef.set(ConnState.Idle)
        assignedSlot.set(-1)
    }
}
