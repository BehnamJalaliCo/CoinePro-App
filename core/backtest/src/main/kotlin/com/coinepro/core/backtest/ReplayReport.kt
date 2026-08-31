package com.coinepro.core.backtest

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.BacktestMetrics as EngineMetrics
// The alias convention this module keeps everywhere: `Engine` is `core:chart`'s full runner, and
// `EngineTrade` its top-level `Trade`. See `StrategyRules` for why the collision exists at all.
import com.coinepro.core.chart.Backtest as Engine
import com.coinepro.core.chart.Trade as EngineTrade
import kotlin.math.min

/**
 * A backtest of the reader, rather than of a rule.
 *
 * ### What this is for
 *
 * Bar replay used to step the bars and stop there. A reader could scrub history, watch a level
 * break and say to themselves that they would have taken it — which is the one claim a chart can
 * never contradict. The point of a replay is to make it contradictable: open the position at the
 * bar you are looking at, name the price that says you are wrong and the price that says you are
 * done, step forward, and find out. What comes out at the end is this — the same document the
 * strategy tab produces, about the reader's own hand.
 *
 * ### Why it is [TradeReport] and not a type of its own
 *
 * Because a second set of statistics is a second definition of a win. Every figure here is
 * `core:chart`'s summariser over `core:chart`'s trades, on the same starting equity and at the same
 * five basis points a side the engine charges, so a reader can put their own session beside a
 * moving-average cross and the comparison means something. That is the entire reason the ledger
 * builds `EngineTrade` rather than a record of its own.
 *
 * ### Closed round trips only
 *
 * An open position's profit is a number that changes while it is being read, and folding it into a
 * win rate would produce a statistic that moves when nothing happened. [openPositions] and
 * [unrealised] are reported separately and in words, so a session read half-way through says what
 * it is missing instead of quietly leaving it out.
 */
data class ReplayReport(
    override val window: BacktestWindow,
    override val startingEquity: Double,
    override val trades: List<EngineTrade>,
    override val equityCurve: DoubleArray,
    override val buyAndHoldCurve: DoubleArray,
    override val excursion: EquityExcursion,
    override val all: EngineMetrics,
    override val longs: EngineMetrics,
    override val shorts: EngineMetrics,
    /** The closed round trips with the reason each ended — the half a strategy cannot report. */
    val roundTrips: List<ReplayRoundTrip>,
    /** How many positions are still running, and therefore absent from every figure above. */
    val openPositions: Int,
    /** What those open positions are worth at the cursor, so the omission can be quantified. */
    val unrealised: Double,
) : TradeReport {

    /**
     * Always true, and not a copy-paste of the strategy report's flag.
     *
     * A reader in a replay may take either side with the same tap, so an empty short column here
     * means "you never sold", which is a finding about the reader. On a strategy run it usually
     * means the run was configured long-only, which is a fact about the settings. Same empty
     * column, two different sentences under it — see `BacktestSheet`'s Performance tab.
     */
    override val allowShorts: Boolean get() = true

    /** How many closed this way. */
    fun countOf(exit: ReplayExit): Int = roundTrips.count { it.exit == exit }

    /**
     * How many of the reader's trades ended where they said they would be wrong.
     *
     * The most useful number on the whole report, and one no strategy backtest can produce. A
     * session with a dozen manual closes and no stop-outs is a reader who moves their stop, and
     * that is a habit worth being shown rather than a statistic worth averaging.
     */
    val stoppedOut: Int get() = countOf(ReplayExit.STOP)

    /** How many ran all the way to the target. */
    val targetsHit: Int get() = countOf(ReplayExit.TARGET)

    /** How many the reader closed by hand, before either level was reached. */
    val closedByHand: Int get() = countOf(ReplayExit.MANUAL)

    /** How many were still open when the session was ended and were closed with it. */
    val closedWithSession: Int get() = countOf(ReplayExit.SESSION_END)

    /**
     * How many trades the reader took without naming a stop.
     *
     * Counted from the round trips rather than from the positions, because the positions are gone
     * by the time this is read. A session where this is most of the list is a session whose
     * drawdown figure was decided by luck, and the report says so in prose rather than leaving the
     * reader to notice.
     */
    val withoutStop: Int
        get() = roundTrips.count { it.exit == ReplayExit.MANUAL || it.exit == ReplayExit.SESSION_END }
}

