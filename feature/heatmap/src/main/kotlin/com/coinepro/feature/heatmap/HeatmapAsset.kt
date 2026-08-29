package com.coinepro.feature.heatmap

import com.coinepro.core.symbols.SymbolMeta
import com.coinepro.core.symbols.SymbolRanking

/**
 * One market, as the heatmap needs it.
 *
 * Almost every figure here is nullable and that is the honest shape of the data rather than
 * laziness. Neither backend sends a capitalisation, the MT5 side sends no volume at all, and the
 * socket feed sends a session high and low only for the symbols currently subscribed. A model that
 * demanded them would force whoever mapped it to invent numbers, and an invented area on a treemap
 * is a lie the reader has no way to detect — it looks exactly like a real one.
 *
 * So the map degrades instead: [HeatmapMetrics.weightOf] falls back to the catalogue's own
 * liquidity ranking when the requested figure is missing, and [HeatmapMetrics.valueOf] answers null
 * so the tile draws neutral. A neutral tile says "nothing known"; a fabricated one says something
 * false.
 */
data class HeatmapAsset(
    /** Classification, display names and the base/quote split, from `core:symbols`. */
    val meta: SymbolMeta,
    val price: Double,
    /** Session change, in percent. The default colour metric. */
    val changePercent: Double? = null,
    /** Change over the longer window the screen was opened on, in percent. */
    val periodPercent: Double? = null,
    /** Realised range over the window, in percent of price. */
    val volatilityPercent: Double? = null,
    /** What [volatilityPercent] usually is for this instrument, so the excess has a sign. */
    val typicalVolatilityPercent: Double? = null,
    val openPrice: Double? = null,
    val previousClose: Double? = null,
    val dayHigh: Double? = null,
    val dayLow: Double? = null,
    val marketCap: Double? = null,
    /** Twenty-four-hour traded quantity, in units of the base asset. */
    val volume: Double? = null,
    /** Twenty-four-hour traded value, in the quote currency. */
    val turnover: Double? = null,
) {
    /** The feed's own spelling, which is what goes back on the wire when a tile is tapped. */
    val symbol: String get() = meta.symbol

    /** What the tile is labelled with: the base alone for a coin, the pair for everything else. */
    val label: String get() = meta.short
}

/**
 * The two numbers a tile is made of: how big it is, and what colour it takes.
 *
 * Kept apart from both the layout and the drawing because this is where the product decisions are —
 * what a missing figure falls back to, and which quantity each colour mode actually plots — and
 * those are the things worth being able to test without a screen.
 */
object HeatmapMetrics {

    /**
     * How much area a market claims under a given sizing.
     *
     * When the requested figure is missing the answer is derived from
     * [SymbolRanking.rank], which is this app's offline answer to "how large is this market" and
     * already exists precisely because the feeds cannot be asked. It is coarse — it knows that
     * Bitcoin outweighs a mid-cap alt, not by how much — so the resulting map is a rough one, and
     * that is still better than either dropping the market or inventing a capitalisation for it.
     *
     * Always strictly positive, so a market can never claim zero area and vanish from a map it is
     * listed on.
     */
    fun weightOf(asset: HeatmapAsset, size: HeatmapSize): Double {
        if (size == HeatmapSize.MONO) return 1.0
        val reported = when (size) {
            HeatmapSize.MARKET_CAP -> asset.marketCap
            HeatmapSize.VOLUME -> asset.volume
            HeatmapSize.TURNOVER -> asset.turnover ?: asset.volume?.let { it * asset.price }
            HeatmapSize.MONO -> null
        }
        val usable = reported?.takeIf { it.isFinite() && it > 0.0 }
        return usable ?: rankWeight(asset)
    }

