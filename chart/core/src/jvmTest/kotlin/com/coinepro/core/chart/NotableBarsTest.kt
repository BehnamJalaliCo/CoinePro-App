package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The bars «چرا این حرکت؟» offers itself on** (run Τ2, C1).
 *
 * The rule is a ratio and not a number, and every case here is about why: a threshold in price
 * would mark every bar on Bitcoin and none on EURUSD, and what a reader actually notices is a
 * candle that is big *for this chart, lately*.
 */
class NotableBarsTest {

    /** A series whose bars all have range [quiet], with [loud] at the given indices. */
    private fun series(count: Int, quiet: Double, vararg loud: Pair<Int, Double>): CandleSeries {
        val ranges = DoubleArray(count) { quiet }
        loud.forEach { (index, range) -> ranges[index] = range }
        return CandleSeries(
            List(count) { index ->
                val low = 100.0
                Candle(t = index * 60_000L, o = low, h = low + ranges[index], l = low, c = low)
            },
        )
    }

    @Test
    fun `a bar more than twice its baseline is notable, and one merely above it is not`() {
        // Twice is the brief's number and it is exclusive: a bar exactly double the average is
        // the average's own arithmetic, not an event.
        assertEquals(listOf(30), NotableBars.of(series(60, quiet = 1.0, 30 to 2.5)))
        assertTrue(NotableBars.of(series(60, quiet = 1.0, 30 to 2.0)).isEmpty())
        assertTrue(NotableBars.of(series(60, quiet = 1.0, 30 to 1.9)).isEmpty())
    }

    @Test
    fun `the baseline is the bars before it and never the bar itself`() {
        // A bar in its own window raises the bar it has to clear, so the biggest candles — the ones
        // this feature exists for — would be the likeliest to hide themselves.
        val one = series(60, quiet = 1.0, 30 to 21.0)
        assertTrue("a twenty-one-times bar was not marked", 30 in NotableBars.of(one))
    }

    @Test
    fun `the first bars of a series are never marked`() {
        // They have no baseline behind them, and a short average would put a dot on the oldest bars
        // of every chart — which reads as a bug rather than as a fact about the market.
        val early = series(60, quiet = 1.0, 0 to 50.0, 5 to 50.0, 19 to 50.0)
        assertTrue("a bar inside the first window was marked", NotableBars.of(early).isEmpty())
    }

    @Test
    fun `a market that did not move at all has nothing to compare against`() {
        // Every bar is infinitely more than an average of zero. The honest answer is silence.
        val flat = CandleSeries(List(60) { Candle(t = it * 60_000L, o = 5.0, h = 5.0, l = 5.0, c = 5.0) })
        assertTrue(NotableBars.of(flat).isEmpty())
        // …and a single move inside a flat stretch is still marked, because *its* window is zero
        // only until it has traded: bar 30's baseline is bars 10–29, all flat, so it is refused.
        assertTrue(NotableBars.of(series(60, quiet = 0.0, 30 to 4.0)).isEmpty())
    }

    @Test
    fun `a series shorter than the window answers nothing rather than guessing`() {
        assertTrue(NotableBars.of(series(20, quiet = 1.0, 19 to 90.0)).isEmpty())
        assertTrue(NotableBars.of(CandleSeries(emptyList())).isEmpty())
    }

    @Test
    fun `a gap in the window averages over the bars that traded`() {
        // Dividing by twenty when four of them never printed would understate the baseline and mark
        // ordinary bars. The window counts what it has.
        val withGaps = CandleSeries(
            List(60) { index ->
                val range = when {
                    index == 40 -> 3.0
                    index % 5 == 0 -> Double.NaN
                    else -> 1.0
                }
                Candle(t = index * 60_000L, o = 100.0, h = 100.0 + range, l = 100.0, c = 100.0)
            },
        )
        // The baseline over bars 20–39 is 1.0 across the bars that traded, so 3.0 clears it.
        assertTrue("the gapped window did not mark the bar", 40 in NotableBars.of(withGaps))
    }

    @Test
    fun `covers answers the same question as the list, because there is one rule`() {
        // Two implementations of one rule drift, and the drift would be invisible from outside: the
        // dot would appear on a bar whose sheet then said there was nothing unusual about it.
        val one = series(60, quiet = 1.0, 30 to 4.0, 45 to 5.0)
        val listed = NotableBars.of(one)
        (0 until one.size).forEach { index ->
            assertEquals(
                "bar $index disagrees between the list and the predicate",
                index in listed,
                NotableBars.covers(one, index),
            )
        }
        assertFalse("an index past the end was covered", NotableBars.covers(one, 999))
    }
}
