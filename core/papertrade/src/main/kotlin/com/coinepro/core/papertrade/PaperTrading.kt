package com.coinepro.core.papertrade

import com.coinepro.core.database.PaperTradeEntity

/**
 * The arithmetic the old paper screen ran on, kept for exactly one purpose.
 *
 * `(exit − entry) × size`, sign flipped for a sell, with no fees, no spread, no swap, no funding
 * and no slippage. That was the whole model, and the screen said so — which was honest about the
 * omission and, the owner's judgement, decoration rather than a product. Everything that trades now
 * goes through [PaperEngine] and is charged properly.
 *
 * This survives because [PaperMigration] needs it. A reader who took forty trades under the old
 * screen was shown a result for each one, and the import has to reproduce **that** number rather
 * than recompute it under rules that did not exist when they took the trade. Back-charging fees
 * onto a closed history would change a reader's past record because the app was updated, which is
 * a record nobody can learn from.
 *
 * There is deliberately no win rate here any more. There was one, and a second definition of what
 * counts as a win — beside `PortfolioMath`'s, which the record now uses — is how two screens come
 * to print different numbers under the same Persian word. See [PaperRecordMath].
 */
object PaperTrading {

    /** Where an old row stands against a price. Null when the price is not a number. */
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
}
