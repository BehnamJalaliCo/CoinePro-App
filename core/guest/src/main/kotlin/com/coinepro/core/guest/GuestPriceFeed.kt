package com.coinepro.core.guest

import com.coinepro.core.common.AppResult
import com.coinepro.core.marketdata.ChartTickSource
import com.coinepro.core.marketdata.PriceTick
import com.coinepro.core.model.Instrument
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.QuoteSource
import com.coinepro.core.symbols.SymbolArtwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A live price for a reader with no account.
 *
 * ### What was missing
 *
 * Two of the faults reported against 4.32.1 are the same fault seen from different screens: prices
 * that do not move, and «تایم‌فریم ۱۰ ثانیه تا ۵۰ ثانیه کار نمی‌کند». Both are true of a *guest*
 * build and neither is true of a signed-in one, because the guest shell has no live feed at all —
 * `MarketDataController` is the signed-in socket, and what a guest was handed instead was a
 * catalogue read once when the screen opened. A sub-minute bar is built entirely out of ticks (see
 * `ChartInterval.Seconds`), so with no ticks a ten-second chart is not slow: it is permanently
 * blank, and will be for as long as the reader stares at it.
 *
 * ### Why this is a poll and the signed-in one is a socket
 *
 * TradeYar publishes `api/v1/public/prices` and no public socket. A poll is therefore the whole of
 * what can honestly be offered here, and the interval is the fastest that route is worth asking:
 * [FOCUSED_MILLIS] while a chart is open, because that is the screen where a price standing still
 * for a second is visible, and [LISTED_MILLIS] for a list of rows, where it is not. **Nothing polls
 * when nothing is on screen** — the loop exists only while some screen has named symbols.
 *
 * The honest limit is worth writing down rather than hiding: this is a second of latency, not the
 * socket's few milliseconds. It is the difference between a chart that moves and a chart that does
 * not, and an account is what buys the rest.
 *
 * ### A repeated snapshot is not a tick
 *
 * The route answers from a cache, so two polls a second apart very often carry the identical price.
 * A quote whose price and 24-hour move are unchanged keeps its **previous instant**, so it is equal
 * to the one before it and every collector downstream — the chart's tick fold, the list's row patch
 * — sees nothing new. Stamping each poll with the clock would turn a flat market into a stream of
 * fake ticks, which on a ten-second chart draws a row of identical candles that never traded.
 */
class GuestPriceFeed(
    private val gateway: GuestGateway,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

    private val _quotes = MutableStateFlow<Map<String, MarketQuote>>(emptyMap())

    /** Every price this feed holds, keyed by ticker in upper case. */
    val quotes: StateFlow<Map<String, MarketQuote>> = _quotes.asStateFlow()

    /** What a list on screen has asked for. */
    private var listed: List<String> = emptyList()

    /** The one symbol a chart is drawing, which is carried whatever the lists ask for. */
    private var focused: String? = null

    /** How many chart tick collectors are attached; above zero the poll runs at its fast rate. */
    private var charts = 0

    private var job: Job? = null

    /** [listed] plus [focused], which is what the route is actually asked for. */
    private val wanted: List<String>
        get() = focused
            ?.takeIf { it.isNotEmpty() && it !in listed }
            ?.let { listed + it }
            ?: listed

    /** Name the symbols a screen is showing. An empty set stops the poll. */
    fun subscribe(symbols: Collection<String>) {
        val next = symbols.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct().sorted()
        if (next == listed) return
        listed = next
        restart()
    }

    /** Also carry this one symbol, because it is the chart the reader is looking at. */
    fun focus(symbol: String?) {
        val next = symbol?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        if (next == focused) return
        focused = next
        restart()
    }

    /**
     * This feed as the chart's ticks.
     *
     * The symbol is focused for as long as something is collecting, and released when nothing is —
     * unlike the signed-in socket, which keeps its focus. The difference is the cost: a socket that
     * carries one extra symbol costs nothing, and a poll that keeps asking for a chart nobody is
     * looking at is a request a second, forever.
     */
    fun chartTicks(): ChartTickSource = ChartTickSource { symbol ->
        val key = symbol.trim().uppercase()
        quotes
            .map { it[key] }
            .onStart {
                focus(key)
                charts += 1
                restart()
            }
            .onCompletion {
                charts = (charts - 1).coerceAtLeast(0)
                if (charts == 0) focus(null) else restart()
            }
            .mapNotNull { quote -> quote?.takeIf { it.price.isFinite() && it.price > 0.0 } }
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

    private fun restart() {
        job?.cancel()
        job = null
        val symbols = wanted
        if (symbols.isEmpty()) return
        val period = if (charts > 0) FOCUSED_MILLIS else LISTED_MILLIS
        job = scope.launch {
            while (isActive) {
                poll(symbols)
                delay(period)
            }
        }
    }

    private suspend fun poll(symbols: List<String>) {
        val answer = runCatching { gateway.prices(symbols) }.getOrNull() ?: return
        val prices = (answer as? AppResult.Success)?.value ?: return
        val at = nowMillis()
        _quotes.value = buildMap {
            putAll(_quotes.value)
            prices.quotes.forEach { row ->
                // The same filter the catalogue applies. A market with no artwork never reaches a
                // list here either — see the house rule, and `GuestMarketCatalogGateway`.
                if (!SymbolArtwork.covers(row.symbol)) return@forEach
                val key = row.symbol.uppercase()
                val previous = get(key)
                val unchanged = previous != null &&
                    previous.price == row.price &&
                    previous.changePercent == row.changePercent24h
                if (unchanged) return@forEach
                put(
                    key,
                    MarketQuote(
                        instrument = Instrument(
                            symbol = row.symbol,
                            displayName = row.symbol,
                            marketType = MarketType.CRYPTO,
                        ),
                        price = row.price,
                        changePercent = row.changePercent24h,
                        timestampEpochMillis = at,
                        source = QuoteSource.LBANK,
                        // The server's verdict on the whole snapshot, applied per row: it has no
                        // per-symbol freshness to give and inventing one would be a claim it never
                        // made.
                        isStale = prices.stale,
                    ),
                )
            }
        }
    }

    private companion object {
        /** While a chart is open. One second is what this route is worth asking. */
        const val FOCUSED_MILLIS = 1_000L

        /** For a list of rows, where a second of latency is not visible. */
        const val LISTED_MILLIS = 3_000L
    }
}
