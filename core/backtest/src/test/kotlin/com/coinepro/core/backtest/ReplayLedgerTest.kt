package com.coinepro.core.backtest

import com.coinepro.core.chart.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rehearsal ledger.
 *
 * The arithmetic is the same shape as the backtest's and is asserted separately, because the two
 * agreeing is the whole reason the ledger builds `core:chart`'s own trade rather than a type of its
 * own: a rehearsal that scores itself differently from the report is a rehearsal that teaches the
 * wrong number.
 *
 * The stop and target cases below are the ones a reader will trust with a real decision, so they
 * are asserted at the level of the fill price rather than of the outcome: "the trade lost money" is
 * true of a stop filled at the stop and of a stop filled at the close, and only one of those is
 * what would have happened.
 */
class ReplayLedgerTest {

    /**
     * Twelve bars flat at 100 except bar 3, which spikes to 120 and closes back at 100, and bar 5,
     * which closes at 110. The spike is there so run-up can be told apart from profit.
     */
    private val bars: List<Candle> = List(12) { index ->
        val close = if (index == 5) 110.0 else 100.0
        val high = if (index == 3) 120.0 else close + 1
        Candle(t = 1_700_000_000L + index * 60L, o = 100.0, h = high, l = 99.0, c = close)
    }

    /** A bar with every price stated, for the cases where the wick is the whole assertion. */
    private fun bar(index: Int, o: Double, h: Double, l: Double, c: Double) =
        Candle(t = 1_700_000_000L + index * 60L, o = o, h = h, l = l, c = c)

    @Test
    fun `a position opens at the close of the bar the reader is looking at`() {
        val session = ReplayLedger.open(ReplaySession(), bars, cursor = 0, isLong = true, size = 2.0)
        val position = session.open.single()

        assertEquals(100.0, position.entryPrice, 1e-9)
        assertEquals(0, position.entryIndex)
        // Five basis points a side on a notional of two hundred is a tenth of a unit.
        assertEquals(0.1, position.entryFee, 1e-9)
        // The bar it opened on is already checked: it was opened at that bar's close, so the high
        // and the low it printed are history the reader had seen when they decided.
        assertEquals(0, position.checkedThrough)
    }

    @Test
    fun `closing charges both sides and the result is the fee-adjusted difference`() {
        val opened = ReplayLedger.open(ReplaySession(), bars, cursor = 0, isLong = true, size = 2.0)
        val closed = ReplayLedger.close(opened, bars, cursor = 5, id = opened.open.single().id)
        val round = closed.closed.single()

        // Gross is (110 − 100) × 2 = 20.00. The fees are 0.10 in and 0.11 out.
        assertEquals(20.0, round.trade.grossPnl, 1e-9)
        assertEquals(0.21, round.trade.fee, 1e-9)
        assertEquals(19.79, round.trade.pnl, 1e-9)
        assertEquals(ReplayExit.MANUAL, round.exit)
        assertTrue("nothing is left open once it has been closed", closed.open.isEmpty())
    }

    @Test
    fun `run-up is measured from the highs of the bars held, not from their closes`() {
        var session = ReplayLedger.open(ReplaySession(), bars, cursor = 0, isLong = true, size = 2.0)
        session = ReplayLedger.mark(session, bars, cursor = 5)
        val closed = ReplayLedger.close(session, bars, cursor = 5, id = session.open.single().id)

        // The spike at bar 3 reached 120 and closed back at 100. The trade made 19.79; it was up
        // forty at one point, and that is the number a stop is judged against.
        assertEquals(40.0, closed.closed.single().trade.runUp, 1e-9)
    }

    @Test
    fun `a close is correct whether or not the position was marked first`() {
        // The envelope is recomputed from the bars at the moment of closing, so a caller that never
        // called `mark` gets the same trade as one that did. A result that depends on an earlier
        // call somebody might forget is a result that is wrong on the day they do.
        val opened = ReplayLedger.open(ReplaySession(), bars, cursor = 0, isLong = true, size = 2.0)
        val unmarked = ReplayLedger.close(opened, bars, cursor = 5, id = opened.open.single().id)
        val marked = ReplayLedger.close(
            ReplayLedger.mark(opened, bars, cursor = 5),
            bars,
            cursor = 5,
            id = opened.open.single().id,
        )

        assertEquals(marked.closed.single().trade, unmarked.closed.single().trade)
    }

