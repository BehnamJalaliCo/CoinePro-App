package com.coinepro.core.chart

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The strategy runner, checked against trades worked out on paper.
 *
 * Every fixture here is small enough that the expected numbers were computed by hand and written
 * into the assertion, which is the only way a backtest can be trusted: a run over a thousand real
 * bars produces a plausible number for any arithmetic, right or wrong. The four claims that matter
 * most are the ones a wrong implementation gets away with for years — that excursions come from
 * bar highs and lows rather than closes, that equity drawdown is peak-to-trough on the account and
 * not the worst trade, that Sortino divides by every return, and that the ratios are annualised.
 */
class BacktestTest {

    private companion object {
        const val HOUR = 3_600L
        const val DAY = 86_400L
        const val DELTA = 1e-9
    }

    /**
     * Five bars with wide ranges and closes that sit nowhere near the extremes.
     *
     * Built that way deliberately: a run-up read off the closes gives 8 here and a run-up read off
     * the highs gives 12, so the two implementations cannot both pass.
     */
    private fun fixture(): CandleSeries = CandleSeries(
        listOf(
            Candle(1_000, o = 100.0, h = 103.0, l = 99.0, c = 102.0),
            Candle(1_000 + HOUR, o = 100.0, h = 110.0, l = 90.0, c = 105.0),
            Candle(1_000 + 2 * HOUR, o = 106.0, h = 112.0, l = 95.0, c = 108.0),
            Candle(1_000 + 3 * HOUR, o = 109.0, h = 115.0, l = 104.0, c = 110.0),
            Candle(1_000 + 4 * HOUR, o = 112.0, h = 118.0, l = 111.0, c = 115.0),
        ),
    )

    /** Enter long on bar 0, leave on bar 2. Fills land on bars 1 and 3. */
    private fun enterAtZeroExitAtTwo(isLong: Boolean = true) = Strategy { index, _, _ ->
        when (index) {
            0 -> Signal.Enter(isLong = isLong, size = 1.0)
            2 -> Signal.Exit
            else -> Signal.Hold
        }
    }

    private fun trade(
        pnlTarget: Double,
        bars: Int,
        entry: Double = 100.0,
    ) = Trade(
        entryIndex = 0,
        entryTime = 0,
        entryPrice = entry,
        exitIndex = bars,
        exitTime = bars * HOUR,
        exitPrice = entry + pnlTarget,
        isLong = true,
        size = 1.0,
        fee = 0.0,
    )

    // ── the fill convention and one hand-computed trade ────────────────────────────────

    @Test
    fun `a signal fills at the next bar's open, never at the close that produced it`() {
        // The single most common way a backtest invents money. Bar 0 closes at 102 and bar 1 opens
        // at 100; a fill at 102 would hand the strategy two points nobody could have had.
        val result = Backtest.run(fixture(), enterAtZeroExitAtTwo(), startingEquity = 1_000.0, feePercent = 0.0)
        val trade = result.trades.single()
        assertEquals(1, trade.entryIndex)
        assertEquals(100.0, trade.entryPrice, DELTA)
        assertEquals(3, trade.exitIndex)
        assertEquals(109.0, trade.exitPrice, DELTA)
        assertEquals(1_000 + HOUR, trade.entryTime)
        assertEquals(1_000 + 3 * HOUR, trade.exitTime)
    }

    @Test
    fun `one hand-computed long trade`() {
        val result = Backtest.run(fixture(), enterAtZeroExitAtTwo(), startingEquity = 1_000.0, feePercent = 0.0)
        val trade = result.trades.single()
        assertEquals(9.0, trade.pnl, DELTA)
        assertEquals(9.0, trade.pnlPercent, DELTA)
        assertEquals(2, trade.barsHeld)
        assertTrue(trade.isWin)
        assertFalse(trade.isLoss)
        assertEquals(listOf(1_000.0, 1_005.0, 1_008.0, 1_009.0, 1_009.0), result.equityCurve.toList())
    }

    @Test
    fun `fees are charged on both sides at the filled price`() {
        // Ten basis points a side: 100 × 0.001 in, 109 × 0.001 out.
        val result = Backtest.run(fixture(), enterAtZeroExitAtTwo(), startingEquity = 1_000.0, feePercent = 0.1)
        val trade = result.trades.single()
        assertEquals(0.209, trade.fee, 1e-12)
        assertEquals(9.0, trade.grossPnl, DELTA)
        assertEquals(8.791, trade.pnl, 1e-12)
        assertEquals(0.209, result.metrics.totalFees, 1e-12)
    }

