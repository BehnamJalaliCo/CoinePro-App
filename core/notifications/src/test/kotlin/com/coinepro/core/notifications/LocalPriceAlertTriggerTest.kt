package com.coinepro.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The half of an alert that the older tests do not cover: expiry, and the trigger-aware evaluation.
 *
 * Two properties here are promises to the reader rather than implementation details. Nothing
 * expires on its own — the one thing TradingView's free tier does that its users resent, and this
 * app does not do it. And an alert written before any of this existed evaluates exactly as it did
 * before, because the trigger is an addition and not a replacement.
 */
class LocalPriceAlertTriggerTest {

    private val base = LocalPriceAlert(
        id = "a",
        symbol = "BTCUSDT",
        condition = LocalAlertCondition.ABOVE,
        value = 65_000.0,
        repeat = AlertRepeat.ALWAYS,
        createdAtEpochMillis = 0L,
    )

    private val barStart = 1_700_000_000_000L

    /** Null expiry is the default and it means never. Nothing here should change that. */
    @Test
    fun `an alert with no expiry never expires`() {
        assertEquals(null, base.expiresAt)
        assertFalse(base.hasExpired(Long.MAX_VALUE))
        assertTrue(LocalPriceAlert.due(base, 70_000.0, null, Long.MAX_VALUE))
    }

    @Test
    fun `an expiry the reader set stops the alert at the instant itself`() {
        val expiring = base.copy(expiresAt = barStart)
        assertFalse(expiring.hasExpired(barStart - 1L))
        assertTrue(expiring.hasExpired(barStart))
        assertTrue(LocalPriceAlert.due(expiring, 70_000.0, null, barStart - 1L))
        assertFalse(LocalPriceAlert.due(expiring, 70_000.0, null, barStart))
    }

    /** An alert made before triggers existed still evaluates through its flat condition. */
    @Test
    fun `an alert with no trigger falls through to its condition`() {
        assertTrue(
            LocalPriceAlert.due(
                alert = base,
                previous = 64_000.0,
                price = 65_000.0,
                series = null,
                changePercent24h = null,
                nowEpochMillis = barStart,
                barStart = barStart,
                barClosed = true,
            ),
        )
    }

    @Test
    fun `a trigger wins over the condition where the alert has one`() {
        // The flat condition is "above 65,000" and would be satisfied; the trigger asks for a
        // crossing, and the price was already above the line.
        val crossing = base.copy(trigger = AlertTrigger.Price(PriceOp.CROSSING_UP, 65_000.0))
        assertFalse(
            LocalPriceAlert.due(
                alert = crossing,
                previous = 65_500.0,
                price = 66_000.0,
                series = null,
                changePercent24h = null,
                nowEpochMillis = barStart,
                barStart = barStart,
                barClosed = true,
            ),
        )
        assertTrue(
            LocalPriceAlert.due(
                alert = crossing,
                previous = 64_900.0,
                price = 65_000.0,
                series = null,
                changePercent24h = null,
                nowEpochMillis = barStart,
                barStart = barStart,
                barClosed = true,
            ),
        )
    }

    /** The setting has to hold at the level it is set: close-only means close-only. */
    @Test
    fun `a close-only alert does not fire mid-bar even when its trigger is satisfied`() {
        val closeOnly = base.copy(
            trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 65_000.0),
            frequency = AlertFrequency.ONCE_PER_BAR_CLOSE,
        )
        assertFalse(
            LocalPriceAlert.due(
                alert = closeOnly,
                previous = 64_000.0,
                price = 66_000.0,
                series = null,
                changePercent24h = null,
                nowEpochMillis = barStart + 30_000L,
                barStart = barStart,
                barClosed = false,
            ),
        )
        assertTrue(
            LocalPriceAlert.due(
                alert = closeOnly,
                previous = 64_000.0,
                price = 66_000.0,
                series = null,
                changePercent24h = null,
                nowEpochMillis = barStart + 60_000L,
                barStart = barStart,
                barClosed = true,
            ),
        )
    }

    @Test
    fun `an inactive or expired alert is refused before anything else is asked`() {
        val trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 1.0)
        val off = base.copy(active = false, trigger = trigger)
        val gone = base.copy(expiresAt = barStart, trigger = trigger)
        listOf(off, gone).forEach { alert ->
            assertFalse(
                alert.id,
                LocalPriceAlert.due(
                    alert = alert,
                    previous = 0.0,
                    price = 70_000.0,
                    series = null,
                    changePercent24h = null,
                    nowEpochMillis = barStart,
                    barStart = barStart,
                    barClosed = true,
                ),
            )
        }
    }

    @Test
    fun `two hundred alerts fit on one phone`() {
        assertEquals(200, LocalPriceAlert.MAX_ALERTS)
    }
}
