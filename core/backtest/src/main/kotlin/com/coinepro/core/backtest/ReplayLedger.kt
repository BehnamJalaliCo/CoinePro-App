package com.coinepro.core.backtest

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.BacktestMetrics as EngineMetrics
// See `StrategyRules` for the alias convention: `Engine` is `core:chart`'s full runner, and
// `EngineTrade` is its top-level `Trade` — not `Backtest.Trade`, which is this module's own.
import com.coinepro.core.chart.Backtest as Engine
import com.coinepro.core.chart.Trade as EngineTrade
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A position opened during a replay session, before it is closed.
 *
 * Not a `PaperTradeEntity` and deliberately not stored anywhere: see [ReplayLedger] for why a
 * rehearsal must not land in the record. The excursion envelope — [highestHigh] and [lowestLow] —
 * is re-derived from the revealed bars every time the cursor moves rather than accumulated, because
 * a reader who steps *backwards* in replay has un-revealed the bars that set it, and an envelope
 * that only ever grew would leave a run-up on the ledger that the reader can no longer see on the
 * chart.
 */
data class ReplayPosition(
    /** Unique within one session, so a row can be closed without matching on price and time. */
    val id: Long,
    /** Which way round. A rehearsal may take either side; the fee is the same both ways. */
    val isLong: Boolean,
    /** Units of the instrument, as the reader typed it. */
    val size: Double,
    /** The bar the reader was looking at when they opened it. */
    val entryIndex: Int,
    /** That bar's open time, unix seconds — the same convention every bar in this app uses. */
    val entryTime: Long,
    /** That bar's close — see [ReplayLedger.open] for why the close and not the next open. */
    val entryPrice: Double,
    /** The entry side's fee, in quote currency. Charged at [ReplayLedger.FEE_PERCENT]. */
    val entryFee: Double,
    /** Highest high of every revealed bar held, inclusive of the entry bar. */
    val highestHigh: Double,
    /** Lowest low of every revealed bar held, inclusive of the entry bar. */
    val lowestLow: Double,
) {
    /** `+1` long, `-1` short, the way every price difference below is multiplied. */
    val direction: Int get() = if (isLong) 1 else -1

    /** What the position is worth at [mark], after the entry fee and before the exit one. */
    fun profit(mark: Double): Double =
        if (!mark.isFinite()) 0.0 else (mark - entryPrice) * direction * size - entryFee

    /** The same, as a percentage of what was committed. Zero on a notional that cannot divide. */
    fun profitPercent(mark: Double): Double {
        val committed = abs(entryPrice * size)
        return if (committed > 0 && committed.isFinite()) profit(mark) / committed * 100 else 0.0
    }

    /** How far this went in your favour before now — the number a tight stop is judged against. */
    val runUp: Double
        get() = max(0.0, (if (isLong) highestHigh - entryPrice else entryPrice - lowestLow) * size)
}

/**
 * One replay session's book: what is open, what has closed, and the next id to hand out.
 *
 * Immutable, like the replay state machine it sits beside, so a session is a value the screen holds
 * and every transition is a pure function of it. That is what makes the arithmetic testable without
 * a chart.
 */
data class ReplaySession(
    /** Positions still running, marked against the replay cursor. */
    val open: List<ReplayPosition> = emptyList(),
    /** Round trips already closed, as the engine's own trade so the arithmetic is shared. */
    val closed: List<EngineTrade> = emptyList(),
    /** The id the next [ReplayLedger.open] hands out. */
    val nextId: Long = 1L,
) {
    /** Whether the reader has done nothing yet, so the bar can stay out of their way. */
    val isEmpty: Boolean get() = open.isEmpty() && closed.isEmpty()
}

/**
 * Trading a rehearsal, kept out of the record.
 *
 * A paper trade could already be taken during replay, because the setup card reads whatever price
 * the chart is showing and the chart during replay is showing the replay bar. What was missing is
 * everything after the entry: no replay position, no running result, no ledger for the session. A
 * reader could open a trade in a rehearsal and then had nowhere to see how it went.
 *
 * ### Why this is not the paper-trading book
 *
 * The one rule this file exists to enforce. A replay session is a rehearsal over history the reader
 * can scrub, step backwards through and start again; the paper-trading book is a record of
 * decisions taken without knowing what came next. Mixing them destroys the only thing that makes
 * the second worth keeping — a journal a trader cannot trust is a journal they stop writing — and
 * the mixing is easy to do by accident, because both are "a trade with no money in it".
 *
 * So: nothing here touches `core:papertrade`, nothing here is persisted, and a session ends when
 * the reader leaves replay. The session result is shown *inside* the replay bar, while the session
 * is still running, precisely because there is nowhere for it to go afterwards. That is the honest
 * shape of a rehearsal: it is worth reading and it is not worth keeping.
 *
 * ### Fees are charged, at the backtest's own default
 *
 * The same five basis points a side the engine charges. A rehearsal that is free to trade teaches
 * a habit of flipping that costs real money later, and the whole point of the exercise is to
 * practise a decision that will be taken with a fee attached.
 */
object ReplayLedger {

    /** Five basis points a side — the engine's default, so the rehearsal costs what a run costs. */
    const val FEE_PERCENT: Double = Engine.DEFAULT_FEE_PERCENT

    /**
     * The notional a session is marked against, so its metrics read at a familiar scale.
     *
     * The same starting equity the backtest report uses. A rehearsal has no stake — nobody funded
     * it — but every metric below is a percentage of *something*, and sharing the engine's number
     * means a session summary and a backtest report can be read side by side.
     */
    const val STARTING_EQUITY: Double = Engine.DEFAULT_STARTING_EQUITY

