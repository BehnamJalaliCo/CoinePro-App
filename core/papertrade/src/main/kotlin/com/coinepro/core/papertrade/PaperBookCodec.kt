package com.coinepro.core.papertrade

/**
 * The whole book as one string, and back again.
 *
 * The format is this repository's own: one row per record, fields separated by characters no field
 * can contain, all of it in a single preference. `LocalAlertStore` established it and the reasons
 * carry over — it is small, it is printable so the next person to debug this can read the value out
 * of the file by eye, and it needs no schema migration for a field that did not exist yesterday.
 *
 * ### Decoding cannot throw
 *
 * The property that matters more than any other here. A malformed row is dropped and the rest of
 * the book is kept; a row written by an older build decodes field-for-field and stops early, taking
 * the default for everything after. A paper account that crashed on its own stored value would be
 * unreachable without clearing the app's data, and the reader would lose a hundred trades of record
 * to fix one bad row.
 *
 * ### Why nothing here is escaped
 *
 * Every field is a ticker, an enum id this file chose, or a number Kotlin printed. None of those
 * can contain a separator. There is deliberately no free-text field in the book — a note on a trade
 * would need `DelimitedText`'s escaping, and it belongs in the journal, which already has it.
 *
 * ### What is deliberately not written
 *
 * [PaperOrder.lastSeenPrice] and [PaperPosition.lastSeenPrice]. They record that this process
 * watched a price, and no process watched anything while the app was shut. Persisting them would
 * let a restarted app claim it had seen a crossing it slept through, which is the exact lie the
 * unwatched-fill rule exists to avoid telling.
 */
object PaperBookCodec {

    private const val ROW = ";"
    private const val FIELD = "|"

    private const val ACCOUNT = "A"
    private const val RULES = "R"
    private const val NEXT = "N"
    private const val ORDER = "O"
    private const val POSITION = "P"
    private const val CLOSED = "C"
    private const val FILL = "F"

    fun encode(book: PaperBook): String {
        val rows = ArrayList<String>(
            4 + book.orders.size + book.positions.size + book.closed.size + book.fills.size,
        )
        rows += row(NEXT, book.nextId)
        rows += row(
            ACCOUNT,
            book.account.balance,
            book.account.startingBalance,
            book.account.openedAtEpochMillis,
            book.account.generation,
        )
        rows += row(
            RULES,
            book.rules.startingBalance,
            book.rules.leverage,
            book.rules.takerFeePercent,
            book.rules.makerFeePercent,
            book.rules.slippagePercent,
            book.rules.assumedSpreadPercent,
            book.rules.stopOutPercent,
        )
        book.orders.forEach { order ->
            rows += row(
                ORDER,
                order.id,
                order.symbol,
                order.side.id,
                order.type.id,
                order.size,
                order.limitPrice,
                order.stopPrice,
                order.stopLoss,
                order.takeProfit,
                flag(order.reduceOnly),
                order.placedAtEpochMillis,
                order.state.id,
                order.settledAtEpochMillis,
                order.rejectedBecause?.id,
                flag(order.triggered),
            )
        }
        book.positions.forEach { position ->
            rows += row(
                POSITION,
                position.id,
                position.symbol,
                position.side.id,
                position.size,
                position.entry,
                position.openedAtEpochMillis,
                position.stopLoss,
                position.takeProfit,
                position.feesPaid,
                position.marginHeld,
                position.leverage,
            )
        }
        book.closed.forEach { trade ->
            rows += row(
                CLOSED,
                trade.id,
                trade.symbol,
                trade.side.id,
                trade.size,
                trade.entry,
                trade.exit,
                trade.openedAtEpochMillis,
                trade.closedAtEpochMillis,
                trade.gross,
                trade.fees,
                trade.reason.id,
                trade.balanceAfter,
            )
        }
        book.fills.forEach { fill ->
            rows += row(
                FILL,
                fill.id,
                fill.orderId,
                fill.symbol,
                fill.side.id,
                fill.size,
                fill.price,
                fill.reference,
                fill.basis.id,
                fill.fee,
                fill.slippage,
                fill.spreadCost,
                flag(fill.watched),
                flag(fill.assumedSpread),
                fill.atEpochMillis,
            )
        }
        return rows.joinToString(ROW)
    }

