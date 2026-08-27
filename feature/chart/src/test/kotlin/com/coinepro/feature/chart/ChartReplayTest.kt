package com.coinepro.feature.chart

import com.coinepro.core.chart.ChartPoint
import com.coinepro.core.chart.Drawing
import com.coinepro.core.chart.DrawingState
import com.coinepro.core.chart.TradeSide
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replay's one safety property, asserted where it can actually fail.
 *
 * The engine itself is tested in `core:chart`. What is tested here is the wiring, which is where
 * the future leaks: the screen draws `visibleSeries`, and every derived thing — indicators,
 * structure levels, the last price — has to come from the same slice. A test that only checked the
 * cursor would pass while a moving average was computed over bars the reader has not been shown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartReplayTest {

    @Test
    fun `entering replay hides the future from everything the screen reads`() = runTest {
        val controller = ChartController("BTCUSDT", FakeCandleGateway(bars = 120), this)
        controller.start()
        runCurrent()

        controller.enterReplay()
        val state = controller.state.value

        assertTrue(state.replay.isOn)
        // 55% of 120 is bar 66, so 67 bars are visible and the other 53 are the future.
        assertEquals(67, state.visibleSeries.bars.size)
        assertEquals(120, state.series.bars.size)
        // The price the header shows is the replayed one, not the real last close.
        assertEquals(state.visibleSeries.bars.last().c, state.lastPrice!!, 0.0)
    }

    @Test
    fun `a series too short to replay refuses rather than entering an empty replay`() = runTest {
        val controller = ChartController("BTCUSDT", FakeCandleGateway(bars = 10), this)
        controller.start()
        runCurrent()

        controller.enterReplay()

        assertFalse(controller.state.value.replay.isOn)
    }

    @Test
    fun `stepping forward reveals exactly one bar`() = runTest {
        val controller = ChartController("BTCUSDT", FakeCandleGateway(bars = 120), this)
        controller.start()
        runCurrent()
        controller.enterReplay()
        val before = controller.state.value.visibleSeries.bars.size

        controller.replayStep()

        assertEquals(before + 1, controller.state.value.visibleSeries.bars.size)
    }

    @Test
    fun `leaving replay restores the whole series`() = runTest {
        val controller = ChartController("BTCUSDT", FakeCandleGateway(bars = 120), this)
        controller.start()
        runCurrent()
        controller.enterReplay()

        controller.exitReplay()

        assertFalse(controller.state.value.replay.isOn)
        assertEquals(120, controller.state.value.visibleSeries.bars.size)
    }
}

private class FakeCandleGateway(private val bars: Int) : CandleGateway {
    override suspend fun load(
        symbol: String,
        timeframe: Timeframe,
        limit: Int,
        before: Long?,
    ): CandlePage = CandlePage(
        symbol = symbol,
        timeframe = timeframe,
        candles = List(bars) { index ->
            val price = 100.0 + index
            OhlcBar(
                t = 1_700_000_000L + index * 3_600L,
                o = price,
                h = price + 1,
                l = price - 1,
                c = price + 0.5,
                v = 10.0,
            )
        },
        hasMore = false,
    )
}

/**
 * The setup read off the drawing.
 *
 * The side comes from the geometry rather than from the reader, and that is the assertion worth
 * having: a drawing whose stop is above its entry is a sell no matter what anybody selected, and
 * the numbers under it have to agree with the lines on screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartSetupTest {

    @Test
    fun `a stop below the entry is a buy, and the target is the 2R the chart draws`() = runTest {
        val controller = ChartController("BTCUSDT", FakeCandleGateway(bars = 60), this)
        controller.place(entry = 100.0, stop = 90.0)

        val setup = controller.state.value.setup!!

        assertEquals(TradeSide.BUY, setup.side)
        assertEquals(100.0, setup.entry, 0.0)
        assertEquals(90.0, setup.stopLoss, 0.0)
        assertEquals(120.0, setup.takeProfit, 0.0)
    }

    @Test
    fun `a stop above the entry is a sell`() = runTest {
        val controller = ChartController("BTCUSDT", FakeCandleGateway(bars = 60), this)
        controller.place(entry = 100.0, stop = 110.0)

        assertEquals(TradeSide.SELL, controller.state.value.setup!!.side)
    }

    @Test
    fun `a stop sitting on the entry is not a setup at all`() = runTest {
        val controller = ChartController("BTCUSDT", FakeCandleGateway(bars = 60), this)
        controller.place(entry = 100.0, stop = 100.0)

        // No risk, therefore no ratio and no size. Refusing here is what stops every number on the
        // sheet being a confident answer to a question the reader did not ask.
        assertNull(controller.state.value.setup)
    }
}

/** Places a completed `longshort` drawing, the way two taps on the chart would. */
private fun ChartController.place(entry: Double, stop: Double) {
    onDrawing(
        DrawingState(
            drawings = listOf(
                Drawing(
                    id = 1,
                    toolId = "longshort",
                    points = listOf(ChartPoint(1_700_000_000L, entry), ChartPoint(1_700_003_600L, stop)),
                ),
            ),
        ),
    )
}
