package com.coinepro.core.papertrade

import kotlin.math.abs
import kotlin.math.min

/** What the ticket asks for. A value rather than eight parameters, so a test can build one. */
data class PaperOrderRequest(
    val symbol: String,
    val side: PaperSide,
    val type: PaperOrderType,
    val size: Double,
    val limitPrice: Double? = null,
    val stopPrice: Double? = null,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val reduceOnly: Boolean = false,
)

/**
 * The simulator, as a set of pure functions over a [PaperBook].
 *
 * Nothing here touches a clock, a store, a coroutine or a screen: every function takes the book,
 * the prices it is allowed to see and the time, and returns the next book. That is what makes the
 * fill rules testable to the standard they need to be tested to — the arithmetic that decides
 * whether a reader's stop cost them thirty basis points or three hundred is checkable in a unit
 * test with three lines of setup and no Android at all.
 *
 * ### The order of operations inside one observation
 *
 * It is deliberate, and it is the pessimistic order:
 *
 *  1. **Protective exits**, stop losses before take profits. One observed price can normally only
 *     reach one of the two, so the ordering matters in exactly one case: a reader who has set a
 *     stop and a target that overlap — a stop above the market and a target below it, which is a
 *     legal thing to ask for and a common thing to do by accident. There the loss-side rule wins,
 *     because a simulator that resolves its own ambiguity in the reader's favour is a simulator
 *     that flatters.
 *  2. **Working orders**, in the order they were placed. First in, first filled — the only
 *     tie-break that does not need a queue this app cannot see.
 *  3. **The stop-out**, last, so it judges the account after everything that happened.
 *
 * ### What it refuses to do
 *
 * It never fills from a stale quote. `MarketQuote` already carries the feed's own judgement of
 * freshness and this trusts it: a reconnect delivers a burst of remembered prices, and an engine
 * that filled from those would trigger a reader's stops against a market that has since moved on.
 *
 * And it does not see inside the gap between two observations. The feed sends a last price, not a
 * bar, so a wick that touched a stop and left before the next quote arrived did not happen as far
 * as this book is concerned. That is a real difference from a real venue, it always favours the
 * reader, and the rules screen says so rather than letting it be discovered on a live stop.
 */
object PaperEngine {

    /** The most round trips one book keeps. Older ones fall off the end of the record. */
    const val MAX_CLOSED = 400

    /** The most fills kept. Shorter than the trade history: this is an audit trail, not a record. */
    const val MAX_FILLS = 150

    fun normalise(symbol: String): String = symbol.trim().uppercase()

    /**
     * Place an order.
     *
     * A market order settles inside this call, against the observation passed in. Everything else
     * rests, unless a limit arrives already marketable — see [PaperFills.marketable] — in which
     * case it is a market order with a ceiling and is treated as one.
     */
    fun place(
        book: PaperBook,
        request: PaperOrderRequest,
        quotes: Map<String, PaperQuote>,
        now: Long,
    ): PaperBook {
        val symbol = normalise(request.symbol)
        if (symbol.isEmpty()) return book
        if (!positive(request.size)) return book
        if (request.type.needsLimit && !positive(request.limitPrice)) return book
        if (request.type.needsStop && !positive(request.stopPrice)) return book
        if (request.stopLoss != null && !positive(request.stopLoss)) return book
        if (request.takeProfit != null && !positive(request.takeProfit)) return book

        val existing = book.positionFor(symbol)
        val size = if (request.reduceOnly) {
            val opposing = existing?.takeIf { it.side != request.side }
                ?: return reject(book, request, symbol, PaperReject.NOTHING_TO_REDUCE, now)
            min(request.size, opposing.size)
        } else {
            request.size
        }

        val order = PaperOrder(
            id = book.nextId,
            symbol = symbol,
            side = request.side,
            type = request.type,
            size = size,
            limitPrice = request.limitPrice,
            stopPrice = request.stopPrice,
            stopLoss = request.stopLoss,
            takeProfit = request.takeProfit,
            reduceOnly = request.reduceOnly,
            placedAtEpochMillis = now,
        )
        val placed = book.copy(orders = book.orders + order, nextId = book.nextId + 1)
        val quote = quotes[symbol]

        return when {
            // A market order with nothing to fill against is refused rather than parked. A market
            // order that waits is not a market order, and a reader who saw «باز کردن» and got a
            // pending row would reasonably think they were in the trade.
            request.type == PaperOrderType.MARKET ->
                if (quote?.fillable == true) {
                    fill(placed, order, quote, PaperFills.taking(order.side, quote, placed.rules), true, now)
                } else {
                    settle(placed, order.id, PaperOrderState.REJECTED, now, PaperReject.NO_PRICE)
                }

            request.type == PaperOrderType.LIMIT && quote?.fillable == true &&
                PaperFills.marketable(order.side, request.limitPrice!!, quote, placed.rules) ->
                fill(
                    placed,
                    order,
                    quote,
                    PaperFills.taking(order.side, quote, placed.rules, cap = request.limitPrice),
                    true,
                    now,
                )

            // Rests. `lastSeenPrice` is seeded here where there is a fresh price, so a crossing
            // that happens while the reader is still on the screen counts as watched.
            else -> placed.copy(
                orders = placed.orders.map {
                    if (it.id == order.id) it.copy(lastSeenPrice = quote?.takeIf { q -> q.fillable }?.last) else it
                },
            )
        }
    }

