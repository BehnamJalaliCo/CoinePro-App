package com.coinepro.core.chart

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The logarithmic price axis.
 *
 * The property that matters is not "the numbers are logs" — it is that **equal distances on screen
 * mean equal percentages**. That is the entire reason a trading chart offers this: a move from 100
 * to 110 and one from 1,000 to 1,100 are the same trade, and a linear axis draws the second ten
 * times taller.
 *
 * And the pairing: [ChartViewport.yOf] and [ChartViewport.priceAt] have to be exact inverses on
 * both axes, or a drawing placed by a finger lands somewhere other than where the finger was.
 */
class LogScaleTest {

    /**
     * A series spanning two decades — 100 to 10,000.
     *
     * Deliberately the shape a linear axis handles worst, which is also the shape this feature
     * exists for: a year of a coin that went up twenty times.
     */
    private val series = CandleSeries(
        (0 until 200).map { index ->
            val price = 100.0 * Math.pow(100.0, index / 199.0)
            Candle(1_700_000_000L + index * 3600, price, price * 1.01, price * 0.99, price, 1.0)
        },
    )

    private fun viewport(log: Boolean) = ChartViewport(
        series = series,
        barsPerView = 200,
        plotWidth = 900f,
        plotHeight = 600f,
        logScale = log,
    )

    @Test
    fun `equal percentages are equal distances`() {
        val view = viewport(log = true)
        // Three doublings, anywhere on the range. On a log axis each one is the same number of
        // pixels; on a linear one they differ by a factor of four.
        val first = view.yOf(200.0) - view.yOf(400.0)
        val second = view.yOf(1_000.0) - view.yOf(2_000.0)
        val third = view.yOf(4_000.0) - view.yOf(8_000.0)
        assertEquals(first.toDouble(), second.toDouble(), 0.5)
        assertEquals(second.toDouble(), third.toDouble(), 0.5)

        val linear = viewport(log = false)
        assertTrue(
            "The linear axis should draw them very differently, or this test proves nothing",
            abs((linear.yOf(200.0) - linear.yOf(400.0)) - (linear.yOf(4_000.0) - linear.yOf(8_000.0))) > 50f,
        )
    }

    @Test
    fun `yOf and priceAt are inverses on both axes`() {
        listOf(true, false).forEach { log ->
            val view = viewport(log)
            listOf(120.0, 500.0, 1_500.0, 9_000.0).forEach { price ->
                val roundTrip = view.priceAt(view.yOf(price))
                // Relative rather than absolute: half a pixel at nine thousand is several units of
                // price, and an absolute tolerance would either fail at the top or pass anything
                // at the bottom.
                assertEquals("log=$log price=$price", 1.0, roundTrip / price, 1e-3)
            }
        }
    }

    @Test
    fun `the axis stays monotonic`() {
        // A higher price is always higher on screen — y decreases. Sounds trivial and is exactly
        // what a sign error in the log branch would break, silently and only on some ranges.
        val view = viewport(log = true)
        var previous = Float.MAX_VALUE
        var price = 100.0
        while (price <= 10_000.0) {
            val y = view.yOf(price)
            assertTrue("$price drew below the price under it", y < previous)
            previous = y
            price *= 1.3
        }
    }

    @Test
    fun `a non-positive value falls back rather than becoming NaN`() {
        // Prices cannot be negative but the panes reuse this function — MACD and rate-of-change
        // routinely go below zero — and a reader can drag a target below the axis. A NaN here
        // would stop the line being drawn at all, with nothing on screen to say why.
        val view = viewport(log = true)
        listOf(0.0, -5.0, -1_000.0).forEach { value ->
            val y = view.yOf(value)
            assertTrue("$value produced $y", y.isFinite())
        }
    }

    @Test
    fun `the toggle changes nothing about the bars or the time axis`() {
        val linear = viewport(log = false)
        val log = viewport(log = true)
        assertEquals(linear.firstVisible, log.firstVisible)
        assertEquals(linear.lastVisible, log.lastVisible)
        assertEquals(linear.barWidth, log.barWidth, 1e-6f)
        assertEquals(linear.xOf(50), log.xOf(50), 1e-6f)

        // The padded range is deliberately *not* the same, and it took a failing test to get that
        // right: eight percent of a 100–10,000 span is 792, so additive padding put the bottom of
        // the axis at −692 — a price with no logarithm, which sent the axis back to the linear
        // fallback and made the toggle do nothing. What has to hold is that both ranges still
        // contain every bar, with headroom above and below.
        listOf(linear, log).forEach { view ->
            assertTrue(view.priceRange.start > 0.0 || !view.logScale)
            assertTrue(view.priceRange.start < series.low.min())
            assertTrue(view.priceRange.endInclusive > series.high.max())
        }
    }
}
