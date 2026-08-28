package com.coinepro.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a local alert fires, and — more importantly — when it does not fire again.
 *
 * The repeat rules are the half of this that people notice. An "above 65,000" alert with no repeat
 * policy fires on every evaluation while the price sits above the line, which around a threshold is
 * a stream of identical notifications and the last thing the reader does before muting the app.
 */
class LocalPriceAlertTest {

    private val base = LocalPriceAlert(
        id = "a",
        symbol = "BTCUSDT",
        condition = LocalAlertCondition.ABOVE,
        value = 65_000.0,
        createdAtEpochMillis = 0L,
    )

    @Test
    fun `above and below fire at the boundary, not past it`() {
        assertTrue(LocalPriceAlert.due(base, 65_000.0, null, 1L))
        assertFalse(LocalPriceAlert.due(base, 64_999.99, null, 1L))

        val below = base.copy(condition = LocalAlertCondition.BELOW, value = 60_000.0)
        assertTrue(LocalPriceAlert.due(below, 60_000.0, null, 1L))
        assertFalse(LocalPriceAlert.due(below, 60_000.01, null, 1L))
    }

    @Test
    fun `percent conditions measure from the price when the alert was made`() {
        val up = base.copy(
            condition = LocalAlertCondition.PERCENT_UP,
            value = 5.0,
            referencePrice = 100.0,
        )
        assertTrue(LocalPriceAlert.due(up, 105.0, null, 1L))
        assertFalse(LocalPriceAlert.due(up, 104.9, null, 1L))

        val down = up.copy(condition = LocalAlertCondition.PERCENT_DOWN)
        assertTrue(LocalPriceAlert.due(down, 95.0, null, 1L))
        assertFalse(LocalPriceAlert.due(down, 95.1, null, 1L))
    }

    /** Without a reference there is no percentage. Refusing beats inventing one from the tick. */
    @Test
    fun `a percent alert with no reference never fires`() {
        val orphan = base.copy(condition = LocalAlertCondition.PERCENT_UP, value = 5.0, referencePrice = null)
        assertFalse(LocalPriceAlert.due(orphan, 1_000_000.0, null, 1L))
    }

    /**
     * A feed that sent no 24-hour figure has not said the market is flat.
     *
     * Treating the absence as a zero would fire every "fell 5%" alert the moment a quote arrived
     * without one — which is what the public route does for a symbol it has just started carrying.
     */
    @Test
    fun `a missing 24-hour change is not a reading of zero`() {
        val over = base.copy(condition = LocalAlertCondition.CHANGE_24H_OVER, value = 5.0)
        val under = base.copy(condition = LocalAlertCondition.CHANGE_24H_UNDER, value = 5.0)
        assertFalse(LocalPriceAlert.due(over, 100.0, null, 1L))
        assertFalse(LocalPriceAlert.due(under, 100.0, null, 1L))
        assertTrue(LocalPriceAlert.due(over, 100.0, 5.0, 1L))
        assertTrue(LocalPriceAlert.due(under, 100.0, -5.0, 1L))
    }

    @Test
    fun `a one-shot deactivates itself and never fires twice`() {
        val fired = base.fired(atEpochMillis = 1_000L)
        assertFalse(fired.active)
        assertFalse(LocalPriceAlert.due(fired, 70_000.0, null, 2_000L))
    }

    @Test
    fun `a daily alert waits a day and stays active`() {
        val fired = base.copy(repeat = AlertRepeat.DAILY).fired(atEpochMillis = 0L)
        assertTrue(fired.active)
        val day = 24 * 60 * 60 * 1000L
        assertFalse(LocalPriceAlert.due(fired, 70_000.0, null, day - 1))
        assertTrue(LocalPriceAlert.due(fired, 70_000.0, null, day))
    }

    @Test
    fun `an always alert still has a cooldown`() {
        val fired = base.copy(repeat = AlertRepeat.ALWAYS).fired(atEpochMillis = 0L)
        assertFalse(
            "Without this an alert fires on every evaluation while the condition holds",
            LocalPriceAlert.due(fired, 70_000.0, null, LocalPriceAlert.COOLDOWN_MILLIS - 1),
        )
        assertTrue(LocalPriceAlert.due(fired, 70_000.0, null, LocalPriceAlert.COOLDOWN_MILLIS))
    }

    @Test
    fun `an inactive alert is never due`() {
        assertFalse(LocalPriceAlert.due(base.copy(active = false), 70_000.0, null, 1L))
    }

    @Test
    fun `every percent condition is marked as one`() {
        assertEquals(
            listOf(
                LocalAlertCondition.PERCENT_UP,
                LocalAlertCondition.PERCENT_DOWN,
                LocalAlertCondition.CHANGE_24H_OVER,
                LocalAlertCondition.CHANGE_24H_UNDER,
            ),
            LocalAlertCondition.entries.filter(LocalAlertCondition::isPercent),
        )
    }
}
