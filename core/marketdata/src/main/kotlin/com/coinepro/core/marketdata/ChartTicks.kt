package com.coinepro.core.marketdata

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/**
 * One price, at one instant, for one market.
 *
 * Unix **seconds**, matching [OhlcBar.t] and every bucket boundary in this module, because the one
 * arithmetic that must never go wrong here is deciding which bar a tick belongs to. The feed reports
 * milliseconds; the conversion happens once, in [chartTicks], rather than at each of the places a
 * tick is compared against a bar's open time.
 */
data class PriceTick(
    val symbol: String,
    val price: Double,
    val epochSeconds: Long,
)

/**
 * Where a chart gets its live prices, as opposed to its history.
 *
 * ### Why this exists at all
 *
 * «قیمت تیک لحظه‌ای نداره و حتی روی یک دقیقه کندل‌ها لایو نیستند.» The chart's only live path was a
 * poll of the *candles* endpoint — one request every few seconds, returning bars a server had
 * already folded. That is not a tick: between two polls the last candle simply does not move, and on
 * the minute chart, where a bar is sixty seconds and the poll was five, the reader watched a candle
 * stand still eleven times out of twelve. It is also the only thing a sub-minute bar could ever have
 * been built from, and no server this app talks to serves one.
 *
 * A price socket is already running in this module for the watchlist. This is the seam that lets the
 * chart read it without the chart knowing what a socket is: an interface with one method, so
 * `ChartController` takes a source rather than a controller, and a test hands it a flow it drives by
 * hand.
 *
 * ### It is a hot feed, and a slow collector must not hold it up
 *
 * Every implementation here is derived from a `StateFlow`, which conflates by construction: a
 * collector that is busy folding one tick into five thousand bars misses the ticks in between and
 * resumes at the newest, which is exactly the right behaviour for a price. Nothing queues, nothing
 * backs up, and the chart is never drawing a price the market has already left behind.
 */
fun interface ChartTickSource {
    /** Prices for [symbol] as they arrive. Never completes; empty while the feed has none. */
    fun ticks(symbol: String): Flow<PriceTick>
}

/** A source that never ticks: the default, and what a preview or a screenshot render gets. */
val NoChartTicks = ChartTickSource { kotlinx.coroutines.flow.emptyFlow() }

/**
 * The live feed this app already runs, as a chart tick source.
 *
 * Two things happen per subscription. The symbol is [MarketDataController.focus]ed, so the socket
 * carries it even when no list on screen has asked for it — a chart of an unstarred market got no
 * ticks at all before this. And the controller's state is narrowed to that one quote, with
 * [distinctUntilChanged] so a snapshot refresh that repeats a price does not count as a tick.
 *
 * The focus is **not** cleared when the flow is cancelled. It is a single slot that the next chart
 * overwrites, and clearing it on cancellation would race the next chart's claim during a symbol
 * switch — the old collector's teardown landing after the new one's `focus`, leaving the feed
 * carrying neither. See [MarketDataController.focused].
 */
fun MarketDataController.chartTicks(): ChartTickSource = ChartTickSource { symbol ->
    val key = symbol.trim().uppercase()
    focus(key)
    state
        .map { it.quotes[key] }
        .mapNotNull { quote ->
            quote?.takeIf { it.price.isFinite() && it.price > 0.0 }
        }
        // A quote carries a timestamp as well as a price, and both matter: a market that trades
        // twice at the same price has ticked twice, and a bar that has since opened needs the
        // second one to know it started at that price rather than staying empty.
        .distinctUntilChanged { old, new ->
            old.price == new.price && old.timestampEpochMillis == new.timestampEpochMillis
        }
        .map { quote ->
            PriceTick(
                symbol = key,
                price = quote.price,
                epochSeconds = quote.timestampEpochMillis / 1_000L,
            )
        }
}
