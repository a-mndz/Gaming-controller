package com.europad.app.net

import com.europad.app.input.InputFrame
import java.util.concurrent.atomic.AtomicInteger

object FrameEncoder {
    /**
     * Wire sequence counter, shared by three threads: the 120 Hz sender, the keeper (pings) and the
     * config retry thread. A plain `var` read-modify-write let two of them hand out the same number,
     * and the server drops any snapshot whose seq is not strictly newer than the last one it applied
     * — so a torn increment silently threw away real input. Atomic increment costs nothing here.
     */
    private val seq = AtomicInteger(0)

    /** Header only: magic, version, flags and a fresh sequence number. Payload is the caller's job. */
    private fun encodeHeader(flags: Byte = 0): Pair<ByteArray, Int> {
        val b = ByteArray(Proto.FRAME_SIZE)
        b[Proto.OFF_MAGIC] = Proto.MAGIC_LO
        b[Proto.OFF_MAGIC + 1] = Proto.MAGIC_HI
        b[Proto.OFF_VERSION] = Proto.VERSION
        b[Proto.OFF_FLAGS] = flags
        val s = nextSeq()
        b[Proto.OFF_SEQ] = (s and 0xFF).toByte()
        b[Proto.OFF_SEQ + 1] = ((s shr 8) and 0xFF).toByte()
        return b to s
    }

    fun encodeSnapshot(frame: InputFrame, tsMs: Long): Pair<ByteArray, Int> {
        val (b, s) = encodeHeader(0)
        putU32(b, Proto.OFF_TIMESTAMP, tsMs)
        putU16(b, Proto.OFF_BUTTONS_LO, frame.buttonsLo)
        putU16(b, Proto.OFF_BUTTONS_HI, frame.buttonsHi)
        for (i in 0 until Proto.AXES_COUNT) {
            val v = frame.axes[i].toInt()
            b[Proto.OFF_AXES + i * 2] = (v and 0xFF).toByte()
            b[Proto.OFF_AXES + i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return b to s
    }

    fun encodePing(tsMs: Long): ByteArray {
        val b = ByteArray(Proto.FRAME_SIZE)
        b[Proto.OFF_MAGIC] = Proto.MAGIC_LO
        b[Proto.OFF_MAGIC + 1] = Proto.MAGIC_HI
        b[Proto.OFF_VERSION] = Proto.VERSION
        b[Proto.OFF_FLAGS] = Proto.FLAG_PING_REQUEST
        val s = nextSeq()
        b[Proto.OFF_SEQ] = (s and 0xFF).toByte()
        b[Proto.OFF_SEQ + 1] = ((s shr 8) and 0xFF).toByte()
        putU32(b, Proto.OFF_TIMESTAMP, tsMs)
        return b
    }

    fun encodeHello(pin: Int): ByteArray {
        val b = ByteArray(Proto.FRAME_SIZE)
        b[Proto.OFF_MAGIC] = Proto.MAGIC_LO
        b[Proto.OFF_MAGIC + 1] = Proto.MAGIC_HI
        b[Proto.OFF_VERSION] = Proto.VERSION
        b[Proto.OFF_FLAGS] = Proto.FLAG_HELLO
        putU16(b, Proto.OFF_BUTTONS_LO, pin)
        return b
    }

    /**
     * Config write-through (remapping): lo = kind | (hiBit shl 8), hi = len | (firstChar shl 8),
     * the remaining chars fill the 8 axis bytes two each (low byte first). Server mirrors this
     * layout in InputFrame.PayloadText.
     */
    fun encodeSetBitKey(bitIndex: Int, key: String): ByteArray {
        val name = key.take(17)
        val b = ByteArray(Proto.FRAME_SIZE)
        b[Proto.OFF_MAGIC] = Proto.MAGIC_LO
        b[Proto.OFF_MAGIC + 1] = Proto.MAGIC_HI
        b[Proto.OFF_VERSION] = Proto.VERSION
        b[Proto.OFF_FLAGS] = Proto.FLAG_CONFIG
        putU16(b, Proto.OFF_BUTTONS_LO, Proto.CFG_SET_BIT_KEY.toInt() or ((bitIndex and 0xFF) shl 8))
        val first = if (name.isEmpty()) 0 else name[0].code and 0xFF
        putU16(b, Proto.OFF_BUTTONS_HI, name.length or (first shl 8))
        for (i in 0 until Proto.AXES_COUNT) {
            val lo = if (1 + i * 2 < name.length) name[1 + i * 2].code and 0xFF else 0
            val hi = if (2 + i * 2 < name.length) name[2 + i * 2].code and 0xFF else 0
            b[Proto.OFF_AXES + i * 2] = (lo and 0xFF).toByte()
            b[Proto.OFF_AXES + i * 2 + 1] = (hi and 0xFF).toByte()
        }
        return b
    }

    fun parseTimestamp(buf: ByteArray): Long {
        require(buf.size >= Proto.OFF_TIMESTAMP + 4)
        return (buf[Proto.OFF_TIMESTAMP].toLong() and 0xFF) or
                ((buf[Proto.OFF_TIMESTAMP + 1].toLong() and 0xFF) shl 8) or
                ((buf[Proto.OFF_TIMESTAMP + 2].toLong() and 0xFF) shl 16) or
                ((buf[Proto.OFF_TIMESTAMP + 3].toLong() and 0xFF) shl 24)
    }

    fun parseFlags(buf: ByteArray): Byte = buf[Proto.OFF_FLAGS]

    private fun nextSeq(): Int = seq.incrementAndGet() and 0xFFFF

    private fun putU16(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
    }

    private fun putU32(b: ByteArray, off: Int, v: Long) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
        b[off + 2] = ((v shr 16) and 0xFF).toByte()
        b[off + 3] = ((v shr 24) and 0xFF).toByte()
    }
}
