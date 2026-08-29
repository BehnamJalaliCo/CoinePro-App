package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seven drawing-time types: the ones that keep the feed's bars and derive geometry from them.
 *
 * `ChartTransformsTest` covers the five that rewrite the series. These are the other kind, and they
 * need their own fixtures because what is being asserted is different: not "how many bars came out"
 * but "what number does each bar draw with", which is a value a reader can check by hand.
 */
class ChartTypesTest {

    private val start = 1_700_000_000L

    /**
     * Four bars whose every derived number is checkable on paper: one heavy up bar, one lighter up
     * bar, one nearly volumeless down bar and one ordinary down bar.
     */
    private val series = CandleSeries(
        listOf(
            Candle(t = start, o = 100.0, h = 104.0, l = 98.0, c = 102.0, v = 100.0),
            Candle(t = start + 3600, o = 102.0, h = 106.0, l = 101.0, c = 103.0, v = 50.0),
            Candle(t = start + 7200, o = 103.0, h = 103.5, l = 99.0, c = 99.5, v = 5.0),
            Candle(t = start + 10800, o = 99.5, h = 101.0, l = 97.0, c = 100.0, v = 25.0),
        ),
    )

    /** The same four bars off a feed that does not report volume, which is the MT5 side. */
    private val silent = CandleSeries(series.bars.map { it.copy(v = null) })

    // ---- the enum ---------------------------------------------------------------------------

    @Test
    fun `every chart type the engine offers is accounted for`() {
        assertEquals(18, ChartType.entries.size)
    }

    @Test
    fun `the four new line types draw as a line and the three new bar types do not`() {
        for (type in listOf(
            ChartType.BASELINE,
            ChartType.HLC_AREA,
            ChartType.STEP_LINE,
            ChartType.LINE_MARKERS,
        )) {
            assertTrue("$type must draw as a line", type.isLine)
        }
        for (type in listOf(ChartType.VOLUME_CANDLES, ChartType.FOOTPRINT, ChartType.TPO)) {
            assertFalse("$type draws a mark per bar, not a line", type.isLine)
        }
        // And the old answers are unchanged.
        assertTrue(ChartType.LINE.isLine && ChartType.AREA.isLine)
        assertFalse(ChartType.CANDLES.isLine || ChartType.HEIKIN_ASHI.isLine)
    }

    @Test
    fun `all seven new types keep a clock on the x axis and the five price-driven ones still do not`() {
        for (type in listOf(
            ChartType.BASELINE,
            ChartType.HLC_AREA,
            ChartType.STEP_LINE,
            ChartType.LINE_MARKERS,
            ChartType.VOLUME_CANDLES,
            ChartType.FOOTPRINT,
            ChartType.TPO,
        )) {
            assertTrue("$type plots the feed's own bars, so its x axis is time", type.isTimeBased)
        }
        for (type in listOf(
            ChartType.RENKO,
            ChartType.RANGE,
            ChartType.LINE_BREAK,
            ChartType.KAGI,
            ChartType.POINT_AND_FIGURE,
        )) {
            assertFalse("$type emits synthetic timestamps", type.isTimeBased)
        }
    }

    @Test
    fun `none of the new types rewrites the series`() {
        for (type in listOf(
            ChartType.BASELINE,
            ChartType.HLC_AREA,
            ChartType.STEP_LINE,
            ChartType.LINE_MARKERS,
            ChartType.VOLUME_CANDLES,
            ChartType.FOOTPRINT,
            ChartType.TPO,
        )) {
            assertEquals(series.bars, ChartTransforms.apply(series, type).bars)
        }
    }

    // ---- volume-scaled candles --------------------------------------------------------------

    @Test
    fun `a bar's width is its share of the window's heaviest volume`() {
        val widths = ChartTransforms.volumeWidths(series)
        assertEquals(4, widths.size)
        assertEquals(1.0, widths[0], 1e-9)
        assertEquals(0.5, widths[1], 1e-9)
        assertEquals(0.25, widths[3], 1e-9)
    }

