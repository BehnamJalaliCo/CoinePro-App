package com.coinepro.core.papertrade

import com.coinepro.core.database.PaperTradeEntity

/**
 * The arithmetic of a trade taken with no money.
 *
 * Deliberately the plainest possible model: `(exit − entry) × size`, with the sign flipped for a
 * sell. No fees, no spread, no swap, no funding and no slippage — and the screen says so in as many
 * words rather than leaving a reader to discover it by comparing against a real fill.
 *
 * That is a decision, not an omission. Modelling fees would require this app to know the reader's
 * broker, their tier and their instrument's contract size, and a simulation that guesses those is
 * more misleading than one that plainly does not include them: it produces a number that *looks*
 * like a real fill and is not. The trader tools already size a position properly with real inputs;
 * this is for practising the decision, not the invoice.
 */
object PaperTrading {

    /** Where the position stands against a price. Null when the price is not a number. */
    fun profit(trade: PaperTradeEntity, price: Double?): Double? {
        val mark = trade.exit ?: price ?: return null
        if (!mark.isFinite()) return null
        val direction = if (trade.buy) 1 else -1
        return (mark - trade.entry) * trade.size * direction
    }

    /** The same, as a percentage of what was committed. Null on a zero entry, which cannot divide. */
    fun profitPercent(trade: PaperTradeEntity, price: Double?): Double? {
        val gain = profit(trade, price) ?: return null
        val committed = trade.entry * trade.size
        if (committed == 0.0 || !committed.isFinite()) return null
        return gain / committed * 100
    }

    val PaperTradeEntity.open: Boolean get() = exit == null

    /**
     * The closed record.
     *
     * Only closed trades count. An open position's profit is a number that changes while being
     * read, and folding it into a win rate would mean a statistic that moves when nothing happened.
     */
    data class Record(val closed: Int, val wins: Int, val net: Double) {
        val winRate: Double? get() = if (closed == 0) null else wins * 100.0 / closed
    }

    fun record(trades: List<PaperTradeEntity>): Record {
        val closed = trades.filter { it.exit != null }
        val results = closed.mapNotNull { profit(it, null) }
        return Record(
            closed = closed.size,
            // Zero is a scratch, as everywhere else in this app.
            wins = results.count { it > 0 },
            net = results.sum(),
        )
    }
}
