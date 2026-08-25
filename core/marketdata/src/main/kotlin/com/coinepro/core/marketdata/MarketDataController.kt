package com.coinepro.core.marketdata

import com.coinepro.core.model.Instrument
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.QuoteSource
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.Retrofit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

private const val REALTIME_CACHE_WRITE_INTERVAL_MS = 30_000L

class MarketDataController(
    retrofit: Retrofit,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val platform: MarketPlatform,
    private val symbols: List<String> = MarketDataSymbols.forPlatform(platform),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val cache: MarketDataCache = NoOpMarketDataCache,
) {
    private fun MarketQuote.belongsToPlatform(): Boolean =
        instrument.marketType == platform.marketType

    private val api = retrofit.create(MarketDataApi::class.java)
    private val parser = MarketWireParser()
    private val baseUrl = retrofit.baseUrl()
    private val started = AtomicBoolean(false)
    private val _state = MutableStateFlow(MarketDataState())

    val state: StateFlow<MarketDataState> = _state.asStateFlow()

    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var monitorJob: Job? = null
    private var reconnectAttempt = 0
    private var lastCacheWriteEpochMillis = 0L

    fun start() {
        if (!started.compareAndSet(false, true)) return
        _state.update { it.copy(connection = MarketConnectionState.CONNECTING, lastError = null) }
        scope.launch {
            restoreCacheIfNeeded()
            refreshSnapshot()
        }
        connectWebSocket()
        monitorJob = scope.launch {
            var fallbackTick = 0
            while (started.get()) {
                delay(5_000)
                refreshFreshness()
                if (_state.value.connection != MarketConnectionState.LIVE) {
                    fallbackTick++
                    if (fallbackTick % 2 == 0) refreshSnapshot()
                } else {
                    fallbackTick = 0
                }
            }
        }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        reconnectJob?.cancel()
        reconnectJob = null
        monitorJob?.cancel()
        monitorJob = null
        socket?.close(1000, "session stopped")
        socket = null
        reconnectAttempt = 0
        _state.update { it.copy(connection = MarketConnectionState.IDLE) }
    }

    fun retry() {
        if (!started.get()) {
            start()
            return
        }
        reconnectAttempt = 0
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.cancel()
        socket = null
        _state.update { it.copy(connection = MarketConnectionState.CONNECTING, lastError = null) }
        scope.launch { refreshSnapshot() }
        connectWebSocket()
    }

    fun syncOnResume() {
        if (!started.get()) {
            start()
            return
        }
        refreshFreshness()
        if (_state.value.connection == MarketConnectionState.LIVE) {
            scope.launch { refreshSnapshot() }
        } else {
            retry()
        }
    }

    private suspend fun restoreCacheIfNeeded() {
        if (_state.value.origin == MarketDataOrigin.NETWORK || _state.value.quotes.isNotEmpty()) return
        val cached = runCatching { cache.read() }.getOrNull() ?: return
        // The cache predates the platform split and can still hold a mixed snapshot written by an
        // older build, so it is filtered on the way in exactly like a live feed.
        val restored = cached.quotes.filter { it.belongsToPlatform() }.associateBy { it.instrument.symbol }
        if (restored.isEmpty()) return
        _state.update { old ->
            if (old.origin == MarketDataOrigin.NETWORK || old.quotes.isNotEmpty()) {
                old
            } else {
                old.copy(
                    quotes = restored,
                    origin = MarketDataOrigin.CACHE,
                    cacheStoredAtEpochMillis = cached.cachedAtEpochMillis,
                )
            }
        }
    }

    private fun connectWebSocket() {
        if (!started.get()) return
        val request = Request.Builder().url(webSocketUrl(baseUrl)).build()
        val newSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!started.get() || socket !== webSocket) {
                        webSocket.close(1000, "superseded")
                        return
                    }
                    reconnectAttempt = 0
                    reconnectJob?.cancel()
                    reconnectJob = null
                    _state.update { old ->
                        old.copy(
                            connection = if (old.origin == MarketDataOrigin.NETWORK && old.quotes.isNotEmpty()) {
                                MarketConnectionState.DEGRADED
                            } else {
                                MarketConnectionState.CONNECTING
                            },
                            lastError = null,
                        )
                    }
                    val subscribe = Gson().toJson(
                        mapOf("action" to "subscribe", "symbols" to symbols),
                    )
                    webSocket.send(subscribe)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (socket !== webSocket) return
                    val batch = parser.parse(text) ?: return
                    applyQuotes(batch.quotes, batch.serverTimeMs, realtimeBatch = true)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (started.get() && socket === webSocket) {
                        markDisconnected("Market stream closed")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (started.get() && socket === webSocket) {
                        markDisconnected(t.message ?: "Market stream unavailable")
                    }
                }
            },
        )
        socket = newSocket
    }

    private fun markDisconnected(message: String) {
        socket = null
        val state = _state.value
        val hasNetworkQuotes = state.origin == MarketDataOrigin.NETWORK && state.quotes.isNotEmpty()
        _state.update {
            it.copy(
                connection = if (hasNetworkQuotes) MarketConnectionState.DEGRADED else MarketConnectionState.OFFLINE,
                lastError = message,
            )
        }
        scope.launch { refreshSnapshot() }
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (!started.get()) return
        reconnectJob?.cancel()
        val seconds = min(15L, 1L shl min(reconnectAttempt, 4))
        reconnectAttempt++
        reconnectJob = scope.launch {
            delay(seconds * 1_000)
            if (started.get()) {
                _state.update { it.copy(connection = MarketConnectionState.CONNECTING) }
                connectWebSocket()
            }
        }
    }

    private suspend fun refreshSnapshot() {
        val snapshot = runCatching { api.snapshot(platform.snapshotPath(), symbols.joinToString(",")) }.getOrElse { error ->
            if (_state.value.connection != MarketConnectionState.LIVE) {
                val state = _state.value
                val hasNetworkQuotes = state.origin == MarketDataOrigin.NETWORK && state.quotes.isNotEmpty()
                _state.update {
                    it.copy(
                        connection = if (hasNetworkQuotes) MarketConnectionState.DEGRADED else MarketConnectionState.OFFLINE,
                        lastError = error.message ?: "Market snapshot unavailable",
                    )
                }
            }
            return
        }
        applyQuotes(snapshot.prices.values.toList(), snapshot.serverTimeMs, realtimeBatch = false)
        if (_state.value.connection != MarketConnectionState.LIVE && _state.value.quotes.isNotEmpty()) {
            _state.update { it.copy(connection = MarketConnectionState.DEGRADED, lastError = null) }
        }
    }

    private fun applyQuotes(
        wireQuotes: List<WireQuoteDto>,
        serverTimeMs: Long?,
        realtimeBatch: Boolean,
    ) {
        val now = nowMillis()
        // Scoped to this controller's platform at the boundary, not by asking callers to filter.
        // A feed that returns a symbol from the other market — a misconfigured subscription, a
        // shared upstream, a symbol that means different things on two venues — must not be able to
        // reach a screen that is showing the other platform.
        val mapped = wireQuotes.mapNotNull { it.toDomain(now) }.filter { it.belongsToPlatform() }
        if (mapped.isEmpty()) return
        _state.update { old ->
            val merged = old.quotes.toMutableMap()
            mapped.forEach { quote ->
                val previous = merged[quote.instrument.symbol]
                if (previous == null || quote.timestampEpochMillis >= previous.timestampEpochMillis) {
                    merged[quote.instrument.symbol] = quote
                }
            }
            val hasFreshQuote = merged.values.any { !it.isStale }
            val nextConnection = when {
                realtimeBatch && hasFreshQuote -> MarketConnectionState.LIVE
                old.connection == MarketConnectionState.LIVE && !hasFreshQuote -> MarketConnectionState.DEGRADED
                else -> old.connection
            }
            old.copy(
                connection = nextConnection,
                quotes = merged,
                lastServerTimeEpochMillis = serverTimeMs ?: old.lastServerTimeEpochMillis,
                lastError = null,
                origin = MarketDataOrigin.NETWORK,
                cacheStoredAtEpochMillis = null,
            )
        }
        persistNetworkSnapshot(now, realtimeBatch)
    }

    private fun persistNetworkSnapshot(now: Long, realtimeBatch: Boolean) {
        if (realtimeBatch && now - lastCacheWriteEpochMillis < REALTIME_CACHE_WRITE_INTERVAL_MS) return
        lastCacheWriteEpochMillis = now
        val snapshot = _state.value.quotes.values.toList()
        scope.launch { runCatching { cache.replace(snapshot, now) } }
    }

    private fun refreshFreshness() {
        val now = nowMillis()
        _state.update { old ->
            val refreshed = old.quotes.mapValues { (_, quote) ->
                quote.copy(
                    isStale = if (old.origin == MarketDataOrigin.CACHE) {
                        true
                    } else {
                        isQuoteStale(quote.source, quote.timestampEpochMillis, now)
                    },
                )
            }
            val nextConnection = if (
                old.connection == MarketConnectionState.LIVE &&
                refreshed.isNotEmpty() &&
                refreshed.values.none { !it.isStale }
            ) {
                MarketConnectionState.DEGRADED
            } else {
                old.connection
            }
            old.copy(connection = nextConnection, quotes = refreshed)
        }
    }
}

