package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** TradingView's strategy «Properties» (5.17.0): slippage, a stop and a target, and the bar magnifier. */
class BacktestPropertiesTest {

    private fun bar(t: Long, o: Double, h: Double, l: Double, c: Double) = Candle(t, o, h, l, c, 1.0)

    /** Enter long on the first bar's close, hold. */
    private val buyAndHold = Strategy { index, _, position ->
        if (index == 0 && position == null) Signal.Enter(isLong = true, size = 1.0) else Signal.Hold
    }

    @Test
    fun `slippage fills a buy higher and a sell lower`() {
        val series = CandleSeries(listOf(bar(0, 100.0, 101.0, 99.0, 100.0), bar(60, 100.0, 101.0, 99.0, 100.0), bar(120, 100.0, 101.0, 99.0, 100.0)))
        val result = Backtest.run(series, buyAndHold, feePercent = 0.0, slippagePercent = 1.0)
        val trade = result.trades.single()
        assertEquals(101.0, trade.entryPrice, 1e-9)
        assertEquals(99.0, trade.exitPrice, 1e-9)
    }

    @Test
    fun `a stop and a target exit inside the bar at their own price`() {
        val series = CandleSeries(
            listOf(
                bar(0, 100.0, 100.0, 100.0, 100.0),
                bar(60, 100.0, 100.5, 97.0, 99.0), // falls through a 2 % stop
                bar(120, 99.0, 99.0, 99.0, 99.0),
            ),
        )
        val stopped = Backtest.run(series, buyAndHold, feePercent = 0.0, stopPercent = 2.0).trades.single()
        assertEquals(98.0, stopped.exitPrice, 1e-9)
        assertEquals(1, stopped.exitIndex)
    }

    @Test
    fun `a bar that opens beyond the stop fills at its open, not at the stop`() {
        val hit = Backtest.firstTouch(listOf(bar(0, 95.0, 96.0, 94.0, 95.0)), long = true, stop = 98.0, target = null)
        assertEquals(95.0, hit!!.second, 1e-9)
    }

    @Test
    fun `without lower bars the nearer extreme is touched first, with them the real order wins`() {
        // Opens nearer the high: the path is open → high → low, so the target is touched first.
        val wide = bar(60, 100.0, 103.0, 96.0, 100.0)
        assertEquals(103.0, Backtest.firstTouch(listOf(wide), long = true, stop = 97.0, target = 103.0)!!.second, 1e-9)
        // The magnifier says the low came first.
        val lower = listOf(bar(60, 100.0, 100.0, 96.0, 97.0), bar(90, 97.0, 103.0, 97.0, 100.0))
        assertEquals(97.0, Backtest.firstTouch(lower, long = true, stop = 97.0, target = 103.0)!!.second, 1e-9)
        assertNull(Backtest.firstTouch(listOf(bar(0, 100.0, 101.0, 99.0, 100.0)), long = true, stop = 90.0, target = 110.0))
    }
}
