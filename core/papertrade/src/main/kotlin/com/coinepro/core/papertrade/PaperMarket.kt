package com.coinepro.core.papertrade

import com.coinepro.core.model.MarketQuote

/**
 * One observation of a price, as the simulator is allowed to see it.
 *
 * This is deliberately not a second source of truth. It is a *view* of the app's own market feed —
 * [asPaperQuote] is the only way one is built from a live quote — and it exists because the fill
 * rules need three things the rest of the app does not carry together: the two sides of the book,
 * whether the feed still trusts its own number, and when it was taken.
 *
 * ### Why [stale] is part of the price
 *
 * A stale quote is not a price, it is a memory of one. The market feed already knows the
 * difference and says so on every quote — fifteen seconds for LBank, ninety for Finnhub. A
 * simulator that filled a stop from a ninety-second-old number would fill it at a price that has
 * not existed for a minute and a half, most visibly on a reconnect, when a burst of remembered
 * quotes arrives at once. So nothing fills from a stale observation; see [PaperEngine.observe].
 *
 * ### Why [bid] and [ask] are nullable and not filled in
 *
 * Both feeds *may* carry a book and mostly do not: LBank's socket ticker and Finnhub's snapshot
 * both reach `MarketQuote` with nulls in these two fields far more often than not. Inventing a
 * spread here would put the guess inside the price, where nothing downstream could tell it apart
 * from a quoted one. Instead the nulls travel, and [PaperFills] widens an assumed spread around
 * [last] *at the moment of the fill*, records that it did, and the screen says so on the fill.
 */
data class PaperQuote(
    val symbol: String,
    val last: Double,
    val bid: Double? = null,
    val ask: Double? = null,
    val stale: Boolean = false,
    val atEpochMillis: Long = 0L,
) {
    /** Whether this observation may fill an order. A stale or impossible price may not. */
    val fillable: Boolean get() = !stale && last.isFinite() && last > 0.0

    /** Whether the two sides really came from the feed rather than being assumed. */
    val quotedBook: Boolean
        get() {
            val bidPrice = bid ?: return false
            val askPrice = ask ?: return false
            return bidPrice.isFinite() && askPrice.isFinite() &&
                bidPrice > 0.0 && askPrice > 0.0 && askPrice >= bidPrice
        }
}

/**
 * The app's own quote, seen as the simulator sees it.
 *
 * An extension rather than a constructor call at the call site, so there is exactly one place that
 * decides what the paper book is allowed to read. The one thing it must never grow is a fallback
 * that fetches: the price a paper order fills at has to be the price the reader was looking at.
 */
fun MarketQuote.asPaperQuote(): PaperQuote = PaperQuote(
    symbol = instrument.symbol,
    last = price,
    bid = bid,
    ask = ask,
    stale = isStale,
    atEpochMillis = timestampEpochMillis,
)