/**
 * Building the reader's report, in pure arithmetic over the bars they have seen.
 *
 * No Compose, no clock — the same discipline [BacktestReports] keeps, for the same reason: every
 * interesting failure here is arithmetic, and arithmetic is far easier to be certain about as an
 * assertion than as a screen somebody looks at.
 */
object ReplayReports {

    /**
     * Where a rehearsal stops being an anecdote.
     *
     * The same thirty the strategy report uses, and it is repeated here rather than imported from
     * the sheet because this is where it is *enforced* rather than merely warned about: below it,
     * the rates on the reader's own report are not printed at all. See
     * [BacktestFormat.ratioIfSampled].
     */
    const val CONFIDENT_TRADES: Int = BacktestFormat.CONFIDENT_TRADES

    /**
     * Summarise a session against the bars revealed so far.
     *
     * The window is the *revealed* bars, not the whole snapshot, and that is the honest boundary: a
     * replay's unrevealed bars are the future as far as the reader is concerned, and a buy-and-hold
     * baseline that ran to the end of the snapshot would compare the reader's trading against a
     * return they could not have known about. The comparison has to be over the same history the
     * decisions were taken in.
     *
     * A session with no closed trades still produces a report — of zeros, with the trade count that
     * explains them. Returning null would leave the screen with nothing to say to a reader who
     * opened one position and has not closed it, which is exactly when they most want to look.
     */
    fun build(session: ReplaySession, bars: List<Candle>, cursor: Int): ReplayReport {
        val last = if (bars.isEmpty()) -1 else min(cursor, bars.lastIndex)
        val revealed = CandleSeries(if (last < 0) emptyList() else bars.take(last + 1))
        val trades = session.trades
        val barSeconds = Engine.inferBarSeconds(revealed)
        val equity = ReplayLedger.STARTING_EQUITY
        val longs = trades.filter { it.isLong }
        val shorts = trades.filter { !it.isLong }
        val curve = BacktestReports.markedCurve(trades, revealed, equity)

        return ReplayReport(
            window = BacktestWindow(
                bars = revealed.size,
                firstTime = revealed.time.firstOrNull() ?: 0L,
                lastTime = revealed.time.lastOrNull() ?: 0L,
                barSeconds = barSeconds,
                // A replay snapshot is what it is. There is no older page to fetch mid-session and
                // no honest way to widen the window a rehearsal was taken in, so the offer the
                // strategy report makes here is deliberately absent rather than shown and inert.
                moreHistoryAvailable = false,
            ),
            startingEquity = equity,
            trades = trades,
            equityCurve = curve,
            buyAndHoldCurve = BacktestReports.buyAndHold(revealed, equity),
            excursion = BacktestReports.excursion(curve),
            all = Engine.summarise(trades, curve, revealed, equity, barSeconds),
            // Each side over its own curve, never a slice of the whole one: a drawdown is a
            // property of a curve rather than of a list, and the longs' worst stretch is not
            // visible in a total that the shorts were paying for at the time.
            longs = Engine.summarise(
                trades = longs,
                equityCurve = BacktestReports.markedCurve(longs, revealed, equity),
                series = revealed,
                startingEquity = equity,
                barSeconds = barSeconds,
            ),
            shorts = Engine.summarise(
                trades = shorts,
                equityCurve = BacktestReports.markedCurve(shorts, revealed, equity),
                series = revealed,
                startingEquity = equity,
                barSeconds = barSeconds,
            ),
            roundTrips = session.closed,
            openPositions = session.open.size,
            unrealised = ReplayLedger.unrealised(session, bars, cursor),
        )
    }
}
