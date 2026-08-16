package com.europad.app.input

class InputFrame {
    var buttonsLo: Int = 0
    var buttonsHi: Int = 0
    val axes = ShortArray(8)

    fun reset() {
        buttonsLo = 0
        buttonsHi = 0
        axes.fill(0)
    }

    fun copyFrom(other: InputFrame) {
        buttonsLo = other.buttonsLo
        buttonsHi = other.buttonsHi
        System.arraycopy(other.axes, 0, axes, 0, axes.size)
    }
}

object ButtonLo {
    const val DPAD_UP = 1 shl 0
    const val DPAD_RIGHT = 1 shl 1
    const val DPAD_DOWN = 1 shl 2
    const val DPAD_LEFT = 1 shl 3
    const val START = 1 shl 4
    const val BACK = 1 shl 5
    const val LB = 1 shl 6
    const val RB = 1 shl 7
    const val A = 1 shl 8
    const val B = 1 shl 9
    const val X = 1 shl 10
    const val Y = 1 shl 11
    const val GUIDE = 1 shl 12
}

object ButtonHi {
    const val IND_L = 1 shl 0
    const val IND_R = 1 shl 1
    const val HAZARD = 1 shl 2
    const val HORN = 1 shl 3
    const val AIR_HORN = 1 shl 4
    const val HANDBRAKE = 1 shl 5
    const val LIGHTS = 1 shl 6
    const val BEAM = 1 shl 7
    const val WARNING = 1 shl 8
    const val WIPERS = 1 shl 9
    const val EXH_BRAKE = 1 shl 10
    const val DIFF_LOCK = 1 shl 11
    const val AXLE_RAISE = 1 shl 12
    const val ENGINE = 1 shl 13
    const val GEAR_UP = 1 shl 14
    const val GEAR_DN = 1 shl 15
}
