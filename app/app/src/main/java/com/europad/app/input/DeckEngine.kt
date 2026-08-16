package com.europad.app.input

import com.europad.app.net.FrameEncoder
import com.europad.app.net.UdpTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

class DeckEngine(
    private val transport: UdpTransport,
    private val scope: CoroutineScope,
) {
    private val latest = InputFrame()
    private val snapshotLock = Any()
    private var senderJob: Job? = null
    private var lastSeqSent = 0

    fun setInput(mutator: (InputFrame) -> Unit) {
        synchronized(snapshotLock) { mutator(latest) }
    }

    fun startSender(rateHz: Int) {
        senderJob?.cancel()
        val periodMs = 1000L / rateHz
        senderJob = scope.launch(Dispatchers.IO) {
            val frame = InputFrame()
            val buf = ByteArray(30)
            while (true) {
                val tsMs = System.nanoTime() / 1_000_000L and 0xFFFFFFFFL
                val bytes: ByteArray
                synchronized(snapshotLock) {
                    frame.copyFrom(latest)
                    val (b, seq) = FrameEncoder.encodeSnapshot(frame, tsMs)
                    b.copyInto(buf)
                    lastSeqSent = seq
                    bytes = buf.copyOf()
                }
                transport.drainIncoming()
                transport.sendSnapshot(bytes)
                Thread.sleep(periodMs)
            }
        }
    }

    fun stopSender() {
        senderJob?.cancel()
        senderJob = null
    }
}
