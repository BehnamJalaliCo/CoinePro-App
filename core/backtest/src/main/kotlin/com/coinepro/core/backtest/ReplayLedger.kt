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
 * How a rehearsal position ended.
 *
 * Kept beside the trade rather than inside it, because [EngineTrade] belongs to `core:chart` and
 * has no room for a reason — every trade the engine makes ends the same way, at a rule's exit. A
 * reader's own trades do not: the whole question a replay session answers is *how* they got out,
 * and «حد ضرر» against «بستن دستی» is the difference between a plan that was tested and a plan
 * that was abandoned. Counting the two is the only thing on the report that a strategy backtest
 * cannot produce, because a strategy has no hand to lose its nerve with.
 */
enum class ReplayExit {
    /** The reader pressed close. */
    MANUAL,

    /** The bar's low reached a long's stop, or its high reached a short's. */
    STOP,

    /** The bar's high reached a long's target, or its low reached a short's. */
    TARGET,

    /** Closed with everything else at the end of the session. */
    SESSION_END,
}

/**
 * One closed rehearsal round trip: the engine's own trade, and how it ended.
 *
 * The trade is `core:chart`'s [EngineTrade] and not a type of this file's, so a session is
 * summarised by exactly the arithmetic the backtest uses — the same fees, the same definition of a
 * win, the same run-up. A second implementation would disagree with the report by a fee and nobody
 * would be able to say which of the two was right.
 */
data class ReplayRoundTrip(val trade: EngineTrade, val exit: ReplayExit)

/**
 * A position opened during a replay session, before it is closed.
 *
 * Not a `PaperTradeEntity` and deliberately not stored anywhere: see [ReplayLedger] for why a
 * rehearsal must not land in the record. The excursion envelope — [highestHigh] and [lowestLow] —
 * is re-derived from the revealed bars every time the cursor moves rather than accumulated, because
 * a reader who steps *backwards* in replay has un-revealed the bars that set it, and an envelope
 * that only ever grew would leave a run-up on the ledger that the reader can no longer see on the
 * chart.
 *
 * ### The stop and the target are the point of the whole exercise
 *
 * A position with no stop is not a rehearsal of anything. Entering is the easy half and every
 * reader is good at it; what a replay is *for* is practising the other half — where you are wrong,
 * where you are done — and finding out, one bar at a time, which of the two price reached first.
 * So both levels are on the position rather than in the reader's head, they are checked against
 * every revealed bar by [ReplayLedger.advance], and the result of hitting one is a closed trade the
 * report counts. Either may be null: a reader may take a position without a plan, and the ledger's
 * job is to show them what that cost rather than to refuse it.
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
    /**
     * Where the reader is wrong, in price, or null when they took the position without saying.
     *
     * Below the entry on a long and above it on a short — enforced at the moment it is set, by
     * [ReplayLedger.stopIsPlaceable], because a stop on the wrong side of the market is an order
     * that fills the instant it exists and is a typo every time.
     */
    val stopLoss: Double? = null,

    /** Where the reader is done, in price, or null. Above the entry on a long, below on a short. */
    val takeProfit: Double? = null,

    /**
     * The last bar this position has already been checked against for a stop or a target.
     *
     * Per position rather than per session, and that is not a detail: a reader who steps back to
     * bar 40 and opens a second position there must have *that* position checked against bars 41
     * onwards, while the first one — already walked to bar 60 — must not be checked against bars
     * 41 to 60 a second time. One session-wide watermark gets exactly this case wrong, in the
     * direction that silently never triggers the new position's stop.
     *
     * It also encodes the rule that a stop is not un-hit by scrubbing backwards. Stepping back
     * lowers the cursor but never lowers this, so the bars already walked stay walked: a fill the
     * reader watched happen cannot be undone by dragging the slider left, which would otherwise be
     * a way to harvest every stop-out for free and would make the report a fiction.
     *
     * Starts at [entryIndex], because the bar a position is opened on is opened at its *close* —
     * the rest of that bar is already history and cannot trigger anything.
     */
    val checkedThrough: Int = entryIndex,
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

    /**
     * What reaching the stop would cost, in quote currency, fees included. Null without a stop.
     *
     * Null rather than a zero or a large number, deliberately: a position with no stop has
     * *unbounded* risk, and any figure printed for it would be the most dangerous number on the
     * screen. It is the same rule `SignalOverlay.riskReward` keeps, for the same reason.
     */
    val plannedRisk: Double?
        get() = stopLoss?.let { stop ->
            max(0.0, (entryPrice - stop) * direction * size) + entryFee + ReplayLedger.fee(stop, size)
        }

    /** What reaching the target would pay, fees included. Null without a target. */
    val plannedReward: Double?
        get() = takeProfit?.let { target ->
            (target - entryPrice) * direction * size - entryFee - ReplayLedger.fee(target, size)
        }

    /**
     * Reward over risk as the reader planned it, or null when either half is missing.
     *
     * After fees on both legs, which is what makes it smaller than the ratio drawn on the chart and
     * is the honest version: a one-to-one trade is a losing trade once the round trip is paid for,
     * and a ratio computed gross never says so.
     */
    val plannedRiskReward: Double?
        get() {
            val risk = plannedRisk ?: return null
            val reward = plannedReward ?: return null
            return if (risk > 0 && reward > 0) reward / risk else null
        }
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
    /** Round trips already closed, each with the reason it ended. */
    val closed: List<ReplayRoundTrip> = emptyList(),
    /** The id the next [ReplayLedger.open] hands out. */
    val nextId: Long = 1L,
) {
    /** Whether the reader has done nothing yet, so the bar can stay out of their way. */
    val isEmpty: Boolean get() = open.isEmpty() && closed.isEmpty()

    /** The closed round trips as the engine's own trades, which is what every statistic reads. */
    val trades: List<EngineTrade> get() = closed.map { it.trade }

    /** How many closed this way. The one count a strategy backtest has no way of producing. */
    fun countOf(exit: ReplayExit): Int = closed.count { it.exit == exit }
}

