package com.coinepro.feature.heatmap

import com.coinepro.core.symbols.SymbolMeta
import com.coinepro.core.symbols.SymbolRanking

/**
 * One market, as the heatmap needs it.
 *
 * Almost every figure here is nullable and that is the honest shape of the data rather than
 * laziness. Neither backend puts a change, a high, a low or a volume on a quote — the snapshot
 * carries a symbol, a price, a bid, an ask and a timestamp, and that is the whole of it. So every
 * figure below except [price] comes from [HeatmapFacts], out of the platform's day table where
 * there is one and out of the market's own daily bars where there is not, and a market that has had
 * neither read yet answers null to all of them.
 *
 * ### What used to be wrong here, and why the shape changed
 *
 * These fields existed before anything wrote them. `HeatmapAssets` filled in [meta], [price] and a
 * [changePercent] taken straight off `MarketQuote.changePercent` — which is null on every quote
 * either feed has ever sent — and left the other eight at their defaults. The result was a map
 * where all five colour modes drew every tile the same neutral grey and all four sizings fell back
 * to the same ranking. It looked like a wall of names because it *was* a wall of names: there was
 * no second variable on the screen at all.
 *
 * A `marketCap` field is gone entirely rather than left unwritten. Nothing on either backend can
 * supply one, and a declared field that is never filled is how the first version of this ended up
 * shipping a control named after a quantity the app does not have.
 *
 * Null is load-bearing, not a placeholder: [HeatmapMetrics.valueOf] answers null so the tile draws
 * as *unknown* — hatched, and visibly not part of the ramp — rather than as neutral. A neutral
 * tile in a field of coloured ones reads as "this market did not move", and saying that about a
 * market nobody has read is the one failure a heatmap must not have.
 */
