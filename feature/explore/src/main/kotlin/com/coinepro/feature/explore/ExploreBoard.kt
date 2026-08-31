package com.coinepro.feature.explore

import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.marketdata.MarketTicker
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolMeta

/**
 * One chip on the strip under the tiles.
 *
 * A [category] of null is «همه» — the whole catalogue, which is what the screen opens on.
 *
 * The strip is **built from the catalogue rather than declared**, and that is the difference
 * between this and a hard-coded row of five. The two backends quote different universes: TradeYar
 * is USDT pairs and nothing else, CoinePro-FX is metals, majors and a handful of indices. A fixed
 * strip would put «فارکس» in front of a crypto reader with nothing behind it, and «شاخص» in front
 * of everyone even though only nine indices in the whole product have artwork to draw. See
 * [ExploreBoard.lenses].
 */
data class ExploreLens(
    val category: SymbolCategory?,
    /** How many markets this chip would show. Never zero — a chip with nothing behind it is not drawn. */
    val count: Int,
)

/**
 * One card in the scrolling row: a market, its price and its move.
 *
 * [price] and [changePercent] are nullable and the card is still worth drawing without the second
 * one — a market the feed quotes but has not reported a day's move for is a real market having a
 * quiet moment, and «—» says that honestly. Without a *price*, though, there is nothing on the card
 * but a logo, so [ExploreBoard.cards] drops those rather than putting an empty rectangle in a row
 * whose whole subject is figures.
 */
data class ExploreCard(
    val meta: SymbolMeta,
    val price: Double?,
    val changePercent: Double?,
) {
    val symbol: String get() = meta.symbol
}

/**
 * What Explore puts on the screen, worked out without a composable in sight.
 *
 * All of it is derived from lists the app already holds — the catalogue from
 * `MarketCatalogGateway` by way of `MarketSearchController`, the day's figures from
 * `MarketTickerStore` — so this file can be tested against a handful of rows rather than against a
 * server, and the screen above it has no arithmetic of its own to get wrong.
 *
 * ### The rule that is not negotiable here
 *
 * Every row that reaches this object has already been through `SymbolArtwork.covers`, because
 * `MarketCatalogGateway` filters the catalogue on it before anybody sees it. Nothing in this file
 * may introduce a symbol from anywhere else — and in particular not from the ticker table, which is
 * eight hundred rows filtered by nothing. Building a "biggest movers" row out of that table is
 * exactly how a screenful of lettered discs would get onto a surface this app does not allow them
 * on. So the ticker table is only ever *joined* to a catalogue row here, never iterated.
 */
object ExploreBoard {

    /**
     * The chips, in a fixed order, keeping only the ones with markets behind them.
     *
     * Order is this enumeration's rather than the catalogue's, so a reader who has learned that
     * «کریپتو» is the second chip does not find it third because the server started quoting a new
     * index. [ORDER] deliberately omits [SymbolCategory.ENERGY] and [SymbolCategory.OTHER]: neither
     * can ever pass `SymbolArtwork.covers` — an energy contract has no mark of its own and `OTHER`
     * has nothing to look up — so a chip for either would be permanently empty, which the filter
     * below would drop anyway. Naming them here would be describing a possibility that does not
     * exist.
     */
    fun lenses(rows: List<MarketSearchRow>): List<ExploreLens> {
        if (rows.isEmpty()) return emptyList()
        val everything = ExploreLens(category = null, count = rows.size)
        val byCategory = ORDER.mapNotNull { category ->
            val count = rows.count { it.meta.category == category }
            if (count == 0) null else ExploreLens(category = category, count = count)
        }
        // One chip is not a choice. On a platform quoting a single category — which TradeYar
        // effectively is — «همه» and «کریپتو» are the same list twice, and a strip offering the
        // reader a decision that has one outcome is chrome.
        return if (byCategory.size <= 1) emptyList() else listOf(everything) + byCategory
    }

    /**
     * The cards for one chip.
     *
     * The order is the catalogue's own, which `MarketSearchController` has already ranked for an
     * empty query: popular markets first, then the majors, then the server's order. It is
     * deliberately **not** re-sorted by the day's move. A row that reordered itself every time the
     * five-second poll answered would be unreadable — the card a reader reached for would be
     * somewhere else by the time their finger arrived — and "biggest mover" is a question the
     * markets tab's own lens strip already answers, on a screen built for scanning rather than for
     * a horizontal row of eight.
     *
     * @param tickers the day's rollup, keyed by symbol, or an empty map on a platform with no such
     *   route. CoinePro-FX has none, and there the change comes from the catalogue's own quote —
     *   which is why this takes both and prefers neither blindly.
     */
    fun cards(
        rows: List<MarketSearchRow>,
        tickers: Map<String, MarketTicker> = emptyMap(),
        category: SymbolCategory? = null,
        limit: Int = CARD_LIMIT,
    ): List<ExploreCard> = rows.asSequence()
        .filter { category == null || it.meta.category == category }
        .map { row ->
            val ticker = tickers[row.meta.symbol.uppercase()]
            ExploreCard(
                meta = row.meta,
                // The rollup first where there is one: it is the day's close-to-now on the venue's
                // own clock, and the catalogue's price is a snapshot that goes stale from the moment
                // it lands. Where there is no rollup the snapshot is the only answer and is a real
                // one.
                price = ticker?.last ?: row.quote?.price,
                // Never derived from `last / open`. The server computes this deliberately — see
                // `MarketTicker.changePercent24h` — because a market that arrived without an open
                // would otherwise become a flat zero percent, which is a specific and wrong claim.
                changePercent = ticker?.changePercent24h ?: row.quote?.changePercent,
            )
        }
        .filter { it.price != null }
        .take(limit)
        .toList()

    /**
     * The categories that can appear, in the order they appear.
     *
     * Metals before forex because on CoinePro-FX gold and silver *are* the product, and indices
     * last because there are nine of them in the whole catalogue.
     */
    private val ORDER: List<SymbolCategory> = listOf(
        SymbolCategory.CRYPTO,
        SymbolCategory.METAL,
        SymbolCategory.FOREX,
        SymbolCategory.INDEX,
    )

    /**
     * How many cards the row holds.
     *
     * Eight, because the row is a horizontal scroller and a scroller a reader cannot reach the end
     * of stops being a row and becomes a second list. What is on this screen is an invitation into
     * the markets tab, not a replacement for it.
     */
    const val CARD_LIMIT = 8

    /**
     * How many stories sit under the cards.
     *
     * Five: enough that the section reads as a feed rather than as a teaser, few enough that «همهٔ
     * اخبار» is still the obvious next tap. The news screen holds the rest.
     */
    const val STORY_LIMIT = 5
}
