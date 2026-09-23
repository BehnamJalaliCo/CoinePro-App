package com.coinepro.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The alert that reads as armed and has never been checked — run Τ2, B6.
 *
 * The assertion this file exists for is the fresh install with no network: **every** alert is
 * unchecked when no pass has ever read prices, because that is exactly the reader who most needs
 * to be told. The second is the opposite guard — that a pill never appears over an alert the
 * reader themselves switched off, or over a row from before the app recorded creation times.
 */
class AlertReachTest {

    private fun alert(id: String, createdAt: Long, active: Boolean = true) = LocalPriceAlert(
        id = id,
        symbol = "BTCUSDT",
        condition = LocalAlertCondition.ABOVE,
        value = 100.0,
        active = active,
        createdAtEpochMillis = createdAt,
    )

    @Test
    fun `no alerts is nothing to report`() {
        assertEquals(emptySet<String>(), AlertReach.uncheckedIds(emptyList(), null))
        assertEquals(emptySet<String>(), AlertReach.uncheckedIds(emptyList(), 1_000L))
    }

    @Test
    fun `a reader whose alerts have never been checked is told about all of them`() {
        // The fresh install with no network. The screen says «فعال» over alerts that have not once
        // been compared against a price, and this is the case where saying so matters most.
        val alerts = listOf(alert("a", 1_000L), alert("b", 2_000L))
        assertEquals(setOf("a", "b"), AlertReach.uncheckedIds(alerts, null))
    }

    @Test
    fun `an alert older than the last pass has been checked`() {
        assertEquals(emptySet<String>(), AlertReach.uncheckedIds(listOf(alert("a", 1_000L)), 2_000L))
    }

    @Test
    fun `an alert created since the last pass has not`() {
        val alerts = listOf(alert("old", 1_000L), alert("new", 3_000L))
        assertEquals(setOf("new"), AlertReach.uncheckedIds(alerts, 2_000L))
    }

    @Test
    fun `an alert created exactly at the pass counts as checked`() {
        // The pass reads the store and then stamps, so an alert bearing the pass's own instant was
        // in that read. Calling it unchecked would put a pill on a row that had just been looked at.
        assertEquals(emptySet<String>(), AlertReach.uncheckedIds(listOf(alert("a", 2_000L)), 2_000L))
    }

    @Test
    fun `a paused alert is never reported`() {
        // It is not failing to watch; it was told not to. Reporting the reader's own decision back
        // to them as a problem is the opposite of what this pill is for.
        val alerts = listOf(alert("off", 3_000L, active = false), alert("on", 3_000L))
        assertEquals(setOf("on"), AlertReach.uncheckedIds(alerts, 2_000L))
    }

    @Test
    fun `a row from before creation times were recorded counts as checked`() {
        // Zero is «unknown», not «the beginning of time». Treating it as unchecked would put a pill
        // on every old alert on the first run after an update — a lie about all of them, told to
        // avoid being wrong about none.
        assertEquals(emptySet<String>(), AlertReach.uncheckedIds(listOf(alert("legacy", 0L)), null))
    }

    @Test
    fun `the answer is a set of ids and nothing else`() {
        // Ids rather than alerts, so the screen looks up its own rows. A second copy of an alert
        // carried alongside the list is a copy that can disagree with it.
        val ids = AlertReach.uncheckedIds(listOf(alert("a", 3_000L)), 1_000L)
        assertTrue(ids.contains("a"))
        assertEquals(1, ids.size)
    }
}
