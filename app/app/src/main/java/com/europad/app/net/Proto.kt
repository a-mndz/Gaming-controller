package com.europad.app.net

object Proto {
    const val MAGIC: Int = 0x01E0
    const val MAGIC_LO: Byte = 0xE0.toByte()
    const val MAGIC_HI: Byte = 0x01
    const val VERSION: Byte = 1
    const val FRAME_SIZE = 30
    const val AXES_COUNT = 8
    const val DEFAULT_PORT = 47910

    const val FLAG_PING_REQUEST: Byte = 1 shl 0
    const val FLAG_PING_REPLY: Byte = 1 shl 1
    const val FLAG_HELLO: Byte = 1 shl 2
    const val FLAG_RUMBLE: Byte = 1 shl 3
    const val FLAG_ACK: Byte = 1 shl 4
    const val FLAG_REJECT: Byte = 1 shl 5

    const val OFF_MAGIC = 0
    const val OFF_VERSION = 2
    const val OFF_FLAGS = 3
    const val OFF_SEQ = 4
    const val OFF_TIMESTAMP = 6
    const val OFF_BUTTONS_LO = 10
    const val OFF_BUTTONS_HI = 12
    const val OFF_AXES = 14

    const val REJECT_VERSION_MISMATCH = 1
    const val REJECT_WRONG_PIN = 2
    const val REJECT_LOBBY_FULL = 3

    object Axis {
        const val LX = 0
        const val LY = 1
        const val RX = 2
        const val RY = 3
        const val LT = 4
        const val RT = 5
        const val STEER = 6
        const val AUX0 = 7
    }
}
