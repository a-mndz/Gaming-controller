package com.europad.app.input

import com.europad.app.net.FrameEncoder
import com.europad.app.net.UdpTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class DeckEngine(
    private val transport: UdpTransport,
    private val scope: CoroutineScope,
) {
    private val latest = InputFrame()
    private val snapshotLock = Any()
    private var senderJob: Job? = null
    @Volatile private var lastSeqSent = 0

    /** Set by [setInput], cleared by the sender. Volatile: written on the UI thread, read on IO. */
    @Volatile private var dirty = false

    /**
     * Wakes the sender the moment input changes. CONFLATED because the only information carried is
     * "something changed" — a burst of finger movements between two sends must not queue a burst of
     * packets, and a signal that arrives before the sender waits is kept, so no wakeup is ever lost.
     */
    private var wake = Channel<Unit>(Channel.CONFLATED)

    fun setInput(mutator: (InputFrame) -> Unit) {
        synchronized(snapshotLock) { mutator(latest) }
        dirty = true
        wake.trySend(Unit)
    }

    /**
     * Streams the snapshot: immediately when it changes, and as a slow heartbeat when it does not.
     *
     * [rateHz] is now a **ceiling**, not a metronome. The old loop sent strictly every `1000/rateHz`
     * ms, which quietly added a whole period of latency to every input: move your finger 1 ms after a
     * tick and the frame waited 7 ms before it was even encoded. At 120 Hz that is up to 8 ms of pure
     * queueing on top of Wi-Fi, sensor and game-loop delay, and it is the cheapest latency in the whole
     * chain to delete — a change now wakes the loop through [wake] and goes out on the next scheduler
     * slot, with [rateHz] only there to stop a fast drag from flooding a congested 2.4 GHz link.
     *
     * The heartbeat matters as much as the fast path. UDP has no retransmit, so the frame that says
     * "steering is back at centre" can simply vanish; re-sending the current snapshot every
     * [heartbeatMs] means any lost state is repaired a few milliseconds later, and it keeps the
     * server's 800 ms failsafe fed while the driver holds a steady input. Idle traffic actually drops
     * versus the old fixed 120 Hz stream.
     *
     * Two hard-won properties are preserved: the loop is cancellable (only `delay`/`withTimeoutOrNull`
     * suspend, never `Thread.sleep`, so `stopSender()` really stops it — a leaked sender broadcasting a
     * frozen frame *is* the "steering never comes back to centre" bug), and pacing is measured against
     * a monotonic clock so encode and socket costs do not accumulate into drift.
     */
    fun startSender(rateHz: Int, heartbeatMs: Long = 12L) {
        senderJob?.cancel()
        val minGapMs = (1000L / rateHz.coerceIn(1, 500)).coerceAtLeast(1L)
        // A heartbeat faster than the send ceiling would turn the ceiling into the real rate.
        val beatMs = heartbeatMs.coerceAtLeast(minGapMs + 1L)
        val ch = Channel<Unit>(Channel.CONFLATED)
        wake = ch
        dirty = true // first frame goes out at once, so the server has state before anything moves
        senderJob = scope.launch(Dispatchers.IO) {
            val frame = InputFrame()
            var lastSentMs = 0L
            while (isActive) {
                val nowMs = System.nanoTime() / 1_000_000L
                val sinceMs = nowMs - lastSentMs

                if (sinceMs < minGapMs) {
                    // Rate ceiling: a change is pending but the link has had a frame too recently.
                    delay(minGapMs - sinceMs)
                    continue
                }

                if (dirty || sinceMs >= beatMs) {
                    dirty = false
                    sendSnapshotNow(frame)
                    lastSentMs = System.nanoTime() / 1_000_000L
                    continue
                }

                // Idle: sleep until input changes or the heartbeat falls due, whichever comes first.
                withTimeoutOrNull(beatMs - sinceMs) { ch.receive() }
            }
        }
    }

    private fun sendSnapshotNow(scratch: InputFrame) {
        val tsMs = System.nanoTime() / 1_000_000L and 0xFFFFFFFFL
        val bytes: ByteArray
        synchronized(snapshotLock) {
            scratch.copyFrom(latest)
            val (b, seq) = FrameEncoder.encodeSnapshot(scratch, tsMs)
            lastSeqSent = seq
            bytes = b
        }
        transport.sendSnapshot(bytes)
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