    // ── run-up and drawdown come from the highs and lows ───────────────────────────────

    @Test
    fun `run-up and drawdown are read from the bar highs and lows, not from the closes`() {
        // Held through bars 1 and 2. Their highs reach 112 and their lows reach 90, while their
        // closes only reach 105 and 108 and never go below the 100 entry at all. A close-based
        // implementation reports a run-up of 8 and a drawdown of 0 — that is, it reports that the
        // trade was never once offside, which is the opposite of what happened.
        val trade = Backtest
            .run(fixture(), enterAtZeroExitAtTwo(), startingEquity = 1_000.0, feePercent = 0.0)
            .trades
            .single()
        assertEquals(112.0, trade.highestHigh, DELTA)
        assertEquals(90.0, trade.lowestLow, DELTA)
        assertEquals(12.0, trade.runUp, DELTA)
        assertEquals(10.0, trade.drawdown, DELTA)
        assertTrue("a close-based run-up would be 8", trade.runUp > 8.0)
        assertTrue("a close-based drawdown would be 0", trade.drawdown > 0.0)
    }

    @Test
    fun `a short trade's excursions are mirrored, not negated`() {
        val trade = Backtest
            .run(fixture(), enterAtZeroExitAtTwo(isLong = false), startingEquity = 1_000.0, feePercent = 0.0)
            .trades
            .single()
        assertEquals(-9.0, trade.pnl, DELTA)
        // The low is the short's run-up and the high is its drawdown — the long's numbers swapped.
        assertEquals(10.0, trade.runUp, DELTA)
        assertEquals(12.0, trade.drawdown, DELTA)
    }

    @Test
    fun `both excursions are floored at zero rather than going negative`() {
        val flat = Trade(0, 0, 100.0, 1, HOUR, 100.0, isLong = true, size = 1.0, fee = 0.0)
        assertEquals(0.0, flat.runUp, DELTA)
        assertEquals(0.0, flat.drawdown, DELTA)
    }

    @Test
    fun `the open position handed to a strategy is marked to the current close and carries its excursion`() {
        var seen: Trade? = null
        val strategy = Strategy { index, _, position ->
            if (index == 2) seen = position
            when (index) {
                0 -> Signal.Enter(isLong = true, size = 1.0)
                else -> Signal.Hold
            }
        }
        Backtest.run(fixture(), strategy, startingEquity = 1_000.0, feePercent = 0.0)
        val position = requireNotNull(seen) { "the strategy was never handed an open position" }
        assertEquals(108.0, position.exitPrice, DELTA)
        assertEquals(8.0, position.pnl, DELTA)
        assertEquals(1, position.barsHeld)
        // The running excursion, so a stop written against drawdown sees the offside move.
        assertEquals(12.0, position.runUp, DELTA)
        assertEquals(10.0, position.drawdown, DELTA)
    }

    @Test
    fun `a position still open at the end is closed at the final close`() {
        val strategy = Strategy { index, _, _ ->
            if (index == 0) Signal.Enter(isLong = true, size = 1.0) else Signal.Hold
        }
        val result = Backtest.run(fixture(), strategy, startingEquity = 1_000.0, feePercent = 0.0)
        val trade = result.trades.single()
        assertEquals(4, trade.exitIndex)
        assertEquals(115.0, trade.exitPrice, DELTA)
        assertEquals(15.0, trade.pnl, DELTA)
        assertEquals(1_015.0, result.equityCurve.last(), DELTA)
    }

    @Test
    fun `an entry signalled on the final bar never fills, because there is no next open`() {
        val strategy = Strategy { index, series, _ ->
            if (index == series.size - 1) Signal.Enter(isLong = true, size = 1.0) else Signal.Hold
        }
        assertTrue(Backtest.run(fixture(), strategy).trades.isEmpty())
    }

    // ── equity drawdown is peak-to-trough on the account ───────────────────────────────

