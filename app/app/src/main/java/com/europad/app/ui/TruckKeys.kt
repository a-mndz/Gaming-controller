package com.europad.app.ui

import com.europad.app.input.ButtonHi

/**
 * The single keymap both phone decks render. Names/indexes must stay in lock-step with the
 * server's ProfileManager.HiBitNames — remaps go over the wire as a bit index into that list.
 */
object TruckKeys {
    val names = listOf(
        "IND_L", "IND_R", "HAZARD", "HORN", "AIR_HORN", "HANDBRAKE",
        "LIGHTS", "BEAM", "WARNING", "WIPERS", "EXH_BRAKE", "DIFF_LOCK",
        "AXLE_RAISE", "ENGINE", "GEAR_UP", "GEAR_DN",
    )

    val labels = mapOf(
        "IND_L" to "Indicator L",
        "IND_R" to "Indicator R",
        "HAZARD" to "Hazards",
        "HORN" to "Horn",
        "AIR_HORN" to "Air Horn",
        "HANDBRAKE" to "Handbrake",
        "LIGHTS" to "Lights",
        "BEAM" to "High Beam",
        "WARNING" to "Warn Beacon",
        "WIPERS" to "Wipers",
        "EXH_BRAKE" to "Exh. Brake",
        "DIFF_LOCK" to "Diff Lock",
        "AXLE_RAISE" to "Axle Raise",
        "ENGINE" to "Engine",
        "GEAR_UP" to "Gear Up",
        "GEAR_DN" to "Gear Down",
    )

    val bits = mapOf(
        "IND_L" to ButtonHi.IND_L,
        "IND_R" to ButtonHi.IND_R,
        "HAZARD" to ButtonHi.HAZARD,
        "HORN" to ButtonHi.HORN,
        "AIR_HORN" to ButtonHi.AIR_HORN,
        "HANDBRAKE" to ButtonHi.HANDBRAKE,
        "LIGHTS" to ButtonHi.LIGHTS,
        "BEAM" to ButtonHi.BEAM,
        "WARNING" to ButtonHi.WARNING,
        "WIPERS" to ButtonHi.WIPERS,
        "EXH_BRAKE" to ButtonHi.EXH_BRAKE,
        "DIFF_LOCK" to ButtonHi.DIFF_LOCK,
        "AXLE_RAISE" to ButtonHi.AXLE_RAISE,
        "ENGINE" to ButtonHi.ENGINE,
        "GEAR_UP" to ButtonHi.GEAR_UP,
        "GEAR_DN" to ButtonHi.GEAR_DN,
    )

    /** Same defaults the server ships in its profiles, so a fresh install matches the PC side. */
    val defaults = mapOf(
        "IND_L" to "[", "IND_R" to "]", "HAZARD" to "F", "HORN" to "H",
        "AIR_HORN" to "N", "HANDBRAKE" to "Space", "LIGHTS" to "L",
        "BEAM" to "K", "WARNING" to "O", "WIPERS" to "P",
        "EXH_BRAKE" to "B", "DIFF_LOCK" to "V", "AXLE_RAISE" to "U",
        "ENGINE" to "E", "GEAR_UP" to "Shift", "GEAR_DN" to "Ctrl",
    )

    /** Everything the server's VkLookup can press. */
    val keyChoices = run {
        val specials = listOf(
            "Space", "Enter", "Shift", "Ctrl", "Alt", "Tab", "Backspace", "Esc",
            "Up", "Down", "Left", "Right",
        )
        val letters = ('A'..'Z').map { it.toString() }
        val digits = ('0'..'9').map { it.toString() }
        val fkeys = (1..12).map { "F$it" }
        letters + digits + specials + fkeys
    }
}
