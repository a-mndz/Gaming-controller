package com.europad.app.input

import com.europad.app.net.FrameEncoder
import com.europad.app.net.UdpTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DeckEngine(
    private val transport: UdpTransport,
    private val scope: CoroutineScope,
) {
    private val latest = InputFrame()
    private val snapshotLock = Any()
    private var senderJob: Job? = null
    @Volatile private var lastSeqSent = 0

    fun setInput(mutator: (InputFrame) -> Unit) {
        synchronized(snapshotLock) { mutator(latest) }
    }

    /**
     * Streams the latest snapshot at [rateHz].
     *
     * Two things this loop must get right, both learned the hard way:
     *
     * 1. **It has to be cancellable.** The old version was `while (true) { ... Thread.sleep() }`.
     *    `Job.cancel()` can only interrupt a coroutine at a suspension point, and `Thread.sleep` is
     *    not one — so `stopSender()` never stopped anything. Leaving the deck (opening the connection
     *    screen) disposed the UI but left the thread broadcasting whatever frame was last written,
     *    forever, and re-entering the deck started a *second* sender. Two senders racing on one
     *    sequence counter make the server drop roughly half the snapshots as stale, and a frozen
     *    frame is exactly the "steering never comes back to centre" symptom. `isActive` + `delay()`
     *    makes cancellation actually land.
     *
     * 2. **Pacing must not drift.** `delay(periodMs)` alone pays the encode + `sendSnapshot` cost on
     *    top of every period, so a nominal 120 Hz sags to whatever the socket feels like. Sleeping
     *    until a monotonic deadline keeps the real rate at the requested one, and skipping missed
     *    deadlines (rather than trying to catch up in a burst) avoids a backlog after a GC pause.
     */
    fun startSender(rateHz: Int) {
        senderJob?.cancel()
        val periodMs = (1000L / rateHz.coerceIn(1, 500)).coerceAtLeast(1L)
        senderJob = scope.launch(Dispatchers.IO) {
            val frame = InputFrame()
            var nextAtMs = System.nanoTime() / 1_000_000L
            while (isActive) {
                val tsMs = System.nanoTime() / 1_000_000L and 0xFFFFFFFFL
                val bytes: ByteArray
                synchronized(snapshotLock) {
                    frame.copyFrom(latest)
                    val (b, seq) = FrameEncoder.encodeSnapshot(frame, tsMs)
                    lastSeqSent = seq
                    bytes = b
                }
                transport.sendSnapshot(bytes)

                nextAtMs += periodMs
                val nowMs = System.nanoTime() / 1_000_000L
                if (nextAtMs <= nowMs) {
                    // Fell behind (GC, scheduler, socket stall). Re-base instead of burst-sending.
                    nextAtMs = nowMs + periodMs
                }
                delay(nextAtMs - nowMs)
            }
        }
    }

    fun stopSender() {
        senderJob?.cancel()
        senderJob = null
    }

    /**
     * Zeroes the snapshot and pushes it a few times off the UI thread.
     *
     * Call after [stopSender]. The server latches the last frame it received until its failsafe
     * fires, so simply going quiet leaves the truck holding whatever steering angle, pedal and keys
     * were live when the deck closed. Three redundant frames because a lone UDP datagram is not
     * guaranteed, and a stuck neutral request is harmless while a stuck full-lock is not.
     *
     * Own daemon thread rather than [scope]: the caller is usually a Compose `onDispose`, and the
     * remembered scope is being cancelled in that same breath (plus a socket write must not run on
     * the main thread).
     */
    fun neutralize() {
        synchronized(snapshotLock) { latest.reset() }
        val frame = InputFrame()
        Thread({
            repeat(3) {
                val tsMs = System.nanoTime() / 1_000_000L and 0xFFFFFFFFL
                val bytes: ByteArray
                synchronized(snapshotLock) {
                    frame.copyFrom(latest)
                    val (b, seq) = FrameEncoder.encodeSnapshot(frame, tsMs)
                    lastSeqSent = seq
                    bytes = b
                }
                transport.sendSnapshot(bytes)
                try { Thread.sleep(12) } catch (_: InterruptedException) { return@Thread }
            }
        }, "europad-neutralize").apply { isDaemon = true }.start()
    }

    /** True while a sender coroutine is live — used to assert the loop really stops. */
    val isSending: Boolean get() = senderJob?.isActive == true
}
