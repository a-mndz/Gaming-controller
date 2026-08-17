package com.europad.app

import com.europad.app.net.TransportAdvisor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportAdvisorTest {

    @Test
    fun `no suggestion when under budget`() {
        val a = TransportAdvisor(budgetMs = 20, holdMs = 3000)
        assertFalse(a.observe(10, 0))
        assertFalse(a.observe(15, 1000))
        assertFalse(a.observe(19, 4000))
    }

    @Test
    fun `suggestion only after the hold window of sustained over-budget samples`() {
        val a = TransportAdvisor(budgetMs = 20, holdMs = 3000)
        assertFalse(a.observe(25, 0))
        assertFalse(a.observe(30, 2000)) // 2s over, not yet 3s
        assertTrue(a.observe(30, 3000))  // exactly 3s sustained
        assertTrue(a.observe(30, 5000))  // condition holds
    }

    @Test
    fun `single good sample resets the hold timer`() {
        val a = TransportAdvisor(budgetMs = 20, holdMs = 3000)
        a.observe(30, 0)
        a.observe(30, 2900)
        assertFalse(a.observe(10, 3000)) // recovers
        assertFalse(a.observe(30, 3100)) // over again — timer restarts
        assertFalse(a.observe(30, 5000)) // only ~2s since restart
        assertTrue(a.observe(30, 6200))
    }

    @Test
    fun `negative rtt keeps last verdict`() {
        val a = TransportAdvisor(budgetMs = 20, holdMs = 3000)
        a.observe(30, 0)
        a.observe(30, 3100) // suggest = true
        assertTrue(a.observe(-1, 4000))
    }

    @Test
    fun `reset clears state`() {
        val a = TransportAdvisor(budgetMs = 20, holdMs = 3000)
        a.observe(30, 0)
        a.observe(30, 3100)
        a.reset()
        assertFalse(a.suggest)
    }
}
