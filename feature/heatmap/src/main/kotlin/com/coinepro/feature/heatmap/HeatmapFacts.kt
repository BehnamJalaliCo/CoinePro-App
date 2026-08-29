package com.coinepro.feature.heatmap

import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.symbols.SymbolMeta
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a market's live price and its daily bars into the figures a tile is drawn from.
 *
 * ### The snapshot carries none of it, and that is why there are two sources here
 *
 * Both backends answer the snapshot endpoint with a symbol, a price, a bid, an ask and a timestamp.
 * There is no change percent, no day's high, no low and no volume anywhere in either response —
 * `MarketQuote.changePercent` is hard-coded null in `MarketDataController` because neither feed's
 * quote object has a field to fill it from. For a long time the only answer available to this app
 * was the market's own bars, at one candle request per market, and `feature/screener` was shaped
 * around the same constraint in the same words.
 *
 * TradeYar has since built the day's table — the whole catalogue's open, high, low, change, volume
 * and turnover in one request — so on that platform the figures below are the venue's own and cost
 * one call for every tile on the map together. CoinePro-FX still has no such route, so there the
 * bars are still the only source and the per-market cost is still real. [HeatmapController] is
 * where it is bounded.
 *
 * ### Two sources, and the venue wins
 *
 * A [HeatmapTicker] is preferred over the bars for every figure both can answer, and that ordering
 * is not a performance choice. The venue's own twenty-four-hour window is what the exchange's site
 * prints and what a reader will hold this map against; a change derived from two daily bars is
 * measured from a midnight boundary the venue does not use, so the two disagree by a few tenths on
 * a quiet day and by more than that on a loud one. Where the venue has spoken, the venue is right.
 *
 * The merge was written a release before the route it expects — see [HeatmapTickerSource] — and
 * nothing here changed when it landed. Bars keep the two figures a twenty-four-hour ticker
 * structurally cannot carry: the multi-week period return, and the median daily range that gives
 * volatility its sign.
 *
 * ### The live price wins over the bar's close, and widens the bar
 *
 * The last daily bar is open — its close is the price as of whenever the page was served, and the
 * live socket is ahead of it. Taking the bar's close would colour a tile from a figure that
 * disagrees with the price the detail sheet shows for the same market one tap later.
 *
 * For the same reason the high and the low are widened by the live price rather than taken from the
 * bar alone: a market trading above its recorded high is not at a hundred and ten percent of its
 * range, it is making a new high, and [HeatmapColour.RANGE] must read at the top of the scale
 * rather than off the end of it.
 */
object HeatmapFacts {

    /**
     * One market's figures, or null where there is no price at all.
     *
     * Null rather than a zero-priced asset: a market with no price has no tile, because area and
     * colour would both be inventions. That is the same rule `HeatmapAssets` applies to a catalogue
     * row with no quote.
     *
     * @param bars oldest first, as every gateway in this app returns them. Empty is the ordinary
     *   state of a market whose candles have not come back yet: the result carries the price, marks
     *   itself unresolved, and answers null to every colour question.
     */
    fun assetOf(
        meta: SymbolMeta,
        quote: MarketQuote?,
        bars: List<OhlcBar> = emptyList(),
        ticker: HeatmapTicker? = null,
        period: HeatmapPeriod = HeatmapPeriod.MONTH,
        resolved: Boolean = bars.isNotEmpty() || ticker != null,
    ): HeatmapAsset? {
        val last = bars.lastOrNull()
        val price = quote?.price?.takeIf { it.isFinite() && it > 0.0 }
            ?: ticker?.lastPrice?.positive()
            ?: last?.c?.takeIf { it.isFinite() && it > 0.0 }
            ?: return null

        // The close before the one in progress. Its absence — a market with exactly one bar of
        // history — falls back to the current bar's own open, which is what "today's move" means
        // when there is no yesterday to compare against.
        val barPreviousClose = bars.getOrNull(bars.size - 2)?.c?.positive()
        val open = ticker?.open?.positive() ?: last?.o?.positive()
        val previousClose = ticker?.previousClose?.positive() ?: barPreviousClose
        val reference = previousClose ?: open

        // Widened by the live price in both directions: a market trading above its recorded high is
        // making a new high, not sitting past the end of its own range.
        //
        // Null when neither source reported a range, and that guard is load-bearing. Without it a
        // market with no data at all takes the live price as both its high and its low, which is a
        // range of exactly zero — and a zero range is not "unknown", it is the specific claim that
        // the price has not moved all day. That is the lie this whole rework exists to remove, and
        // it would have crept back in through arithmetic rather than through a field.
        val reportedHigh = (ticker?.high ?: last?.h)?.takeIf { it.isFinite() }
        val reportedLow = (ticker?.low ?: last?.l)?.takeIf { it.isFinite() }
        val high = reportedHigh?.let { max(it, price) }
        val low = reportedLow?.let { min(it, price) }

        // Zero is what the MT5 side sends when it has no volume to report, and it is not a claim
        // that nothing traded. Treated as unknown, so a volume-sized map falls back to the ranking
        // for that market rather than shrinking the entire forex catalogue to nothing.
        val volume = ticker?.volume?.positive() ?: last?.v?.positive()
        val turnover = ticker?.turnover?.positive() ?: barTurnover(last, volume)

        return HeatmapAsset(
            meta = meta,
            price = price,
            changePercent = ticker?.changePercent?.takeIf { it.isFinite() }
                ?: reference?.let { percentFrom(it, price) },
            periodPercent = periodPercent(bars, price, period),
            volatilityPercent = dayRangePercent(high, low, price),
            typicalVolatilityPercent = typicalRangePercent(bars),
            openPrice = open,
            previousClose = previousClose,
            dayHigh = high,
            dayLow = low,
            volume = volume,
            turnover = turnover,
            fundingRatePercent = ticker?.fundingRatePercent?.takeIf { it.isFinite() },
            resolved = resolved,
        )
    }

