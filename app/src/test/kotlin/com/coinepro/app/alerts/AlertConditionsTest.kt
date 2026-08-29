package com.coinepro.app.alerts

import com.coinepro.core.notifications.AlertFrequency
import com.coinepro.core.notifications.AlertRepeat
import com.coinepro.core.notifications.AlertTrigger
import com.coinepro.core.notifications.ChannelOp
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.core.notifications.MoveOp
import com.coinepro.core.notifications.PriceOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The routing, which is the part of alerts that cannot be checked by looking at it.
 *
 * Each trigger is compared against a different number — the price for most of them, the indicator's
 * own output for one, the drawn line's level for another — and every one of these tests exists
 * because getting that wrong produces an alert that fires on something the reader can see is not
 * what they asked for, rather than an alert that visibly does not work.
 */
class AlertConditionsTest {

    @Test
    fun `a price alert fires above its level and stays quiet below it`() {
        val alert = alert(trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 65_000.0))
        assertTrue(AlertConditions.due(alert, sample(price = 65_100.0), NOW))
        assertFalse(AlertConditions.due(alert, sample(price = 64_900.0), NOW))
    }

    @Test
    fun `a crossing without a previous sample refuses to fire`() {
        val alert = alert(trigger = AlertTrigger.Price(PriceOp.CROSSING_UP, 100.0))
        assertFalse(AlertConditions.due(alert, sample(price = 101.0, previous = null), NOW))
        assertTrue(AlertConditions.due(alert, sample(price = 101.0, previous = 99.0), NOW))
        assertFalse(AlertConditions.due(alert, sample(price = 101.0, previous = 100.5), NOW))
    }

    @Test
    fun `a channel alert fires on leaving the band and not while it sits inside`() {
        val alert = alert(trigger = AlertTrigger.Channel(ChannelOp.EXITING, 90.0, 110.0))
        assertTrue(AlertConditions.due(alert, sample(price = 112.0, previous = 105.0), NOW))
        assertFalse(AlertConditions.due(alert, sample(price = 105.0, previous = 100.0), NOW))
    }

    @Test
    fun `a multi-bar move is measured from the bar the reader asked for`() {
        val alert = alert(trigger = AlertTrigger.Move(MoveOp.UP_PERCENT, 5.0, bars = 3))
        val closes = listOf(100.0, 101.0, 102.0)
        assertTrue(AlertConditions.due(alert, sample(price = 106.0, closes = closes), NOW))
        // Four percent from the same bar, which is the whole point of the window: measured from the
        // last close instead it would be nearly two, and from the first bar of the day, anything.
        assertFalse(AlertConditions.due(alert, sample(price = 104.0, closes = closes), NOW))
    }

    @Test
    fun `an indicator alert reads the indicator's output rather than the price`() {
        val alert = alert(trigger = AlertTrigger.Indicator("rsi", 14, PriceOp.CROSSING_UP, 70.0))
        val rsi = mapOf(AlertIndicatorKey("rsi", 14) to AlertIndicatorReading(previous = 68.0, current = 71.5))
        // The price crosses nothing and the RSI crosses seventy, so the alert is about the RSI.
        assertTrue(AlertConditions.due(alert, sample(price = 65_000.0, previous = 64_000.0, indicators = rsi), NOW))

        val flat = mapOf(AlertIndicatorKey("rsi", 14) to AlertIndicatorReading(previous = 64.0, current = 65.0))
        assertFalse(AlertConditions.due(alert, sample(price = 71.5, previous = 68.0, indicators = flat), NOW))
    }

    @Test
    fun `an indicator with no reading at all never fires`() {
        val alert = alert(trigger = AlertTrigger.Indicator("rsi", 14, PriceOp.LESS_THAN, 70.0))
        // A warm-up that never completed, or a study with no value per bar. Less-than would be
        // satisfied by a fabricated zero, which is exactly the wrong way to be wrong.
        assertFalse(AlertConditions.due(alert, sample(price = 10.0), NOW))
    }

    @Test
    fun `a drawing touch is a sign change against the line's own level`() {
        val alert = alert(trigger = AlertTrigger.DrawingTouch("7"))
        val rising = mapOf("7" to listOf(100.0, 105.0))
        assertTrue(AlertConditions.due(alert, sample(price = 106.0, previous = 99.0, drawingLevels = rising), NOW))
        assertFalse(AlertConditions.due(alert, sample(price = 96.0, previous = 95.0, drawingLevels = rising), NOW))
    }

    @Test
    fun `a drawing that no longer exists never fires`() {
        val alert = alert(trigger = AlertTrigger.DrawingTouch("7"))
        assertFalse(AlertConditions.due(alert, sample(price = 106.0, previous = 99.0), NOW))
    }

    @Test
    fun `every condition of a multi-condition alert is asked about its own number`() {
        val alert = alert(
            trigger = AlertTrigger.MultiCondition(
                listOf(
                    AlertTrigger.Price(PriceOp.GREATER_THAN, 65_000.0),
                    AlertTrigger.Indicator("rsi", 14, PriceOp.GREATER_THAN, 70.0),
                ),
            ),
        )
        val hot = mapOf(AlertIndicatorKey("rsi", 14) to AlertIndicatorReading(previous = 70.5, current = 71.0))
        val cool = mapOf(AlertIndicatorKey("rsi", 14) to AlertIndicatorReading(previous = 60.0, current = 61.0))
        assertTrue(AlertConditions.due(alert, sample(price = 66_000.0, indicators = hot), NOW))
        assertFalse(AlertConditions.due(alert, sample(price = 66_000.0, indicators = cool), NOW))
        assertFalse(AlertConditions.due(alert, sample(price = 64_000.0, indicators = hot), NOW))
    }

    @Test
    fun `a close-only alert is judged on the closed bar and ignores a wick`() {
        val alert = alert(
            trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 100.0),
            frequency = AlertFrequency.ONCE_PER_BAR_CLOSE,
        )
        // The price is through the level right now and the bar closed back under it. That is the
        // exact case the setting exists to refuse.
        val wick = sample(price = 105.0, closes = listOf(98.0, 99.0), barStart = 20_000L, closedBarStart = 10_000L)
        assertFalse(AlertConditions.due(alert, wick, NOW))

        val closedThrough = sample(price = 105.0, closes = listOf(98.0, 101.0), barStart = 20_000L, closedBarStart = 10_000L)
        assertTrue(AlertConditions.due(alert, closedThrough, NOW))
    }

    @Test
    fun `a close-only alert with no closed bar has nothing to report`() {
        val alert = alert(
            trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 100.0),
            frequency = AlertFrequency.ONCE_PER_BAR_CLOSE,
        )
        assertFalse(AlertConditions.due(alert, sample(price = 105.0), NOW))
    }

    @Test
    fun `an expired alert is never due, whatever the market did`() {
        val alert = alert(
            trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 100.0),
            expiresAt = NOW - 1L,
        )
        assertFalse(AlertConditions.due(alert, sample(price = 105.0), NOW))
    }

    @Test
    fun `reading a bar close shifts the whole window back one bar`() {
        val reading = sample(price = 105.0, closes = listOf(97.0, 98.0, 99.0), closedBarStart = 10_000L).atBarClose()
        assertEquals(99.0, reading?.price ?: 0.0, 1e-9)
        assertEquals(98.0, reading?.previousPrice ?: 0.0, 1e-9)
        assertEquals(listOf(97.0, 98.0), reading?.closes)
        assertEquals(10_000L, reading?.barStart ?: 0L)
    }

    @Test
    fun `only the triggers that need bars ask for them`() {
        assertFalse(AlertConditions.needsOf(alert(trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 1.0))).candles)
        assertTrue(AlertConditions.needsOf(alert(trigger = AlertTrigger.Price(PriceOp.CROSSING, 1.0))).candles)
        assertTrue(AlertConditions.needsOf(alert(trigger = AlertTrigger.Move(MoveOp.UP, 1.0))).candles)
        // A bar policy needs a bar even when the condition it governs does not.
        assertTrue(
            AlertConditions.needsOf(
                alert(trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 1.0), frequency = AlertFrequency.ONCE_PER_BAR),
            ).candles,
        )
    }

    @Test
    fun `an alert names the indicators and drawings it will need`() {
        val needs = AlertConditions.needsOf(
            alert(
                trigger = AlertTrigger.MultiCondition(
                    listOf(
                        AlertTrigger.Indicator("rsi", 14, PriceOp.GREATER_THAN, 70.0),
                        AlertTrigger.DrawingTouch("7"),
                    ),
                ),
            ),
        )
        assertEquals(setOf(AlertIndicatorKey("rsi", 14)), needs.indicators)
        assertEquals(setOf("7"), needs.drawings)
    }

    @Test
    fun `a trend line's level follows its slope past both anchors`() {
        val points = listOf(1_000L to 100.0, 2_000L to 200.0)
        assertEquals(150.0, AlertDrawingLevel.levelAt("trend", points, 1_500L) ?: 0.0, 1e-9)
        assertEquals(300.0, AlertDrawingLevel.levelAt("trend", points, 3_000L) ?: 0.0, 1e-9)
        assertEquals(50.0, AlertDrawingLevel.levelAt("trend", points, 500L) ?: 0.0, 1e-9)
    }

    @Test
    fun `a one-anchor drawing is a horizontal level and a vertical one has no level`() {
        assertEquals(100.0, AlertDrawingLevel.levelAt("hline", listOf(1_000L to 100.0), 9_000L) ?: 0.0, 1e-9)
        assertNull(AlertDrawingLevel.levelAt("vline", listOf(1_000L to 100.0), 9_000L))
        assertNull(AlertDrawingLevel.levelAt("trend", emptyList(), 9_000L))
        // Two anchors on one instant have no slope, and dividing by that span is the bug this
        // avoids: the level would come back as an infinity and every comparison against it would
        // answer nonsense.
        assertEquals(
            100.0,
            AlertDrawingLevel.levelAt("trend", listOf(1_000L to 100.0, 1_000L to 200.0), 9_000L) ?: 0.0,
            1e-9,
        )
    }

    private companion object {
        const val NOW = 1_700_000_000_000L

        fun alert(
            trigger: AlertTrigger? = null,
            frequency: AlertFrequency? = null,
            expiresAt: Long? = null,
        ) = LocalPriceAlert(
            id = "a1",
            symbol = "BTCUSDT",
            condition = LocalAlertCondition.ABOVE,
            value = 0.0,
            repeat = AlertRepeat.ALWAYS,
            trigger = trigger,
            frequency = frequency,
            expiresAt = expiresAt,
        )

        fun sample(
            price: Double,
            previous: Double? = null,
            closes: List<Double> = emptyList(),
            barStart: Long = 0L,
            closedBarStart: Long? = null,
            indicators: Map<AlertIndicatorKey, AlertIndicatorReading> = emptyMap(),
            drawingLevels: Map<String, List<Double>> = emptyMap(),
        ) = AlertSample(
            symbol = "BTCUSDT",
            price = price,
            previousPrice = previous,
            changePercent24h = null,
            closes = closes,
            barStart = barStart,
            closedBarStart = closedBarStart,
            timeframe = "H1",
            indicators = indicators,
            drawingLevels = drawingLevels,
        )
    }
}