    /** Withdraw a working order. A settled one is left alone rather than rewritten. */
    fun cancel(book: PaperBook, orderId: Long, now: Long): PaperBook {
        val order = book.orders.firstOrNull { it.id == orderId && it.working } ?: return book
        return settle(book, order.id, PaperOrderState.CANCELLED, now, null)
    }

    /**
     * Move a working order's levels or its size.
     *
     * [PaperOrder.lastSeenPrice] is cleared, because the continuity that field records was
     * continuity against the *old* level. Keeping it would let an order moved across the market
     * claim it had watched a crossing that never happened to it.
     */
    fun amend(
        book: PaperBook,
        orderId: Long,
        limitPrice: Double? = null,
        stopPrice: Double? = null,
        size: Double? = null,
    ): PaperBook {
        val order = book.orders.firstOrNull { it.id == orderId && it.working } ?: return book
        if (size != null && !positive(size)) return book
        if (limitPrice != null && !positive(limitPrice)) return book
        if (stopPrice != null && !positive(stopPrice)) return book
        val amended = order.copy(
            limitPrice = limitPrice ?: order.limitPrice,
            stopPrice = stopPrice ?: order.stopPrice,
            size = size ?: order.size,
            lastSeenPrice = null,
        )
        return book.copy(orders = book.orders.map { if (it.id == orderId) amended else it })
    }

    /**
     * Attach, move or remove a position's stop loss and take profit.
     *
     * Deliberately unvalidated beyond "a positive number". A stop above entry on a long is a
     * breakeven stop and a legitimate thing to want; a take profit already through the market fills
     * on the next observation, which is also what a real venue does with one. Refusing either would
     * be the app deciding it knows what the reader meant.
     */
    fun setProtection(
        book: PaperBook,
        positionId: Long,
        stopLoss: Double?,
        takeProfit: Double?,
    ): PaperBook {
        val position = book.positions.firstOrNull { it.id == positionId } ?: return book
        if (stopLoss != null && !positive(stopLoss)) return book
        if (takeProfit != null && !positive(takeProfit)) return book
        return book.copy(
            positions = book.positions.map {
                if (it.id == position.id) it.copy(stopLoss = stopLoss, takeProfit = takeProfit) else it
            },
        )
    }

    /**
     * Close part or all of a position at market.
     *
     * [fraction] is a share of what is open rather than a number of units, because that is the
     * decision a reader is actually making — take half off, take a third off — and because a unit
     * count typed into a field can exceed the position and quietly become a reversal.
     */
    fun closePosition(
        book: PaperBook,
        positionId: Long,
        fraction: Double,
        quotes: Map<String, PaperQuote>,
        now: Long,
        reason: PaperCloseReason = PaperCloseReason.MANUAL,
    ): PaperBook {
        val position = book.positions.firstOrNull { it.id == positionId } ?: return book
        val quote = quotes[position.symbol]?.takeIf { it.fillable } ?: return book
        val share = fraction.coerceIn(0.0, 1.0)
        if (share <= 0.0) return book
        val size = if (share >= 1.0) position.size else position.size * share
        val priced = PaperFills.taking(position.side.opposite, quote, book.rules)
        return reduce(book, position, size, priced, quote, now, reason, orderId = 0L, watched = true)
    }

