package com.europad.app.ui

/**
 * ETS2 gears are *relative*: GEAR_UP (Shift) and GEAR_DN (Ctrl) each move one step over R-N-D.
 * A stateless "R = one Ctrl" mapping desyncs the moment the truck is not in N, so the selector has
 * to plan the number of presses from the gear it believes it is in.
 *
 * No Compose types in here so the numbers can be unit-tested on the JVM.
 */
object GearShift {
    val order = listOf("R", "N", "D")

    /**
     * Presses needed to get from [from] to [to].
     *
     * @return `up to presses` — `up == true` means GEAR_UP (Shift), `false` means GEAR_DN (Ctrl).
     *   `null` when already in that gear, or when either name is not a known gear (nothing to send).
     */
    fun plan(from: String, to: String): Pair<Boolean, Int>? {
        val a = order.indexOf(from)
        val b = order.indexOf(to)
        if (a < 0 || b < 0 || a == b) return null
        return (b > a) to (if (b > a) b - a else a - b)
    }
}