    /**
     * A book from stored text, or a fresh one from nothing.
     *
     * [fallback] is the book a reader gets when there is nothing stored — the first run, and the
     * run after they cleared the app's data. It carries the clock, which this file has no business
     * reading for itself.
     */
    fun decode(raw: String?, fallback: PaperBook = PaperBook()): PaperBook {
        val rows = raw.orEmpty().split(ROW).filter(String::isNotBlank)
        if (rows.isEmpty()) return fallback

        var account = fallback.account
        var rules = fallback.rules
        var nextId = 0L
        val orders = ArrayList<PaperOrder>()
        val positions = ArrayList<PaperPosition>()
        val closed = ArrayList<PaperClosedTrade>()
        val fills = ArrayList<PaperFill>()

        rows.forEach { line ->
            val parts = line.split(FIELD)
            when (parts.firstOrNull()) {
                NEXT -> nextId = parts.num(1)?.toLong() ?: 0L
                ACCOUNT -> account = PaperAccount(
                    balance = parts.num(1) ?: account.balance,
                    startingBalance = parts.num(2) ?: account.startingBalance,
                    openedAtEpochMillis = parts.num(3)?.toLong() ?: account.openedAtEpochMillis,
                    generation = parts.num(4)?.toInt() ?: account.generation,
                )
                RULES -> rules = PaperRules(
                    startingBalance = parts.num(1) ?: rules.startingBalance,
                    leverage = parts.num(2) ?: rules.leverage,
                    takerFeePercent = parts.num(3) ?: rules.takerFeePercent,
                    makerFeePercent = parts.num(4) ?: rules.makerFeePercent,
                    slippagePercent = parts.num(5) ?: rules.slippagePercent,
                    assumedSpreadPercent = parts.num(6) ?: rules.assumedSpreadPercent,
                    stopOutPercent = parts.num(7) ?: rules.stopOutPercent,
                ).sane()
                ORDER -> decodeOrder(parts)?.let(orders::add)
                POSITION -> decodePosition(parts)?.let(positions::add)
                CLOSED -> decodeClosed(parts)?.let(closed::add)
                FILL -> decodeFill(parts)?.let(fills::add)
                // Anything else is a record a later build writes and this one does not know.
                // Skipped rather than treated as corruption, so a downgrade loses a feature and
                // not the reader's account.
                else -> Unit
            }
        }

        val highest = maxOf(
            orders.maxOfOrNull { it.id } ?: 0L,
            positions.maxOfOrNull { it.id } ?: 0L,
            closed.maxOfOrNull { it.id } ?: 0L,
            fills.maxOfOrNull { it.id } ?: 0L,
        )
        return PaperBook(
            rules = rules,
            account = account,
            orders = orders,
            positions = positions,
            closed = closed,
            fills = fills,
            // Never below the highest id read back. A truncated `N` row would otherwise hand out an
            // id a position already holds, and two positions with one id is a book that closes the
            // wrong one.
            nextId = maxOf(nextId, highest + 1L),
        )
    }

    private fun decodeOrder(parts: List<String>): PaperOrder? {
        val id = parts.num(1)?.toLong() ?: return null
        val symbol = parts.text(2) ?: return null
        val side = PaperSide.fromId(parts.getOrNull(3)) ?: return null
        val type = PaperOrderType.fromId(parts.getOrNull(4)) ?: return null
        val size = parts.num(5) ?: return null
        return PaperOrder(
            id = id,
            symbol = symbol,
            side = side,
            type = type,
            size = size,
            limitPrice = parts.num(6),
            stopPrice = parts.num(7),
            stopLoss = parts.num(8),
            takeProfit = parts.num(9),
            reduceOnly = parts.flag(10),
            placedAtEpochMillis = parts.num(11)?.toLong() ?: 0L,
            state = PaperOrderState.fromId(parts.getOrNull(12)) ?: PaperOrderState.WORKING,
            settledAtEpochMillis = parts.num(13)?.toLong(),
            rejectedBecause = PaperReject.fromId(parts.getOrNull(14)),
            triggered = parts.flag(15),
        )
    }