    @Test
    fun `the thinnest bar is clamped to a fifth of its slot rather than drawn as a hairline`() {
        // Five against a peak of a hundred is a twentieth, and a twentieth of a slot is invisible —
        // a reader would take it for a gap in the feed rather than for a quiet hour.
        val widths = ChartTransforms.volumeWidths(series)
        assertEquals(0.2, widths[2], 1e-9)
        assertTrue(widths.all { it >= 0.2 - 1e-9 && it <= 1.0 + 1e-9 })
    }

    @Test
    fun `a feed with no volume draws ordinary full-width candles`() {
        val widths = ChartTransforms.volumeWidths(silent)
        assertEquals(4, widths.size)
        assertTrue(widths.all { it == 1.0 })
        assertEquals(0, ChartTransforms.volumeWidths(CandleSeries.EMPTY).size)
    }

    // ---- footprint --------------------------------------------------------------------------

    @Test
    fun `a footprint cuts the bar into equal rows and lands the top row on the high`() {
        val rows = ChartTransforms.footprint(series, index = 0, rows = 4)
        assertEquals(4, rows.size)
        assertEquals(98.0, rows[0].low, 1e-9)
        assertEquals(99.5, rows[0].high, 1e-9)
        assertEquals(101.0, rows[2].low, 1e-9)
        // The top row reaches the bar's own high exactly, so no hairline is left under the wick.
        assertEquals(104.0, rows[3].high, 1e-9)
        assertEquals(100.0, rows.sumOf { it.total }, 1e-9)
    }

    @Test
    fun `an up bar's volume is attributed to the buy column and a down bar's to the sell column`() {
        val up = ChartTransforms.footprint(series, index = 0, rows = 4)
        assertTrue(up.all { it.sell == 0.0 })
        assertEquals(25.0, up[0].buy, 1e-9)

        val down = ChartTransforms.footprint(series, index = 2, rows = 5)
        assertEquals(5, down.size)
        assertTrue(down.all { it.buy == 0.0 })
        assertEquals(1.0, down[4].sell, 1e-9)
    }

    @Test
    fun `a feed with no volume produces no footprint rather than a grid of zeros`() {
        // The same rule the volume pane follows: a market where nobody traded and a feed that does
        // not report volume look identical once zeros are drawn, and only one of them is true.
        for (index in silent.bars.indices) {
            assertEquals(emptyList<FootprintRow>(), ChartTransforms.footprint(silent, index, rows = 6))
        }
        assertEquals(emptyList<FootprintRow>(), ChartTransforms.footprint(series, index = 9, rows = 6))
    }

    // ---- TPO --------------------------------------------------------------------------------

    @Test
    fun `a TPO cell exists only where price actually traded`() {
        // A staircase of four bars over 100..105, five rows of one point each, two bars a bracket.
        // Bracket 0 covers rows 0..3, bracket 1 covers rows 2..4, and rows 2 and 3 are the ones
        // both brackets visited — which is the profile's widest point and the whole reason to draw
        // this chart.
        val profile = CandleSeries(
            listOf(
                Candle(start, 100.0, 102.0, 100.0, 102.0),
                Candle(start + 3600, 102.0, 103.0, 101.0, 103.0),
                Candle(start + 7200, 103.0, 104.0, 102.0, 104.0),
                Candle(start + 10800, 104.0, 105.0, 103.0, 105.0),
            ),
        )
        val cells = ChartTransforms.tpo(profile, fromIndex = 0, toIndex = 3, rows = 5, bracketBars = 2)
        assertEquals(7, cells.size)
        assertEquals(cells.size, cells.toSet().size)
        assertEquals(TpoBracket(0, 0), cells.first())
        assertEquals(TpoBracket(4, 1), cells.last())
        assertEquals(listOf(0, 1), cells.filter { it.rowIndex == 2 }.map { it.bracket })
        assertEquals(listOf(0, 1), cells.filter { it.rowIndex == 3 }.map { it.bracket })
        assertEquals(1, cells.count { it.rowIndex == 0 })
        assertEquals(1, cells.count { it.rowIndex == 4 })
    }

