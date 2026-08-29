package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Symbol comparison.
 *
 * What is worth pinning here is not the arithmetic — a percentage is a percentage — but the two
 * behaviours that are silently wrong the moment somebody "simplifies" them: that a missing bar is
 * carried and never interpolated, and that the base series keeps every bar it arrived with. Both
 * produce a chart that looks entirely plausible when they break, which is exactly the kind of bug a
 * test has to hold down.
 */
class ComparisonTest {

    private val hour = 3600L
    private val day = 24 * hour

    /** A bar whose every field is the close, because nothing here reads the wick. */
    private fun bar(t: Long, close: Double) = Candle(t = t, o = close, h = close, l = close, c = close)

    /**
     * Five daily bars across a weekend: Friday, Saturday, Sunday, Monday, Tuesday.
     *
     * The base is a market that trades every day — a crypto pair — and is the grid everything else
     * has to fit onto.
     */
    private val monday = 1_700_000_000L

    private val base = CandleSeries(
        listOf(
            bar(monday, 100.0),
            bar(monday + day, 110.0),
            bar(monday + 2 * day, 120.0),
            bar(monday + 3 * day, 130.0),
            bar(monday + 4 * day, 140.0),
        ),
    )

    // ── align ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a market that closed for the weekend carries its last price forward`() {
        // Trades on day 0 and day 1, then nothing until day 4: a weekend, or a holiday.
        val other = CandleSeries(
            listOf(
                bar(monday, 50.0),
                bar(monday + day, 52.0),
                bar(monday + 4 * day, 60.0),
            ),
        )

        val aligned = align(base, other, symbol = "XAUUSD", label = "طلا")

