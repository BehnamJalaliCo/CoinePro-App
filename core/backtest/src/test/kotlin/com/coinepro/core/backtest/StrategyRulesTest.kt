package com.coinepro.core.backtest

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The adapter between the three named rules and the real engine.
 *
 * Two of these guard conversions that are wrong silently rather than loudly: a fee expressed in the
 * wrong unit and a position size that means something different on every symbol both produce a
 * report that looks entirely plausible and is off by a factor.
 */
class StrategyRulesTest {

    @Test
    fun `a round-trip cost is charged as half of it on each side`() {
        // Five basis points in and out together is 0.025 percent per side. Handing the fraction
        // straight to an engine that charges per side would double every cost in the report.
        assertEquals(0.025, StrategyRules.feePercentPerSide(0.0005), 1e-12)
        assertEquals(0.0, StrategyRules.feePercentPerSide(0.0), 1e-12)
    }

    @Test
    fun `every trade is for the quantity buy-and-hold would hold`() {
        val series = CandleSeries(List(10) { candle(it, 200.0) })
        // Ten thousand of equity against a first close of two hundred is fifty units, which is
        // exactly what the buy-and-hold line on the same chart holds.
        assertEquals(50.0, StrategyRules.positionSize(series, 10_000.0), 1e-9)
    }

    @Test
    fun `a rule whose fast average is not faster than its slow one is refused rather than run`() {
        val series = CandleSeries(rising(300))
        val refused = StrategyRules.directions(
            series,
            Backtest.Settings(strategy = Backtest.Strategy.MA_CROSS, fast = 50, slow = 50),
        )
        assertNull("a line crossing itself is not a strategy", refused)
    }

    @Test
    fun `a cross rule wants nothing while its slow average is still warming up`() {
        val series = CandleSeries(rising(300))
        val wanted = StrategyRules.directions(
            series,
            Backtest.Settings(strategy = Backtest.Strategy.MA_CROSS, fast = 10, slow = 40),
        )!!
        // Bar zero has no average of forty behind it, so the only honest answer is flat.
        assertEquals(StrategyRules.FLAT, wanted[0])
        assertEquals(StrategyRules.LONG, wanted[series.size - 1])
    }

    @Test
    fun `a short is produced only when the caller asked for one`() {
        val series = CandleSeries(falling(300))
        val settings = Backtest.Settings(strategy = Backtest.Strategy.MA_CROSS, fast = 10, slow = 40)

        val longOnly = StrategyRules.directions(series, settings, allowShorts = false)!!
        assertTrue(
            "a long-only run must never want a short, whatever the market did",
            longOnly.none { it == StrategyRules.SHORT },
        )

        val both = StrategyRules.directions(series, settings, allowShorts = true)!!
        assertTrue(
            "a falling market with shorts allowed must produce a short somewhere",
            both.any { it == StrategyRules.SHORT },
        )
    }

    @Test
    fun `a run over too little history is refused rather than reported`() {
        val short = CandleSeries(rising(Backtest.MINIMUM_BARS - 1))
        assertNull(StrategyRules.run(short))
        assertNotNull(StrategyRules.run(CandleSeries(rising(Backtest.MINIMUM_BARS + 60))))
    }

    @Test
    fun `an entry fills at the next bar's open, never at the close that produced the signal`() {
        val series = CandleSeries(rising(300))
        val result = StrategyRules.run(
            series,
            Backtest.Settings(strategy = Backtest.Strategy.MA_CROSS, fast = 5, slow = 20),
        )!!
        val first = result.trades.first()
        assertEquals(series[first.entryIndex].o, first.entryPrice, 1e-9)
    }

    private fun rising(count: Int): List<Candle> =
        List(count) { index -> candle(index, 100.0 + index) }

    private fun falling(count: Int): List<Candle> =
        List(count) { index -> candle(index, 100.0 + count - index) }

    private fun candle(index: Int, price: Double) =
        Candle(t = index * 60L, o = price, h = price + 0.5, l = price - 0.5, c = price)
}