    @Test
    fun `a session that never moved still prints one row of letters`() {
        val flat = CandleSeries(
            (0 until 6).map { Candle(start + it * 3600, 50.0, 50.0, 50.0, 50.0) },
        )
        val cells = ChartTransforms.tpo(flat, 0, 5, rows = 10, bracketBars = 2)
        assertEquals(3, cells.size)
        assertTrue(cells.all { it.rowIndex == 0 })
        assertEquals(listOf(0, 1, 2), cells.map { it.bracket })
        assertEquals(emptyList<TpoBracket>(), ChartTransforms.tpo(CandleSeries.EMPTY, 0, 0, 10, 2))
    }

    // ---- step line --------------------------------------------------------------------------

    @Test
    fun `a step line holds the previous close until the next bar prints`() {
        val steps = ChartTransforms.stepLine(series)
        assertEquals(4, steps.size)
        // The first bar has nothing behind it and holds its own close; the rest hold the last one.
        assertEquals(102.0, steps[0], 1e-9)
        assertEquals(102.0, steps[1], 1e-9)
        assertEquals(103.0, steps[2], 1e-9)
        assertEquals(99.5, steps[3], 1e-9)
        assertEquals(0, ChartTransforms.stepLine(CandleSeries.EMPTY).size)
    }

    // ---- baseline ---------------------------------------------------------------------------

    @Test
    fun `the baseline halves never both hold a value at the same bar`() {
        val (above, below) = ChartTransforms.baselineSplit(series, base = 101.0)
        assertEquals(4, above.size)
        assertEquals(4, below.size)
        for (index in above.indices) {
            assertTrue(
                "bar $index is on both sides of the base at once",
                above[index].isNaN() != below[index].isNaN(),
            )
        }
        assertEquals(102.0, above[0], 1e-9)
        assertEquals(103.0, above[1], 1e-9)
        assertTrue(above[2].isNaN() && above[3].isNaN())
        assertEquals(99.5, below[2], 1e-9)
        assertEquals(100.0, below[3], 1e-9)
    }

    @Test
    fun `a close sitting exactly on the base counts as above it`() {
        // The same rule as a doji closing on its open: one side has to own it, and picking the
        // upper one keeps a flat market drawn in one colour rather than flickering between two.
        val (above, below) = ChartTransforms.baselineSplit(series, base = 102.0)
        assertEquals(102.0, above[0], 1e-9)
        assertTrue(below[0].isNaN())
    }

    @Test
    fun `the default base level is the window's opening close`() {
        assertEquals(102.0, ChartTransforms.defaultBaseLevel(series), 1e-9)
        assertEquals(0.0, ChartTransforms.defaultBaseLevel(CandleSeries.EMPTY), 1e-9)
    }

    // ---- derived row counts -----------------------------------------------------------------

    @Test
    fun `a derived row count is the span measured in eighths of an average bar`() {
        // The fixture's average true range is 4.5, so a row is 0.5625. The window spans 97 to 106,
        // which is nine points, which is sixteen rows; one bar of it spans six, which is eleven.
        assertEquals(16, ChartTransforms.defaultRows(series))
        assertEquals(11, ChartTransforms.defaultRows(series, fromIndex = 0, toIndex = 0))
    }

    @Test
    fun `a derived row count stays between four and sixty-four however still or violent the market`() {
        val flat = CandleSeries((0 until 20).map { Candle(start + it * 60, 5.0, 5.0, 5.0, 5.0) })
        assertEquals(4, ChartTransforms.defaultRows(flat))

        val trending = CandleSeries(
            (0 until 200).map { index ->
                val base = 100.0 + index
                Candle(start + index * 60, base, base + 0.5, base - 0.5, base + 0.4)
            },
        )
        assertEquals(64, ChartTransforms.defaultRows(trending))
        assertEquals(4, ChartTransforms.defaultRows(CandleSeries.EMPTY))
    }
}
