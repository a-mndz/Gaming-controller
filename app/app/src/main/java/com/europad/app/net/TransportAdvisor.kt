package com.europad.app.net

/**
 * FR-1.9: if median RTT stays above the deck's latency budget (120 Hz decks: 20 ms, 240 Hz
 * decks: 15 ms) for 3 continuous seconds, the deck should suggest USB or 5 GHz Wi-Fi.
 * Pure state machine — unit-tested without a link.
 */
class TransportAdvisor(private val budgetMs: Long = 20, private val holdMs: Long = 3000) {

    private var overSince = -1L
    private var lastSample = -1L

    /** Feed one RTT sample; returns true while the sustained over-budget condition holds. */
    fun observe(rttMs: Long, nowMs: Long): Boolean {
        if (rttMs < 0) return suggest // no sample — keep the last verdict
        lastSample = nowMs
        if (rttMs > budgetMs) {
            if (overSince < 0) overSince = nowMs
        } else {
            overSince = -1
        }
        return suggest
    }

    val suggest: Boolean
        get() = lastSample >= 0 && overSince in 0..lastSample && lastSample - overSince >= holdMs

    fun reset() {
        overSince = -1
        lastSample = -1
    }
}