data class HeatmapAsset(
    /** Classification, display names and the base/quote split, from `core:symbols`. */
    val meta: SymbolMeta,
    val price: Double,
    /** Change against the previous daily close, in percent. The default colour metric. */
    val changePercent: Double? = null,
    /** Change over the window the reader chose, in percent. See [HeatmapPeriod]. */
    val periodPercent: Double? = null,
    /** The day's realised range, in percent of price. */
    val volatilityPercent: Double? = null,
    /** What [volatilityPercent] usually is for this instrument, so the excess has a sign. */
    val typicalVolatilityPercent: Double? = null,
    val openPrice: Double? = null,
    val previousClose: Double? = null,
    val dayHigh: Double? = null,
    val dayLow: Double? = null,
    /** Traded quantity over the day, in units of the base asset. Never interchangeable with below. */
    val volume: Double? = null,
    /** Traded value over the day, in the quote currency. The comparable one across a mixed list. */
    val turnover: Double? = null,
    /**
     * A perpetual's funding rate for the coming period, in percent.
     *
     * Present only where the feed carries a swap market, which is the perpetuals on TradeYar's day
     * table and nothing on CoinePro-FX — see [HeatmapTicker]. It is read in the detail sheet and by
     * nothing else, deliberately: a colour mode for a figure that exists on a tenth of the
     * catalogue would be a map that is nine-tenths hatched.
     */
    val fundingRatePercent: Double? = null,
    /**
     * Whether this market's own bars have been read.
     *
     * Distinct from "every figure is null", which is also true of a market whose bars arrived and
     * turned out to hold one candle. The screen counts these to tell the reader how much of the
     * map is real yet, and the counting has to mean "asked and answered" rather than "has numbers".
     */
    val resolved: Boolean = false,
) {
    /** The feed's own spelling, which is what goes back on the wire when a tile is tapped. */
    val symbol: String get() = meta.symbol

    /** What the tile is labelled with: the base alone for a coin, the pair for everything else. */
    val label: String get() = meta.short

    /** What the day's range spans, or null where the day has no range to speak of. */
    val dayRange: ClosedFloatingPointRange<Double>?
        get() {
            val high = dayHigh?.takeIf { it.isFinite() } ?: return null
            val low = dayLow?.takeIf { it.isFinite() } ?: return null
            return if (high >= low) low..high else null
        }
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
     * When the requested figure is missing the answer is derived from [SymbolRanking.rank], which
     * is this app's offline answer to "how large is this market" and already exists precisely
     * because the feeds cannot be asked. That is a fallback for *area* and not for colour, and the
     * asymmetry is deliberate: a rough area still tells the reader roughly the right thing about
     * importance, while a fabricated colour would tell them something specific and false about
     * price.
     *
     * Always strictly positive, so a market can never claim zero area and vanish from a map it is
     * listed on.
     */
    fun weightOf(asset: HeatmapAsset, size: HeatmapSize): Double {
        if (size == HeatmapSize.MONO) return 1.0
        val reported = when (size) {
            HeatmapSize.VOLUME -> asset.volume
            HeatmapSize.TURNOVER -> asset.turnover ?: asset.volume?.let { it * asset.price }
            HeatmapSize.LIQUIDITY, HeatmapSize.MONO -> null
        }
        val usable = reported?.takeIf { it.isFinite() && it > 0.0 }
        return usable ?: rankWeight(asset)
    }

    /**
     * The quantity the colour ramp plots, or null when this market cannot answer that question.
     *
     * Null is a real answer and the tile draws as unknown for it. The alternative — treating a
     * missing figure as zero, or borrowing a neighbouring metric — paints an unread market with
     * the same colour as a market that genuinely did not move, and those are not the same thing.
     */
    fun valueOf(asset: HeatmapAsset, colour: HeatmapColour): Double? = when (colour) {
        HeatmapColour.CHANGE -> asset.changePercent
        HeatmapColour.PERFORMANCE -> asset.periodPercent
        HeatmapColour.VOLATILITY -> volatilityExcess(asset)
        HeatmapColour.RANGE -> rangePosition(asset)
        HeatmapColour.GAP -> gap(asset)
    }?.takeIf { it.isFinite() }

    /**
     * Whether the map, as a whole, can answer this colour question at all.
     *
     * Asked of a whole list rather than of one market because the answer drives a control: a mode
     * nothing can answer is labelled as having no data in the settings sheet instead of sitting
     * there as a radio button that changes nothing. One market answering is enough to keep the
     * mode live — the rest draw as unknown, which is a fact about those markets and not about the
     * mode.
     */
    fun anyValueFor(assets: List<HeatmapAsset>, colour: HeatmapColour): Boolean =
        assets.any { valueOf(it, colour) != null }

    /**
     * Whether the map can size by this figure, as opposed to falling back to the ranking.
     *
     * [HeatmapSize.LIQUIDITY] and [HeatmapSize.MONO] need nothing and are always true. The other
     * two are true only where some market reports a traded quantity — which the MT5 forex side
     * never does, so on a forex-only catalogue both are honestly reported as unavailable rather
     * than quietly drawing the ranking under a volume label.
     */
    fun anyWeightFor(assets: List<HeatmapAsset>, size: HeatmapSize): Boolean = when (size) {
        HeatmapSize.LIQUIDITY, HeatmapSize.MONO -> true
        HeatmapSize.VOLUME -> assets.any { it.volume?.takeIf { v -> v.isFinite() && v > 0.0 } != null }
        HeatmapSize.TURNOVER -> assets.any {
            val turnover = it.turnover ?: it.volume?.times(it.price)
            turnover?.takeIf { t -> t.isFinite() && t > 0.0 } != null
        }
    }

    /**
     * The band the colour scale is held inside for each metric.
     *
     * They are not the same size and cannot be. A session change of twenty-five percent is extreme;
     * a position of twenty-five percent inside the day's range is unremarkable, and holding that
     * one to the same ceiling would saturate every tile on the map. See
     * [HeatmapColours.scaleFor] for what the band is for.
     */
    fun scaleBoundsOf(colour: HeatmapColour): Pair<Double, Double> = when (colour) {
        HeatmapColour.CHANGE -> 0.5 to 25.0
        // A quarter's move is a larger number than a day's by construction, so the same ceiling
        // would put every coin at the end of the ramp and make the mode a two-colour map.
        HeatmapColour.PERFORMANCE -> 1.0 to 60.0
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
     * How far the day's range sits above or below this instrument's own normal range.
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