    /** Seven bars stepping down in tens, so three losing round trips are unavoidable. */
    private fun staircaseDown(): CandleSeries = CandleSeries(
        listOf(100.0, 100.0, 90.0, 90.0, 80.0, 80.0, 70.0).mapIndexed { index, price ->
            Candle(1_000 + index * HOUR, o = price, h = price, l = price, c = price)
        },
    )

    @Test
    fun `max equity drawdown is peak-to-trough on the curve, not the largest losing trade`() {
        // Three losses of ten each. The worst single trade is ten; the account fell thirty, and
        // thirty is what the reader had to sit through. Reporting the largest loser as "drawdown"
        // is the mistake, and it understates by the length of the losing run.
        val flipEveryBar = Strategy { _, _, position ->
            if (position == null) Signal.Enter(isLong = true, size = 1.0) else Signal.Exit
        }
        val result = Backtest.run(staircaseDown(), flipEveryBar, startingEquity = 1_000.0, feePercent = 0.0)
        val metrics = result.metrics

        assertEquals(3, metrics.totalTrades)
        assertEquals(3, metrics.losingTrades)
        assertEquals(10.0, metrics.largestLoss, DELTA)
        assertEquals(30.0, metrics.maxEquityDrawdown, DELTA)
        assertEquals(3.0, metrics.maxEquityDrawdownPercent, DELTA)
        assertTrue(
            "the account fell further than any one trade did",
            metrics.maxEquityDrawdown > metrics.largestLoss,
        )
        assertEquals(-30.0, metrics.netProfit, DELTA)
        assertEquals(-30.0, metrics.buyAndHoldReturn, DELTA)
        assertEquals(0.0, metrics.profitFactor, DELTA)
        assertEquals(-10.0, metrics.expectancy, DELTA)
        assertEquals(metrics.averagePnl, metrics.expectancy, DELTA)
    }

    @Test
    fun `run-up, drawdown and the longest underwater stretch are all read off the curve`() {
        // Curve: 100, 90, 80, 95, 101, 99, 100. Peak 100 at bar 0, trough 80 at bar 2, back above
        // the peak at bar 4 — four bars underwater — and a second, shallower dip that never
        // recovers by the end.
        val metrics = Backtest.summarise(
            trades = emptyList(),
            equityCurve = doubleArrayOf(100.0, 90.0, 80.0, 95.0, 101.0, 99.0, 100.0),
            series = CandleSeries.EMPTY,
            startingEquity = 100.0,
            barSeconds = HOUR,
        )
        assertEquals(20.0, metrics.maxEquityDrawdown, DELTA)
        assertEquals(20.0, metrics.maxEquityDrawdownPercent, DELTA)
        assertEquals(21.0, metrics.maxEquityRunUp, DELTA)
        assertEquals(26.25, metrics.maxEquityRunUpPercent, DELTA)
        assertEquals(4, metrics.longestDrawdownBars)
    }

    @Test
    fun `a curve that never falls is never underwater, however long it stays flat`() {
        // The off-by-one that hides here: treating every bar that merely *matches* the peak as the
        // end of a drawdown reports an account that never lost a currency unit as permanently one
        // bar underwater.
        val flat = Backtest.summarise(
            trades = emptyList(),
            equityCurve = DoubleArray(50) { 1_000.0 },
            series = CandleSeries.EMPTY,
            startingEquity = 1_000.0,
            barSeconds = HOUR,
        )
        assertEquals(0, flat.longestDrawdownBars)
        assertEquals(0.0, flat.maxEquityDrawdown, DELTA)

        val rising = Backtest.summarise(
            trades = emptyList(),
            equityCurve = doubleArrayOf(1_000.0, 1_001.0, 1_001.0, 1_002.0),
            series = CandleSeries.EMPTY,
            startingEquity = 1_000.0,
            barSeconds = HOUR,
        )
        assertEquals(0, rising.longestDrawdownBars)
    }

    @Test
    fun `a drawdown still running at the last bar is counted to the end`() {
        // The one the reader is actually sitting in. Dropping it reports the most comfortable
        // version of the run, which is the version nobody needs.
        val metrics = Backtest.summarise(
            trades = emptyList(),
            equityCurve = doubleArrayOf(100.0, 100.0, 90.0, 90.0, 80.0, 80.0, 70.0),
            series = CandleSeries.EMPTY,
            startingEquity = 100.0,
            barSeconds = HOUR,
        )
        // Peak at bar 1, still below it at bar 6.
        assertEquals(5, metrics.longestDrawdownBars)
        assertEquals(30.0, metrics.maxEquityDrawdown, DELTA)
    }

