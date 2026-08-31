package com.coinepro.core.backtest

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.Backtest as Engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reader's own backtest.
 *
 * What is asserted here is mostly *agreement*: that the report of a replay session is the same
 * arithmetic the strategy report uses, over the same starting equity, so the two can be read side
 * by side. A rehearsal scored by its own summariser would drift from the strategy tab by a fee and
 * nobody would be able to say which of the two was right.
 */
class ReplayReportTest {

    /** A staircase: forty bars rising a point each, with a two-point range on every bar. */
    private val bars: List<Candle> = List(40) { index ->
        val close = 100.0 + index
        Candle(
            t = 1_700_000_000L + index * 3_600L,
            o = close - 0.5,
            h = close + 1.0,
            l = close - 1.0,
            c = close,
        )
    }

    private fun sessionWithTwoTrades(): ReplaySession {
        var session = ReplayLedger.open(ReplaySession(), bars, cursor = 2, isLong = true, size = 1.0)
        session = ReplayLedger.close(session, bars, cursor = 6, id = 1L)
        session = ReplayLedger.open(session, bars, cursor = 10, isLong = false, size = 1.0)
        session = ReplayLedger.close(session, bars, cursor = 14, id = 2L)
        return session
    }

    @Test
    fun `every metric is the engine's own, over the engine's own starting equity`() {
        val session = sessionWithTwoTrades()
        val report = ReplayReports.build(session, bars, cursor = 20)
        val revealed = CandleSeries(bars.take(21))
        val expected = Engine.summarise(
            trades = session.trades,
            equityCurve = BacktestReports.markedCurve(
                session.trades,
                revealed,
                ReplayLedger.STARTING_EQUITY,
            ),
            series = revealed,
            startingEquity = ReplayLedger.STARTING_EQUITY,
        )

        assertEquals(expected, report.all)
        assertEquals(Engine.DEFAULT_STARTING_EQUITY, report.startingEquity, 1e-9)
        // One long, one short, and the short lost on a rising staircase.
        assertEquals(2, report.all.totalTrades)
        assertEquals(1, report.all.winningTrades)
        assertEquals(1, report.all.losingTrades)
        assertEquals(1, report.longTrades.size)
        assertEquals(1, report.shortTrades.size)
    }

    @Test
    fun `the expectancy is the average trade, decomposed`() {
        // Arithmetically the same number, which is the point of reporting both: expectancy shows
        // which of the two levers — being right more often, or being right by more — the result
        // came from, and a reader who finds them disagreeing has found a bug rather than a nuance.
        val report = ReplayReports.build(sessionWithTwoTrades(), bars, cursor = 20)
        assertEquals(report.all.averagePnl, report.all.expectancy, 1e-9)
    }

    @Test
    fun `the equity curve has one point per revealed bar and ends on what was realised`() {
        val report = ReplayReports.build(sessionWithTwoTrades(), bars, cursor = 20)

        assertEquals(21, report.equityCurve.size)
        assertEquals(ReplayLedger.STARTING_EQUITY, report.equityCurve.first(), 1e-9)
        assertEquals(
            ReplayLedger.STARTING_EQUITY + report.all.netProfit,
            report.equityCurve.last(),
            1e-9,
        )
        assertEquals(21, report.window.bars)
    }

    @Test
    fun `the drawdown is the equity curve's worst fall, not the worst trade`() {
        // The distinction that decides position size. Two ordinary losses in a row take the account
        // further down than either of them did alone, and a report that quoted the largest losing
        // trade instead would understate exactly the stretch a reader has to sit through.
        var session = ReplayLedger.open(ReplaySession(), bars, cursor = 2, isLong = false, size = 1.0)
        session = ReplayLedger.close(session, bars, cursor = 5, id = 1L)
        session = ReplayLedger.open(session, bars, cursor = 6, isLong = false, size = 1.0)
        session = ReplayLedger.close(session, bars, cursor = 9, id = 2L)
        val report = ReplayReports.build(session, bars, cursor = 12)

        assertTrue("both trades lost", report.all.netProfit < 0)
        assertTrue(
            "the fall is deeper than the worse single trade",
            report.all.maxEquityDrawdown > report.all.largestLoss,
        )
        assertEquals(report.excursion.drawdown, report.all.maxEquityDrawdown, 1e-9)
    }