    /** One side's fee on a filled notional, in quote currency. */
    fun fee(price: Double, size: Double): Double = abs(price * size) * FEE_PERCENT / 100.0

    /**
     * Open a position at the close of the bar the replay cursor is on.
     *
     * The close, not the next bar's open, and that is the one place this deliberately differs from
     * the backtest engine: a reader in replay is *at* that close, looking at it, deciding. The
     * engine fills at the next open because a rule cannot know a close until the bar is over; a
     * person watching a replay bar print can. Filling them the same way would charge the reader a
     * bar of slippage for a decision they actually made.
     *
     * Refuses a non-positive size, a cursor outside the bars and a price that is not a number,
     * returning the session unchanged — a refusal a screen can ignore, because there is nothing
     * useful to say about a trade nobody could have placed.
     */
    fun open(
        session: ReplaySession,
        bars: List<Candle>,
        cursor: Int,
        isLong: Boolean,
        size: Double,
    ): ReplaySession {
        val bar = bars.getOrNull(cursor) ?: return session
        if (size <= 0 || !size.isFinite() || !bar.c.isFinite() || bar.c <= 0) return session
        val position = ReplayPosition(
            id = session.nextId,
            isLong = isLong,
            size = size,
            entryIndex = cursor,
            entryTime = bar.t,
            entryPrice = bar.c,
            entryFee = fee(bar.c, size),
            highestHigh = bar.c,
            lowestLow = bar.c,
        )
        return session.copy(open = session.open + position, nextId = session.nextId + 1)
    }

    /**
     * Re-derive every open position's excursion envelope from the bars revealed so far.
     *
     * Called whenever the cursor moves. Recomputed rather than extended, so stepping backwards
     * shrinks the envelope again — see [ReplayPosition]. Highs and lows of the bars held, never
     * their closes: a rehearsal that ran in your favour intrabar did run, and the closes do not
     * know it.
     */
    fun mark(session: ReplaySession, bars: List<Candle>, cursor: Int): ReplaySession {
        if (session.open.isEmpty()) return session
        val last = min(cursor, bars.lastIndex)
        val marked = session.open.map { position ->
            var high = position.entryPrice
            var low = position.entryPrice
            for (index in position.entryIndex..last) {
                val bar = bars.getOrNull(index) ?: break
                high = max(high, bar.h)
                low = min(low, bar.l)
            }
            position.copy(highestHigh = high, lowestLow = low)
        }
        return session.copy(open = marked)
    }

    /**
     * Close one position at the cursor's close, moving it to [ReplaySession.closed].
     *
     * The closed record is `core:chart`'s own [EngineTrade] rather than a type of this file's, so
     * a session can be summarised by exactly the arithmetic the backtest uses — the same fees, the
     * same definition of a win, the same run-up. A second implementation would disagree with the
     * report by a fee and nobody would be able to say which was right.
     */
    fun close(session: ReplaySession, bars: List<Candle>, cursor: Int, id: Long): ReplaySession {
        val position = session.open.firstOrNull { it.id == id } ?: return session
        val bar = bars.getOrNull(cursor) ?: return session
        val exitFee = fee(bar.c, position.size)
        val trade = EngineTrade(
            entryIndex = position.entryIndex,
            entryTime = position.entryTime,
            entryPrice = position.entryPrice,
            exitIndex = max(position.entryIndex, cursor),
            exitTime = bar.t,
            exitPrice = bar.c,
            isLong = position.isLong,
            size = position.size,
            fee = position.entryFee + exitFee,
            // The exit price is a price the position really passed through, so it belongs inside
            // the envelope even when the reader closed on a wick the bar had not printed yet.
            highestHigh = max(position.highestHigh, bar.c),
            lowestLow = min(position.lowestLow, bar.c),
        )
        return session.copy(
            open = session.open.filterNot { it.id == id },
            closed = session.closed + trade,
        )
    }

    /**
     * Close everything at the cursor.
     *
     * What "end the session" means. An open position left behind would put the session's result on
     * an unrealised number, and a rehearsal that finishes on a hope is the rehearsal that teaches
     * the wrong lesson.
     */
    fun closeAll(session: ReplaySession, bars: List<Candle>, cursor: Int): ReplaySession {
        var next = session
        session.open.forEach { position -> next = close(next, bars, cursor, position.id) }
        return next
    }

    /** The open book's mark-to-market at the cursor, in quote currency. */
    fun unrealised(session: ReplaySession, bars: List<Candle>, cursor: Int): Double {
        val mark = bars.getOrNull(cursor)?.c ?: return 0.0
        return session.open.sumOf { it.profit(mark) }
    }

    /** Everything already realised in this session, in quote currency. */
    fun realised(session: ReplaySession): Double = session.closed.sumOf { it.pnl }

    /**
     * The session's closed trades, summarised with the backtest's own arithmetic.
     *
     * Closed only. An open position's profit is a number that changes while it is being read, and
     * folding it into a win rate would mean a statistic that moves when nothing happened — the
     * same rule `core:papertrade` keeps, for the same reason.
     *
     * Every metric here can be absent: a session of two winning trades has an infinite profit
     * factor and no average loss at all. Render it through [BacktestFormat], never directly.
     */
    fun summary(session: ReplaySession, bars: List<Candle>, cursor: Int): EngineMetrics {
        if (session.closed.isEmpty()) return EngineMetrics()
        val revealed = CandleSeries(bars.take(min(cursor, bars.lastIndex) + 1))
        return Engine.summarise(
            trades = session.closed,
            equityCurve = BacktestReports.markedCurve(session.closed, revealed, STARTING_EQUITY),
            series = revealed,
            startingEquity = STARTING_EQUITY,
        )
    }
}