        assertEquals(50.0, aligned.values[0], 1e-9)
        assertEquals(52.0, aligned.values[1], 1e-9)
        assertEquals(52.0, aligned.values[2], 1e-9)
        assertEquals(52.0, aligned.values[3], 1e-9)
        assertEquals(60.0, aligned.values[4], 1e-9)
        assertEquals("XAUUSD", aligned.symbol)
        assertEquals("طلا", aligned.label)
    }

    @Test
    fun `the gap is flat and not a straight line drawn between the two known prices`() {
        val other = CandleSeries(
            listOf(
                bar(monday, 50.0),
                bar(monday + day, 52.0),
                bar(monday + 4 * day, 60.0),
            ),
        )

        val aligned = align(base, other)

        // Interpolation would put 54.67 and 57.33 in the gap — prices nobody traded at, which a
        // reader would take a level off. The carried value is the same 52.0 twice.
        assertEquals(aligned.values[1], aligned.values[2], 1e-9)
        assertEquals(aligned.values[2], aligned.values[3], 1e-9)
        assertNotEquals(54.666, aligned.values[2], 0.5)
    }

    @Test
    fun `bars older than the compared instrument are NaN rather than back-filled`() {
        // A newer listing: its first bar is the base chart's third.
        val other = CandleSeries(
            listOf(
                bar(monday + 2 * day, 8.0),
                bar(monday + 3 * day, 9.0),
                bar(monday + 4 * day, 10.0),
            ),
        )

        val aligned = align(base, other)

        assertTrue(aligned.values[0].isNaN())
        assertTrue(aligned.values[1].isNaN())
        assertEquals(8.0, aligned.values[2], 1e-9)
        assertEquals(10.0, aligned.values[4], 1e-9)
    }

    @Test
    fun `the base keeps every bar it arrived with whatever the other series looks like`() {
        val sparse = CandleSeries(listOf(bar(monday + 3 * day, 7.0)))
        val dense = CandleSeries((0 until 40).map { bar(monday + it * hour, 1.0 + it) })

        assertEquals(base.size, align(base, sparse).size)
        assertEquals(base.size, align(base, dense).size)
        assertEquals(base.size, align(base, CandleSeries.EMPTY).size)
        assertEquals(base.time.toList(), align(base, sparse).times.toList())
    }

    @Test
    fun `an empty comparison feed is an empty overlay and not an empty chart`() {
        val aligned = align(base, CandleSeries.EMPTY)

        assertEquals(5, aligned.size)
        assertTrue(aligned.values.all { it.isNaN() })
    }

    @Test
    fun `a bar between two of the other series' bars takes the earlier one`() {
        // Offset timestamps: the compared feed stamps its bars half a day late, so no base bar
        // matches exactly. The answer must be the last price known at that moment, never the next.
        val other = CandleSeries(
            listOf(
                bar(monday + day / 2, 20.0),
                bar(monday + day + day / 2, 30.0),
            ),
        )

        val aligned = align(base, other)

        assertTrue(aligned.values[0].isNaN())
        assertEquals(20.0, aligned.values[1], 1e-9)
        assertEquals(30.0, aligned.values[2], 1e-9)
        assertEquals(30.0, aligned.values[4], 1e-9)
    }

    // ── rebase ────────────────────────────────────────────────────────────────────────

    private fun series(vararg values: Double) = ComparisonSeries(
        symbol = "X",
        label = "X",
        colour = comparisonColour(0),
        values = values,
        times = LongArray(values.size) { monday + it * day },
    )

    @Test
    fun `percent reads zero at the anchor and the real move after it`() {
        val rebased = rebase(series(50.0, 55.0, 45.0), ComparisonBasis.PERCENT, 0, base.close)

        assertEquals(0.0, rebased[0], 1e-9)
        assertEquals(10.0, rebased[1], 1e-9)
        assertEquals(-10.0, rebased[2], 1e-9)
    }

    @Test
    fun `the anchor is the first visible bar and not the first bar loaded`() {
        val rebased = rebase(series(50.0, 55.0, 66.0), ComparisonBasis.PERCENT, 1, base.close)

        // Anchored on 55.0: the bar to its left is now negative, and the last is 20 percent up.
        assertEquals(0.0, rebased[1], 1e-9)
        assertEquals(20.0, rebased[2], 1e-9)
        assertTrue(rebased[0] < 0.0)
    }

    @Test
    fun `indexed and percent differ by exactly one hundred at every bar`() {
        val values = series(50.0, 55.0, 45.0, 61.5, 50.0)
        val percent = rebase(values, ComparisonBasis.PERCENT, 0, base.close)
        val indexed = rebase(values, ComparisonBasis.INDEXED_100, 0, base.close)

        assertEquals(percent.size, indexed.size)
        for (index in percent.indices) {
            assertEquals(percent[index] + 100.0, indexed[index], 1e-9)
        }
    }

    @Test
    fun `a negative anchor still reports a rise as a rise`() {
        val rebased = rebase(series(-40.0, -30.0), ComparisonBasis.PERCENT, 0, base.close)

        assertEquals(25.0, rebased[1], 1e-9)
    }

    @Test
    fun `rebasing anchors past a leading NaN instead of blanking the overlay`() {
        val young = series(Double.NaN, Double.NaN, 20.0, 22.0)
        val percent = rebase(young, ComparisonBasis.PERCENT, 0, base.close)

        assertTrue(percent[0].isNaN())
        assertTrue(percent[1].isNaN())
        assertEquals(0.0, percent[2], 1e-9)
        assertEquals(10.0, percent[3], 1e-9)
    }

    @Test
    fun `a series with nothing visible has nothing to anchor on and stays NaN`() {
        val blank = series(Double.NaN, Double.NaN)

        assertTrue(rebase(blank, ComparisonBasis.PERCENT, 0, base.close).all { it.isNaN() })
        assertTrue(rebase(series(0.0, 5.0), ComparisonBasis.PERCENT, 0, base.close).all { it.isNaN() })
    }

    @Test
    fun `a ratio divides bar by bar and refuses a zero base`() {
        val other = series(50.0, 60.0, 70.0)
        val under = doubleArrayOf(100.0, 0.0, 35.0)

        val ratio = rebase(other, ComparisonBasis.RATIO, 0, under)

        assertEquals(0.5, ratio[0], 1e-9)
        assertTrue(ratio[1].isNaN())
        assertEquals(2.0, ratio[2], 1e-9)
    }

    @Test
    fun `absolute returns the prices untouched and does not alias the source`() {
        val source = series(2300.0, 2310.0)
        val absolute = rebase(source, ComparisonBasis.ABSOLUTE, 0, base.close)

        assertEquals(2300.0, absolute[0], 1e-9)
        assertEquals(2310.0, absolute[1], 1e-9)
        absolute[0] = 0.0
        assertEquals(2300.0, source.values[0], 1e-9)
    }

    // ── combinedRange ─────────────────────────────────────────────────────────────────

    @Test
    fun `the combined range spans every finite value and ignores the missing bars`() {
        val range = combinedRange(
            listOf(
                doubleArrayOf(Double.NaN, 4.0, 9.0),
                doubleArrayOf(-2.0, Double.NaN, 3.0),
            ),
        )

        assertEquals(-2.0, range!!.start, 1e-9)
        assertEquals(9.0, range.endInclusive, 1e-9)
    }

    @Test
    fun `a range over nothing finite is null rather than a fabricated axis`() {
        assertNull(combinedRange(emptyList()))
        assertNull(combinedRange(listOf(DoubleArray(0))))
        assertNull(combinedRange(listOf(doubleArrayOf(Double.NaN, Double.NaN))))
        assertNull(combinedRange(listOf(doubleArrayOf(Double.POSITIVE_INFINITY))))
    }

    // ── palette ───────────────────────────────────────────────────────────────────────

    @Test
    fun `the four comparison colours are four different colours`() {
        val palette = (0 until MAX_COMPARISONS).map(::comparisonColour)

        assertEquals(4, MAX_COMPARISONS)
        assertEquals(MAX_COMPARISONS, palette.toSet().size)
    }

    @Test
    fun `no comparison colour is the market up or down colour`() {
        for (index in 0 until MAX_COMPARISONS) {
            assertFalse(
                "slot $index collides with a market colour",
                comparisonColour(index) in MARKET_COLOURS,
            )
        }
    }

    @Test
    fun `a slot past the cap wraps instead of throwing`() {
        assertEquals(comparisonColour(0), comparisonColour(MAX_COMPARISONS))
        assertEquals(comparisonColour(1), comparisonColour(-3))
    }

    @Test
    fun `two alignments of the same feeds compare equal so the chart does not redraw`() {
        val other = CandleSeries(listOf(bar(monday, 50.0), bar(monday + day, 52.0)))

        assertEquals(align(base, other, "XAUUSD"), align(base, other, "XAUUSD"))
        assertNotEquals(align(base, other, "XAUUSD"), align(base, other, "EURUSD"))
    }
}