    // ── the trade statistics ───────────────────────────────────────────────────────────

    @Test
    fun `the trade statistics on a hand-built book`() {
        val trades = listOf(
            trade(pnlTarget = 30.0, bars = 5),
            trade(pnlTarget = -10.0, bars = 2),
            trade(pnlTarget = 20.0, bars = 5),
            trade(pnlTarget = -20.0, bars = 2),
        )
        val metrics = Backtest.summarise(
            trades = trades,
            equityCurve = doubleArrayOf(1_000.0, 1_030.0, 1_020.0, 1_040.0, 1_020.0),
            series = CandleSeries.EMPTY,
            startingEquity = 1_000.0,
            barSeconds = DAY,
        )
        assertEquals(50.0, metrics.grossProfit, DELTA)
        assertEquals(30.0, metrics.grossLoss, DELTA)
        assertEquals(20.0, metrics.netProfit, DELTA)
        assertEquals(2.0, metrics.netProfitPercent, DELTA)
        assertEquals(50.0 / 30.0, metrics.profitFactor, 1e-12)
        assertEquals(4, metrics.totalTrades)
        assertEquals(2, metrics.winningTrades)
        assertEquals(2, metrics.losingTrades)
        assertEquals(50.0, metrics.percentProfitable, DELTA)
        assertEquals(5.0, metrics.averagePnl, DELTA)
        assertEquals(25.0, metrics.averageWin, DELTA)
        assertEquals(15.0, metrics.averageLoss, DELTA)
        assertEquals(25.0 / 15.0, metrics.winLossRatio, 1e-12)
        assertEquals(30.0, metrics.largestWin, DELTA)
        assertEquals(20.0, metrics.largestLoss, DELTA)
        assertEquals(3.5, metrics.averageBarsInTrade, DELTA)
        assertEquals(5.0, metrics.averageBarsInWinners, DELTA)
        assertEquals(2.0, metrics.averageBarsInLosers, DELTA)
        // Expectancy decomposed is average P&L, which is the point of writing it that way.
        assertEquals(5.0, metrics.expectancy, DELTA)
    }

    @Test
    fun `a book with no losses reports an infinite profit factor rather than a division by zero`() {
        val metrics = Backtest.summarise(
            trades = listOf(trade(pnlTarget = 10.0, bars = 1)),
            equityCurve = doubleArrayOf(1_000.0, 1_010.0),
            series = CandleSeries.EMPTY,
        )
        assertTrue(metrics.profitFactor.isInfinite())
        assertFalse(metrics.profitFactor.isNaN())
        assertEquals(0.0, metrics.winLossRatio, DELTA)
    }

    // ── Sharpe, Sortino and annualisation ──────────────────────────────────────────────

    /** Mean 0.005, population deviation 0.0206155…, two negatives summing to 0.0005 squared. */
    private val sampleReturns = doubleArrayOf(0.02, -0.01, 0.03, -0.02)

    @Test
    fun `Sortino divides the negative squares by the count of all returns, not of the negatives`() {
        // sqrt(0.0005 / 4) = 0.011180…  →  0.005 / 0.011180… = 0.447213…
        // Dividing by the two negatives instead gives sqrt(0.0005 / 2) = 0.015811… and a ratio of
        // 0.316227…, which is a different number entirely.
        val correct = 0.005 / sqrt(0.0005 / 4)
        val wrongDivisor = 0.005 / sqrt(0.0005 / 2)
        assertEquals(0.4472135954999579, correct, 1e-12)
        assertEquals(correct, Backtest.sortino(sampleReturns, periodsPerYear = 1.0), 1e-12)
        assertTrue(
            "the two divisors must not agree, or this test proves nothing",
            kotlin.math.abs(correct - wrongDivisor) > 0.1,
        )
    }

    @Test
    fun `Sortino ignores the upside that Sharpe punishes`() {
        // Same downside, a much larger win. Sharpe falls because the deviation grows; Sortino rises
        // because the numerator grows and the downside did not.
        val calm = doubleArrayOf(0.02, -0.01, 0.03, -0.02)
        val spiky = doubleArrayOf(0.02, -0.01, 0.30, -0.02)
        assertTrue(Backtest.sharpe(spiky, 1.0) < Backtest.sortino(spiky, 1.0))
        assertTrue(Backtest.sortino(spiky, 1.0) > Backtest.sortino(calm, 1.0))
    }

