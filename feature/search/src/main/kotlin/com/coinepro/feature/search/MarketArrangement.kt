package com.coinepro.feature.search

import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.marketdata.MarketTicker
import com.coinepro.core.marketdata.MarketTickerStore
import kotlin.math.abs

/**
 * Which slice of the board the reader is looking at.
 *
 * This is a **second axis over the same list**, not a replacement for the category tabs. Those
 * answer "what kind of thing is this" — crypto, forex, metal, the reader's own watchlist — and a
 * reader who opens the markets tab is rarely asking that. They are asking where something is
 * happening today, and 441 markets in catalogue order does not answer it at any scroll depth.
 *
 * The two axes compose: «کریپتو» plus «بیشترین رشد» is the crypto risers, which is a question
 * neither axis can ask alone.
 *
 * [NONE] is first and is the default, because the catalogue order is the one arrangement that is
 * true without a single figure having arrived — and on a connection that comes and goes, the
 * screen's first frame is exactly that case.
 */
internal enum class MarketLens(val labelRes: Int) {
    NONE(R.string.markets_lens_all),
    HOT(R.string.markets_lens_hot),
    GAINERS(R.string.markets_lens_gainers),
    LOSERS(R.string.markets_lens_losers),
}

/**
 * The two figures the list can be ordered by.
 *
 * Turnover rather than volume, and the distinction is not pedantry: [MarketTicker.volume24h] is
 * counted in the base asset, so ordering a mixed list by it ranks a count of bitcoin against a
 * count of dogecoin and puts the cheapest token on the board at the top. [MarketTicker.turnover24h]
 * is in the quote currency and is the only one of the two that means "most traded" across markets
 * priced in different things. The server's own relay has this exact bug elsewhere and told us so.
 */
internal enum class MarketSortKey { CHANGE, TURNOVER }

internal data class MarketSort(val key: MarketSortKey, val descending: Boolean)

/**
 * What one more tap on a heading does.
 *
 * Largest first, then smallest first, then off — three states rather than two, and the third is the
 * one that matters. The lens below already has an order of its own, and a sort with no way back to
 * it would take «داغ» away from a reader who only wanted to glance down the change column.
 */
internal fun nextMarketSort(current: MarketSort?, key: MarketSortKey): MarketSort? = when {
    current?.key != key -> MarketSort(key, descending = true)
    current.descending -> MarketSort(key, descending = false)
    else -> null
}

/**
 * The rows in the order the reader asked for, from the day's table.
 *
 * ### Where the unknowns go, and why not in the middle
 *
 * Every field of [MarketTicker] but the symbol and the price is nullable, and the server omits
 * rather than zeroes — deliberately, because `changePercent24h = 0.0` is a claim that a market was
 * flat and null is the truth that nobody said. Two different treatments follow from that, and they
 * are different because the two controls mean different things:
 *
 * * Under a **sort**, a row with no figure sinks to the bottom in *both* directions. That is the
 *   rule `sortRows` already applies to the watchlist, and the reason is the same: reading a missing
 *   figure as zero floats every unquoted market to the top of an ascending sort, which produces a
 *   list ordered by what the feed has not sent yet.
 * * Under a **lens**, a row with no figure is dropped rather than sunk. «بیشترین رشد» is defined by
 *   the sign of a number, and a row with no number has no sign — a tail of unknowns under the
 *   losers would be a list of markets that are not losers.
 *
 * ### The lens picks the rows, the sort picks the order
 *
 * They compose in that order and only in that order: the lens narrows, then the sort rearranges
 * what is left. So «بیشترین افت» sorted by turnover is "of the markets that fell today, the ones
 * most money went through", which is a real question; the other way round it would be a sort with a
 * filter applied afterwards, which would silently drop rows out of the middle of the reader's list.
 *
 * ### Ties keep the catalogue's order
 *
 * Every sort here is Kotlin's, which is stable, so two markets both at +0.00% stay in the order
 * they arrived in rather than swapping places on each poll. Deliberately not `asReversed()` for the
 * descending case — that reverses the ties too, and a list whose ties shuffle every five seconds is
 * a list that looks alive when nothing has changed.
 *
 * @param tickers the shared store's state. An empty table is not an error and not an empty market:
 *   it is the state before the first poll lands, and on CoinePro-FX it is the permanent state,
 *   because that platform has no such route. The caller keeps the lens at [MarketLens.NONE] there.
 */