    @Test
    fun `stepping backwards in a replay un-reveals the bars that set the envelope`() {
        var session = ReplayLedger.open(ReplaySession(), bars, cursor = 0, isLong = true, size = 2.0)
        session = ReplayLedger.mark(session, bars, cursor = 5)
        assertEquals(120.0, session.open.single().highestHigh, 1e-9)

        // Back to bar 2, before the spike. An envelope that only ever grew would leave a run-up on
        // the ledger the reader can no longer see on the chart.
        session = ReplayLedger.mark(session, bars, cursor = 2)
        assertEquals(101.0, session.open.single().highestHigh, 1e-9)
    }

    @Test
    fun `the open book is marked against the replay bar and the closed book is not`() {
        var session = ReplayLedger.open(ReplaySession(), bars, cursor = 0, isLong = true, size = 2.0)
        // Up ten a unit on two units, less the entry fee already paid.
        assertEquals(19.9, ReplayLedger.unrealised(session, bars, cursor = 5), 1e-9)
        assertEquals(0.0, ReplayLedger.realised(session), 1e-12)

        session = ReplayLedger.closeAll(session, bars, cursor = 5)
        assertEquals(0.0, ReplayLedger.unrealised(session, bars, cursor = 5), 1e-12)
        assertEquals(19.79, ReplayLedger.realised(session), 1e-9)
        assertEquals(ReplayExit.SESSION_END, session.closed.single().exit)
    }

    @Test
    fun `a short earns when the price falls and pays when it rises`() {
        var session = ReplayLedger.open(ReplaySession(), bars, cursor = 5, isLong = false, size = 1.0)
        session = ReplayLedger.close(session, bars, cursor = 7, id = session.open.single().id)
        val trade = session.closed.single().trade

        // Sold at 110, bought back at 100: ten gross, less 0.055 in and 0.05 out.
        assertEquals(10.0, trade.grossPnl, 1e-9)
        assertEquals(9.895, trade.pnl, 1e-9)
    }

    @Test
    fun `a session of nothing but winners has no profit factor and renders a dash`() {
        var session = ReplayLedger.open(ReplaySession(), bars, cursor = 0, isLong = true, size = 2.0)
        session = ReplayLedger.close(session, bars, cursor = 5, id = session.open.single().id)
        val summary = ReplayLedger.summary(session, bars, cursor = 5)

        assertEquals(19.79, summary.netProfit, 1e-9)
        assertEquals(100.0, summary.percentProfitable, 1e-9)
        assertTrue(summary.profitFactor.isInfinite())
        assertEquals(BacktestFormat.ABSENT, BacktestFormat.ratio(summary.profitFactor))
    }

    @Test
    fun `a trade nobody could have placed is refused rather than recorded`() {
        val zeroSize = ReplayLedger.open(ReplaySession(), bars, cursor = 0, isLong = true, size = 0.0)
        assertTrue("a position of nothing is not a position", zeroSize.isEmpty)

        val offTheEnd = ReplayLedger.open(ReplaySession(), bars, cursor = 99, isLong = true, size = 1.0)
        assertTrue("there is no bar to fill against", offTheEnd.isEmpty)
    }

    // ── Stops and targets ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a long is stopped by the bar's low and fills at the stop, not at the close`() {
        // Bar 1 dips to 94 and closes back at 101. A stop scored on the close would report this
        // position still open and comfortably ahead; the reader would have been out at 95.
        val walk = listOf(
            bar(0, 100.0, 101.0, 99.0, 100.0),
            bar(1, 100.0, 102.0, 94.0, 101.0),
            bar(2, 101.0, 103.0, 100.0, 102.0),
        )
        val opened = ReplayLedger.open(
            ReplaySession(), walk, cursor = 0, isLong = true, size = 1.0, stopLoss = 95.0,
        )
        val after = ReplayLedger.advance(opened, walk, cursor = 2)
        val round = after.closed.single()