    @Test
    fun `the un-annualised Sharpe is the per-bar figure, and annualising scales it by the square root of the periods`() {
        val perBar = Backtest.sharpe(sampleReturns, periodsPerYear = 1.0)
        assertEquals(0.005 / sqrt(0.0017 / 4), perBar, 1e-12)
        assertEquals(0.24253562503633297, perBar, 1e-12)
        assertEquals(perBar * sqrt(365.0), Backtest.sharpe(sampleReturns, 365.0), 1e-12)
    }

    @Test
    fun `annualisation changes with the bar length`() {
        // The same return series is a different Sharpe on hourly bars and on daily ones, and the
        // ratio between them is exactly sqrt(24). A figure quoted without this scaling is not a
        // Sharpe; on hourly bars it is ninety-four times too small.
        assertEquals(8_760.0, Backtest.periodsPerYear(HOUR), 1e-9)
        assertEquals(365.0, Backtest.periodsPerYear(DAY), 1e-9)

        val hourly = Backtest.sharpe(sampleReturns, Backtest.periodsPerYear(HOUR))
        val daily = Backtest.sharpe(sampleReturns, Backtest.periodsPerYear(DAY))
        assertEquals(daily * sqrt(24.0), hourly, 1e-9)
        assertTrue(hourly > daily)

        val hourlySortino = Backtest.sortino(sampleReturns, Backtest.periodsPerYear(HOUR))
        val dailySortino = Backtest.sortino(sampleReturns, Backtest.periodsPerYear(DAY))
        assertEquals(dailySortino * sqrt(24.0), hourlySortino, 1e-9)
    }

    @Test
    fun `an unknown bar length leaves the ratios un-annualised rather than infinite`() {
        assertEquals(1.0, Backtest.periodsPerYear(0), DELTA)
        assertEquals(1.0, Backtest.periodsPerYear(-60), DELTA)
        assertEquals(
            Backtest.sharpe(sampleReturns, 1.0),
            Backtest.sharpe(sampleReturns, Backtest.periodsPerYear(0)),
            1e-12,
        )
    }

    @Test
    fun `a run reports a different Sharpe for the same trades on a different timeframe`() {
        val hourly = Backtest.run(
            fixture(),
            enterAtZeroExitAtTwo(),
            startingEquity = 1_000.0,
            feePercent = 0.0,
            barSeconds = HOUR,
        )
        val daily = Backtest.run(
            fixture(),
            enterAtZeroExitAtTwo(),
            startingEquity = 1_000.0,
            feePercent = 0.0,
            barSeconds = DAY,
        )
        assertEquals(hourly.trades, daily.trades)
        assertEquals(daily.metrics.sharpeRatio * sqrt(24.0), hourly.metrics.sharpeRatio, 1e-9)
        assertEquals(8_760.0, hourly.metrics.periodsPerYear, 1e-9)
        assertEquals(365.0, daily.metrics.periodsPerYear, 1e-9)
    }

    @Test
    fun `the bar length is inferred from the median gap, so one outage does not redefine the timeframe`() {
        val gapped = CandleSeries(
            listOf(0L, HOUR, 2 * HOUR, 3 * HOUR, 3 * HOUR + 5 * DAY).map { time ->
                Candle(1_000 + time, o = 100.0, h = 100.0, l = 100.0, c = 100.0)
            },
        )
        assertEquals(HOUR, Backtest.inferBarSeconds(gapped))
        assertEquals(0L, Backtest.inferBarSeconds(CandleSeries.EMPTY))
    }

    // ── the empty cases ────────────────────────────────────────────────────────────────