internal fun arrangeMarkets(
    rows: List<MarketSearchRow>,
    tickers: MarketTickerStore.MarketTickerState,
    lens: MarketLens,
    sort: MarketSort?,
): List<MarketSearchRow> {
    val lensed = when (lens) {
        MarketLens.NONE -> rows
        MarketLens.GAINERS -> rows
            .withFigure(tickers) { it.changePercent24h }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .map { it.first }
        MarketLens.LOSERS -> rows
            .withFigure(tickers) { it.changePercent24h }
            .filter { it.second < 0.0 }
            .sortedBy { it.second }
            .map { it.first }
        MarketLens.HOT -> hottest(rows, tickers)
    }
    if (sort == null) return lensed

    val figure: (MarketTicker) -> Double? = when (sort.key) {
        MarketSortKey.CHANGE -> MarketTicker::changePercent24h
        MarketSortKey.TURNOVER -> MarketTicker::turnover24h
    }
    val keyed = lensed.map { row -> row to tickers[row.meta.symbol]?.let(figure) }
    val present = keyed.mapNotNull { (row, value) -> value?.let { row to it } }
    val absent = keyed.filter { it.second == null }.map { it.first }
    val ordered = if (sort.descending) present.sortedByDescending { it.second } else present.sortedBy { it.second }
    return ordered.map { it.first } + absent
}

/**
 * «داغ» — the busiest markets among the ones that actually moved today.
 *
 * The server has no such list and no such field, so this is the app's own definition, and it is
 * written out here rather than buried in a score:
 *
 * * A market qualifies when its absolute move is at least the **middle of today's board** — the
 *   median of every absolute move the table carries. That is the table's own centre rather than a
 *   threshold picked in this file, which is the point: on a quiet day «۳٪» would name nothing and
 *   on a violent one it would name everything, whereas "moved more than a typical market did" means
 *   the same thing on both.
 * * The survivors are ordered by [MarketTicker.turnover24h] — the money that went through them.
 *
 * What this deliberately is **not** is a blended score. Something of the form
 * `turnover × |change|` sorts fine, looks authoritative, has no unit, and cannot be checked by
 * anybody reading the list; a gate plus an order can be read off the two columns the row already
 * shows. Nothing here is weighted against anything, because there is no exchange rate between a
 * percentage and a dollar and inventing one would be inventing the answer.
 *
 * A market missing either figure is not hot — it is unknown — and it is left out entirely. Half of
 * this definition cannot be applied to it, and a row admitted on the strength of the other half
 * would be sitting in a list whose title makes a claim about it.
 */
private fun hottest(
    rows: List<MarketSearchRow>,
    tickers: MarketTickerStore.MarketTickerState,
): List<MarketSearchRow> {
    val scored = rows.mapNotNull { row ->
        val ticker = tickers[row.meta.symbol] ?: return@mapNotNull null
        val turnover = ticker.turnover24h ?: return@mapNotNull null
        val move = ticker.changePercent24h ?: return@mapNotNull null
        Triple(row, turnover, abs(move))
    }
    if (scored.isEmpty()) return emptyList()
    // The upper of the two middles on an even count, rather than the average of them. It keeps the
    // arithmetic to an index, and it errs towards a shorter list — which is the right direction for
    // a tab whose whole promise is that it is shorter than the one beside it.
    val gate = scored.map { it.third }.sorted()[scored.size / 2]
    return scored.filter { it.third >= gate }.sortedByDescending { it.second }.map { it.first }
}

/**
 * Each row paired with one of its figures, dropping the rows the table has no figure for.
 *
 * One pass with one lookup per row rather than a filter followed by a second lookup in the
 * comparator: this re-runs on every poll of a table with eight hundred rows in it, inside a
 * `remember` on the composition's thread, and a comparator that reads a map is a map read per
 * comparison rather than per row.
 */
private fun List<MarketSearchRow>.withFigure(
    tickers: MarketTickerStore.MarketTickerState,
    of: (MarketTicker) -> Double?,
): List<Pair<MarketSearchRow, Double>> = mapNotNull { row ->
    tickers[row.meta.symbol]?.let(of)?.let { row to it }
}

/** The day's figures for one row, or null where the table does not carry the symbol. */
internal fun MarketTickerStore.MarketTickerState.tickerFor(row: MarketSearchRow): MarketTicker? =
    this[row.meta.symbol]