    /** Everything, at market, wherever there is a price to close against. */
    fun closeAll(
        book: PaperBook,
        quotes: Map<String, PaperQuote>,
        now: Long,
        reason: PaperCloseReason = PaperCloseReason.MANUAL,
    ): PaperBook {
        var next = book
        book.positions.forEach { position ->
            next = closePosition(next, position.id, 1.0, quotes, now, reason)
        }
        return next
    }

    /**
     * Close and take the other side, in one action, at one price.
     *
     * Two taps would do the same thing and would cost the reader a second look at the market
     * between them. The two fills are still two fills and both are charged.
     */
    fun reverse(book: PaperBook, positionId: Long, quotes: Map<String, PaperQuote>, now: Long): PaperBook {
        val position = book.positions.firstOrNull { it.id == positionId } ?: return book
        val size = position.size
        val side = position.side.opposite
        val closed = closePosition(book, positionId, 1.0, quotes, now, PaperCloseReason.REVERSE)
        if (closed === book) return book
        return place(
            closed,
            PaperOrderRequest(position.symbol, side, PaperOrderType.MARKET, size),
            quotes,
            now,
        )
    }

    /**
     * One observation of the market, applied to the whole book.
     *
     * This is the only function that fills a working order, and it is the only one that can close
     * a position the reader did not close themselves.
     */
    fun observe(book: PaperBook, quotes: Map<String, PaperQuote>, now: Long): PaperBook {
        if (book.positions.isEmpty() && book.working.isEmpty()) return book
        var next = protect(book, quotes, now)
        next = work(next, quotes, now)
        next = stopOut(next, quotes, now)
        return remember(next, quotes)
    }

    /** New assumptions, from here on. Nothing already filled is recomputed under them. */
    fun applyRules(book: PaperBook, rules: PaperRules): PaperBook = book.copy(rules = rules.sane())

    /**
     * Start again, keeping the record.
     *
     * The closed trades and the fills survive, which is the whole difference between a reset and a
     * wipe: a reader who blew an account and wants another go should not have to delete the
     * evidence to get one. Open positions and working orders do not survive, and the screen says so
     * before it happens — they are dropped rather than closed, because closing them would write a
     * result into the record that the reader never decided on.
     */
    fun reset(book: PaperBook, rules: PaperRules, now: Long): PaperBook {
        val sane = rules.sane()
        return book.copy(
            rules = sane,
            account = PaperAccount(
                balance = sane.startingBalance,
                startingBalance = sane.startingBalance,
                openedAtEpochMillis = now,
                generation = book.account.generation + 1,
            ),
            orders = emptyList(),
            positions = emptyList(),
        )
    }

    /** Everything, including the record. Behind a confirmation, and never anywhere else. */
    fun wipe(rules: PaperRules, now: Long): PaperBook {
        val sane = rules.sane()
        return PaperBook(
            rules = sane,
            account = PaperAccount(
                balance = sane.startingBalance,
                startingBalance = sane.startingBalance,
                openedAtEpochMillis = now,
                generation = 1,
            ),
        )
    }

    /* ---------------------------------------------------------------- one observation, in parts */

    private fun protect(book: PaperBook, quotes: Map<String, PaperQuote>, now: Long): PaperBook {
        var next = book
        book.positions.forEach { held ->
            val position = next.positions.firstOrNull { it.id == held.id } ?: return@forEach
            val quote = quotes[position.symbol]?.takeIf { it.fillable } ?: return@forEach
            val long = position.side == PaperSide.BUY
            val stop = position.stopLoss
            val target = position.takeProfit
            val seen = position.lastSeenPrice
            // The stop first, always. See the class comment. And at market, never at the stop —
            // rule 4 in `PaperFills`, which is the whole reason this feature was rebuilt.
            val hitStop = stop?.let { PaperFills.reached(seen, quote.last, it, upward = !long) }
            if (hitStop != null && hitStop.filled) {
                next = reduce(
                    next, position, position.size,
                    PaperFills.taking(position.side.opposite, quote, next.rules), quote, now,
                    PaperCloseReason.STOP_LOSS, orderId = 0L, watched = hitStop.watched,
                )
                return@forEach
            }
            val hitTarget = target?.let { PaperFills.reached(seen, quote.last, it, upward = long) }
            if (hitTarget != null && hitTarget.filled) {
                next = reduce(
                    next, position, position.size, PaperFills.resting(target), quote, now,
                    PaperCloseReason.TAKE_PROFIT, orderId = 0L, watched = hitTarget.watched,
                )
            }
        }
        return next
    }

