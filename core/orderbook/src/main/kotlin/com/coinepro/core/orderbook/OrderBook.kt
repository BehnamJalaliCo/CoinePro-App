package com.coinepro.core.orderbook

/**
 * One resting price level: everything queued at [price], on one side of the book.
 *
 * [quantity] is in the instrument's own unit — base coin for a crypto pair, lots for a broker's
 * market book — and is deliberately **not** normalised to a currency value here. Multiplying by the
 * price to get a notional is a decision about what the reader is looking for, and a ladder scaled
 * by notional and one scaled by size disagree about which wall is the big one. The screen picks;
 * this type carries what the venue sent.
 *
 * [orders] is how many separate resting orders make up that quantity, and it is the one figure a
 * size column cannot express: one order of forty behaves nothing like fifty orders of eight-tenths.
 * The first is a single participant who can withdraw the whole wall in one message; the second is a
 * crowd that has to be lifted. A ladder that prints only the sum shows those two as the same bar,
 * which is precisely the distinction a scalper is reading the ladder to find.
 *
 * It is **nullable and stays nullable**. LBank's futures book carries it and its spot book does
 * not; MetaTrader 5's market book has no such concept at all and never will, because a broker's
 * Level II is aggregated before it reaches the terminal. Null means "this venue did not say", and
 * that must never be flattened to zero — a zero here would draw a level with no orders behind it
 * standing next to a quantity that is plainly there.
 */
data class DepthLevel(val price: Double, val quantity: Double, val orders: Int? = null)

/** Which half of the book. Bids are resting buyers, asks are resting sellers. */
enum class BookSide {
    BID,
    ASK,
}

/**
 * A level with the volume standing between it and the touch, for the depth curve.
 *
 * [total] includes this level's own [quantity]. That is the convention every depth chart uses —
 * the curve at a price answers "how much would I have to lift to reach here", and the level you
 * stop on is part of what you lifted.
 */
data class CumulativeDepth(val price: Double, val quantity: Double, val total: Double)

/**
 * The resting liquidity on both sides of one market at one instant.
 *
 * ### Why the ordering is enforced rather than assumed
 *
 * [bids] descend from the best bid, [asks] ascend from the best ask, and [init] refuses anything
 * else. This looks pedantic and it is the single most important line in the file. A ladder is read
 * by *shape*: the reader's eye takes the long bars as the walls and decides which side is heavy
 * from where they sit relative to the spread. Hand it a book sorted the wrong way and it still
 * draws — same rows, same bars, same colours — and it says the opposite of what is true. There is
 * no visible symptom, which is exactly why it has to be a thrown exception at the boundary rather
 * than a convention in a comment.
 *
 * Producers do not construct this directly. [of] sorts, merges duplicate prices and drops the rows
 * a feed could not fill; the constructor then re-checks its work, so a future producer that forgets
 * [of] fails loudly on its first response rather than quietly for the life of the release.
 *
 * [at] is the **venue's** own timestamp in epoch milliseconds, or `0` when the venue publishes none.
 * A book is only ever a claim about an instant that has already passed, and a ladder that cannot
 * say how stale it is cannot be acted on. LBank's futures book is one of the venues that publishes
 * none — its payload is `symbol`, `asks`, `bids` and nothing else — so on crypto this is `0` and
 * stays `0`. It is never filled from a relay's clock or the phone's: a time that is not the venue's
 * would read as freshness the data does not have, on the one screen where age is the whole subject.
 *
 * [maxAgeMillis] is the other half of that answer and is not interchangeable with it. It is an
 * upper bound rather than an instant — "this book is at most this old, plus flight time" — declared
 * by whatever cache the relay serves it from. Where [at] is absent this is the only honest thing a
 * screen can say about staleness, and where both are present they answer different questions. Null
 * means no bound was declared, which is weaker than a large bound and must not be shown as a small
 * one.
 *
 * [truncated] says the venue holds more levels than these. It is not an error: twenty levels is
 * what a phone can show and what both feeds are asked for. It exists so the deepest row can be
 * marked as an edge of the request rather than read as the end of the book — a reader who takes a
 * truncated bottom row for the last resting order has drawn a conclusion about liquidity that the
 * data does not support.
 */