    /** Quantity at the bar's typical price, which is the closest a candle gets to a traded value. */
    private fun barTurnover(last: OhlcBar?, volume: Double?): Double? {
        val bar = last ?: return null
        val quantity = volume ?: return null
        val typicalPrice = (bar.h + bar.l + bar.c) / 3.0
        return typicalPrice.takeIf { it.isFinite() && it > 0.0 }?.times(quantity)
    }

    /** A figure is a figure only if it is finite and above zero. Used often enough to name. */
    private fun Double.positive(): Double? = takeIf { it.isFinite() && it > 0.0 }

    /**
     * The move over [period]'s worth of bars, measured from a close rather than from an open.
     *
     * Close to price rather than open to price, because the opening print of a bar thirty sessions
     * ago is a single tick at a moment nobody was watching, while its close is where the market
     * agreed to leave it. Every terminal quotes a period return the same way.
     *
     * Null when the series is shorter than the window. A shorter series would still produce a
     * number — the move since whenever the history happens to start — and that number would be
     * labelled "three months" on a market listed six weeks ago.
     */
    private fun periodPercent(bars: List<OhlcBar>, price: Double, period: HeatmapPeriod): Double? {
        val index = bars.size - 1 - period.bars
        if (index < 0) return null
        val reference = bars[index].c.takeIf { it.isFinite() && it > 0.0 } ?: return null
        return percentFrom(reference, price)
    }

    /** The day's high-low span as a percentage of price, which is what makes markets comparable. */
    private fun dayRangePercent(high: Double?, low: Double?, price: Double): Double? {
        val top = high ?: return null
        val bottom = low ?: return null
        if (price <= 0.0) return null
        return ((top - bottom) / price * 100.0).takeIf { it.isFinite() && it >= 0.0 }
    }

    /**
     * What this instrument's daily range usually is, as the **median** of its recent closed bars.
     *
     * The median rather than the mean, and that choice is the whole point of the figure. A mean is
     * moved by exactly the days a volatility map exists to find — one gap, one liquidation cascade,
     * one central bank morning — so a market that had a violent week would report a large "normal"
     * and then read as calm through the rest of it. The median says what an ordinary day looks like
     * for this market, which is the thing today is being compared against.
     *
     * The bar in progress is excluded: it is the numerator of that comparison, and including it in
     * its own baseline pulls the answer toward zero.
     */
    private fun typicalRangePercent(bars: List<OhlcBar>): Double? {
        if (bars.size < MIN_TYPICAL_BARS + 1) return null
        val closed = bars.subList(max(0, bars.size - 1 - TYPICAL_WINDOW), bars.size - 1)
        val ranges = closed.mapNotNull { bar ->
            val close = bar.c.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
            ((bar.h - bar.l) / close * 100.0).takeIf { it.isFinite() && it >= 0.0 }
        }.sorted()
        if (ranges.size < MIN_TYPICAL_BARS) return null
        return ranges[ranges.size / 2].takeIf { it > 0.0 }
    }

    private fun percentFrom(reference: Double, price: Double): Double? =
        ((price - reference) / reference * 100.0).takeIf { it.isFinite() }

    /**
     * How many closed days a "normal" is taken over.
     *
     * A month of sessions: long enough that one loud day cannot be the median, short enough that
     * the answer still describes the regime the market is in now rather than the one it was in last
     * spring.
     */
    private const val TYPICAL_WINDOW = 30

    /**
     * Below this many usable days there is no normal to speak of.
     *
     * A median of three numbers is one of those three numbers, and calling it "what this market
     * usually does" would be a claim built on a week.
     */
    private const val MIN_TYPICAL_BARS = 10
}
