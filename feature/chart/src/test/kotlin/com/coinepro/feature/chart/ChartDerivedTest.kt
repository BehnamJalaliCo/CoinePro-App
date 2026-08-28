package com.coinepro.feature.chart

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.DrawingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The indicator results, and the rule that lets them be carried across states.
 *
 * Two bugs live here if this is wrong, and they are opposites. Recompute too often and a drawing
 * drag janks on a cheap phone — every frame recomputing every switched-on indicator, none of whose
 * inputs moved. Carry too eagerly and the chart draws last timeframe's moving average over this
 * timeframe's candles, silently, with nothing on screen to say so.
 *
 * So the carry is *checked* rather than trusted, and these tests are that check.
 */
class ChartDerivedTest {

    private fun series(bars: Int, base: Double = 100.0) = CandleSeries(
        (0 until bars).map { index ->
            val price = base + index * 0.25
            Candle(1_700_000_000L + index * 3600, price, price + 1, price - 1, price + 0.4, 10.0)
        },
    )

    private val bars = series(300)

    @Test
    fun `a state with no indicators produces nothing and costs nothing`() {
        val state = ChartUiState(symbol = "BTCUSDT", series = bars)
        assertTrue(state.overlays.isEmpty())
        assertTrue(state.panes.isEmpty())
        assertTrue(state.levels.isEmpty())
        assertTrue(state.markers.isEmpty())
    }

    @Test
    fun `the four readings come from one computation`() {
        // Reading all four used to run each structure study three times over. They are one value
        // now, and `assertSame` is the assertion that says so.
        val state = ChartUiState(
            symbol = "BTCUSDT",
            series = bars,
            activeIndicators = setOf("ema", "rsi", "pivots"),
        )
        val first = state.derived
        assertSame(first, state.derived)
        assertSame(first.overlays, state.overlays)
        assertSame(first.panes, state.panes)
    }

    @Test
    fun `a drawing frame carries the result rather than recomputing it`() {
        val state = ChartUiState(
            symbol = "BTCUSDT",
            series = bars,
            activeIndicators = setOf("ema", "rsi", "pivots"),
        )
        // What `onDrawing` does on every frame of a drag.
        val dragged = state.copy(drawing = DrawingState(), carried = state.derived)
        assertSame("A drag frame recomputed every indicator", state.derived, dragged.derived)
    }

    @Test
    fun `a carried result is discarded when the bars change`() {
        // The bug the key exists to prevent: last timeframe's averages over this timeframe's
        // candles, with nothing on screen to say so.
        val state = ChartUiState(symbol = "BTCUSDT", series = bars, activeIndicators = setOf("ema"))
        val stale = state.derived
        val reloaded = state.copy(series = series(300, base = 500.0), carried = stale)
        assertNotSame(stale, reloaded.derived)
        assertFalse(stale.overlays.first().values.raw(299) == reloaded.overlays.first().values.raw(299))
    }

    @Test
    fun `a carried result is discarded when an indicator is switched on or off`() {
        val state = ChartUiState(symbol = "BTCUSDT", series = bars, activeIndicators = setOf("ema"))
        val added = state.copy(activeIndicators = setOf("ema", "sma"), carried = state.derived)
        assertNotSame(state.derived, added.derived)
        assertEquals(2, added.overlays.size)
    }

    @Test
    fun `a carried result is discarded when a lookback changes`() {
        // The one an identity check on the series alone would miss: same bars, same indicators,
        // different period. It has to be compared by value.
        val state = ChartUiState(symbol = "BTCUSDT", series = bars, activeIndicators = setOf("ema"))
        val reperiodded = state.copy(indicatorPeriods = mapOf("ema" to 200), carried = state.derived)
        assertNotSame(state.derived, reperiodded.derived)
        assertTrue(reperiodded.overlays.first().label!!.contains("200"))
    }

    @Test
    fun `replay derives from the visible bars, never from the future`() {
        // The safety property replay exists for. An average computed over every bar would place
        // itself using prices the reader is not allowed to have seen yet.
        val state = ChartUiState(symbol = "BTCUSDT", series = bars, activeIndicators = setOf("ema"))
        val full = state.overlays.first().values
        val rewound = state.copy(
            replay = com.coinepro.core.chart.Replay.enter(bars.bars, startIndex = 100)!!,
        )
        val partial = rewound.overlays.first().values
        assertTrue("Replay must not compute past the visible edge", partial.size < full.size)
    }
}
