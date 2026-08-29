package com.coinepro.feature.heatmap

import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.symbols.SymbolArtwork

/**
 * The catalogue rows the market screens already hold, plus whatever bars have arrived, as heatmap
 * input.
 *
 * Two filters, and both are the product's rules rather than conveniences.
 *
 * A row with no quote is dropped: a market the feed has not priced has no size to claim and no
 * price to measure a move from, and a square with nothing behind it at all is not a market the
 * reader can act on.
 *
 * A row with no artwork is dropped as well, which is this app's standing rule — see
 * [SymbolArtwork]. The catalogue gateway already applies it, so on the normal path this filter
 * removes nothing; it is here because a heatmap can also be handed a list assembled somewhere else,
 * and the rule is that the filter is applied wherever a list is built, not once and hopefully.
 *
 * ### What changed, and why it matters more than it looks
 *
 * This function used to read `quote.changePercent` and stop. That field is null on every quote
 * either backend has ever sent, so every asset it produced carried a price and nothing else, and
 * the map that came out of it was two hundred grey squares with names on them. Every figure now
 * comes from [HeatmapFacts] over the market's own daily bars, and a market whose bars have not
 * arrived is marked unresolved rather than being quietly presented as a market that did not move.
 *
 * @param bars daily bars by symbol, oldest first. Empty is the ordinary first state: the map draws
 *   immediately, entirely unknown, and fills in as the candles land.
 * @param tickers the venue's own twenty-four-hour statistics, where a route for them exists. Wins
 *   over the bars wherever both can answer — see [HeatmapFacts].
 * @param resolved symbols that have been asked about, whether or not the answer had anything in it.
 *   Distinct from the keys of [bars]: a market that answered with nothing has still been read, and
 *   the coverage line above the map must not count it as pending forever.
 */
fun heatmapAssetsFrom(
    rows: List<MarketSearchRow>,
    bars: Map<String, List<OhlcBar>> = emptyMap(),
    tickers: Map<String, HeatmapTicker> = emptyMap(),
    period: HeatmapPeriod = HeatmapPeriod.MONTH,
    resolved: Set<String> = bars.keys + tickers.keys,
): List<HeatmapAsset> = rows.mapNotNull { row ->
    if (!SymbolArtwork.covers(row.meta)) return@mapNotNull null
    HeatmapFacts.assetOf(
        meta = row.meta,
        quote = row.quote,
        bars = bars[row.meta.symbol].orEmpty(),
        ticker = tickers[row.meta.symbol],
        period = period,
        resolved = row.meta.symbol in resolved,
    )
}
