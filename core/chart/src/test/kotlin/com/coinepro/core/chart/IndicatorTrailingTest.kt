package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two studies that drew a plausible line and were saying nothing.
 *
 * Both bugs came from the web app they were ported from, and both survived a parity fixture,
 * because a fixture recorded from a wrong implementation reproduces the wrong implementation
 * exactly. What catches this class of defect is a test of the *property* the study exists to have —
 * a trailing stop that trails, a mean that is a mean of real numbers — so that is what this file
 * asserts and why it is separate from `IndicatorParityTest`.
 */
class IndicatorTrailingTest {

    /**
     * A steady rise, then a pullback: sixty bars up from 100 to about 200, eight bars back down.
     *
     * The pullback is around ten points, which is roughly four true ranges — a large but entirely
     * ordinary retracement, and precisely the move a trailing stop is on the chart to catch.
     */
    private fun trendThenPullback(): Triple<DoubleArray, DoubleArray, DoubleArray> {
        val closes = ArrayList<Double>()
        var price = 100.0
        repeat(60) {
            price += 1.7
            closes += price
        }
        repeat(8) {
            price -= 2.0
            closes += price
        }
        val close = closes.toDoubleArray()
        val high = DoubleArray(close.size) { close[it] + 0.5 }
        val low = DoubleArray(close.size) { close[it] - 0.5 }
        return Triple(high, low, close)
    }

    @Test
    fun `a pullback into the band ends the trend`() {
        val (high, low, close) = trendThenPullback()

        val result = Indicators.supertrend(high, low, close, period = 10, multiplier = 3.0)

        val sides = close.indices.mapNotNull { result.trend[it] }.distinct()
        // Before the ratchet was corrected this walk produced one side for all sixty-eight bars:
        // the band sat three average ranges below where the *trend started* rather than below where
        // price is now, so a ten-point pullback off a hundred-point run passed nowhere near it.
        assertTrue("SuperTrend never changed side: $sides", sides.size > 1)
        val flip = close.indices.first { index ->
            val here = result.trend[index]
            here != null && here < 0
        }
        assertTrue("flipped at bar $flip, which is not in the pullback", flip >= 60)
    }

    @Test
    fun `the band tightens towards price while the trend runs, and never backs away`() {
        val (high, low, close) = trendThenPullback()

        val result = Indicators.supertrend(high, low, close, period = 10, multiplier = 3.0)

        var previous: Double? = null
        for (index in close.indices) {
            val side = result.trend[index] ?: continue
            if (side < 0) break
            val band = result.line[index] ?: continue
            // A stop that can walk backwards is not a stop. This is the property the whole
            // indicator rests on, and it is the one the inverted comparison removed.
            previous?.let { assertTrue("band fell at bar $index: $it then $band", band >= it - 1e-9) }
            previous = band
        }
        assertNotNull("the trend leg produced no band at all", previous)
    }

    @Test
    fun `the band ends up within a few ranges of price rather than of where the trend began`() {
        val (high, low, close) = trendThenPullback()

        val result = Indicators.supertrend(high, low, close, period = 10, multiplier = 3.0)

        // At the top of the run the stop must be near the top of the run. The broken version left
        // it near 100 — a hundred points and fifty bars behind the market it was supposedly
        // following.
        val atPeak = requireNotNull(result.line[59])
        assertTrue("the stop sat at $atPeak while price was ${close[59]}", close[59] - atPeak < 15.0)
    }

    // ── the stochastic's warm-up ──────────────────────────────────────────────────────

    @Test
    fun `percent D publishes nothing until every bar in its window is a real reading`() {
        val (high, low, close) = trendThenPullback()

        val result = Indicators.stochastic(high, low, close, period = 14, smoothing = 3)

        // %K is real from bar 13; %D is a three-bar mean of it, so it is real from bar 15. The two
        // bars in between used to be published as numbers near zero — the array's own empty head,
        // averaged in and drawn — and a strategy reading a %D/%K cross took a trade on each of them.
        assertNotNull(result.k[13])
        assertNull(result.d[13])
        assertNull(result.d[14])
        assertNotNull(result.d[15])
    }

    @Test
    fun `the first published percent D is the mean of the three percent K readings under it`() {
        val (high, low, close) = trendThenPullback()

        val result = Indicators.stochastic(high, low, close, period = 14, smoothing = 3)

        val mean = (0..2).sumOf { requireNotNull(result.k[13 + it]) } / 3
        assertEquals(mean, requireNotNull(result.d[15]), 1e-9)
    }
}