    private fun decodePosition(parts: List<String>): PaperPosition? {
        val id = parts.num(1)?.toLong() ?: return null
        val symbol = parts.text(2) ?: return null
        val side = PaperSide.fromId(parts.getOrNull(3)) ?: return null
        val size = parts.num(4) ?: return null
        val entry = parts.num(5) ?: return null
        // A position with no size or no entry cannot be marked, so it is not a position. Dropped
        // rather than restored as a row whose profit is permanently unknown.
        if (size <= 0.0 || entry <= 0.0) return null
        return PaperPosition(
            id = id,
            symbol = symbol,
            side = side,
            size = size,
            entry = entry,
            openedAtEpochMillis = parts.num(6)?.toLong() ?: 0L,
            stopLoss = parts.num(7),
            takeProfit = parts.num(8),
            feesPaid = parts.num(9) ?: 0.0,
            marginHeld = parts.num(10) ?: 0.0,
            leverage = parts.num(11) ?: 1.0,
        )
    }

    private fun decodeClosed(parts: List<String>): PaperClosedTrade? {
        val id = parts.num(1)?.toLong() ?: return null
        val symbol = parts.text(2) ?: return null
        val side = PaperSide.fromId(parts.getOrNull(3)) ?: return null
        val size = parts.num(4) ?: return null
        val entry = parts.num(5) ?: return null
        val exit = parts.num(6) ?: return null
        return PaperClosedTrade(
            id = id,
            symbol = symbol,
            side = side,
            size = size,
            entry = entry,
            exit = exit,
            openedAtEpochMillis = parts.num(7)?.toLong() ?: 0L,
            closedAtEpochMillis = parts.num(8)?.toLong() ?: 0L,
            gross = parts.num(9) ?: 0.0,
            fees = parts.num(10) ?: 0.0,
            reason = PaperCloseReason.fromId(parts.getOrNull(11)) ?: PaperCloseReason.MANUAL,
            balanceAfter = parts.num(12) ?: 0.0,
        )
    }

    private fun decodeFill(parts: List<String>): PaperFill? {
        val id = parts.num(1)?.toLong() ?: return null
        val symbol = parts.text(3) ?: return null
        val side = PaperSide.fromId(parts.getOrNull(4)) ?: return null
        val size = parts.num(5) ?: return null
        val price = parts.num(6) ?: return null
        return PaperFill(
            id = id,
            orderId = parts.num(2)?.toLong() ?: 0L,
            symbol = symbol,
            side = side,
            size = size,
            price = price,
            reference = parts.num(7) ?: price,
            basis = PaperFillBasis.fromId(parts.getOrNull(8)) ?: PaperFillBasis.TAKEN,
            fee = parts.num(9) ?: 0.0,
            slippage = parts.num(10) ?: 0.0,
            spreadCost = parts.num(11) ?: 0.0,
            watched = parts.flag(12),
            assumedSpread = parts.flag(13),
            atEpochMillis = parts.num(14)?.toLong() ?: 0L,
        )
    }

    private fun row(tag: String, vararg fields: Any?): String =
        (listOf(tag) + fields.map { field -> field?.toString().orEmpty() }).joinToString(FIELD)

    private fun flag(value: Boolean): String = if (value) "1" else "0"

    /** A finite number, or null. `NaN` and the infinities are stored as text and must not return. */
    private fun List<String>.num(index: Int): Double? =
        getOrNull(index)?.toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun List<String>.text(index: Int): String? = getOrNull(index)?.takeIf(String::isNotBlank)

    private fun List<String>.flag(index: Int): Boolean = getOrNull(index) == "1"
}
