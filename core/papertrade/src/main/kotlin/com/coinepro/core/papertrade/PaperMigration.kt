package com.coinepro.core.papertrade

import com.coinepro.core.database.PaperTradeEntity
import kotlin.math.abs

/**
 * The old paper trades, carried into the new book.
 *
 * There was one Room table with seven columns and no account behind it: a symbol, a side, an entry,
 * a size and an optional exit. A reader who took forty of those over a few weeks has a record, and
 * a rebuild that silently emptied it would be the app deleting the only thing the old screen was
 * good for. So this runs exactly once — on the first launch that finds nothing in the new store —
 * and never writes to Room again. The table is left where it is rather than dropped: dropping it is
 * a migration in `core:database`, which this module does not own, and an unread table costs
 * nothing.
 *
 * ### What is carried and what cannot be
 *
 * Carried: every closed trade, with its result exactly as the old arithmetic computed it, and every
 * open position at its entry and size.
 *
 * Not carried, because it never existed: fees, spread and slippage. The old model charged none of
 * them and stated so. Back-charging them now would rewrite the reader's history to results they
 * never saw, so the imported trades keep a zero cost and the new rules apply from here on. A run
 * whose statistics change because the app was updated is a run nobody can learn from.
 */
object PaperMigration {

    /**
     * A book derived from the old table.
     *
     * The balance walks forward from [PaperRules.startingBalance] through the closed results in the
     * order they closed, so the equity curve the record draws is a real one from the first imported
     * trade. Open positions are restored with the margin they would hold under the current rules;
     * where the old table holds more open notional than the new account has money — which one
     * reader in a hundred will have, from a size typed with an extra zero — the account simply
     * starts with no free margin until something is closed. That is a real state, not a broken one,
     * and the screen reads it correctly.
     */
    fun fromLegacy(rows: List<PaperTradeEntity>, rules: PaperRules, now: Long): PaperBook {
        val sane = rules.sane()
        val fresh = PaperBook(
            rules = sane,
            account = PaperAccount(
                balance = sane.startingBalance,
                startingBalance = sane.startingBalance,
                openedAtEpochMillis = now,
            ),
        )
        if (rows.isEmpty()) return fresh

        var nextId = 1L
        var balance = sane.startingBalance
        val closed = rows
            .filter { it.exit != null && usable(it) }
            .sortedBy { it.closedAtEpochMillis ?: it.openedAtEpochMillis }
            .map { row ->
                val exit = row.exit ?: 0.0
                val side = if (row.buy) PaperSide.BUY else PaperSide.SELL
                // The *old* arithmetic, deliberately. See `PaperTrading`: the reader was shown this
                // number, and an import that recomputed it under today's rules would rewrite their
                // history because the app was updated.
                val gross = PaperTrading.profit(row, null) ?: 0.0
                balance += gross
                PaperClosedTrade(
                    id = nextId++,
                    symbol = row.symbol,
                    side = side,
                    size = row.size,
                    entry = row.entry,
                    exit = exit,
                    openedAtEpochMillis = row.openedAtEpochMillis,
                    closedAtEpochMillis = row.closedAtEpochMillis ?: row.openedAtEpochMillis,
                    gross = gross,
                    // Zero, and honestly so. See the class comment.
                    fees = 0.0,
                    reason = PaperCloseReason.MANUAL,
                    balanceAfter = balance,
                )
            }
            .takeLast(PaperEngine.MAX_CLOSED)

        val open = rows
            .filter { it.exit == null && usable(it) }
            .sortedBy { it.openedAtEpochMillis }
            .map { row ->
                val side = if (row.buy) PaperSide.BUY else PaperSide.SELL
                PaperPosition(
                    id = nextId++,
                    symbol = row.symbol,
                    side = side,
                    size = row.size,
                    entry = row.entry,
                    openedAtEpochMillis = row.openedAtEpochMillis,
                    feesPaid = 0.0,
                    marginHeld = abs(row.entry * row.size) / sane.leverage,
                    leverage = sane.leverage,
                )
            }

        return fresh.copy(
            account = fresh.account.copy(balance = balance),
            positions = open,
            closed = closed,
            nextId = nextId,
        )
    }

    /** A row the arithmetic can survive. A zero entry or size is a row that cannot be marked. */
    private fun usable(row: PaperTradeEntity): Boolean =
        row.symbol.isNotBlank() &&
            row.entry.isFinite() && row.entry > 0.0 &&
            row.size.isFinite() && row.size > 0.0 &&
            (row.exit?.isFinite() ?: true)
}