data class OrderBook(
    val symbol: String,
    val bids: List<DepthLevel>,
    val asks: List<DepthLevel>,
    val at: Long,
    val truncated: Boolean,
    val maxAgeMillis: Long? = null,
) {
    init {
        require(bids.zipWithNext().all { (a, b) -> a.price > b.price }) {
            "bids must descend from the best bid: $bids"
        }
        require(asks.zipWithNext().all { (a, b) -> a.price < b.price }) {
            "asks must ascend from the best ask: $asks"
        }
    }

    /** The highest price a resting buyer will pay, or null on an empty side. */
    val bestBid: Double? get() = bids.firstOrNull()?.price

    /** The lowest price a resting seller will take, or null on an empty side. */
    val bestAsk: Double? get() = asks.firstOrNull()?.price

    /**
     * Ask minus bid — the cost of crossing, which is the number a scalper is actually here for.
     *
     * Null when either side is empty, because a spread against nothing is not zero, it is unknown,
     * and a zero would read on the ladder as a perfectly tight market.
     */
    val spread: Double? get() {
        val bid = bestBid ?: return null
        val ask = bestAsk ?: return null
        return ask - bid
    }

    /**
     * The midpoint, which is the price a ladder centres on.
     *
     * Not the last trade: a book has no last trade in it, and centring on one that came from
     * somewhere else puts the spread row off-centre whenever the two feeds disagree by a tick.
     */
    val midPrice: Double? get() {
        val bid = bestBid ?: return null
        val ask = bestAsk ?: return null
        return (bid + ask) / 2.0
    }

    /**
     * A book whose best bid is at or above its best ask, which cannot happen in a real market.
     *
     * It happens in a *relayed* one all the time: two sides of a snapshot assembled a few
     * milliseconds apart, or a stale side left in place through a fast move. The ladder must not
     * present a negative spread as a trading opportunity, so it asks this first and says the book
     * is momentarily inconsistent instead.
     */
    val crossed: Boolean get() {
        val bid = bestBid ?: return false
        val ask = bestAsk ?: return false
        return bid >= ask
    }

    /** Everything resting on the buy side of the loaded book, summed. */
    val bidVolume: Double get() = bids.sumOf { it.quantity }

    /** Everything resting on the sell side of the loaded book, summed. */
    val askVolume: Double get() = asks.sumOf { it.quantity }

    /**
     * Buy volume as a share of all volume within [levels] of the touch, in `0.0..1.0`.
     *
     * `1.0` is bids only, `0.0` is asks only, `0.5` is parity. Null when there is no volume in the
     * band at all, which is not the same as balance: an empty book and a perfectly matched one look
     * identical as a number and mean opposite things, and a meter parked at the centre for an empty
     * book is the more misleading of the two.
     *
     * ### Why the band is a parameter and not a property of the book
     *
     * Imbalance is read as "which side is pressing **now**", and that reading only survives while
     * the levels in it are levels that could actually trade. The app now loads a hundred a side so
     * the depth curve can show where the size really sits, and folding all hundred into this number
     * would quietly change what it means: orders resting a percent out get pulled and replaced
     * constantly and rarely fill, so they smooth the figure and make it less true at exactly the
     * moment it matters. Widening the fetch must not widen the reading.
     *
     * So the caller names its band. There is no default, deliberately — a default is how the wrong
     * window gets chosen by nobody. `OrderBookGateway.IMBALANCE_LEVELS` is the band the screen uses
     * and carries the reasoning for its size; a caller that genuinely wants the whole loaded book
     * passes [bids] and [asks] sizes and has therefore said so out loud.
     */
    fun imbalance(levels: Int): Double? {
        require(levels > 0) { "levels must be positive: $levels" }
        val bid = bids.take(levels).sumOf { it.quantity }
        val ask = asks.take(levels).sumOf { it.quantity }
        val total = bid + ask
        return if (total <= 0.0) null else bid / total
    }

    /**
     * The largest single resting level on either side, which is what a per-row bar is scaled
     * against.
     *
     * Deliberately shared across the two sides rather than one maximum per side. Scaling each side
     * to its own largest bar makes the biggest bid and the biggest ask the same length whatever
     * their sizes are, so the one picture the ladder exists to draw — which side is heavier — is
     * flattened out of it.
     */
    val largestQuantity: Double
        get() = maxOf(
            bids.maxOfOrNull { it.quantity } ?: 0.0,
            asks.maxOfOrNull { it.quantity } ?: 0.0,
        )

    /** The larger of the two sides' totals, for scaling the cumulative area behind the bars. */
    val largestCumulative: Double get() = maxOf(bidVolume, askVolume)

    /**
     * The depth curve for one side: each level with everything between it and the touch.
     *
     * Walks outwards from the best price, which is the direction the list is already in, so the
     * running total is monotonic and the area drawn behind the bars only ever grows away from the
     * spread. Reversing either side to draw it would produce a curve that falls as it leaves the
     * touch, which is the shape of a book that does not exist.
     */
    fun cumulative(side: BookSide): List<CumulativeDepth> {
        val levels = when (side) {
            BookSide.BID -> bids
            BookSide.ASK -> asks
        }
        var running = 0.0
        return levels.map { level ->
            running += level.quantity
            CumulativeDepth(price = level.price, quantity = level.quantity, total = running)
        }
    }

    /**
     * The [levels] rows nearest the spread on each side.
     *
     * The ladder shows fewer rows than it loads — a phone fits eight or ten a side, and the request
     * asks for twenty so that the imbalance is measured over something wider than the screen. This
     * is how the screen narrows the book without recomputing the loaded one, and it carries
     * [truncated] forward: cutting rows off here means the deepest visible row is an edge of the
     * *view*, on top of any edge of the request, and both mean the same thing to a reader.
     */
    fun top(levels: Int): OrderBook {
        require(levels > 0) { "levels must be positive: $levels" }
        if (bids.size <= levels && asks.size <= levels) return this
        return copy(
            bids = bids.take(levels),
            asks = asks.take(levels),
            truncated = true,
        )
    }

    companion object {
        /**
         * Builds a book from whatever a feed sent, in whatever order it sent it.
         *
         * Four things happen here and every one of them exists because a real feed has done it:
         *
         * * **Rows that could not be filled are dropped.** A level with a non-finite or
         *   non-positive price is not a level at zero, it is a row the relay could not parse, and
         *   carrying it puts a bar at the bottom of the ladder that no order rests behind.
         * * **Zero quantities are dropped.** A depth feed publishes a zero to say a level has been
         *   *removed*, which is an instruction about a book you already hold, not a level in a
         *   snapshot. Drawn as a row it is an empty rung on the ladder.
         * * **Duplicate prices are summed.** Two rows at one price is one price with two orders on
         *   it. Kept as two rows the ladder shows the same rung twice and halves the apparent wall.
         *   [DepthLevel.orders] is summed with them, because merging two queues at one price makes
         *   one queue holding both counts — and it stays null unless at least one row named a
         *   count, so a feed that does not publish the field never acquires a fabricated one.
         * * **Both sides are sorted.** See the note on the class for why this is not left to the
         *   sender's promise.
         */
        fun of(
            symbol: String,
            bids: List<DepthLevel>,
            asks: List<DepthLevel>,
            at: Long,
            truncated: Boolean = false,
            maxAgeMillis: Long? = null,
        ): OrderBook = OrderBook(
            symbol = symbol.uppercase(),
            bids = bids.normalise().sortedByDescending { it.price },
            asks = asks.normalise().sortedBy { it.price },
            at = at,
            truncated = truncated,
            maxAgeMillis = maxAgeMillis,
        )

        /** An honest nothing: the symbol is known, the book is not. Never shown as a market. */
        fun empty(symbol: String, at: Long = 0L): OrderBook =
            OrderBook(symbol.uppercase(), emptyList(), emptyList(), at, truncated = false)

        private fun List<DepthLevel>.normalise(): List<DepthLevel> = this
            .filter { it.price.isFinite() && it.price > 0.0 && it.quantity.isFinite() && it.quantity > 0.0 }
            .groupBy { it.price }
            .map { (price, rows) ->
                DepthLevel(
                    price = price,
                    quantity = rows.sumOf { it.quantity },
                    // `takeIf` before `sum`, not `sumOf { it.orders ?: 0 }`: the second turns a
                    // side where nobody published a count into a column of zeroes, which reads as
                    // "no orders rest here" beside a bar that is plainly there.
                    orders = rows.mapNotNull { it.orders }.takeIf { it.isNotEmpty() }?.sum(),
                )
            }
    }
}
