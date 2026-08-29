package com.coinepro.feature.heatmap

import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.MarketTicker
import com.coinepro.core.marketdata.MarketTickerStore
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import kotlinx.coroutines.flow.first

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
 * median over daily bars. The day's own figures do **not** come from here any more where a
 * [HeatmapTickerSource] is wired: that answers them for the whole catalogue in one call, and what
 * is left for this source is the two figures a rolling twenty-four-hour window structurally cannot
 * carry — the multi-week period return and the median daily range. On CoinePro-FX, which has no
 * such route, this is still where every figure on the map comes from.
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
 * ### This type existed before the route did, and that is why the route cost one line to adopt
 *
 * It was written against a relay that had not been built, on the reasoning that the data was
 * already at the venue and only the route was missing. TradeYar has since built it —
 * `GET /api/mobile/v1/market/tickers`, the whole catalogue in one request behind a five-second
 * cache — and adopting it turned out to be exactly what was predicted: [MarketTickerHeatmapSource]
 * maps one row onto one of these, and nothing above this type changed. [HeatmapFacts] already
 * preferred a ticker over a bar wherever both can answer, and [HeatmapController] already asked for
 * one if it was given a source.
 *
 * CoinePro-FX still has no equivalent, so a map on that platform draws from bars exactly as it did
 * before, and that is the reason this remains an interface with two possible answers rather than a
 * gateway call.
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

/**
 * [HeatmapTickerSource] over the shared [MarketTickerStore].
 *
 * ### Why it waits for the store's *next* table rather than reading the one it is holding
 *
 * The store polls only while a screen is holding it, so the table sitting in its state is as old as
 * whenever the last reader let go — a minute, an hour, or the last time the app was opened. Drawing
 * a map from that would be indistinguishable on screen from drawing it from a table fetched a
 * second ago, and a stale change percent that looks live is the exact failure this rework exists to
 * remove. [MarketTickerStore.start] loads immediately when nothing else is holding the store, so in
 * the ordinary case this is one round trip; when another screen is already reading it the wait is
 * that store's own poll interval, and the answer could not have been fresher than that anyway.
 *
 * ### The start/stop pair is balanced around the wait, not around the screen
 *
 * The store is reference counted, and this source is asked once per map open rather than subscribed
 * to. Raising the count and not lowering it would leave a five-second poll running against the
 * whole catalogue for the rest of the process, on somebody's mobile data, for a map they have
 * already navigated away from. `finally` rather than a plain call, so the count comes down when the
 * screen is closed mid-wait — which is the common case on a slow connection.
 */
class MarketTickerHeatmapSource(private val store: MarketTickerStore) : HeatmapTickerSource {

    override suspend fun tickers(): Map<String, HeatmapTicker> {
        // Empty rather than a wait, and this is the whole reason the store carries the flag:
        // CoinePro-FX has no such route, so a map on that platform has to go straight back to its
        // candles instead of hanging on a table that is never going to arrive.
        if (!store.supported) return emptyMap()
        val held = store.state.value.table
        store.start()
        val table = try {
            store.state.first { it.table !== held }.table
        } finally {
            store.stop()
        }
        return table.tickers.mapValues { (_, ticker) -> ticker.asHeatmapTicker() }
    }
}

/**
 * One row of the day's table as the map's own ticker.
 *
 * Two fields are deliberately left absent rather than filled from something that resembles them.
 *
 * `open_24h` is the price twenty-four rolling hours ago; [HeatmapTicker.open] is a session's opening
 * print, and [HeatmapColour.GAP] measures that against the previous daily close. Handing the rolling
 * figure to it would turn a mode that correctly reads near zero across the crypto block — because
 * crypto does not close — into a small non-zero number on every tile, under a label that says gap.
 * The venue's own change percent is carried, so the reference is not needed for anything else.
 *
 * There is no previous close on this route at all, which is why [HeatmapTicker.previousClose] stays
 * null and [HeatmapFacts] falls through to the bar for it.
 *
 * The funding rate arrives as a fraction — the server's own sample body carries `0.00009263` on
 * BTCUSDT, which is nine thousandths of a percent — and the field it lands in is named for a
 * percentage. Scaling it here rather than where it is drawn means the detail sheet is not the place
 * somebody has to remember.
 */
private fun MarketTicker.asHeatmapTicker(): HeatmapTicker = HeatmapTicker(
    symbol = symbol,
    lastPrice = last,
    changePercent = changePercent24h,
    high = high24h,
    low = low24h,
    volume = volume24h,
    turnover = turnover24h,
    fundingRatePercent = fundingRate?.times(100.0),
)