    private fun work(book: PaperBook, quotes: Map<String, PaperQuote>, now: Long): PaperBook {
        var next = book
        book.working.sortedBy { it.placedAtEpochMillis }.forEach { placed ->
            val order = next.orders.firstOrNull { it.id == placed.id && it.working } ?: return@forEach
            val quote = quotes[order.symbol]?.takeIf { it.fillable } ?: return@forEach
            next = advance(next, order, quote, now)
        }
        return next
    }

    /** One working order against one fresh observation. */
    private fun advance(book: PaperBook, order: PaperOrder, quote: PaperQuote, now: Long): PaperBook {
        var current = order
        var next = book
        var justTriggered = false

        if (current.type.needsStop && !current.triggered) {
            val stop = current.stopPrice ?: return settle(next, current.id, PaperOrderState.REJECTED, now, PaperReject.INVALID)
            val reach = PaperFills.reached(
                current.lastSeenPrice,
                quote.last,
                stop,
                upward = current.side == PaperSide.BUY,
            )
            if (!reach.filled) return next
            if (current.type == PaperOrderType.STOP) {
                // Rule 4: a stop is a trigger, not a price. It pays whatever the market is.
                val priced = PaperFills.taking(current.side, quote, next.rules)
                return fill(next, current, quote, priced, reach.watched, now)
            }
            current = current.copy(triggered = true, lastSeenPrice = quote.last)
            next = next.copy(orders = next.orders.map { if (it.id == current.id) current else it })
            justTriggered = true
        }

        val limit = current.limitPrice ?: return next
        // A triggered stop-limit that *arrives* already through its own limit is marketable, exactly
        // as a limit order placed there would be, and is charged as a taker.
        //
        // Only in the pass that triggered it. An order that has been resting since an earlier
        // observation is in the book at its own price, and a market that rises past a resting sell
        // limit lifts it at the limit — the seller does not get the better price the market has
        // moved to. Checking this on every pass gave a resting order the best price it ever saw,
        // which is a simulator paying its reader to leave orders lying around.
        if (justTriggered && PaperFills.marketable(current.side, limit, quote, next.rules)) {
            val priced = PaperFills.taking(current.side, quote, next.rules, cap = limit)
            return fill(next, current, quote, priced, watched = true, now = now)
        }
        val reach = PaperFills.reached(
            current.lastSeenPrice,
            quote.last,
            limit,
            upward = current.side == PaperSide.SELL,
        )
        if (!reach.filled) return next
        // Rule 5: its own price, watched or not. The gap's better price is not credited.
        return fill(next, current, quote, PaperFills.resting(limit), reach.watched, now)
    }

    /**
     * The account fell through its stop-out level, so the book is closed for it.
     *
     * Only bites above one times leverage, where margin is less than the notional. Everything goes
     * at once rather than largest-loser-first: a partial stop-out needs a maintenance-margin ladder
     * per instrument that neither of this app's venues publishes in a form the app reads, and
     * inventing one would be inventing the number that decides when a reader is wiped out.
     */
    private fun stopOut(book: PaperBook, quotes: Map<String, PaperQuote>, now: Long): PaperBook {
        if (book.positions.isEmpty() || book.marginUsed <= 0.0) return book
        val marks = marksFrom(quotes, fresh = true)
        // Only when every open position has a fresh mark. A margin level computed with a position
        // missing is a number that can cross the line because a websocket dropped.
        if (!book.positions.all { marks[it.symbol] != null }) return book
        val level = book.marginLevelPercent(marks) ?: return book
        if (level > book.rules.stopOutPercent) return book
        return closeAll(book, quotes, now, PaperCloseReason.LIQUIDATION)
    }

    /**
     * Record what every still-working order and open position was compared against.
     *
     * Only from a fillable observation. A stale quote leaves the remembered price alone, so the
     * next fresh one is correctly treated as a crossing nobody watched — which is exactly what a
     * dropped socket is.
     */
    private fun remember(book: PaperBook, quotes: Map<String, PaperQuote>): PaperBook =
        book.copy(
            orders = book.orders.map { order ->
                val quote = quotes[order.symbol]
                if (!order.working || quote?.fillable != true) order else order.copy(lastSeenPrice = quote.last)
            },
            positions = book.positions.map { position ->
                val quote = quotes[position.symbol]
                if (quote?.fillable != true) position else position.copy(lastSeenPrice = quote.last)
            },
        )

    /* ------------------------------------------------------------------------- filling and money */

