package com.coinepro.core.papertrade

import kotlin.math.abs

/**
 * How a paper order gets its price. The whole simulator stands or falls here.
 *
 * ### The rule this file exists to refuse
 *
 * Fill everything at the last traded price. It is one line, it is what almost every paper-trading
 * screen does, and it teaches a habit that loses money: a trader who learns that entering and
 * leaving is free will scalp a market where the round trip costs them the spread twice and the fee
 * twice, and will not understand why a strategy that "worked" on paper bleeds. Every rule below
 * exists to make the paper cost of immediacy resemble the real one.
 *
 * ### The five rules
 *
 *  1. **Taking costs the spread.** A buy pays the ask, a sell hits the bid. Never the mid, never
 *     last. Where the feed sends a real book those are the feed's own two numbers; where it does
 *     not — which is most of the time on both of this app's feeds — half of
 *     [PaperRules.assumedSpreadPercent] is widened each side of last and the fill is stamped
 *     [PaperFill.assumedSpread] so the screen can say the spread was assumed rather than quoted.
 *  2. **Taking also costs slippage.** [PaperRules.slippagePercent] moves the fill further against
 *     the trader, on market and triggered-stop fills only. A limit that slipped would not be a
 *     limit.
 *  3. **Resting costs neither.** An order that waited gets its own price exactly, and the maker
 *     fee. That is what a limit order buys you and the simulator has to show it being bought.
 *  4. **A stop is a trigger, not a price.** A stop that goes at 95 does not fill at 95; it becomes
 *     a market order and pays rule 1 and rule 2 from wherever the price actually is. This is the
 *     single most expensive thing a beginner does not know, and a simulator that fills stops at
 *     the stop price hides it completely.
 *  5. **Where the path was not watched, take the worse candidate.** The app only sees prices while
 *     it is open and the feed is fresh. When the first observation after a gap in watching is
 *     already through a level, the fill takes the price that is worse for the trader: a limit gets
 *     its own limit rather than the better price the gap is showing, and a stop gets the market
 *     rather than its trigger. Under that rule the watched and unwatched prices come out identical,
 *     which is the point — the honesty is in [PaperFill.watched] recording that nobody was looking,
 *     not in a second price rule nobody could check.
 *
 * ### What this still gets wrong, and says so
 *
 * A resting order here fills on touch. A real one joins a queue and may watch the price touch its
 * level and leave without it. There is no order book to model that against — `core:orderbook` has
 * depth for the venues but not this app's own paper queue — so touch-fill is optimistic and the
 * rules screen says so in as many words rather than leaving the reader to find out on a real fill.
 */
object PaperFills {

    /** The two sides a fill crosses, and whether the feed really sent them. */
    data class Sides(val bid: Double, val ask: Double, val assumed: Boolean) {
        fun forSide(side: PaperSide): Double = if (side == PaperSide.BUY) ask else bid
    }

    /**
     * One priced fill, before it is charged to an account.
     *
     * The two costs are **per unit**, not per fill. A price is a property of the market and a cost
     * per unit is too; multiplying by a size here would mean every caller that fills part of an
     * order has to remember to divide again, and the one that forgets prints a spread cost ten
     * times the real one beside a correct price.
     */
    data class Priced(
        val price: Double,
        val basis: PaperFillBasis,
        /** What the spread cost, per unit, against last. Zero on a resting fill. */
        val spreadPerUnit: Double,
        /** What slippage cost, per unit, on top of the spread. Zero on a resting fill. */
        val slippagePerUnit: Double,
        val assumedSpread: Boolean,
    )

    /**
     * The book to fill against.
     *
     * A quoted book is used exactly as sent, including a wide one — a wide spread is information,
     * not an error, and narrowing it would be the app deciding it knows the market better than the
     * venue. Where there is no book, the assumed spread is symmetric around last: asymmetry would
     * be a directional claim, and this has no basis for one.
     */
    fun sides(quote: PaperQuote, rules: PaperRules): Sides {
        val bid = quote.bid
        val ask = quote.ask
        if (quote.quotedBook && bid != null && ask != null) {
            return Sides(bid = bid, ask = ask, assumed = false)
        }
        val half = quote.last * rules.assumedSpreadPercent / 100.0 / 2.0
        return Sides(bid = quote.last - half, ask = quote.last + half, assumed = true)
    }

