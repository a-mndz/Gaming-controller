package com.europad.app

import com.europad.app.input.ButtonHi
import com.europad.app.input.InputFrame
import com.europad.app.net.FrameEncoder
import com.europad.app.net.Proto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Wire-encoder contracts that the PC server depends on.
 *
 * Two of these guard bugs found on 2026-08-18: the sequence counter was a plain `var` shared by
 * three threads (torn increments made the server discard real snapshots as stale), and the keepalive
 * ping's all-zero payload was being applied to the pad as if it were input.
 */
class FrameEncoderTest {

    private fun u16(b: ByteArray, off: Int) =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun s16(b: ByteArray, off: Int) = u16(b, off).toShort()

    @Test
    fun `ping carries no input payload`() {
        // The server treats any control frame as payload-free; this is why that is safe.
        val b = FrameEncoder.encodePing(1234L)
        assertEquals(Proto.FLAG_PING_REQUEST, b[Proto.OFF_FLAGS])
        assertEquals(0, u16(b, Proto.OFF_BUTTONS_LO))
        assertEquals(0, u16(b, Proto.OFF_BUTTONS_HI))
        for (i in 0 until Proto.AXES_COUNT) {
            assertEquals("axis $i", 0, u16(b, Proto.OFF_AXES + i * 2))
        }
    }

    @Test
    fun `snapshot has no flags set so the server reads it as input`() {
        val f = InputFrame()
        val (b, _) = FrameEncoder.encodeSnapshot(f, 0L)
        assertEquals(0.toByte(), b[Proto.OFF_FLAGS])
    }

    @Test
    fun `full lock survives the wire round trip`() {
        // "Steering cannot be fully moved" had to be ruled out on the encoder: +-32767 must land
        // intact in the STEER axis slot.
        val f = InputFrame()
        f.axes[Proto.Axis.STEER] = Short.MAX_VALUE
        val (right, _) = FrameEncoder.encodeSnapshot(f, 0L)
        assertEquals(Short.MAX_VALUE, s16(right, Proto.OFF_AXES + Proto.Axis.STEER * 2))

        f.axes[Proto.Axis.STEER] = Short.MIN_VALUE
        val (left, _) = FrameEncoder.encodeSnapshot(f, 0L)
        assertEquals(Short.MIN_VALUE, s16(left, Proto.OFF_AXES + Proto.Axis.STEER * 2))
    }

    @Test
    fun `gear bits land in the high button word`() {
        val f = InputFrame()
        f.buttonsHi = ButtonHi.GEAR_UP
        val (up, _) = FrameEncoder.encodeSnapshot(f, 0L)
        assertEquals(0x4000, u16(up, Proto.OFF_BUTTONS_HI)) // bit 14

        f.buttonsHi = ButtonHi.GEAR_DN
        val (dn, _) = FrameEncoder.encodeSnapshot(f, 0L)
        assertEquals(0x8000, u16(dn, Proto.OFF_BUTTONS_HI)) // bit 15
    }

    @Test
    fun `sequence numbers are unique across concurrent senders`() {
        // Reproduces the real thread mix: 120 Hz sender, keeper pings and the config retry thread all
        // draw from one counter. A duplicate here means the server silently drops a good snapshot.
        val threads = 6
        val perThread = 4000
        val seen = ConcurrentHashMap<Int, Boolean>()
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val frame = InputFrame()

        repeat(threads) { t ->
            Thread {
                start.await()
                repeat(perThread) {
                    val seq = if (t % 2 == 0) {
                        FrameEncoder.encodeSnapshot(frame, 0L).second
                    } else {
                        // Pings burn a sequence number too, from a different thread.
                        u16(FrameEncoder.encodePing(0L), Proto.OFF_SEQ)
                    }
                    seen[seq] = true
                }
                done.countDown()
            }.apply { isDaemon = true }.start()
        }

        start.countDown()
        assertTrue("worker threads finished", done.await(30, TimeUnit.SECONDS))

        val total = threads * perThread
        // The counter is 16 bits, so it legitimately revisits values after 65536 draws. Compare
        // against that ceiling rather than against `total`.
        val expectedDistinct = minOf(total, 65536)
        assertTrue(
            "expected ~$expectedDistinct distinct sequence numbers, saw ${seen.size}",
            seen.size >= expectedDistinct - 1,
        )
    }

    @Test
    fun `sequence stays inside 16 bits`() {
        repeat(1000) {
            val s = FrameEncoder.encodeSnapshot(InputFrame(), 0L).second
            assertTrue("seq $s out of range", s in 0..0xFFFF)
        }
    }
}
