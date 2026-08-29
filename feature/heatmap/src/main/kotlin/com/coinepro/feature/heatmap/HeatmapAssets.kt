package com.coinepro.feature.heatmap

import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.symbols.SymbolArtwork

/**
 * The catalogue rows the market screens already hold, as heatmap input.
 *
 * Two filters, and both are the product's rules rather than conveniences.
 *
 * A row with no quote is dropped: a market the feed has not priced has no change to colour and no
 * size to claim, and a grey square sitting among two hundred live ones reads as a rendering fault.
 *
 * A row with no artwork is dropped as well, which is this app's standing rule — see
 * [SymbolArtwork]. The catalogue gateway already applies it, so on the normal path this filter
 * removes nothing; it is here because a heatmap can also be handed a list assembled somewhere else,
 * and the rule is that the filter is applied wherever a list is built, not once and hopefully.
 *
 * Everything a tile could be sized by is left null. Neither backend sends a capitalisation or a
 * per-symbol volume with the catalogue, so [HeatmapMetrics.weightOf] falls back to the offline
 * liquidity ranking. A caller that does have real figures — a venue endpoint that returns
 * twenty-four-hour statistics, say — should build [HeatmapAsset] itself and fill them in, and the
 * map becomes proportional rather than ranked without anything else changing.
 */
fun heatmapAssetsFrom(rows: List<MarketSearchRow>): List<HeatmapAsset> = rows.mapNotNull { row ->
    val quote = row.quote ?: return@mapNotNull null
    if (!quote.price.isFinite() || quote.price <= 0.0) return@mapNotNull null
    if (!SymbolArtwork.covers(row.meta)) return@mapNotNull null
    HeatmapAsset(
        meta = row.meta,
        price = quote.price,
        changePercent = quote.changePercent,
    )
}
