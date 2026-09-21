package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The row under the head: how far the price has moved.
 *
 * The head printed four prices and stopped, so the figure a reader arrives with lived only in the
 * page header of the symbol screen — which is not on screen in fullscreen and never was in the
 * panes layout. These say what the row carries and, more importantly, *which* move it describes.
 */
class ChartLegendChangeRowTest {

    private fun bar(open: Double, close: Double) =
        Candle(t = 1_700_000_000L, o = open, h = maxOf(open, close), l = minOf(open, close), c = close, v = 1.0)

    private fun row(
        open: Double = 100.0,
        close: Double = 101.0,
        change: ChartLegendChange? = null,
    ) = legendChangeRow(bar(open, close), decimals = 2, change = change)

    private fun text(
        open: Double = 100.0,
        close: Double = 101.0,
        change: ChartLegendChange? = null,
    ) = row(open, close, change).alternatives.first()

    @Test
    fun `the move is printed as a figure and as a percentage`() {
        val reading = text(open = 100.0, close = 101.0)
        assertTrue("the absolute move is in the instrument's own units", reading.contains("1.00"))
        assertTrue("and the percentage is what makes it comparable", reading.contains("1.00%"))
    }

    @Test
    fun `a fall is signed with a minus and never with a hyphen`() {
        val reading = text(open = 100.0, close = 99.5)
        assertTrue("U+2212, the app's minus", reading.contains("−"))
        assertFalse("a hyphen reads as a dash between two numbers", reading.contains("-"))
    }

    @Test
    fun `a rise says so, because the row is a change and not a price`() {
        assertTrue(text(open = 100.0, close = 101.0).startsWith("\u2066+"))
    }

    @Test
    fun `a flat bar claims neither direction`() {
        val reading = text(open = 100.0, close = 100.0)
        assertFalse("+0.00 claims a rise that did not happen", reading.contains("+"))
        assertFalse(reading.contains("−"))
    }

    @Test
    fun `each figure is isolated, so the signs cannot land on the wrong number`() {
        val reading = text(open = 100.0, close = 99.5)
        // One isolate opened and popped per run. Without them the two signed numbers merge into a
        // single bidirectional run on a right-to-left screen and reorder against each other.
        assertEquals(2, reading.count { it == '\u2066' })
        assertEquals(2, reading.count { it == '\u2069' })
    }

    @Test
    fun `the narrowest form of the row is still the percentage`() {
        val alternatives = row().alternatives
        assertTrue(
            "the last resort has to be the figure that means something without knowing the price",
            alternatives.last().contains("%"),
        )
        assertTrue("and every form of it says which way", alternatives.all { it.contains("+") })
    }

    @Test
    fun `the caller's session move wins over the bar's own`() {
        val reading = text(change = ChartLegendChange(absolute = 42.5, percent = 3.25))
        assertTrue(reading.contains("42.50"))
        assertTrue(reading.contains("3.25%"))
        assertFalse("the bar's own move is not a second answer printed beside it", reading.contains("1.00%"))
    }

    @Test
    fun `with nothing handed over the bar answers for itself`() {
        val reading = text(open = 200.0, close = 190.0)
        assertTrue(reading.contains("10.00"))
        assertTrue(reading.contains("5.00%"))
    }

    @Test
    fun `a bar that opened at nothing has no percentage rather than a percentage of zero`() {
        assertTrue(
            "the empty set, not a claim that it rose without limit",
            text(open = 0.0, close = 5.0).contains(NO_VALUE),
        )
    }

    @Test
    fun `the row offers nothing to hide or delete`() {
        // It describes the series above it. An eye here would be a second switch for one line, and
        // a remove would be an offer to delete arithmetic.
        assertFalse(row().primary)
    }

    @Test
    fun `the move is named without spending the row's width on a word`() {
        val named = row().label
        assertNotEquals("", named)
        assertTrue("a Persian word here flips the direction of a row of Latin figures", named.length <= 2)
    }
}

/**
 * The market's state, on the instrument's own name.
 *
 * A row of its own would have cost a quarter of the plate's height budget to say one word, so it
 * rides the name — and it says nothing at all when the market is trading, which is the rule the
 * search list already follows.
 */
class ChartLegendSeriesNameTest {

    @Test
    fun `an open market is not announced`() {
        assertEquals("XAUUSD", legendSeriesName("XAUUSD", ChartMarketStatus.OPEN))
    }

    @Test
    fun `a caller that does not know says nothing either`() {
        assertEquals("XAUUSD", legendSeriesName("XAUUSD", null))
    }

    @Test
    fun `a shut market is named on the row the reader is already looking at`() {
        val shut = legendSeriesName("XAUUSD", ChartMarketStatus.WEEKEND)
        assertTrue("the instrument is still the first thing on the row", shut.startsWith("XAUUSD"))
        assertTrue("and it is longer for saying so", shut.length > "XAUUSD".length)
    }

    @Test
    fun `a weekend and a mid-week halt are told apart`() {
        assertNotEquals(
            legendSeriesName("XAUUSD", ChartMarketStatus.WEEKEND),
            legendSeriesName("XAUUSD", ChartMarketStatus.CLOSED),
        )
    }

    @Test
    fun `a nameless series still gets the word`() {
        val shut = legendSeriesName("", ChartMarketStatus.CLOSED)
        assertTrue(shut.isNotBlank())
        assertFalse("and not a separator with nothing in front of it", shut.startsWith(" "))
    }
}

/**
 * How much of the legend the plate can print.
 *
 * The height budget used to be enforced by a clip, so a row past it was sliced through the middle
 * and the «+N» counter — which counts only what the line cap dropped — said nothing about it. These
 * pin the shape of the answer rather than a number of rows, which is a function of dimensions that
 * are allowed to change.
 */
class ChartLegendRowsThatFitTest {

    private fun fit(budget: Float, heights: List<Float>) =
        legendRowsThatFit(budget = budget, padding = 5f, gap = 2f, heights = heights)

    private val four = List(4) { 24f }

    @Test
    fun `a plate with no room for a whole row prints nothing`() {
        assertEquals(0, fit(20f, four))
    }

    @Test
    fun `a plate never promises more rows than it can paint`() {
        val budget = 100f
        val rows = fit(budget, four)
        val used = rows * 24f + (rows - 1) * 2f + 10f
        assertTrue("every row, the gaps between them and the padding around them", used <= budget)
    }

    @Test
    fun `a taller plate never holds fewer rows`() {
        val counts = listOf(0f, 30f, 60f, 90f, 150f, 400f).map { fit(it, List(8) { 24f }) }
        assertEquals(counts, counts.sorted())
    }

    @Test
    fun `short rows are not measured as tall ones`() {
        // The change row is plain text and the rows around it carry buttons. Measuring them all at
        // the tallest throws away a study that would have fitted.
        assertTrue(fit(75f, listOf(24f, 12f, 24f)) > fit(75f, listOf(24f, 24f, 24f)))
    }

    @Test
    fun `a plate of no size at all is not a negative number of rows`() {
        assertEquals(0, fit(0f, four))
        assertEquals(0, fit(Float.NaN, four))
    }

    @Test
    fun `nothing to print is nothing to fit`() {
        assertEquals(0, fit(500f, emptyList()))
    }
}
