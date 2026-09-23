package com.coinepro.app.widget

import com.coinepro.core.datastore.WidgetMarket
import com.coinepro.core.datastore.WidgetSnapshot

/**
 * Which market a single-symbol widget draws, and what it has room to say — run Τ2, B7.
 *
 * ### Why the second widget is a different question, not a smaller one
 *
 * `MarketsWidget` follows the watchlist and answers «how is my list». This answers «where is
 * gold», which is a question about *one* instrument that a reader wants at a glance without
 * scanning a list — and on a home screen it is a two-cell tile rather than a twelve-cell one.
 * Reviews of every app in this category ask for both, and they ask for them in different words.
 *
 * ### The pick is a rule, and the rule is about what to do when the answer is not there
 *
 * A widget is configured once, with a ticker, and then lives for months. In that time the reader
 * can unstar the market, the catalogue can drop it, and a refresh can come back with fewer rows
 * than it used to. Every one of those leaves a widget whose symbol is not in the snapshot — and the
 * wrong answer is to draw *something*, because a tile that quietly starts showing a different
 * instrument is worse than one that says it cannot find this one.
 *
 * So: null, and the provider says so in as many words.
 */
object SymbolWidgetPick {

    /**
     * The market this widget is for, or null where the snapshot does not carry it.
     *
     * Matched case-insensitively because a symbol travels through a preference, a deep link and a
     * feed, and exactly one of those three has ever guaranteed its case.
     */
    fun marketFor(snapshot: WidgetSnapshot, symbol: String?): WidgetMarket? {
        val key = symbol?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
        return snapshot.markets.firstOrNull { it.symbol.trim().uppercase() == key }
    }

    /**
     * What a tile of this size can carry.
     *
     * The price is never dropped: a price widget without a price is a decoration. Everything else
     * gives way to it in order — the name first, because the ticker already says which market this
     * is; then the change, because it is the second most-asked-for fact and the first to become
     * unreadable when it has to share a line.
     */
    fun layoutFor(widthDp: Int, heightDp: Int): SymbolWidgetLayout = SymbolWidgetLayout(
        name = widthDp >= NAME_MIN_WIDTH_DP && heightDp >= NAME_MIN_HEIGHT_DP,
        change = heightDp >= CHANGE_MIN_HEIGHT_DP,
        // «When was this» is the one thing a price tile cannot honestly leave out — a stale price
        // and a live one look identical, and on a trading app that is not cosmetic. It is the last
        // thing dropped, and only where the tile is too short to hold two lines at all.
        freshness = heightDp >= FRESHNESS_MIN_HEIGHT_DP,
    )

    /** Below this the tile is one column of digits and the name would wrap. */
    const val NAME_MIN_WIDTH_DP = 140

    /** Two lines of text and a name needs a third. */
    const val NAME_MIN_HEIGHT_DP = 90

    /** The price alone fits in one cell; the change needs a second line. */
    const val CHANGE_MIN_HEIGHT_DP = 60

    /** And the freshness a third, short, one. */
    const val FRESHNESS_MIN_HEIGHT_DP = 76
}

/** What a single-symbol tile draws beside the price. */
data class SymbolWidgetLayout(
    val name: Boolean,
    val change: Boolean,
    val freshness: Boolean,
)