/**
 * Where a triggered order would have filled, and which order it was.
 *
 * Returned by [ReplayLedger.triggeredBy] rather than applied there, so the rule that decides a fill
 * can be asserted on its own. Every argument about whether a backtest is honest is an argument
 * about this one function.
 */
data class ReplayFill(val price: Double, val exit: ReplayExit)

/**
 * Trading a rehearsal, kept out of the record.
 *
 * A paper trade could already be taken during replay, because the setup card reads whatever price
 * the chart is showing and the chart during replay is showing the replay bar. What was missing is
 * everything after the entry: no replay position, no stop, no target, no running result and no
 * ledger for the session. A reader could open a trade in a rehearsal and then had nowhere to see
 * how it went — which is the whole of what a replay is for.
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
 *
 * ### Every convention is the pessimistic one
 *
 * Because a backtest that flatters the reader is worse than no backtest at all, and a backtest of
 * the reader's *own* trading is the one they are most inclined to believe. Where a bar touches both
 * the stop and the target the stop is taken; where a bar gaps through a stop the fill is the gap,
 * not the stop; a target fills at the target and never better. Each is stated again at the function
 * that implements it, because each is a place where a small kindness would turn the report into a
 * flattering fiction.
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
     * The size that commits the whole notional at [price], which is what a strategy run commits.
     *
     * Offered so a reader's session is comparable with a report rather than merely correct.
     * `StrategyRules` sizes every rule at enough units to be worth [STARTING_EQUITY] at the first
     * bar — the same quantity buy-and-hold holds — and a rehearsal typed as "1" on an instrument
     * quoted at forty thousand is a position a thousandth of that size, whose net profit sits
     * beside a strategy's in the same column and means something completely different.
     *
     * Zero on a price that cannot divide, which [open] then refuses.
     */
    fun stakeSize(price: Double): Double =
        if (price.isFinite() && price > 0) STARTING_EQUITY / price else 0.0

    /**
     * Whether a stop can be placed there at all, against the price the reader is looking at.
     *
     * Below the market on a long, above it on a short, and positive. A long stop above the current
     * price is an order that fills at the moment it is placed: it is a typo in every case that is
     * not a misunderstanding, and accepting it would close the position on the next bar for a
     * reason the reader would read as a bug in the app.
     *
     * Note what this does *not* require: that the stop be below the *entry*. Moving a stop up to
     * break-even, or above it behind a running trend, is the single most useful thing a trader
     * learns to do, and a rule written against the entry price would forbid the exercise this
     * screen exists to teach.
     */
    fun stopIsPlaceable(isLong: Boolean, stop: Double, mark: Double): Boolean =
        stop.isFinite() && stop > 0 && mark.isFinite() && (if (isLong) stop < mark else stop > mark)

    /** The mirror for a target: above the market on a long, below it on a short, and positive. */
    fun targetIsPlaceable(isLong: Boolean, target: Double, mark: Double): Boolean =
        target.isFinite() && target > 0 && mark.isFinite() &&
            (if (isLong) target > mark else target < mark)

    /**
     * Open a position at the close of the bar the replay cursor is on.
     *
     * The close, not the next bar's open, and that is the one place this deliberately differs from
     * the backtest engine: a reader in replay is *at* that close, looking at it, deciding. The
     * engine fills at the next open because a rule cannot know a close until the bar is over; a
     * person watching a replay bar print can. Filling them the same way would charge the reader a
     * bar of slippage for a decision they actually made.
     *
     * [stopLoss] and [takeProfit] are optional and are checked against that same close. A level on
     * the wrong side of the market **refuses the whole order** rather than being quietly dropped:
     * a reader who typed a stop and got a position without one is a reader trading unprotected
     * while believing they are not, and there is no worse outcome available here.
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
        stopLoss: Double? = null,
        takeProfit: Double? = null,
    ): ReplaySession {
        val bar = bars.getOrNull(cursor) ?: return session
        if (size <= 0 || !size.isFinite() || !bar.c.isFinite() || bar.c <= 0) return session
        if (stopLoss != null && !stopIsPlaceable(isLong, stopLoss, bar.c)) return session
        if (takeProfit != null && !targetIsPlaceable(isLong, takeProfit, bar.c)) return session
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
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            checkedThrough = cursor,
        )
        return session.copy(open = session.open + position, nextId = session.nextId + 1)
    }

    /**
     * Move an open position's stop and target, against the bar the cursor is on.
     *
     * Both are replaced, and null clears. That is the honest signature for a control that shows two
     * fields: "leave whichever one I did not type" is a rule the reader cannot see, and the day it
     * is misread is the day somebody thinks they removed a target and did not.
     *
     * A level on the wrong side of the current price refuses the whole edit, exactly as [open]
     * does. Refusing rather than clamping matters most here: a reader trailing a stop upward past
     * the market meant a different number, and a stop silently moved to *almost* where they typed
     * is a stop they will not check again.
     */
    fun protect(
        session: ReplaySession,
        bars: List<Candle>,
        cursor: Int,
        id: Long,
        stopLoss: Double?,
        takeProfit: Double?,
    ): ReplaySession {
        val position = session.open.firstOrNull { it.id == id } ?: return session
        val mark = bars.getOrNull(cursor)?.c ?: return session
        if (stopLoss != null && !stopIsPlaceable(position.isLong, stopLoss, mark)) return session
        if (takeProfit != null && !targetIsPlaceable(position.isLong, takeProfit, mark)) return session
        return session.copy(
            open = session.open.map {
                if (it.id == id) it.copy(stopLoss = stopLoss, takeProfit = takeProfit) else it
            },
        )
    }

    /**
     * Where [position] would have been filled on [bar], or null when nothing was touched.
     *
     * The three rules that decide whether this whole feature is honest:
     *
     * **A stop is a low for a long and a high for a short.** Not a close. A bar that closed above
     * the stop having traded through it is a bar that took the reader out; scoring it on the close
     * is the single most common way a hand-rolled backtest invents money, because most stops are
     * hit intrabar and recovered by the close on exactly the days a reader remembers fondly.
     *
     * **A bar that touches both the stop and the target is a stop.** Bar data cannot say which came
     * first — the high and the low of one candle carry no order — so one of the two has to be
     * assumed, and the pessimistic assumption is the only defensible one. Taking the target here
     * would turn every wide, violent bar into a winner and would flatter precisely the setups that
     * are most dangerous to hold. A backtest that flatters the reader is worse than none, and this
     * is the line where that principle is either kept or quietly abandoned.
     *
     * **A gap through a stop fills at the gap, not at the stop.** A stop is a market order once it
     * is touched, and a market that opened below a long's stop never traded at that stop again. The
     * fill is the worse of the two. A target, by contrast, is a limit and fills at exactly the
     * target even when the bar gapped far past it: assuming the better price would be assuming a
     * fill nobody was queued for.
     */
    fun triggeredBy(position: ReplayPosition, bar: Candle): ReplayFill? {
        if (!bar.h.isFinite() || !bar.l.isFinite()) return null
        val stop = position.stopLoss
        // The stop is asked first, and that ordering *is* the pessimism: on a bar that reached both
        // levels the answer is decided by which question is put first, and this one is.
        if (stop != null && (if (position.isLong) bar.l <= stop else bar.h >= stop)) {
            return ReplayFill(stopFill(position, stop, bar), ReplayExit.STOP)
        }
        val target = position.takeProfit
        if (target != null && (if (position.isLong) bar.h >= target else bar.l <= target)) {
            return ReplayFill(target, ReplayExit.TARGET)
        }
        return null
    }

    /** The worse of the stop and the bar's open — see the gap rule in [triggeredBy]. */
    private fun stopFill(position: ReplayPosition, stop: Double, bar: Candle): Double {
        val open = bar.o.takeIf { it.isFinite() && it > 0 } ?: return stop
        return if (position.isLong) min(stop, open) else max(stop, open)
    }

    /**
     * Walk every open position up to the cursor and close the ones whose stop or target was hit.
     *
     * Called whenever the cursor moves, before anything is drawn. Each position resumes from its
     * own [ReplayPosition.checkedThrough] and stops at the first bar that triggered it, so a
     * position is filled at the *earliest* level it reached rather than at the last — the ordinary
     * bug here is a loop that keeps walking and reports the trade closing at a stop it reached
     * three bars after the target had already taken it out.
     *
     * The bar a position was opened on is never checked. It was opened at that bar's close, so the
     * high and the low it printed are history the reader had already seen when they decided; a stop
     * "hit" by them would be a stop hit before it existed.
     *
     * Stepping backwards resolves nothing and un-resolves nothing. See
     * [ReplayPosition.checkedThrough]: a fill the reader watched happen cannot be undone by
     * dragging the slider left.
     */
    fun advance(session: ReplaySession, bars: List<Candle>, cursor: Int): ReplaySession {
        if (session.open.isEmpty() || bars.isEmpty()) return session
        val last = min(cursor, bars.lastIndex)
        val survivors = ArrayList<ReplayPosition>(session.open.size)
        var closed = session.closed

        session.open.forEach { position ->
            var index = max(position.checkedThrough + 1, position.entryIndex + 1)
            var fill: ReplayFill? = null
            var at = index
            while (index <= last) {
                val bar = bars.getOrNull(index) ?: break
                val hit = triggeredBy(position, bar)
                if (hit != null) {
                    fill = hit
                    at = index
                    break
                }
                index++
            }
            if (fill == null) {
                survivors += position.copy(checkedThrough = max(position.checkedThrough, last))
            } else {
                closed = closed + roundTrip(
                    position = position,
                    bars = bars,
                    exitIndex = at,
                    exitPrice = fill.price,
                    exit = fill.exit,
                    // A stop or a target fills part-way through its bar. The rest of that bar
                    // happened after the reader was out of the position and must not enter the
                    // excursion envelope — otherwise a stop-out on a bar that later ran fifty
                    // points would be reported as a trade that was up fifty, which is the exact
                    // opposite of what happened to the reader.
                    throughExitBar = false,
                )
            }
        }
        return session.copy(open = survivors, closed = closed)
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
            val envelope = envelope(bars, position.entryIndex, last, position.entryPrice)
            position.copy(highestHigh = envelope.first, lowestLow = envelope.second)
        }
        return session.copy(open = marked)
    }

    /**
     * Close one position at the cursor's close, moving it to [ReplaySession.closed].
     *
     * The envelope is recomputed here from the bars rather than read off the position, so a close
     * is correct whether or not [mark] happened to have been called first. The two agree by
     * construction — they walk the same bars — and a function whose result depends on an earlier
     * call somebody might forget is a function that is wrong on the day they do.
     */
    fun close(
        session: ReplaySession,
        bars: List<Candle>,
        cursor: Int,
        id: Long,
        exit: ReplayExit = ReplayExit.MANUAL,
    ): ReplaySession {
        val position = session.open.firstOrNull { it.id == id } ?: return session
        val bar = bars.getOrNull(cursor) ?: return session
        return session.copy(
            open = session.open.filterNot { it.id == id },
            closed = session.closed + roundTrip(
                position = position,
                bars = bars,
                exitIndex = max(position.entryIndex, cursor),
                exitPrice = bar.c,
                exit = exit,
                // A manual close happens at the close of a bar the reader has watched in full, so
                // the whole of that bar was lived through and belongs in the envelope.
                throughExitBar = true,
            ),
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
        session.open.forEach { position ->
            next = close(next, bars, cursor, position.id, ReplayExit.SESSION_END)
        }
        return next
    }

    /** The open book's mark-to-market at the cursor, in quote currency. */
    fun unrealised(session: ReplaySession, bars: List<Candle>, cursor: Int): Double {
        val mark = bars.getOrNull(cursor)?.c ?: return 0.0
        return session.open.sumOf { it.profit(mark) }
    }

    /** Everything already realised in this session, in quote currency. */
    fun realised(session: ReplaySession): Double = session.closed.sumOf { it.trade.pnl }

    /**
     * The session's closed trades, summarised with the backtest's own arithmetic.
     *
     * Closed only. An open position's profit is a number that changes while it is being read, and
     * folding it into a win rate would mean a statistic that moves when nothing happened — the
     * same rule `core:papertrade` keeps, for the same reason.
     *
     * Every metric here can be absent: a session of two winning trades has an infinite profit
     * factor and no average loss at all. Render it through [BacktestFormat], never directly, and
     * on a rehearsal-sized sample render the rates through [BacktestFormat.ratioIfSampled] as well
     * — six trades is not a win rate.
     */
    fun summary(session: ReplaySession, bars: List<Candle>, cursor: Int): EngineMetrics {
        if (session.closed.isEmpty()) return EngineMetrics()
        val revealed = CandleSeries(bars.take(min(cursor, bars.lastIndex) + 1))
        return Engine.summarise(
            trades = session.trades,
            equityCurve = BacktestReports.markedCurve(session.trades, revealed, STARTING_EQUITY),
            series = revealed,
            startingEquity = STARTING_EQUITY,
        )
    }

    /**
     * The trade a closing position becomes, with the envelope the reader actually lived through.
     *
     * [throughExitBar] is the whole reason this is one function rather than two copies: a manual
     * close is taken at a bar's close, so that bar happened in full, while a stop or a target fills
     * part-way through its bar and the remainder of it belongs to nobody. Getting this backwards
     * would report a run-up on trades that were stopped out before the run.
     */
    private fun roundTrip(
        position: ReplayPosition,
        bars: List<Candle>,
        exitIndex: Int,
        exitPrice: Double,
        exit: ReplayExit,
        throughExitBar: Boolean,
    ): ReplayRoundTrip {
        val through = if (throughExitBar) exitIndex else exitIndex - 1
        val envelope = envelope(bars, position.entryIndex, through, position.entryPrice)
        val exitFee = fee(exitPrice, position.size)
        return ReplayRoundTrip(
            trade = EngineTrade(
                entryIndex = position.entryIndex,
                entryTime = position.entryTime,
                entryPrice = position.entryPrice,
                exitIndex = exitIndex,
                exitTime = bars.getOrNull(exitIndex)?.t ?: position.entryTime,
                exitPrice = exitPrice,
                isLong = position.isLong,
                size = position.size,
                fee = position.entryFee + exitFee,
                // The exit price is a price the position really passed through, so it belongs
                // inside the envelope even when the fill is a wick the bars above do not cover.
                highestHigh = max(envelope.first, exitPrice),
                lowestLow = min(envelope.second, exitPrice),
            ),
            exit = exit,
        )
    }

    /**
     * The highest high and the lowest low of the bars in `from..to`, seeded at the entry price.
     *
     * `to` below `from` is the ordinary case for a position stopped out on the bar after its entry,
     * and gives the seed back rather than an empty range's worth of infinities.
     */
    private fun envelope(
        bars: List<Candle>,
        from: Int,
        to: Int,
        seed: Double,
    ): Pair<Double, Double> {
        var high = seed
        var low = seed
        for (index in from..to) {
            val bar = bars.getOrNull(index) ?: break
            if (bar.h.isFinite()) high = max(high, bar.h)
            if (bar.l.isFinite()) low = min(low, bar.l)
        }
        return high to low
    }
}