    /**
     * A fill that takes liquidity: market orders, triggered stops, and a limit that arrives already
     * marketable.
     *
     * [cap] is the limit price of a marketable limit order, and it is what keeps rule 2 from
     * violating rule 3's promise: a buy limit at 100 may fill at 99.98 with slippage, and must
     * never fill at 100.01. Null where nothing caps it.
     */
    fun taking(
        side: PaperSide,
        quote: PaperQuote,
        rules: PaperRules,
        cap: Double? = null,
    ): Priced {
        val sides = sides(quote, rules)
        val touch = sides.forSide(side)
        val slipped = touch * (1.0 + rules.slippagePercent / 100.0 * side.direction)
        val capped = when {
            cap == null -> slipped
            side == PaperSide.BUY -> minOf(slipped, cap)
            else -> maxOf(slipped, cap)
        }
        // Guarded rather than trusted: a hostile or half-parsed quote can produce a non-positive
        // price here, and a fill at zero would credit the account with the whole notional.
        val price = if (capped.isFinite() && capped > 0.0) capped else quote.last
        return Priced(
            price = price,
            basis = PaperFillBasis.TAKEN,
            spreadPerUnit = abs(touch - quote.last),
            slippagePerUnit = abs(price - touch),
            assumedSpread = sides.assumed,
        )
    }

    /** A fill the market came to. Its own price, no spread, no slippage — that is the whole point. */
    fun resting(price: Double): Priced = Priced(
        price = price,
        basis = PaperFillBasis.RESTED,
        spreadPerUnit = 0.0,
        slippagePerUnit = 0.0,
        assumedSpread = false,
    )

    /** A side's fee on a filled notional, in quote currency. Never negative. */
    fun fee(price: Double, size: Double, percent: Double): Double =
        abs(price * size) * abs(percent) / 100.0

    fun feeFor(priced: Priced, size: Double, rules: PaperRules): Double = when (priced.basis) {
        PaperFillBasis.TAKEN -> fee(priced.price, size, rules.takerFeePercent)
        PaperFillBasis.RESTED -> fee(priced.price, size, rules.makerFeePercent)
    }

    /**
     * Whether an order's level has been reached by this observation, and whether anybody saw it
     * happen.
     *
     * [previous] is the price this order was last compared against while the app was running. Null
     * means nothing was: the app has restarted, or the feed went stale across the move. A previous
     * price already on the far side means the same thing — the order should have filled then, so
     * something was not watched — and is treated as unwatched rather than as impossible.
     */
    fun reached(previous: Double?, current: Double, level: Double, upward: Boolean): Reach {
        val there = if (upward) current >= level else current <= level
        if (!there) return Reach.NOT_YET
        val near = when {
            previous == null -> return Reach.UNWATCHED
            upward -> previous < level
            else -> previous > level
        }
        return if (near) Reach.WATCHED else Reach.UNWATCHED
    }

    enum class Reach {
        NOT_YET,
        WATCHED,
        UNWATCHED,
        ;

        val filled: Boolean get() = this != NOT_YET
        val watched: Boolean get() = this == WATCHED
    }

    /**
     * Whether a limit order is already fillable at the moment it is placed.
     *
     * A buy limit at or above the ask is not a resting order at all, it is a market order with a
     * ceiling, and every venue treats it that way. Simulating it as resting would let a reader
     * place a limit far through the market and be shown a maker fee for a trade that took
     * liquidity.
     */
    fun marketable(side: PaperSide, limit: Double, quote: PaperQuote, rules: PaperRules): Boolean {
        val sides = sides(quote, rules)
        return if (side == PaperSide.BUY) limit >= sides.ask else limit <= sides.bid
    }
}
