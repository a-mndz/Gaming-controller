package com.europad.app.ui

import kotlinx.serialization.Serializable

/**
 * Represents a control element's position as fractional values (0.0-1.0) relative to canvas dimensions.
 * Used for persisting custom layout configurations.
 *
 * @property id Element identifier (e.g., "LIGHTS", "WHEEL", "ACCEL")
 * @property cx Center X as fraction of canvas width (0.0-1.0)
 * @property cy Center Y as fraction of canvas height (0.0-1.0)
 * @property w Width as fraction of canvas width (0.0-1.0)
 * @property h Height as fraction of canvas height (0.0-1.0)
 */
@Serializable
data class ElementPosition(
    val id: String,
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float
) {
    /**
     * Returns true if all position values are within valid range [0.0, 1.0].
     */
    fun isValid(): Boolean = cx in 0.0f..1.0f && cy in 0.0f..1.0f && w in 0.0f..1.0f && h in 0.0f..1.0f

    /**
     * Returns a clamped version of this position with all values constrained to [0.0, 1.0].
     */
    fun clamp(): ElementPosition = ElementPosition(
        id = id,
        cx = cx.coerceIn(0.0f, 1.0f),
        cy = cy.coerceIn(0.0f, 1.0f),
        w = w.coerceIn(0.0f, 1.0f),
        h = h.coerceIn(0.0f, 1.0f)
    )

    /**
     * Converts this ElementPosition to a DeckRect for use in layout calculations.
     */
    fun toDeckRect() = DeckRect(cx, cy, w, h)

    companion object {
        /**
         * Creates an ElementPosition from a DeckRect.
         */
        fun fromDeckRect(id: String, rect: DeckRect) = ElementPosition(
            id = id,
            cx = rect.cx,
            cy = rect.cy,
            w = rect.w,
            h = rect.h
        )

        /**
         * Creates a validated ElementPosition, clamping values to [0.0, 1.0].
         */
        fun create(id: String, cx: Float, cy: Float, w: Float, h: Float): ElementPosition {
            return ElementPosition(
                id = id,
                cx = cx.coerceIn(0.0f, 1.0f),
                cy = cy.coerceIn(0.0f, 1.0f),
                w = w.coerceIn(0.0f, 1.0f),
                h = h.coerceIn(0.0f, 1.0f)
            )
        }
    }
}

/**
 * Pure drag/resize/overlap math for the layout editor. Fractions of the *inner* canvas (the deck
 * area inside its 3% margin), so the editor and [DeckLayout.place]-style rendering agree exactly.
 * No Compose types so it unit-tests on the JVM.
 */
object LayoutEdit {
    /** Accessibility floor for an interactive element (Requirement 9.2). */
    const val MIN_TOUCH_DP = 44f

    /** Moves an element to [cx]/[cy], clamped so it stays fully inside the canvas. */
    fun moved(p: ElementPosition, cx: Float, cy: Float): ElementPosition = ElementPosition.create(
        id = p.id,
        cx = cx.coerceIn(p.w / 2f, 1f - p.w / 2f),
        cy = cy.coerceIn(p.h / 2f, 1f - p.h / 2f),
        w = p.w,
        h = p.h,
    )

    /**
     * Resizes an element by its bottom-right corner: the top-left edge stays put, so [w]/[h] are
     * clamped to at least [MIN_TOUCH_DP] and at most the room left on the canvas.
     *
     * @param canvasWDp inner canvas width in dp, @param canvasHDp inner canvas height in dp
     */
    fun resized(p: ElementPosition, w: Float, h: Float, canvasWDp: Float, canvasHDp: Float): ElementPosition {
        val left = (p.cx - p.w / 2f).coerceIn(0f, 1f)
        val top = (p.cy - p.h / 2f).coerceIn(0f, 1f)
        // A canvas smaller than the touch target would give minW > 1: cap so the range stays valid.
        val minW = if (canvasWDp > 0f) (MIN_TOUCH_DP / canvasWDp).coerceAtMost(1f - left) else 0f
        val minH = if (canvasHDp > 0f) (MIN_TOUCH_DP / canvasHDp).coerceAtMost(1f - top) else 0f
        val newW = w.coerceIn(minW, 1f - left)
        val newH = h.coerceIn(minH, 1f - top)
        return ElementPosition.create(p.id, left + newW / 2f, top + newH / 2f, newW, newH)
    }

    /** True AABB intersection — matches the spec's overlap definition, no centre-distance fudge. */
    fun overlaps(a: DeckRect, b: DeckRect): Boolean =
        !(a.right <= b.left || a.left >= b.right || a.bottom <= b.top || a.top >= b.bottom)
}

/**
 * Identifies all supported control elements that can be repositioned.
 */
enum class ElementId(val displayName: String) {
    LIGHTS("Lights"),
    WIPER("Wiper"),
    VIPER("Windshield Washer"),
    HANDBRAKE("Handbrake"),
    SETTINGS("Settings"),
    MENU("Menu"),
    CAMERA("Camera"),
    GEAR("Gear Selector"),
    INDICATORS("Turn Signals"),
    WHEEL("Steering Wheel"),
    ACCEL("Accelerator"),
    BRAKE("Brake"),
    GYRO_ACCEL("Gyro Accelerator"),
    GYRO_BRAKE("Gyro Brake");

    companion object {
        /**
         * All elements that exist in wheel/touch mode.
         */
        val wheelModeElements = listOf(
            LIGHTS, WIPER, VIPER, HANDBRAKE, SETTINGS, MENU, CAMERA,
            GEAR, INDICATORS, WHEEL, ACCEL, BRAKE
        )

        /**
         * All elements that exist in gyro mode (no wheel, gyro-specific pedals).
         */
        val gyroModeElements = listOf(
            LIGHTS, WIPER, VIPER, HANDBRAKE, SETTINGS, MENU, CAMERA,
            GEAR, INDICATORS, GYRO_ACCEL, GYRO_BRAKE
        )
    }
}

/**
 * A saved layout preset containing all element positions for a specific mode.
 */
@Serializable
data class LayoutPreset(
    val version: Int = 1,
    val mode: String,
    val elements: Map<String, ElementPosition>,
    val timestamp: Long = System.currentTimeMillis()
)
