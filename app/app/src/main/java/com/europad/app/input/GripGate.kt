package com.europad.app.input

/**
 * Schmitt-trigger gate over the gravity in-plane ratio, deciding whether the grip is usable for
 * tilt steering.
 *
 * Why a plain threshold is not enough: a grip that hovers near the flat point trips the flag on
 * single samples — noise, a cornering dip, a slouched lean-back — and every trip used to drop the
 * driver's declared centre and re-seed it from whatever tilt came next. Felt from the driver's
 * seat: the wheel occasionally lets go and pulls straight mid-corner. Three guards, all needed:
 *
 *  1. **Hysteresis** — leaving "usable" trips at [TRIP_RATIO], but re-entering requires the
 *     stronger [RECOVER_RATIO], so a grip wandering along one threshold cannot flap.
 *  2. **Dwell** — a too-flat reading only *dips*: the last steering value freezes (what a held
 *     wheel does) and the centre is kept. Only [FLAT_DWELL_MS] of continuous dip commits to
 *     [State.Flat] — that is a phone set down, not a stir.
 *  3. **Freeze semantics** — a Dip freezes instead of bleeding, so a 200 ms wobble mid-corner
 *     cannot soften the axis the way the old immediate bleed-to-centre did.
 *
 * Pure arithmetic over per-sample ratios and intervals so the whole policy runs on the JVM.
 */
class GripGate(
    private val tripRatio: Float = TRIP_RATIO,
    private val recoverRatio: Float = RECOVER_RATIO,
    private val dwellMs: Float = FLAT_DWELL_MS,
) {
    enum class State { Usable, Dip, Flat }

    /** True once a dip has outlasted the dwell; cleared only by [RECOVER_RATIO] or [reset]. */
    var isFlat: Boolean = false
        private set

    private var dipMs = 0f

    fun sample(ratio: Float, dtMs: Float): State {
        if (isFlat) {
            if (ratio >= recoverRatio) {
                isFlat = false
                dipMs = 0f
                return State.Usable
            }
            return State.Flat
        }

        if (ratio >= tripRatio) {
            dipMs = 0f
            return State.Usable
        }

        dipMs += dtMs.coerceAtLeast(0f)
        return if (dipMs >= dwellMs) {
            isFlat = true
            State.Flat
        } else {
            State.Dip
        }
    }

    fun reset() {
        isFlat = false
        dipMs = 0f
    }

    companion object {
        /** In-plane ratio below which the angle is too flat to trust. Matches the old threshold. */
        const val TRIP_RATIO = 0.26f

        /** Ratio required to come back off a committed flat — above trip, so the seam cannot flap. */
        const val RECOVER_RATIO = 0.35f

        /** Continuous too-flat time that means "set down", not "stirring through a soft grip". */
        const val FLAT_DWELL_MS = 350f
    }
}