internal fun webSocketUrl(baseUrl: HttpUrl): String {
    require(baseUrl.isHttps) { "Market stream requires HTTPS/WSS" }
    val resolved = requireNotNull(baseUrl.resolve("ws/prices")) { "Invalid market stream URL" }
    return resolved.toString().replaceFirst("https://", "wss://")
}

internal fun WireQuoteDto.toDomain(nowMs: Long): MarketQuote? {
    val normalizedSymbol = symbol?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
    val normalizedPrice = price?.takeIf { it > 0 } ?: run {
        val normalizedBid = bid ?: return null
        val normalizedAsk = ask ?: return null
        if (normalizedBid <= 0 || normalizedAsk <= 0) return null
        (normalizedBid + normalizedAsk) / 2
    }
    val timestamp = ts ?: receivedAtMs ?: 0L
    val sourceKind = when {
        source.orEmpty().contains("finnhub", ignoreCase = true) -> QuoteSource.FINNHUB
        source.orEmpty().contains("lbank", ignoreCase = true) -> QuoteSource.LBANK
        else -> QuoteSource.UNKNOWN
    }
    val marketType = when {
        normalizedSymbol == "XAUUSD" || normalizedSymbol == "XAGUSD" -> MarketType.FOREX
        normalizedSymbol.endsWith("USDT") && normalizedSymbol.length > 4 -> MarketType.CRYPTO
        else -> return null
    }
    val displayName = when (normalizedSymbol) {
        "XAUUSD" -> "Gold"
        "XAGUSD" -> "Silver"
        else -> normalizedSymbol.removeSuffix("USDT")
    }
    return MarketQuote(
        instrument = Instrument(normalizedSymbol, displayName, marketType),
        price = normalizedPrice,
        bid = bid,
        ask = ask,
        changePercent = null,
        timestampEpochMillis = timestamp,
        source = sourceKind,
        isStale = isQuoteStale(sourceKind, timestamp, nowMs),
    )
}

internal fun isQuoteStale(source: QuoteSource, timestampMs: Long, nowMs: Long): Boolean {
    if (timestampMs <= 0L) return true
    val age = nowMs - timestampMs
    if (age < -10_000L) return true
    val threshold = when (source) {
        QuoteSource.LBANK -> 15_000L
        QuoteSource.FINNHUB -> 90_000L
        QuoteSource.UNKNOWN -> 30_000L
    }
    return age > threshold
}
