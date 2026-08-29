package com.coinepro.feature.heatmap

import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe

/**
 * Where a market's daily bars come from.
 *
 * An interface rather than a `CandleGateway` in [HeatmapController]'s constructor, for two reasons
 * that are both about keeping this module honest.
 *
 * The first is testability: the map's whole subject is what happens to a tile when the figures
 * behind it are absent, partial or nonsense, and a test that wants to prove a market with one bar
 * of history draws as unknown should not have to fake a paging protocol and a venue name to say so.
 *
 * The second is that this module does not own its own wiring. `core:marketdata` builds the two real
 * gateways — the crypto one is a plain mobile route, the forex one needs a second token minted from
 * the mobile one — and the heatmap asks a much smaller question than the chart does. Naming that
 * question in its own type keeps the answer to "what does the map need" one line long, which is
 * what makes it possible to hand it in from the app with a single argument.
 */
fun interface HeatmapBarSource {
    /**
     * Daily bars for [symbol], oldest first, or an empty list where they cannot be had.
     *
     * Empty rather than an exception, and never a throw: a symbol one backend does not carry is an
     * ordinary outcome on a mixed catalogue, and a map that stopped resolving because one market
     * answered 404 would fill in halfway and then never finish.
     */
    suspend fun bars(symbol: String): List<OhlcBar>
}

/**
 * [HeatmapBarSource] over the app's own candle gateway.
 *
 * Daily bars, because the period modes measure in daily closes and the typical daily range is a
 * median over daily bars. The day's own figures do **not** have to come from here — see
 * [HeatmapTickerSource], which answers them for the whole catalogue in one call once the route
 * exists — but until it does, this is where every figure on the map comes from.
 *
 * [LIMIT] is chosen from the longest window the map offers rather than from a round number: a
 * ninety-day performance reading needs ninety-one bars to have a reference at all, and the typical
 * daily range is a median over the thirty closed days before today. A hundred and twenty covers
 * both with room for the weekends a forex series does not contain, and is still a small response.
 */
class CandleHeatmapBarSource(
    private val gateway: CandleGateway,
    private val timeframe: Timeframe = Timeframe.D1,
    private val limit: Int = LIMIT,
) : HeatmapBarSource {

    override suspend fun bars(symbol: String): List<OhlcBar> =
        runCatching { gateway.load(symbol, timeframe, limit = limit).candles }.getOrDefault(emptyList())

    private companion object {
        const val LIMIT = 120
    }
}

/**
 * One market's own twenty-four-hour statistics, as a venue reports them.
 *
 * ### Why this type exists before the route does
 *
 * Every figure here is available upstream today. LBank publishes the whole spot catalogue's
 * twenty-four-hour statistics in a single call, and the same again for its perpetuals with a
 * funding rate attached; TradeYar already relays that venue for the ticker, the candles and the
 * depth book, so it is a route they can add without a new upstream integration. What is missing is
 * the relay, not the data — and the exact ask is recorded in this module's `## SERVER ASKS`.
 *
 * So the module is shaped for it now. The day that route ships, the map's day figures stop being a
 * hundred and twenty candle requests and become one call for the entire catalogue, and nothing
 * above this interface changes: [HeatmapFacts] already prefers a ticker over a bar wherever both
 * can answer, and [HeatmapController] already asks for one if it is given a source.
 *
 * Building the interface after the route would mean a rewrite of the resolution path at the moment
 * the route lands, which is the same as saying the feature would stay broken for one release longer
 * than it had to.
 *
 * Every field is nullable because a relay may carry some and not others, and a partially-filled
 * ticker is worth more than none: a change with no volume still colours the map.
 */
data class HeatmapTicker(
    val symbol: String,
    /** The venue's own last price, used only where the live socket has nothing for this market. */
    val lastPrice: Double? = null,
    /** Percentage change over the venue's rolling twenty-four hours. */
    val changePercent: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val open: Double? = null,
    /** The close the change is measured from, where the venue reports it separately from [open]. */
    val previousClose: Double? = null,
    /** Traded quantity over the window, in units of the base asset. */
    val volume: Double? = null,
    /** Traded value over the window, in the quote currency. */
    val turnover: Double? = null,
    /**
     * The perpetual's funding rate for the coming period, in percent.
     *
     * Only ever present for a swap market, which is why it is last and nullable. It is read in the
     * detail sheet and nowhere else: it is not a colour mode, because a map coloured by a figure
     * that exists for a tenth of the catalogue would be a map that is nine-tenths hatched, and
     * because adding a control for data no backend serves yet is precisely the fault this rework
     * removed.
     */
    val fundingRatePercent: Double? = null,
)

/**
 * Twenty-four-hour statistics for the whole catalogue, in one call.
 *
 * Deliberately a batch and not a per-symbol lookup. The upstream route answers the entire venue at
 * once, and an interface that asked symbol by symbol would throw that away and reintroduce the
 * per-market cost this exists to remove — the mistake would be invisible, because it would still
 * work.
 */
fun interface HeatmapTickerSource {
    /**
     * Every market the venue has statistics for, keyed by the feed's own spelling of the symbol.
     *
     * Empty rather than an exception, for the reason [HeatmapBarSource.bars] gives: the map must
     * degrade to whatever it can still derive from bars rather than stop.
     */
    suspend fun tickers(): Map<String, HeatmapTicker>
}