        assertTrue("the position is gone from the open book", after.open.isEmpty())
        assertEquals(ReplayExit.STOP, round.exit)
        assertEquals(95.0, round.trade.exitPrice, 1e-9)
        assertEquals(1, round.trade.exitIndex)
        // Five gross against, plus 0.05 in and 0.0475 out.
        assertEquals(-5.0975, round.trade.pnl, 1e-9)
    }

    @Test
    fun `a bar that touches both the stop and the target is a stop`() {
        // The pinned pessimism. One candle carries a high and a low and no order between them, so
        // which was reached first is unknowable — and a rehearsal that resolves the unknown in the
        // reader's favour is worse than no rehearsal at all. Change this and the report becomes a
        // flattering fiction on exactly the violent bars where it matters most.
        val walk = listOf(
            bar(0, 100.0, 101.0, 99.0, 100.0),
            bar(1, 100.0, 130.0, 90.0, 120.0),
        )
        val opened = ReplayLedger.open(
            ReplaySession(),
            walk,
            cursor = 0,
            isLong = true,
            size = 1.0,
            stopLoss = 95.0,
            takeProfit = 110.0,
        )
        val round = ReplayLedger.advance(opened, walk, cursor = 1).closed.single()

        assertEquals(ReplayExit.STOP, round.exit)
        assertEquals(95.0, round.trade.exitPrice, 1e-9)
    }

    @Test
    fun `a short's stop is its high and a short's target is its low`() {
        val walk = listOf(
            bar(0, 100.0, 101.0, 99.0, 100.0),
            bar(1, 100.0, 104.0, 99.0, 100.0),
        )
        val stopped = ReplayLedger.advance(
            ReplayLedger.open(
                ReplaySession(), walk, cursor = 0, isLong = false, size = 1.0, stopLoss = 103.0,
            ),
            walk,
            cursor = 1,
        )
        assertEquals(ReplayExit.STOP, stopped.closed.single().exit)
        assertEquals(103.0, stopped.closed.single().trade.exitPrice, 1e-9)

        val hit = ReplayLedger.advance(
            ReplayLedger.open(
                ReplaySession(), walk, cursor = 0, isLong = false, size = 1.0, takeProfit = 99.5,
            ),
            walk,
            cursor = 1,
        )
        assertEquals(ReplayExit.TARGET, hit.closed.single().exit)
        assertEquals(99.5, hit.closed.single().trade.exitPrice, 1e-9)
    }

    @Test
    fun `a gap through a stop fills at the gap and a gap through a target fills at the target`() {
        // A stop is a market order once it is touched, and a market that opened at 90 never traded
        // at 95 again. A target is a limit, and a limit is not improved by the market gapping past
        // it — assuming the better fill would be assuming a queue nobody was in.
        val down = listOf(bar(0, 100.0, 101.0, 99.0, 100.0), bar(1, 90.0, 92.0, 88.0, 91.0))
        val stopped = ReplayLedger.advance(
            ReplayLedger.open(
                ReplaySession(), down, cursor = 0, isLong = true, size = 1.0, stopLoss = 95.0,
            ),
            down,
            cursor = 1,
        )
        assertEquals(90.0, stopped.closed.single().trade.exitPrice, 1e-9)

        val up = listOf(bar(0, 100.0, 101.0, 99.0, 100.0), bar(1, 115.0, 118.0, 114.0, 117.0))
        val hit = ReplayLedger.advance(
            ReplayLedger.open(
                ReplaySession(), up, cursor = 0, isLong = true, size = 1.0, takeProfit = 105.0,
            ),
            up,
            cursor = 1,
        )
        assertEquals(105.0, hit.closed.single().trade.exitPrice, 1e-9)
    }

    @Test
    fun `the bar a position was opened on cannot stop it`() {
        // Bar 0 traded down to 90 before closing at 100, and the position was opened at that close.
        // A stop at 95 was not in the market while the low printed; triggering on it would close a
        // trade before it existed.
        val walk = listOf(
            bar(0, 100.0, 101.0, 90.0, 100.0),
            bar(1, 100.0, 101.0, 99.0, 100.0),
        )
        val opened = ReplayLedger.open(
            ReplaySession(), walk, cursor = 0, isLong = true, size = 1.0, stopLoss = 95.0,
        )
        val after = ReplayLedger.advance(opened, walk, cursor = 1)

        assertTrue("still open, because bar 0 is not checked", after.closed.isEmpty())
        assertEquals(1, after.open.single().checkedThrough)
    }

    @Test
    fun `the earliest level reached is the one that fills`() {
        val walk = listOf(
            bar(0, 100.0, 101.0, 99.0, 100.0),
            bar(1, 100.0, 112.0, 99.0, 100.0),
            bar(2, 100.0, 101.0, 90.0, 95.0),
        )
        val round = ReplayLedger.advance(
            ReplayLedger.open(
                ReplaySession(),
                walk,
                cursor = 0,
                isLong = true,
                size = 1.0,
                stopLoss = 95.0,
                takeProfit = 110.0,
            ),
            walk,
            cursor = 2,
        ).closed.single()

        // The target at bar 1 came first. A loop that kept walking would report the stop at bar 2
        // on a position that was already closed and paid.
        assertEquals(ReplayExit.TARGET, round.exit)
        assertEquals(1, round.trade.exitIndex)
    }

    @Test
    fun `a stop already hit is not un-hit by stepping backwards`() {
        // Otherwise scrubbing left is a way to harvest every stop-out for free, and the report of
        // the session becomes whatever the reader last dragged the slider to.
        val walk = listOf(
            bar(0, 100.0, 101.0, 99.0, 100.0),
            bar(1, 100.0, 101.0, 99.0, 100.0),
            bar(2, 100.0, 101.0, 90.0, 95.0),
        )
        val stopped = ReplayLedger.advance(
            ReplayLedger.open(
                ReplaySession(), walk, cursor = 0, isLong = true, size = 1.0, stopLoss = 95.0,
            ),
            walk,
            cursor = 2,
        )
        assertEquals(1, stopped.closed.size)

        val steppedBack = ReplayLedger.advance(stopped, walk, cursor = 0)
        assertEquals(1, steppedBack.closed.size)
        assertSame("nothing to re-walk, so the session is handed straight back", stopped, steppedBack)
    }

    @Test
    fun `a position opened after stepping back is checked from its own entry bar`() {
        // The case a single session-wide watermark gets wrong, silently and in the direction that
        // never triggers: the first position has already been walked to bar 4, and the second one
        // must still be checked against bars 2 and 3.
        val walk = listOf(
            bar(0, 100.0, 101.0, 99.0, 100.0),
            bar(1, 100.0, 101.0, 99.0, 100.0),
            bar(2, 100.0, 101.0, 96.0, 100.0),
            bar(3, 100.0, 101.0, 99.0, 100.0),
            bar(4, 100.0, 101.0, 99.0, 100.0),
        )
        var session = ReplayLedger.open(
            ReplaySession(), walk, cursor = 0, isLong = true, size = 1.0, stopLoss = 90.0,
        )
        session = ReplayLedger.advance(session, walk, cursor = 4)
        assertEquals("the first position survives to the end", 4, session.open.single().checkedThrough)

        // Back to bar 1, and a second position with a tighter stop that bar 2 takes out.
        session = ReplayLedger.open(
            session, walk, cursor = 1, isLong = true, size = 1.0, stopLoss = 97.0,
        )
        session = ReplayLedger.advance(session, walk, cursor = 4)

        assertEquals(1, session.closed.size)
        assertEquals(ReplayExit.STOP, session.closed.single().exit)
        assertEquals(2, session.closed.single().trade.exitIndex)
        assertEquals("the untouched first position is still open", 1, session.open.size)
    }

    @Test
    fun `a stopped-out trade does not claim the run the bar made after the fill`() {
        // Bar 1 takes the stop at 95 and then runs to 130. The reader was out at 95 and did not
        // see a penny of it, so the trade's run-up must not include the high of the bar that
        // stopped it — otherwise every stop-out on a reversal bar is reported as a missed winner.
        val walk = listOf(
            bar(0, 100.0, 101.0, 99.0, 100.0),
            bar(1, 100.0, 130.0, 94.0, 129.0),
        )
        val round = ReplayLedger.advance(
            ReplayLedger.open(
                ReplaySession(), walk, cursor = 0, isLong = true, size = 1.0, stopLoss = 95.0,
            ),
            walk,
            cursor = 1,
        ).closed.single()

        assertEquals(101.0, round.trade.highestHigh, 1e-9)
        assertEquals(1.0, round.trade.runUp, 1e-9)
    }

    @Test
    fun `a manual close lives through the whole of the bar it closes on`() {
        // The mirror of the case above. A manual close is taken at a bar's close, so that bar
        // happened in full and its high is a run the reader watched and chose not to take.
        val walk = listOf(
            bar(0, 100.0, 101.0, 99.0, 100.0),
            bar(1, 100.0, 130.0, 99.0, 100.0),
        )
        val opened = ReplayLedger.open(ReplaySession(), walk, cursor = 0, isLong = true, size = 1.0)
        val round = ReplayLedger.close(opened, walk, cursor = 1, id = opened.open.single().id)
            .closed.single()

        assertEquals(130.0, round.trade.highestHigh, 1e-9)
        assertEquals(30.0, round.trade.runUp, 1e-9)
    }

    @Test
    fun `a position with no levels is never closed by stepping`() {
        val walk = listOf(
            bar(0, 100.0, 101.0, 99.0, 100.0),
            bar(1, 100.0, 200.0, 10.0, 100.0),
        )
        val after = ReplayLedger.advance(
            ReplayLedger.open(ReplaySession(), walk, cursor = 0, isLong = true, size = 1.0),
            walk,
            cursor = 1,
        )
        assertTrue("no stop and no target is a position that only a hand can close", after.closed.isEmpty())
    }

    @Test
    fun `a level on the wrong side of the market refuses the whole order`() {
        // Refused rather than dropped. A reader who typed a stop and got a position without one is
        // trading unprotected while believing they are not, and there is no worse outcome here.
        val stopAbove = ReplayLedger.open(
            ReplaySession(), bars, cursor = 0, isLong = true, size = 1.0, stopLoss = 101.0,
        )
        assertTrue(stopAbove.isEmpty)

        val targetBelow = ReplayLedger.open(
            ReplaySession(), bars, cursor = 0, isLong = true, size = 1.0, takeProfit = 99.0,
        )
        assertTrue(targetBelow.isEmpty)

        val shortStopBelow = ReplayLedger.open(
            ReplaySession(), bars, cursor = 0, isLong = false, size = 1.0, stopLoss = 99.0,
        )
        assertTrue(shortStopBelow.isEmpty)

        val negative = ReplayLedger.open(
            ReplaySession(), bars, cursor = 0, isLong = true, size = 1.0, stopLoss = -5.0,
        )
        assertTrue(negative.isEmpty)
    }

    @Test
    fun `a stop can be moved to break-even but never through the market`() {
        val walk = listOf(
            bar(0, 100.0, 101.0, 99.0, 100.0),
            bar(1, 100.0, 111.0, 99.0, 110.0),
        )
        val opened = ReplayLedger.open(
            ReplaySession(), walk, cursor = 0, isLong = true, size = 1.0, stopLoss = 95.0,
        )
        val id = opened.open.single().id

        // At bar 1 the market is 110, so the entry price is now below it and a break-even stop is
        // a legitimate order. A rule written against the entry rather than the market would forbid
        // the single most useful thing a trader learns to do.
        val breakEven = ReplayLedger.protect(opened, walk, cursor = 1, id = id, stopLoss = 100.0, takeProfit = null)
        assertEquals(100.0, breakEven.open.single().stopLoss!!, 1e-9)

        val throughTheMarket = ReplayLedger.protect(
            breakEven, walk, cursor = 1, id = id, stopLoss = 115.0, takeProfit = null,
        )
        assertEquals("refused, not clamped", 100.0, throughTheMarket.open.single().stopLoss!!, 1e-9)

        val cleared = ReplayLedger.protect(breakEven, walk, cursor = 1, id = id, stopLoss = null, takeProfit = null)
        assertNull("null clears, which is what a screen with two empty fields means", cleared.open.single().stopLoss)
    }

    @Test
    fun `the planned risk and reward are stated after the fees both legs will cost`() {
        val opened = ReplayLedger.open(
            ReplaySession(),
            bars,
            cursor = 0,
            isLong = true,
            size = 1.0,
            stopLoss = 90.0,
            takeProfit = 120.0,
        )
        val position = opened.open.single()

        // Ten against, plus 0.05 in and 0.045 out.
        assertEquals(10.095, position.plannedRisk!!, 1e-9)
        // Twenty for, less 0.05 in and 0.06 out.
        assertEquals(19.89, position.plannedReward!!, 1e-9)
        assertEquals(19.89 / 10.095, position.plannedRiskReward!!, 1e-9)
    }

    @Test
    fun `a position without a stop has no risk figure at all`() {
        // Null rather than a number, because the risk is unbounded and any figure printed for it
        // would be the most dangerous one on the screen.
        val position = ReplayLedger
            .open(ReplaySession(), bars, cursor = 0, isLong = true, size = 1.0, takeProfit = 120.0)
            .open.single()

        assertNull(position.plannedRisk)
        assertNull(position.plannedRiskReward)
    }

    @Test
    fun `the offered size commits the same stake a strategy run commits`() {
        // So that a reader's net profit and a rule's net profit are the same kind of number. One
        // unit of an instrument quoted at forty thousand and one unit of an instrument quoted at
        // two are not comparable results, and the report puts them in the same column.
        assertEquals(100.0, ReplayLedger.stakeSize(100.0), 1e-9)
        assertEquals(0.0, ReplayLedger.stakeSize(0.0), 1e-12)
        assertEquals(
            ReplayLedger.STARTING_EQUITY,
            ReplayLedger.stakeSize(250.0) * 250.0,
            1e-6,
        )
    }
}
