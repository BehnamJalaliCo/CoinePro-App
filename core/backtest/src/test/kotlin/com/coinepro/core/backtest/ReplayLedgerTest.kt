package com.coinepro.core.backtest

import com.coinepro.core.chart.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rehearsal ledger.
 *
 * The arithmetic is the same shape as the backtest's and is asserted separately, because the two
 * agreeing is the whole reason the ledger builds `core:chart`'s own trade rather than a type of its
 * own: a rehearsal that scores itself differently from the report is a rehearsal that teaches the
 * wrong number.
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

    @Test
    fun `a position opens at the close of the bar the reader is looking at`() {
        val session = ReplayLedger.open(ReplaySession(), bars, cursor = 0, isLong = true, size = 2.0)
        val position = session.open.single()

        assertEquals(100.0, position.entryPrice, 1e-9)
        assertEquals(0, position.entryIndex)
        // Five basis points a side on a notional of two hundred is a tenth of a unit.
        assertEquals(0.1, position.entryFee, 1e-9)
    }

    @Test
    fun `closing charges both sides and the result is the fee-adjusted difference`() {
        val opened = ReplayLedger.open(ReplaySession(), bars, cursor = 0, isLong = true, size = 2.0)
        val closed = ReplayLedger.close(opened, bars, cursor = 5, id = opened.open.single().id)
        val trade = closed.closed.single()

        // Gross is (110 − 100) × 2 = 20.00. The fees are 0.10 in and 0.11 out.
        assertEquals(20.0, trade.grossPnl, 1e-9)
        assertEquals(0.21, trade.fee, 1e-9)
        assertEquals(19.79, trade.pnl, 1e-9)
        assertTrue("nothing is left open once it has been closed", closed.open.isEmpty())
    }

    @Test
    fun `run-up is measured from the highs of the bars held, not from their closes`() {
        var session = ReplayLedger.open(ReplaySession(), bars, cursor = 0, isLong = true, size = 2.0)
        session = ReplayLedger.mark(session, bars, cursor = 5)
        val closed = ReplayLedger.close(session, bars, cursor = 5, id = session.open.single().id)

        // The spike at bar 3 reached 120 and closed back at 100. The trade made 19.79; it was up
        // forty at one point, and that is the number a stop is judged against.
        assertEquals(40.0, closed.closed.single().runUp, 1e-9)
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
    }

    @Test
    fun `a short earns when the price falls and pays when it rises`() {
        var session = ReplayLedger.open(ReplaySession(), bars, cursor = 5, isLong = false, size = 1.0)
        session = ReplayLedger.close(session, bars, cursor = 7, id = session.open.single().id)
        val trade = session.closed.single()

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
}