    /**
     * The quantity the colour ramp plots, or null when this market cannot answer that question.
     *
     * Null is a real answer and the tile draws neutral for it. The alternative — treating a missing
     * figure as zero — paints an unknown market with the same colour as a market that genuinely did
     * not move, and those are not the same thing.
     */
    fun valueOf(asset: HeatmapAsset, colour: HeatmapColour): Double? = when (colour) {
        HeatmapColour.CHANGE -> asset.changePercent
        HeatmapColour.PERFORMANCE -> asset.periodPercent ?: asset.changePercent
        HeatmapColour.VOLATILITY -> volatilityExcess(asset)
        HeatmapColour.RANGE -> rangePosition(asset)
        HeatmapColour.GAP -> gap(asset)
    }?.takeIf { it.isFinite() }

    /**
     * The band the colour scale is held inside for each metric.
     *
     * They are not the same size and cannot be. A session change of twenty-five percent is extreme;
     * a position of twenty-five percent inside the day's range is unremarkable, and holding that
     * one to the same ceiling would saturate every tile on the map. See
     * [HeatmapColours.scaleFor] for what the band is for.
     */
    fun scaleBoundsOf(colour: HeatmapColour): Pair<Double, Double> = when (colour) {
        HeatmapColour.CHANGE, HeatmapColour.PERFORMANCE -> 0.5 to 25.0
        HeatmapColour.VOLATILITY -> 0.2 to 10.0
        // Already a percentage *of the range*, so the full extent of the scale is the whole answer
        // and there is nothing to normalise away.
        HeatmapColour.RANGE -> 100.0 to 100.0
        HeatmapColour.GAP -> 0.2 to 10.0
    }

    /** The scale for a whole map, using the metric's own band. */
    fun scaleFor(values: List<Double>, colour: HeatmapColour): Double {
        val (floor, ceiling) = scaleBoundsOf(colour)
        return HeatmapColours.scaleFor(values, floor, ceiling)
    }

    /**
     * How far the window's range sits above or below this instrument's own normal range.
     *
     * Expressed in percentage points of price rather than as a multiple, so a quiet major and a
     * lively alt are compared on the same axis instead of the alt permanently reading as calm
     * because its normal is large.
     */
    private fun volatilityExcess(asset: HeatmapAsset): Double? {
        val actual = asset.volatilityPercent?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        val typical = asset.typicalVolatilityPercent?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        return actual - typical
    }

    /**
     * Where the last price sits in the session's range: -100 at the low, +100 at the high.
     *
     * A range of zero — a market that has not moved at all since the session opened, which happens
     * to a closed forex pair over a weekend — has no meaningful position inside it, so it answers
     * null rather than the midpoint. The midpoint would be a claim.
     */
    private fun rangePosition(asset: HeatmapAsset): Double? {
        val high = asset.dayHigh?.takeIf { it.isFinite() } ?: return null
        val low = asset.dayLow?.takeIf { it.isFinite() } ?: return null
        val span = high - low
        if (span <= 0.0) return null
        return ((asset.price - low) / span * 2.0 - 1.0) * 100.0
    }

    /** The opening gap against the previous close, in percent. */
    private fun gap(asset: HeatmapAsset): Double? {
        val open = asset.openPrice?.takeIf { it.isFinite() } ?: return null
        val close = asset.previousClose?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        return (open - close) / close * 100.0
    }

    /**
     * A weight from the offline liquidity ranking.
     *
     * The reciprocal rather than the rank itself, because a treemap wants "how much" and the rank
     * is "which place". An unranked market — anything outside the majors the app knows by name —
     * takes the floor rather than zero: it still gets a tile, just the smallest one.
     */
    private fun rankWeight(asset: HeatmapAsset): Double {
        val rank = SymbolRanking.rank(asset.meta)
        val place = if (rank == SymbolRanking.UNRANKED) UNRANKED_PLACE else rank
        return 1.0 / (1.0 + place.toDouble())
    }

    /** Behind every ranked major, and shared by all of them so their order stays the feed's. */
    private const val UNRANKED_PLACE = 1024
}
