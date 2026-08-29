package com.coinepro.core.backtest

import com.coinepro.core.chart.Backtest as Engine
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.Trade as EngineTrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind the five tabs.
 *
 * Every value asserted here is hand-computed from the trade beneath it rather than read back from
 * the code that produced it, because the failure this guards against is not a crash: it is a
 * plausible number in a report a reader acts on.
 */
class BacktestReportTest {

    /** Ten bars, closing 100 through 109, one minute apart. */
    private val series = CandleSeries(
        List(10) { index ->
            val close = 100.0 + index
            Candle(t = index * 60L, o = close, h = close + 1, l = close - 1, c = close)
        },
    )

    /**
     * Long two units from bar 1 at 101 to bar 4 at 104, forty basis points of fee on the round
     * trip. Gross is (104 − 101) × 2 = 6.00 and net is 5.60.
     */
    private val trade = EngineTrade(
        entryIndex = 1,
        entryTime = 60L,
        entryPrice = 101.0,
        exitIndex = 4,
        exitTime = 240L,
        exitPrice = 104.0,
        isLong = true,
        size = 2.0,
        fee = 0.4,
        highestHigh = 105.0,
        lowestLow = 100.0,
    )

    @Test
    fun `a subset's equity curve is marked to every bar, not stepped at the exit`() {
        val curve = BacktestReports.markedCurve(listOf(trade), series, startingEquity = 1000.0)

        // Before the entry there is nothing to mark.
        assertEquals(1000.0, curve[0], 1e-9)
        // At the entry bar the position is flat and the entry side of the fee is already paid:
        // half of 0.40 is 0.20.
        assertEquals(999.8, curve[1], 1e-9)
        // Two bars on, the position is two units up two: 4.00 less the 0.20 already paid.
        assertEquals(1003.8, curve[3], 1e-9)
        // At the exit bar the whole round trip is realised: 6.00 gross less 0.40 of fees.
        assertEquals(1005.6, curve[4], 1e-9)
        assertEquals(1005.6, curve[9], 1e-9)
    }

    @Test
    fun `run-up and drawdown are reported with the bars they ran over`() {
        val curve = BacktestReports.markedCurve(listOf(trade), series, startingEquity = 1000.0)
        val excursion = BacktestReports.excursion(curve)

        // The only fall is the entry fee, on the bar it was charged.
        assertEquals(0.2, excursion.drawdown, 1e-9)
        assertEquals(0, excursion.drawdownFrom)
        assertEquals(1, excursion.drawdownTo)

        // The rise runs from that trough to the exit: 1005.60 − 999.80.
        assertEquals(5.8, excursion.runUp, 1e-9)
        assertEquals(1, excursion.runUpFrom)
        assertEquals(4, excursion.runUpTo)
    }

    @Test
    fun `an empty curve produces an excursion that draws nothing`() {
        val excursion = BacktestReports.excursion(DoubleArray(0))
        assertEquals(0.0, excursion.runUp, 1e-12)
        assertEquals(0.0, excursion.drawdown, 1e-12)
        assertEquals(0, excursion.runUpFrom)
        assertEquals(0, excursion.drawdownTo)
    }

    @Test
    fun `buy-and-hold is the same money in the same instrument`() {
        val curve = BacktestReports.buyAndHold(series, startingEquity = 1000.0)
        assertEquals(1000.0, curve[0], 1e-9)
        // A hundred and nine over a hundred, on a thousand.
        assertEquals(1090.0, curve[9], 1e-9)
    }

    @Test
    fun `a profit factor with no losing trade is not a number and renders as a dash`() {
        val metrics = Engine.summarise(
            trades = listOf(trade),
            equityCurve = BacktestReports.markedCurve(listOf(trade), series, 1000.0),
            series = series,
            startingEquity = 1000.0,
        )

        assertTrue(
            "a run with no loser divides by zero and the engine says so",
            metrics.profitFactor.isInfinite(),
        )
        assertEquals(BacktestFormat.ABSENT, BacktestFormat.ratio(metrics.profitFactor))
        // The win/loss ratio has the same hole and must not print a zero as though it were measured.
        assertEquals(0.0, metrics.winLossRatio, 1e-12)
    }

    @Test
    fun `the largest winner's share of the profit is reported, and is zero when nothing was made`() {
        val losing = trade.copy(exitPrice = 99.0)
        val metrics = Engine.summarise(
            trades = listOf(losing),
            equityCurve = BacktestReports.markedCurve(listOf(losing), series, 1000.0),
            series = series,
            startingEquity = 1000.0,
        )
        assertEquals(0.0, BacktestReports.bestTradeShare(metrics), 1e-12)

        val winning = Engine.summarise(
            trades = listOf(trade),
            equityCurve = BacktestReports.markedCurve(listOf(trade), series, 1000.0),
            series = series,
            startingEquity = 1000.0,
        )
        // One trade made all of it, which is the finding the number exists to surface.
        assertEquals(1.0, BacktestReports.bestTradeShare(winning), 1e-9)
    }

    @Test
    fun `dispersion separates a steady rule from one carried by a single trade`() {
        val steady = listOf(trade, trade.copy(entryIndex = 5, exitIndex = 8))
        assertEquals(0.0, BacktestReports.pnlDispersion(steady), 1e-9)

        // One trade at 5.60 and one at −2.40 average 1.60; each is 4.00 away from it.
        val lumpy = listOf(trade, trade.copy(entryIndex = 5, exitIndex = 8, exitPrice = 100.0))
        assertEquals(4.0, BacktestReports.pnlDispersion(lumpy), 1e-9)
        assertEquals(0.0, BacktestReports.pnlDispersion(emptyList()), 1e-12)
    }

    @Test
    fun `a report states the window it ran over`() {
        val long = CandleSeries(
            List(400) { index ->
                val close = 100.0 + index
                Candle(t = OPEN_TIME + index * 3600L, o = close, h = close + 1, l = close - 1, c = close)
            },
        )
        val report = BacktestReports.build(long, moreHistoryAvailable = true)!!

        assertEquals(400, report.window.bars)
        assertEquals(OPEN_TIME, report.window.firstTime)
        assertEquals(OPEN_TIME + 399 * 3600L, report.window.lastTime)
        assertEquals(3600L, report.window.barSeconds)
        assertTrue("the report must say the run did not cover everything", report.window.moreHistoryAvailable)
    }

    @Test
    fun `a long-only run reports no short trades and a shorts tab with nothing in it`() {
        val long = CandleSeries(
            List(400) { index ->
                val close = 100.0 + index
                Candle(t = OPEN_TIME + index * 3600L, o = close, h = close + 1, l = close - 1, c = close)
            },
        )
        val report = BacktestReports.build(long, allowShorts = false)!!

        assertTrue(report.shortTrades.isEmpty())
        assertEquals(0, report.shorts.totalTrades)
        assertEquals(report.all.netProfit, report.longs.netProfit, 1e-9)
    }

    private companion object {
        /** A real instant rather than the epoch, so a zero timestamp still means "absent". */
        const val OPEN_TIME = 1_700_000_000L
    }
}
