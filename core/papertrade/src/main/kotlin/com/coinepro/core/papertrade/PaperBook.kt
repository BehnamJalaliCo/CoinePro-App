package com.coinepro.core.papertrade

import kotlin.math.abs

/** Which way round. */
enum class PaperSide(val id: String) {
    BUY("b"),
    SELL("s"),
    ;

    val opposite: PaperSide get() = if (this == BUY) SELL else BUY

    /** `+1` long, `−1` short — the multiplier every price difference in this module uses. */
    val direction: Int get() = if (this == BUY) 1 else -1

    companion object {
        fun fromId(id: String?): PaperSide? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The four order types, which are the four both venues really take.
 *
 * MT5 has all four natively. LBank's spot API takes market and limit, and its futures API adds the
 * two stop forms. So a reader practising any of these is practising something they could place.
 *
 * What this app's *own* one-tap execution sends is narrower than that, and the rules screen says
 * so: `core:execution` posts a signal id, a venue and a quantity, which is a market order and
 * nothing else. Simulating only market orders for that reason would have been the wrong reading of
 * "honest ceiling" — the reader is practising to trade the venue, not to press this app's button.
 */
enum class PaperOrderType(val id: String) {
    MARKET("m"),
    LIMIT("l"),
    STOP("s"),
    STOP_LIMIT("sl"),
    ;

    /** Whether the type rests in the book waiting for a price rather than filling on arrival. */
    val works: Boolean get() = this != MARKET

    companion object {
        fun fromId(id: String?): PaperOrderType? = entries.firstOrNull { it.id == id }
    }
}

enum class PaperOrderState(val id: String) {
    WORKING("w"),
    FILLED("f"),
    CANCELLED("c"),
    REJECTED("r"),
    ;

    companion object {
        fun fromId(id: String?): PaperOrderState? = entries.firstOrNull { it.id == id }
    }
}

/** Why an order never became a position. Each one is a sentence the screen has to be able to say. */
enum class PaperReject(val id: String) {
    /** Free margin would not cover it. */
    MARGIN("margin"),

    /** No usable observation of this symbol's price — unknown ticker, or a feed that is stale. */
    NO_PRICE("price"),

    /** A size, a limit or a stop that is not a positive finite number. */
    INVALID("invalid"),

    /** Reduce-only, with nothing on the other side to reduce. */
    NOTHING_TO_REDUCE("reduce"),
    ;

    companion object {
        fun fromId(id: String?): PaperReject? = entries.firstOrNull { it.id == id }
    }
}

/** How a position ended. Kept because a stop-out and a decision are not the same record. */
enum class PaperCloseReason(val id: String) {
    MANUAL("manual"),
    STOP_LOSS("sl"),
    TAKE_PROFIT("tp"),
    /** The account fell through its stop-out level and the book was closed for it. */
    LIQUIDATION("liq"),
    /** Closed as the first half of a reversal. */
    REVERSE("rev"),
    ;

    companion object {
        fun fromId(id: String?): PaperCloseReason? = entries.firstOrNull { it.id == id }
    }
}

/**
 * How a fill got its price. This is the field the whole feature is built to be able to print.
 *
 * A reader who cannot tell why they got 100.02 rather than 100.00 cannot tell a good simulator
 * from a flattering one, and a flattering one teaches a habit that costs money.
 *
 * Only two values, because there are only two things that can happen: the order either took the
 * price the market was offering, or it rested and the market came to it. Everything else about a
 * fill — whether the crossing was watched, whether the spread was quoted or assumed — is a fact
 * *about* the fill rather than a third way of getting one, and lives on [PaperFill] as its own
 * flag. Folding those into this enum is how a "basis" becomes a bag of unrelated adjectives.
 */
enum class PaperFillBasis(val id: String) {
    /** Took the offer: paid the ask, or hit the bid, plus slippage. Market and triggered stops. */
    TAKEN("taken"),

    /** The market came to a resting order: filled at that order's own price, with no slippage. */
    RESTED("rested"),
    ;

    companion object {
        fun fromId(id: String?): PaperFillBasis? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A working or settled order.
 *
 * [lastSeenPrice] is the one field that is deliberately **not** persisted, and that omission is the
 * mechanism behind the unwatched-crossing rule rather than an oversight. It records the last price
 * this order was actually compared against while the app was running; after a restart it decodes
 * as null, which is precisely the truth — nothing watched the market in between — and the first
 * observation afterwards is therefore treated as a crossing nobody saw.
 */
data class PaperOrder(
    val id: Long,
    val symbol: String,
    val side: PaperSide,
    val type: PaperOrderType,
    val size: Double,
    val limitPrice: Double? = null,
    val stopPrice: Double? = null,
    /** Attached to the position this order opens, so a setup arrives with its stop already on. */
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    /** Closes existing size rather than opening more. Never flips a position. */
    val reduceOnly: Boolean = false,
    val placedAtEpochMillis: Long,
    val state: PaperOrderState = PaperOrderState.WORKING,
    val settledAtEpochMillis: Long? = null,
    val rejectedBecause: PaperReject? = null,
    /** A stop-limit whose stop has gone: it is a resting limit from here on. */
    val triggered: Boolean = false,
    /** Transient. See the class comment — its absence after a restart is load-bearing. */
    val lastSeenPrice: Double? = null,
) {
    val working: Boolean get() = state == PaperOrderState.WORKING

    /** The price the order is waiting on, which is what a list row shows. */
    val restingPrice: Double?
        get() = when (type) {
            PaperOrderType.MARKET -> null
            PaperOrderType.LIMIT -> limitPrice
            PaperOrderType.STOP -> stopPrice
            PaperOrderType.STOP_LIMIT -> if (triggered) limitPrice else stopPrice
        }
}

/**
 * An open position, which is one symbol on one side and not a pile of tickets.
 *
 * Netted rather than hedged: a second buy on a symbol already long averages into the same
 * position. Both venues net — MT5's default account mode and every LBank product — and a simulator
 * that let a reader hold a long and a short in the same instrument would be teaching a shape their
 * broker will refuse.
 */
data class PaperPosition(
    val id: Long,
    val symbol: String,
    val side: PaperSide,
    /** Units still open. Always positive; a position at zero is closed and removed. */
    val size: Double,
    /** Volume-weighted average of everything that has been added to it. */
    val entry: Double,
    val openedAtEpochMillis: Long,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    /** Entry-side fees charged so far, in quote currency, for the part still open. */
    val feesPaid: Double = 0.0,
    /** Quote currency set aside for this position. Zero at one times leverage. */
    val marginHeld: Double = 0.0,
    val leverage: Double = 1.0,
    /**
     * Transient, and not persisted, for exactly the reason [PaperOrder.lastSeenPrice] is not: after
     * a restart nothing watched this position's stop, and the fill it takes on the next
     * observation has to be able to say so.
     */
    val lastSeenPrice: Double? = null,
) {
    val notional: Double get() = abs(entry * size)

    /** What it is worth against a mark, before the exit fee. Null where there is no mark. */
    fun unrealised(mark: Double?): Double? {
        if (mark == null || !mark.isFinite()) return null
        return (mark - entry) * size * side.direction
    }

    /** The same as a share of the notional. Null where nothing can divide. */
    fun unrealisedPercent(mark: Double?): Double? {
        val gain = unrealised(mark) ?: return null
        val committed = notional
        if (committed <= 0.0 || !committed.isFinite()) return null
        return gain / committed * 100.0
    }
}

/**
 * A round trip that is over.
 *
 * [balanceAfter] is here because it is what makes the equity curve a real one. `PortfolioMath`
 * draws a balance curve only when *every* trade carries a balance and falls back to cumulative
 * profit otherwise — and a paper account always knows its own balance, so this book gets the
 * better of the two curves and a drawdown percentage that means something.
 */
data class PaperClosedTrade(
    val id: Long,
    val symbol: String,
    val side: PaperSide,
    val size: Double,
    val entry: Double,
    val exit: Double,
    val openedAtEpochMillis: Long,
    val closedAtEpochMillis: Long,
    /** Before costs. */
    val gross: Double,
    /** Entry share plus exit, in quote currency, always positive. */
    val fees: Double,
    val reason: PaperCloseReason,
    val balanceAfter: Double,
) {
    val net: Double get() = gross - fees

    /** Net as a share of what was committed. Null on a notional that cannot divide. */
    val netPercent: Double?
        get() {
            val committed = abs(entry * size)
            return if (committed > 0.0 && committed.isFinite()) net / committed * 100.0 else null
        }
}

/**
 * One fill, kept so the reader can audit a price rather than take it on trust.
 *
 * [slippage] and [spreadCost] are separated because they are two different lessons: the spread is
 * what the market charges everybody for immediacy, and slippage is what *speed* costs on top. A
 * single "cost" number would let a reader conclude their limit orders were slipping.
 */
data class PaperFill(
    val id: Long,
    val orderId: Long,
    val symbol: String,
    val side: PaperSide,
    val size: Double,
    val price: Double,
    /** What the last-traded price was at the moment of the fill, to compare [price] against. */
    val reference: Double,
    val basis: PaperFillBasis,
    val fee: Double,
    val slippage: Double,
    val spreadCost: Double,
    /**
     * Whether the app actually watched the price reach this fill's level.
     *
     * False after a restart, and after a run of stale quotes. It changes no price — see
     * [PaperFills] for why the pessimistic rule makes the watched and unwatched prices identical —
     * and it is recorded because it changes what the fill is *evidence of*. A reader comparing an
     * unwatched fill against a real broker's is comparing against a market nobody was looking at.
     */
    val watched: Boolean,
    /** The spread crossed was widened around last rather than quoted, because the feed sent none. */
    val assumedSpread: Boolean,
    val atEpochMillis: Long,
) {
    /** How far the fill landed from the reference, in quote currency, always as a cost. */
    val totalCost: Double get() = fee + slippage + spreadCost
}

/**
 * The assumptions the simulation runs on, every one of them the reader's to change.
 *
 * They are settings rather than constants because there is no single honest value: LBank's spot
 * taker fee, LBank futures, and an MT5 broker's commission-plus-spread are three different numbers,
 * and picking one and calling it "the" fee would be quietly asserting a venue the reader may not
 * trade. What is *not* negotiable is that they are applied and that the screen prints them.
 *
 * The defaults match `Backtest.DEFAULT_FEE_PERCENT` and `DEFAULT_STARTING_EQUITY` in `core:chart`
 * so a paper account and a backtest report can be read side by side. They are repeated rather than
 * imported because `core:chart` is a Compose module and this one has no business pulling a UI
 * dependency in for two numbers.
 */
data class PaperRules(
    val startingBalance: Double = DEFAULT_STARTING_BALANCE,
    /**
     * One is the default and it is not a placeholder: at one times, margin is the whole notional,
     * nothing can be liquidated, and the reader is practising the decision rather than the
     * leverage. Anything above it turns the stop-out on.
     */
    val leverage: Double = 1.0,
    /** Charged where the fill took liquidity — market, triggered stop, marketable limit. */
    val takerFeePercent: Double = DEFAULT_TAKER_FEE_PERCENT,
    /** Charged where the order rested and was traded through. */
    val makerFeePercent: Double = DEFAULT_MAKER_FEE_PERCENT,
    /** Added against the trader on taking fills only. A limit that slipped would not be a limit. */
    val slippagePercent: Double = DEFAULT_SLIPPAGE_PERCENT,
    /** Half of this is widened each side of last where the feed sends no book of its own. */
    val assumedSpreadPercent: Double = DEFAULT_ASSUMED_SPREAD_PERCENT,
    /** Margin level, in percent, at which everything open is closed. MT5's own default is 50. */
    val stopOutPercent: Double = DEFAULT_STOP_OUT_PERCENT,
) {
    /** Every field forced back into a range the arithmetic can survive. */
    fun sane(): PaperRules = copy(
        startingBalance = startingBalance.coerceIn(MIN_STARTING_BALANCE, MAX_STARTING_BALANCE),
        leverage = leverage.coerceIn(1.0, MAX_LEVERAGE),
        takerFeePercent = takerFeePercent.coerceIn(0.0, MAX_FEE_PERCENT),
        makerFeePercent = makerFeePercent.coerceIn(0.0, MAX_FEE_PERCENT),
        slippagePercent = slippagePercent.coerceIn(0.0, MAX_SLIPPAGE_PERCENT),
        assumedSpreadPercent = assumedSpreadPercent.coerceIn(0.0, MAX_SLIPPAGE_PERCENT),
        stopOutPercent = stopOutPercent.coerceIn(0.0, 100.0),
    )

    companion object {
        const val DEFAULT_STARTING_BALANCE = 10_000.0
        const val DEFAULT_TAKER_FEE_PERCENT = 0.05
        const val DEFAULT_MAKER_FEE_PERCENT = 0.02
        const val DEFAULT_SLIPPAGE_PERCENT = 0.02
        const val DEFAULT_ASSUMED_SPREAD_PERCENT = 0.04
        const val DEFAULT_STOP_OUT_PERCENT = 50.0

        const val MIN_STARTING_BALANCE = 100.0
        const val MAX_STARTING_BALANCE = 10_000_000.0
        const val MAX_LEVERAGE = 500.0
        const val MAX_FEE_PERCENT = 5.0
        const val MAX_SLIPPAGE_PERCENT = 5.0
    }
}

/** Cash, and when this account started. Everything else is derived from the book. */
data class PaperAccount(
    /** Realised cash. Fees are taken from it at the moment of the fill, as a broker takes them. */
    val balance: Double = PaperRules.DEFAULT_STARTING_BALANCE,
    val startingBalance: Double = PaperRules.DEFAULT_STARTING_BALANCE,
    val openedAtEpochMillis: Long = 0L,
    /**
     * How many times the reader has started again.
     *
     * Kept because a reset must not read as a losing streak that vanished. The history of previous
     * runs is not deleted — [PaperBook.closed] keeps its rows — so the number tells a reader which
     * run a trade belongs to.
     */
    val generation: Int = 1,
)

/**
 * Everything the simulator knows, as one value.
 *
 * Immutable and whole, for the same reason `ReplaySession` is: every transition is then a pure
 * function that a test can hold in one hand, and there is exactly one thing to persist. The engine
 * never mutates; it returns the next book.
 */
data class PaperBook(
    val rules: PaperRules = PaperRules(),
    val account: PaperAccount = PaperAccount(),
    val orders: List<PaperOrder> = emptyList(),
    val positions: List<PaperPosition> = emptyList(),
    val closed: List<PaperClosedTrade> = emptyList(),
    val fills: List<PaperFill> = emptyList(),
    val nextId: Long = 1L,
) {
    val working: List<PaperOrder> get() = orders.filter { it.working }

    /** Every symbol the book needs a price for. What the screen subscribes to, and nothing more. */
    val tracked: Set<String>
        get() = (positions.map { it.symbol } + working.map { it.symbol }).toSet()

    val marginUsed: Double get() = positions.sumOf { it.marginHeld }

    fun positionFor(symbol: String): PaperPosition? = positions.firstOrNull { it.symbol == symbol }

    /**
     * Balance plus what the open book is worth right now.
     *
     * Null-tolerant on purpose: a position whose symbol has no mark contributes nothing rather than
     * zero, and [equityIsComplete] says whether that happened. An equity figure quietly missing a
     * position is the one number on this screen that must never be quietly wrong.
     */
    fun equity(marks: Map<String, Double?>): Double =
        account.balance + positions.sumOf { it.unrealised(marks[it.symbol]) ?: 0.0 }

    /** Whether every open position had a mark, so [equity] is the whole account. */
    fun equityIsComplete(marks: Map<String, Double?>): Boolean =
        positions.all { marks[it.symbol]?.isFinite() == true }

    fun unrealised(marks: Map<String, Double?>): Double =
        positions.sumOf { it.unrealised(marks[it.symbol]) ?: 0.0 }

    /** Equity over margin, as a percentage. Null with nothing open, which is not "infinite". */
    fun marginLevelPercent(marks: Map<String, Double?>): Double? {
        val used = marginUsed
        if (used <= 0.0) return null
        return equity(marks) / used * 100.0
    }

    fun freeMargin(marks: Map<String, Double?>): Double = equity(marks) - marginUsed
}