    @Test
    fun `the buy-and-hold baseline stops where the reader's view stops`() {
        // A replay's unrevealed bars are the future. Comparing a session against a return that ran
        // to the end of the snapshot would be comparing the reader's trading against information
        // they could not have had, which is the one comparison a replay exists to prevent.
        val report = ReplayReports.build(sessionWithTwoTrades(), bars, cursor = 20)

        assertEquals(21, report.buyAndHoldCurve.size)
        assertEquals(bars[20].t, report.window.lastTime)
        assertNotEquals(bars.last().t, report.window.lastTime)
    }

    @Test
    fun `an open position is left out of every statistic and reported in its own right`() {
        // Its profit changes while it is being read, and a win rate that moves when nothing happened
        // is not a win rate. So it is stated separately rather than folded in or silently dropped.
        var session = sessionWithTwoTrades()
        session = ReplayLedger.open(session, bars, cursor = 16, isLong = true, size = 1.0)
        session = ReplayLedger.mark(session, bars, cursor = 20)
        val report = ReplayReports.build(session, bars, cursor = 20)

        assertEquals(2, report.all.totalTrades)
        assertEquals(1, report.openPositions)
        assertEquals(
            ReplayLedger.unrealised(session, bars, cursor = 20),
            report.unrealised,
            1e-9,
        )
    }

    @Test
    fun `how each trade ended is counted, which is the half a strategy cannot report`() {
        var session = ReplayLedger.open(
            ReplaySession(), bars, cursor = 2, isLong = true, size = 1.0, stopLoss = 100.0,
        )
        session = ReplayLedger.close(session, bars, cursor = 4, id = 1L)
        session = ReplayLedger.open(
            session, bars, cursor = 5, isLong = false, size = 1.0, stopLoss = 108.0,
        )
        session = ReplayLedger.advance(session, bars, cursor = 12)
        session = ReplayLedger.open(session, bars, cursor = 13, isLong = true, size = 1.0)
        session = ReplayLedger.closeAll(session, bars, cursor = 16)
        val report = ReplayReports.build(session, bars, cursor = 16)

        assertEquals(3, report.roundTrips.size)
        assertEquals(1, report.closedByHand)
        assertEquals(1, report.stoppedOut)
        assertEquals(1, report.closedWithSession)
        assertEquals(0, report.targetsHit)
    }

    @Test
    fun `a rehearsal-sized sample prints no rate at all`() {
        // Six trades is four and two dressed as a measurement. The dash sends the reader to the
        // count beside it, which is the number that settles it — see `BacktestFormat`.
        val report = ReplayReports.build(sessionWithTwoTrades(), bars, cursor = 20)

        assertEquals(
            BacktestFormat.ABSENT,
            BacktestFormat.percentIfSampled(report.all.percentProfitable, report.all.totalTrades, 1),
        )
        assertEquals(
            BacktestFormat.ABSENT,
            BacktestFormat.ratioIfSampled(report.all.profitFactor, report.all.totalTrades),
        )
        // The same figure is printed once the sample can carry it.
        assertEquals("50.0%", BacktestFormat.percentIfSampled(50.0, 30, 1).let(::stripIsolates))
        assertEquals("1.50", stripIsolates(BacktestFormat.ratioIfSampled(1.5, 30)))
    }

    @Test
    fun `a session that never traded is a report of zeros rather than nothing`() {
        // Returning null would leave the screen with nothing to say to a reader who has just opened
        // their first position and not closed it — which is exactly when they look.
        val report = ReplayReports.build(ReplaySession(), bars, cursor = 10)

        assertEquals(0, report.all.totalTrades)
        assertEquals(0.0, report.all.netProfit, 1e-12)
        assertEquals(11, report.equityCurve.size)
        assertTrue(report.equityCurve.all { it == ReplayLedger.STARTING_EQUITY })
    }

    @Test
    fun `the long and short columns are summarised over their own curves`() {
        // Not slices of the whole run's: a drawdown is a property of a curve, and the longs' worst
        // stretch is invisible inside a total the shorts were paying for at the time.
        val report = ReplayReports.build(sessionWithTwoTrades(), bars, cursor = 20)

        assertEquals(1, report.longs.totalTrades)
        assertEquals(1, report.shorts.totalTrades)
        assertEquals(
            report.all.netProfit,
            report.longs.netProfit + report.shorts.netProfit,
            1e-9,
        )
    }

    /** The bidirectional isolates a percentage is wrapped in for a Persian screen. */
    private fun stripIsolates(text: String): String = text.filter { it.code !in 0x2066..0x2069 }
}