    /**
     * Turn a priced order into money.
     *
     * Reducing an opposing position comes first and can consume the whole fill; whatever is left
     * opens or adds. That single path is what makes a reversal one order rather than a special
     * case, and it is why a fill can both realise a loss and open a position in the other
     * direction.
     */
    private fun fill(
        book: PaperBook,
        order: PaperOrder,
        quote: PaperQuote,
        priced: PaperFills.Priced,
        watched: Boolean,
        now: Long,
    ): PaperBook {
        var next = book
        var remaining = order.size
        var anythingFilled = false
        val opposing = next.positionFor(order.symbol)?.takeIf { it.side != order.side }
        if (opposing != null) {
            val closing = min(remaining, opposing.size)
            next = reduce(
                next, opposing, closing, priced, quote, now,
                PaperCloseReason.MANUAL, order.id, watched,
            )
            remaining -= closing
            anythingFilled = true
        }
        if (remaining > 0.0 && !order.reduceOnly) {
            val opened = increase(next, order, remaining, priced, quote, watched, now)
            if (opened == null) {
                // Refused for margin. The order is only *rejected* where nothing at all happened:
                // a reversal whose closing leg went through and whose opening leg could not be
                // margined really did fill, and marking it rejected would tell a reader their
                // position is still open when it is flat.
                return settle(
                    next,
                    order.id,
                    if (anythingFilled) PaperOrderState.FILLED else PaperOrderState.REJECTED,
                    now,
                    if (anythingFilled) null else PaperReject.MARGIN,
                )
            }
            next = opened
        }
        return settle(next, order.id, PaperOrderState.FILLED, now, null)
    }

    /**
     * Open or add to a position. Null where the account cannot margin it.
     *
     * The average entry is volume-weighted, so a second buy at a worse price moves the whole
     * position's break-even rather than hiding behind the first one's.
     */
    private fun increase(
        book: PaperBook,
        order: PaperOrder,
        size: Double,
        priced: PaperFills.Priced,
        quote: PaperQuote,
        watched: Boolean,
        now: Long,
    ): PaperBook? {
        val rules = book.rules
        val notional = abs(priced.price * size)
        val margin = notional / rules.leverage
        val fee = PaperFills.feeFor(priced, size, rules)
        val marks = bookMarks(book) + marksFrom(mapOf(quote.symbol to quote), fresh = false)
        if (book.freeMargin(marks) < margin + fee) return null

        val existing = book.positionFor(order.symbol)
        val positions = if (existing == null) {
            book.positions + PaperPosition(
                id = book.nextId,
                symbol = order.symbol,
                side = order.side,
                size = size,
                entry = priced.price,
                openedAtEpochMillis = now,
                stopLoss = order.stopLoss,
                takeProfit = order.takeProfit,
                feesPaid = fee,
                marginHeld = margin,
                leverage = rules.leverage,
            )
        } else {
            val total = existing.size + size
            book.positions.map {
                if (it.id != existing.id) {
                    it
                } else {
                    it.copy(
                        size = total,
                        entry = (existing.entry * existing.size + priced.price * size) / total,
                        feesPaid = existing.feesPaid + fee,
                        marginHeld = existing.marginHeld + margin,
                        // An order that carries its own protection replaces the position's; one
                        // that does not leaves the reader's existing stop exactly where it was.
                        stopLoss = order.stopLoss ?: existing.stopLoss,
                        takeProfit = order.takeProfit ?: existing.takeProfit,
                    )
                }
            }
        }
        return book
            .copy(
                positions = positions,
                account = book.account.copy(balance = book.account.balance - fee),
                nextId = if (existing == null) book.nextId + 1 else book.nextId,
            )
            .record(order.id, order.symbol, order.side, size, priced, fee, watched, quote.last, now)
    }

