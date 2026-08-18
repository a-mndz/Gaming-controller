package com.europad.app.input

/**
 * Two-stage brake pedal.
 *
 * A press applies [BRAKE_FIRST_LEVEL] straight away, and holding the pedal down past
 * [BRAKE_STAGE_MS] commits to a full stop. That gives one control two distinct jobs: a dab for
 * speed limits, roundabouts and traffic, and a deliberate hold for a real stop — without turning the
 * pedal into an analog slider you have to aim at.
 *
 * The wire axis is a 0..32767 trigger and the server scales it with `clamped * 255 / 32767`, so 0.5
 * arrives at ETS2 as 127 of 255 — genuinely half brake, not an on/off press.
 */
object PedalStage {
    /** Hold longer than this and the brake goes to the floor. */
    const val BRAKE_STAGE_MS = 3000L

    /** Brake level applied for the first [BRAKE_STAGE_MS] of a hold. */
    const val BRAKE_FIRST_LEVEL = 0.5f

    /** Pedal level for a press that has been held [heldMs]. `stageMs <= 0` means plain on/off. */
    fun levelForHold(
        heldMs: Long,
        stageMs: Long = BRAKE_STAGE_MS,
        firstLevel: Float = BRAKE_FIRST_LEVEL,
    ): Float = if (stageMs <= 0L || heldMs >= stageMs) 1f else firstLevel.coerceIn(0f, 1f)

    /** 0f..1f pedal level to the protocol's 0..32767 trigger word. */
    fun axis(level: Float): Short = (level.coerceIn(0f, 1f) * Short.MAX_VALUE).toInt().toShort()
}