    @Test
    fun `zero trades produces zeros throughout and not one NaN`() {
        val result = Backtest.run(fixture(), Strategy { _, _, _ -> Signal.Hold }, startingEquity = 1_000.0)
        val metrics = result.metrics

        assertTrue(result.trades.isEmpty())
        assertEquals(listOf(1_000.0, 1_000.0, 1_000.0, 1_000.0, 1_000.0), result.equityCurve.toList())
        listOf(
            "netProfit" to metrics.netProfit,
            "netProfitPercent" to metrics.netProfitPercent,
            "grossProfit" to metrics.grossProfit,
            "grossLoss" to metrics.grossLoss,
            "totalFees" to metrics.totalFees,
            "profitFactor" to metrics.profitFactor,
            "percentProfitable" to metrics.percentProfitable,
            "averagePnl" to metrics.averagePnl,
            "averageWin" to metrics.averageWin,
            "averageLoss" to metrics.averageLoss,
            "winLossRatio" to metrics.winLossRatio,
            "largestWin" to metrics.largestWin,
            "largestLoss" to metrics.largestLoss,
            "averageBarsInTrade" to metrics.averageBarsInTrade,
            "averageBarsInWinners" to metrics.averageBarsInWinners,
            "averageBarsInLosers" to metrics.averageBarsInLosers,
            "maxEquityRunUp" to metrics.maxEquityRunUp,
            "maxEquityRunUpPercent" to metrics.maxEquityRunUpPercent,
            "maxEquityDrawdown" to metrics.maxEquityDrawdown,
            "maxEquityDrawdownPercent" to metrics.maxEquityDrawdownPercent,
            "sharpeRatio" to metrics.sharpeRatio,
            "sortinoRatio" to metrics.sortinoRatio,
            "expectancy" to metrics.expectancy,
        ).forEach { (name, value) ->
            assertFalse("$name is NaN", value.isNaN())
            assertFalse("$name is infinite", value.isInfinite())
            assertEquals(name, 0.0, value, DELTA)
        }
        assertEquals(0, metrics.totalTrades)
        assertEquals(0, metrics.longestDrawdownBars)

        // The one metric deliberately still computed: it is a property of the bars, and a strategy
        // that sat out a rise is exactly what the comparison exists to expose.
        assertEquals(12.745098039215685, metrics.buyAndHoldReturn, 1e-9)
    }

    @Test
    fun `a flat equity curve gives a Sharpe of zero rather than a NaN`() {
        val flat = DoubleArray(50) { 1_000.0 }
        assertEquals(0.0, Backtest.sharpe(Backtest.returns(flat), 365.0), DELTA)
        assertEquals(0.0, Backtest.sortino(Backtest.returns(flat), 365.0), DELTA)
        assertFalse(Backtest.sharpe(Backtest.returns(flat), 365.0).isNaN())
    }

    @Test
    fun `an empty series runs without throwing and reports nothing`() {
        val result = Backtest.run(CandleSeries.EMPTY, Strategy { _, _, _ -> Signal.Hold })
        assertTrue(result.trades.isEmpty())
        assertEquals(0, result.equityCurve.size)
        assertEquals(0, result.metrics.totalTrades)
        assertEquals(0.0, result.metrics.sharpeRatio, DELTA)
        assertEquals(0.0, result.metrics.buyAndHoldReturn, DELTA)
        assertEquals(0.0, result.metrics.periodsPerYear, DELTA)
    }

    @Test
    fun `a zero or negative size is refused rather than opening a position worth nothing`() {
        val strategy = Strategy { index, _, _ ->
            if (index == 0) Signal.Enter(isLong = true, size = 0.0) else Signal.Hold
        }
        assertTrue(Backtest.run(fixture(), strategy).trades.isEmpty())
    }

    @Test
    fun `an exit while flat and an entry while already in are both ignored`() {
        val noisy = Strategy { _, _, _ -> Signal.Enter(isLong = true, size = 1.0) }
        // One entry, and every later Enter is dropped because a position is already open, so the
        // run ends with exactly one trade closed at the final bar.
        assertEquals(1, Backtest.run(fixture(), noisy, feePercent = 0.0).trades.size)

        val exitOnly = Strategy { _, _, _ -> Signal.Exit }
        assertTrue(Backtest.run(fixture(), exitOnly).trades.isEmpty())
    }

    @Test
    fun `a returns series is empty for a curve too short to have moved`() {
        assertEquals(0, Backtest.returns(DoubleArray(0)).size)
        assertEquals(0, Backtest.returns(doubleArrayOf(1_000.0)).size)
        assertNull(Backtest.returns(doubleArrayOf(1_000.0)).firstOrNull())
    }
}