    /** Close [size] units of [position], realising the result into the balance. */
    private fun reduce(
        book: PaperBook,
        position: PaperPosition,
        size: Double,
        priced: PaperFills.Priced,
        quote: PaperQuote,
        now: Long,
        reason: PaperCloseReason,
        orderId: Long,
        watched: Boolean,
    ): PaperBook {
        val closing = min(size, position.size)
        if (closing <= 0.0) return book
        val share = closing / position.size
        val exitSide = position.side.opposite
        val fee = PaperFills.feeFor(priced, closing, book.rules)
        val gross = (priced.price - position.entry) * closing * position.side.direction
        val entryFeeShare = position.feesPaid * share
        val balance = book.account.balance + gross - fee
        val remaining = position.size - closing

        val trade = PaperClosedTrade(
            id = book.nextId,
            symbol = position.symbol,
            side = position.side,
            size = closing,
            entry = position.entry,
            exit = priced.price,
            openedAtEpochMillis = position.openedAtEpochMillis,
            closedAtEpochMillis = now,
            gross = gross,
            fees = entryFeeShare + fee,
            reason = reason,
            balanceAfter = balance,
        )
        val positions = if (remaining > MIN_SIZE) {
            book.positions.map {
                if (it.id != position.id) {
                    it
                } else {
                    it.copy(
                        size = remaining,
                        feesPaid = position.feesPaid - entryFeeShare,
                        marginHeld = position.marginHeld * (remaining / position.size),
                    )
                }
            }
        } else {
            book.positions.filterNot { it.id == position.id }
        }
        return book
            .copy(
                positions = positions,
                closed = (book.closed + trade).takeLast(MAX_CLOSED),
                account = book.account.copy(balance = balance),
                nextId = book.nextId + 1,
            )
            .record(orderId, position.symbol, exitSide, closing, priced, fee, watched, quote.last, now)
    }

    private fun PaperBook.record(
        orderId: Long,
        symbol: String,
        side: PaperSide,
        size: Double,
        priced: PaperFills.Priced,
        fee: Double,
        watched: Boolean,
        reference: Double,
        now: Long,
    ): PaperBook {
        val fill = PaperFill(
            id = nextId,
            orderId = orderId,
            symbol = symbol,
            side = side,
            size = size,
            price = priced.price,
            reference = reference,
            basis = priced.basis,
            fee = fee,
            // Per-unit, scaled by what actually filled. A partial fill costs a partial spread.
            slippage = priced.slippagePerUnit * size,
            spreadCost = priced.spreadPerUnit * size,
            watched = watched,
            assumedSpread = priced.assumedSpread,
            atEpochMillis = now,
        )
        return copy(fills = (fills + fill).takeLast(MAX_FILLS), nextId = nextId + 1)
    }

    private fun settle(
        book: PaperBook,
        orderId: Long,
        state: PaperOrderState,
        now: Long,
        reason: PaperReject?,
    ): PaperBook = book.copy(
        orders = book.orders.map {
            if (it.id != orderId) {
                it
            } else {
                it.copy(
                    state = state,
                    settledAtEpochMillis = now,
                    rejectedBecause = reason,
                    lastSeenPrice = null,
                )
            }
        },
    )

    private fun reject(
        book: PaperBook,
        request: PaperOrderRequest,
        symbol: String,
        reason: PaperReject,
        now: Long,
    ): PaperBook = book.copy(
        orders = book.orders + PaperOrder(
            id = book.nextId,
            symbol = symbol,
            side = request.side,
            type = request.type,
            size = request.size,
            limitPrice = request.limitPrice,
            stopPrice = request.stopPrice,
            placedAtEpochMillis = now,
            state = PaperOrderState.REJECTED,
            settledAtEpochMillis = now,
            rejectedBecause = reason,
        ),
        nextId = book.nextId + 1,
    )

    private fun marksFrom(quotes: Map<String, PaperQuote>, fresh: Boolean): Map<String, Double?> =
        quotes.mapValues { (_, quote) ->
            when {
                fresh && !quote.fillable -> null
                quote.last.isFinite() && quote.last > 0.0 -> quote.last
                else -> null
            }
        }

    /**
     * Every open position marked at its own entry.
     *
     * The floor under a margin check, not a price: a margin test run with an unmarked position
     * silently treated as worthless would let a reader open a position the account cannot carry.
     * Marking at entry says "no news since", which is the only neutral assumption available.
     */
    private fun bookMarks(book: PaperBook): Map<String, Double?> =
        book.positions.associate { it.symbol to it.entry }

    private fun positive(value: Double?): Boolean = value != null && value.isFinite() && value > 0.0

    /** Below this a remaining position is rounding error rather than a holding. */
    private const val MIN_SIZE = 1e-12
}

private val PaperOrderType.needsLimit: Boolean
    get() = this == PaperOrderType.LIMIT || this == PaperOrderType.STOP_LIMIT

private val PaperOrderType.needsStop: Boolean
    get() = this == PaperOrderType.STOP || this == PaperOrderType.STOP_LIMIT
