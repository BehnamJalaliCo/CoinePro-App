package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The legend, as data.
 *
 * It was painted straight onto the canvas, so nothing about it could be asserted and nothing about
 * it could be pressed — a reader who wanted an EMA gone had to find it again in the indicator
 * sheet. Building the rows first is what makes both possible; these say what a row carries.
 */
class ChartLegendRowsTest {

    private val series = CandleSeries(
        List(6) { index ->
            Candle(
                t = 1_700_000_000L + index * 3_600L,
                o = 100.0 + index,
                h = 102.0 + index,
                l = 99.0 + index,
                c = 101.0 + index,
                v = 10.0 + index,
            )
        },
    )

    private fun line(label: String?, value: Double) =
        ChartLine(values = Line.of(series.size) { value }, colour = 0xFF4C9AFF, label = label)

    private fun rowsFor(
        decoration: ChartDecoration,
        index: Int = series.size - 1,
        tracking: Boolean = false,
        rebased: List<DoubleArray> = emptyList(),
    ) = legendRows(decoration, series, index, tracking, rebased, seriesLabel = "XAUUSD")

    @Test
    fun `the first row is the price and carries its four numbers`() {
        val head = rowsFor(ChartDecoration()).first()
        assertEquals(ChartLegendTarget.Series, head.target)
        assertEquals("XAUUSD", head.label)
        // Close first in every form, including the widest. The conventional order is OHLC and this
        // deliberately is not it: whichever form a narrow row picks, the close must survive, and at
        // the end of the string it is the first thing an ellipsis takes.
        assertTrue(
            "the widest form names all four prices",
            head.alternatives.first().let { it.startsWith("C ") && it.contains("O ") && it.contains("H ") && it.contains("L ") },
        )
        assertTrue("every form leads with the close", head.alternatives.all { it.startsWith("C ") || it.first().isDigit() })
        assertTrue("the last resort is the close alone", head.alternatives.last().startsWith("C "))
        assertTrue(
            "and the close is in every form of it",
            head.alternatives.all { it.contains(formatPrice(series.bars.last().c, decimalsFor(series.bars.last().c))) },
        )
        assertNull("the price takes the direction colour the candles are using", head.colour)
    }

    @Test
    fun `an overlay gets a row that names it and reads it`() {
        val rows = rowsFor(ChartDecoration(overlays = listOf(line("EMA 20", 105.5))))
        val row = rows.first { it.target == ChartLegendTarget.Overlay(0) }
        assertEquals("EMA 20", row.label)
        assertTrue("the reading is the value at that bar", row.alternatives.single().contains("105.5"))
        assertEquals(0xFF4C9AFF, row.colour)
        assertTrue("an overlay can be hidden, configured and removed", row.primary)
    }

    @Test
    fun `an overlay with no name is not given a row`() {
        val rows = rowsFor(ChartDecoration(overlays = listOf(line(null, 1.0))))
        assertTrue(rows.none { it.target == ChartLegendTarget.Overlay(0) })
    }

    @Test
    fun `a value that is not there reads as the empty set`() {
        val absent = ChartLine(values = Line.empty(series.size), colour = 0xFF00C2D1, label = "RSI 14")
        val rows = rowsFor(ChartDecoration(overlays = listOf(absent)))
        val row = rows.first { it.target == ChartLegendTarget.Overlay(0) }
        assertEquals(NO_VALUE, row.alternatives.single())
        assertFalse("never a dash, which is a minus sign in a column of signed numbers", row.alternatives.single().contains("-"))
    }

    @Test
    fun `a pane names itself always and reads its lines only while tracking`() {
        val pane = ChartPane(title = "RSI 14", lines = listOf(line("RSI", 62.5)))
        val resting = rowsFor(ChartDecoration(panes = listOf(pane)))
        assertNotNull(resting.firstOrNull { it.target == ChartLegendTarget.Pane(0) })
        assertTrue(
            "a resting legend does not print an oscillator's number",
            resting.none { it.alternatives.any { text -> text.contains("62.5") } },
        )

        val tracked = rowsFor(ChartDecoration(panes = listOf(pane)), tracking = true)
        assertTrue(
            "holding the crosshair is how a reader asks for it",
            tracked.any { it.alternatives.any { text -> text.contains("62.5") } },
        )
        assertTrue(
            "and only the pane's own row carries the buttons",
            tracked.filter { it.target == ChartLegendTarget.Pane(0) }.count { it.primary } == 1,
        )
    }

    @Test
    fun `the reading follows the bar the crosshair is on`() {
        val decoration = ChartDecoration()
        val early = rowsFor(decoration, index = 0).first().alternatives.last()
        val late = rowsFor(decoration, index = series.size - 1).first().alternatives.last()
        assertFalse("a legend that reads the same bar wherever the finger is, is a header", early == late)
    }

    @Test
    fun `a comparison keeps its own basis in its reading`() {
        val comparison = ComparisonSeries(
            symbol = "BTCUSDT",
            label = "BTC",
            colour = 0xFFE69F00,
            values = DoubleArray(series.size) { 100.0 },
            times = series.time.copyOf(),
        )
        val rebased = listOf(DoubleArray(series.size) { 12.5 })
        val rows = rowsFor(
            ChartDecoration(comparisons = listOf(comparison), comparisonBasis = ComparisonBasis.PERCENT),
            rebased = rebased,
        )
        val row = rows.first { it.target == ChartLegendTarget.Comparison(0) }
        assertEquals("BTC", row.label)
        assertTrue("a percentage is signed, because it is a change", row.alternatives.single().startsWith("+"))
        assertTrue(row.alternatives.single().endsWith("%"))
    }

    @Test
    fun `every row names something the caller can act on by index`() {
        val decoration = ChartDecoration(
            overlays = listOf(line("EMA 20", 1.0), line("EMA 50", 2.0)),
            panes = listOf(ChartPane(title = "MACD", lines = listOf(line("MACD", 0.1)))),
        )
        val rows = rowsFor(decoration)
        assertTrue(rows.any { it.target == ChartLegendTarget.Overlay(1) })
        assertTrue(rows.any { it.target == ChartLegendTarget.Pane(0) })
        // The indices are the caller's own list positions, which is the only identity that survives
        // a state emission rebuilding every ChartLine.
        assertEquals(
            listOf(ChartLegendTarget.Overlay(0), ChartLegendTarget.Overlay(1)),
            rows.map { it.target }.filterIsInstance<ChartLegendTarget.Overlay>(),
        )
    }
}
