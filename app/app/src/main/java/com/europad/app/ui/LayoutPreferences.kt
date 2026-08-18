package com.europad.app.ui

import android.content.SharedPreferences
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages persistent storage of custom control element positions.
 * Positions are stored as fractional values (0.0-1.0) for resolution independence.
 *
 * Layouts are stored separately for wheel and gyro modes, allowing independent customization.
 */
object LayoutPreferences {
    private const val KEY_LAYOUT_WHEEL = "layout_wheel"
    private const val KEY_LAYOUT_GYRO = "layout_gyro"
    private const val KEY_FIRST_TIME_EDIT = "layout_first_time_edit"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    /**
     * Load custom positions for the given steering mode.
     * Returns null if no custom layout exists or if data is corrupted, indicating defaults should be used.
     *
     * @param prefs SharedPreferences instance to read from
     * @param mode "wheel" or "gyro"
     * @return Map of element IDs to positions, or null if not found/corrupted
     */
    fun load(prefs: SharedPreferences, mode: String): Map<String, ElementPosition>? {
        val key = if (mode == "gyro") KEY_LAYOUT_GYRO else KEY_LAYOUT_WHEEL
        val stored = prefs.getString(key, null) ?: return null

        return try {
            val preset = json.decodeFromString<LayoutPreset>(stored)
            preset.elements
                .mapValues { (_, pos) -> pos.clamp() }
                .takeIf { it.isNotEmpty() }
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Save custom positions for the given steering mode.
     * Serializes to JSON format in SharedPreferences.
     *
     * @param prefs SharedPreferences instance to write to
     * @param mode "wheel" or "gyro"
     * @param positions Map of element IDs to positions
     */
    fun save(prefs: SharedPreferences, mode: String, positions: Map<String, ElementPosition>) {
        val key = if (mode == "gyro") KEY_LAYOUT_GYRO else KEY_LAYOUT_WHEEL
        val preset = LayoutPreset(
            mode = mode,
            elements = positions.mapValues { (_, pos) -> pos.clamp() }
        )

        prefs.edit()
            .putString(key, json.encodeToString(preset))
            .apply()
    }

    /**
     * Clear custom positions for the given mode, reverting to default layout.
     *
     * @param prefs SharedPreferences instance to modify
     * @param mode "wheel" or "gyro"
     */
    fun clear(prefs: SharedPreferences, mode: String) {
        val key = if (mode == "gyro") KEY_LAYOUT_GYRO else KEY_LAYOUT_WHEEL
        prefs.edit()
            .remove(key)
            .apply()
    }

    /**
     * Clear all custom layouts (both wheel and gyro modes).
     *
     * @param prefs SharedPreferences instance to modify
     */
    fun clearAll(prefs: SharedPreferences) {
        prefs.edit()
            .remove(KEY_LAYOUT_WHEEL)
            .remove(KEY_LAYOUT_GYRO)
            .apply()
    }

    /**
     * Check if a custom layout exists for the given mode.
     *
     * @param prefs SharedPreferences instance to check
     * @param mode "wheel" or "gyro"
     * @return true if a custom layout exists
     */
    fun hasCustomLayout(prefs: SharedPreferences, mode: String): Boolean {
        val key = if (mode == "gyro") KEY_LAYOUT_GYRO else KEY_LAYOUT_WHEEL
        return prefs.contains(key)
    }

    /**
     * Check if the user has entered edit mode before.
     * Returns false on first entry, true afterwards.
     *
     * @param prefs SharedPreferences instance to check
     * @return true if user has seen edit mode instructions before
     */
    fun hasSeenEditModeInstructions(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(KEY_FIRST_TIME_EDIT, false)
    }

    /**
     * Mark that the user has seen edit mode instructions.
     * Called after the first-time instruction overlay is dismissed.
     *
     * @param prefs SharedPreferences instance to modify
     */
    fun markEditModeInstructionsSeen(prefs: SharedPreferences) {
        prefs.edit()
            .putBoolean(KEY_FIRST_TIME_EDIT, true)
            .apply()
    }
}
