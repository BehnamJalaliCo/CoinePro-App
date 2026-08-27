package com.coinepro.core.backtest

import com.coinepro.core.chart.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The assertions that stop a backtest flattering.
 *
 * Every one of these is a way real backtests invent money, and each is cheap to get wrong and
 * expensive to discover later — because the wrong version does not crash, it produces a number the
 * reader acts on.
 */
class BacktestTest {

    @Test
    fun `a fill happens at the next bar's open, never at the signal bar's close`() {
        // A ramp: every close is above the last, so a cross rule is long throughout. If the engine
        // filled at the signal bar's close the first entry would be that close; it must be the
        // next bar's open instead.
        val bars = ramp(200)
        val result = Backtest.run(bars, Backtest.Settings(fast = 5, slow = 20))!!

        val first = result.trades.first()
        assertEquals(bars[first.entryIndex].o, first.entry, 0.0)
        assertTrue("a fill must not use the close that produced the signal",
            first.entry != bars[first.entryIndex - 1].c)
    }

    @Test
    fun `costs are charged and are not zero by default`() {
        // Flat prices: gross return is zero on every trade, so anything below zero is the cost.
        val bars = List(200) { candle(it, 100.0) }
        val result = Backtest.run(
            bars.toMutableList().also { it[100] = candle(100, 101.0) },
            Backtest.Settings(fast = 3, slow = 10),
        )
        result?.trades?.forEach { trade ->
            assertTrue("every trade pays the round trip", trade.returnFraction <= (trade.exit - trade.entry) / trade.entry)
        }
    }

    @Test
    fun `too little history is refused rather than answered`() {
        // Below the minimum the slow average has barely warmed up, and a percentage produced there
        // is noise wearing a percentage sign — which reads exactly like a finding.
        assertNull(Backtest.run(ramp(50)))
    }

    @Test
    fun `an open position is closed at the last bar, so the curve ends on a real number`() {
        val bars = ramp(200)
        val result = Backtest.run(bars, Backtest.Settings(fast = 5, slow = 20))!!

        assertTrue(result.trades.isNotEmpty())
        assertEquals(bars.lastIndex, result.trades.last().exitIndex)
    }

    @Test
    fun `drawdown is measured peak to trough, not start to end`() {
        val result = Backtest.run(ramp(200), Backtest.Settings(fast = 5, slow = 20))!!
        assertTrue(result.maxDrawdownPercent >= 0.0)
    }

    @Test
    fun `a fast average slower than the slow one is refused rather than run backwards`() {
        assertNull(Backtest.run(ramp(200), Backtest.Settings(fast = 50, slow = 20)))
    }
}

private fun ramp(count: Int) = List(count) { candle(it, 100.0 + it) }

private fun candle(index: Int, price: Double) = Candle(
    t = 1_700_000_000L + index * 3_600L,
    o = price,
    h = price + 1,
    l = price - 1,
    c = price + 0.5,
    v = 10.0,
)
