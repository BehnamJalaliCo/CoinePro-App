package com.coinepro.feature.chart

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.common.BidiText
import com.coinepro.core.symbols.MarketStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The move at the top of the chart, and the state chips beside the instrument.
 *
 * The heading printed a ratio and nothing else, and a ratio is the one figure on a chart a reader
 * cannot check against anything. What is asserted here is everything that made adding the absolute
 * move a correctness question rather than a layout one: the sign character, the precision the move
 * is quoted at, and the fact that a flat window is not a fall.
 */
class ChartHeadlineTest {

    /** The sign a keyboard produces, which must never appear in a market figure in this app. */
    private val hyphen = '-'

    private fun plain(value: String) = BidiText.strip(value)

    private fun series(closes: List<Double>) = CandleSeries(
        closes.mapIndexed { index, close ->
            Candle(1_700_000_000L + index * 3_600, close, close + 1, close - 1, close, 10.0)
        },
    )

    @Test
    fun `the move leads and the ratio follows it in brackets`() {
        val line = plain(ChartHeadline.move(absolute = 5.12, percent = 1.63, price = 319.70))

        assertEquals("+5.12 (+1.63%)", line)
    }

    @Test
    fun `a fall is written with U+2212 and never with a hyphen`() {
        // `MarketNumberFormatter.signedPercent` writes a hyphen, which is why the heading could not
        // keep using it: the legend drawn over the plot has always used U+2212, so the same move
        // appeared twice on one screen with two different minus signs.
        val line = plain(ChartHeadline.move(absolute = -5.12, percent = -1.63, price = 319.70))

        assertEquals("−5.12 (−1.63%)", line)
        assertFalse(line.any { it == hyphen })
    }

    @Test
    fun `a window that has not moved is unsigned and is not a fall`() {
        // «+0.00» claims a rise that did not happen and the sell colour claims a fall that did not.
        val line = plain(ChartHeadline.move(absolute = 0.0, percent = 0.0, price = 319.70))

        assertEquals("0.00 (0.00%)", line)
        assertNull(ChartHeadline.rising(0.0, 0.0))
    }

    @Test
    fun `the move is quoted at the price's own precision, not the percentage's`() {
        // The move and the price sit one above the other in the heading, so they have to agree: a
        // sub-unit instrument moving 0.0012 rounds to 0.00 at the two decimals a percentage uses,
        // which reads as a market that did not move.
        val line = plain(ChartHeadline.move(absolute = 0.0012, percent = 0.23, price = 0.5241))

        assertEquals("+0.0012 (+0.23%)", line)
        // And the same rule the other way: gold is quoted to one place on this chart, and the move
        // must not claim a precision the price above it does not have.
        assertEquals("+5.1 (+0.19%)", plain(ChartHeadline.move(5.12, 0.19, 2_643.18)))
    }

    @Test
    fun `with no price the move sets its own precision rather than refusing`() {
        // The frame before the first candles land has a window and no last price. Six places off a
        // move of 0.0012 is the move's own magnitude speaking, which is the only honest answer
        // available and is better than «—» beside a chart that is drawing.
        assertEquals("+0.001200 (+0.23%)", plain(ChartHeadline.move(0.0012, 0.23, null)))
    }

    @Test
    fun `either half missing degrades to the other rather than to nothing`() {
        assertEquals("+5.12", plain(ChartHeadline.move(5.12, null, 319.70)))
        assertEquals("+1.63%", plain(ChartHeadline.move(null, 1.63, 319.70)))
        assertEquals(ChartHeadline.NO_VALUE, ChartHeadline.move(null, null, 319.70))
    }

    @Test
    fun `a figure the feed cannot have sent is the empty set and never a stray sign`() {
        assertEquals(ChartHeadline.NO_VALUE, ChartHeadline.move(Double.NaN, Double.NaN, 1.0))
        assertEquals(ChartHeadline.NO_VALUE, ChartHeadline.figure(Double.POSITIVE_INFINITY, 2))
    }

    @Test
    fun `the whole line is isolated once so the bracket cannot travel`() {
        // Isolating each number separately leaves the bracket outside both runs, and a right-to-left
        // paragraph then moves it to the far end of the row.
        val line = ChartHeadline.move(absolute = 5.12, percent = 1.63, price = 319.70)

        assertEquals(1, line.count { it == '⁦' })
        assertEquals(1, line.count { it == '⁩' })
    }

    @Test
    fun `the direction follows the move and falls back to the ratio`() {
        assertTrue(ChartHeadline.rising(5.12, 1.63) == true)
        assertTrue(ChartHeadline.rising(-5.12, -1.63) == false)
        assertTrue(ChartHeadline.rising(null, 1.63) == true)
        assertNull(ChartHeadline.rising(null, null))
    }

    @Test
    fun `the absolute move is measured over the same two bars as the percentage`() {
        val state = ChartUiState(symbol = "XAUUSD", series = series(listOf(100.0, 110.0, 120.0)))

        assertEquals(20.0, state.changeAbsolute!!, 1e-9)
        assertEquals(20.0, state.changePercent!!, 1e-9)
    }

    @Test
    fun `a series that opened at zero has a move even though it has no ratio`() {
        // A percentage divides and a difference does not, so the two are allowed to disagree about
        // whether there is an answer — and the heading then prints the half that exists.
        val state = ChartUiState(symbol = "XAUUSD", series = series(listOf(0.0, 4.0)))

        assertEquals(4.0, state.changeAbsolute!!, 1e-9)
        assertNull(state.changePercent)
    }

    @Test
    fun `an empty series has neither figure`() {
        val state = ChartUiState(symbol = "XAUUSD")

        assertNull(state.changeAbsolute)
        assertNull(state.changePercent)
    }

    @Test
    fun `a trading market is one chip and a replay adds the second`() {
        val open = MarketStatus(open = true, weekend = false)

        assertEquals(listOf(ChartHeaderState.MARKET_OPEN), chartHeaderStates(open, replayOn = false))
        assertEquals(
            listOf(ChartHeaderState.MARKET_OPEN, ChartHeaderState.REPLAY),
            chartHeaderStates(open, replayOn = true),
        )
    }

    @Test
    fun `a closed weekend is not drawn as a closed venue`() {
        // Two days a week is not a fault and must not be coloured as one — see HeaderStateChip.
        val weekend = MarketStatus(open = false, weekend = true)
        val shut = MarketStatus(open = false, weekend = false)

        assertEquals(listOf(ChartHeaderState.MARKET_WEEKEND), chartHeaderStates(weekend, false))
        assertEquals(listOf(ChartHeaderState.MARKET_CLOSED), chartHeaderStates(shut, false))
    }
}
