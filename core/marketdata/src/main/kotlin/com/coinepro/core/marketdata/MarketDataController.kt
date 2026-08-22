package com.coinepro.core.marketdata

import com.coinepro.core.model.Instrument
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

class MarketDataController(
    retrofit: Retrofit,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val symbols: List<String> = MarketDataSymbols.default,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
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

    fun start() {
        if (!started.compareAndSet(false, true)) return
        _state.update { it.copy(connection = MarketConnectionState.CONNECTING, lastError = null) }
        scope.launch { refreshSnapshot() }
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
        socket?.cancel()
        socket = null
        _state.update { it.copy(connection = MarketConnectionState.CONNECTING, lastError = null) }
        scope.launch { refreshSnapshot() }
        connectWebSocket()
    }

    private fun connectWebSocket() {
        if (!started.get()) return
        val request = Request.Builder().url(webSocketUrl(baseUrl)).build()
        socket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    reconnectAttempt = 0
                    _state.update {
                        it.copy(connection = MarketConnectionState.LIVE, lastError = null)
                    }
                    val subscribe = Gson().toJson(
                        mapOf("action" to "subscribe", "symbols" to symbols),
                    )
                    webSocket.send(subscribe)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val batch = parser.parse(text) ?: return
                    applyQuotes(batch.quotes, batch.serverTimeMs)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (started.get()) markDisconnected("Market stream closed")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (started.get()) markDisconnected(t.message ?: "Market stream unavailable")
                }
            },
        )
    }

    private fun markDisconnected(message: String) {
        socket = null
        val hasQuotes = _state.value.quotes.isNotEmpty()
        _state.update {
            it.copy(
                connection = if (hasQuotes) MarketConnectionState.DEGRADED else MarketConnectionState.OFFLINE,
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
        val snapshot = runCatching { api.snapshot(symbols.joinToString(",")) }.getOrElse { error ->
            if (_state.value.connection != MarketConnectionState.LIVE) {
                val hasQuotes = _state.value.quotes.isNotEmpty()
                _state.update {
                    it.copy(
                        connection = if (hasQuotes) MarketConnectionState.DEGRADED else MarketConnectionState.OFFLINE,
                        lastError = error.message ?: "Market snapshot unavailable",
                    )
                }
            }
            return
        }
        applyQuotes(snapshot.prices.values.toList(), snapshot.serverTimeMs)
        if (_state.value.connection != MarketConnectionState.LIVE) {
            _state.update { it.copy(connection = MarketConnectionState.DEGRADED, lastError = null) }
        }
    }

    private fun applyQuotes(wireQuotes: List<WireQuoteDto>, serverTimeMs: Long?) {
        val now = nowMillis()
        val mapped = wireQuotes.mapNotNull { it.toDomain(now) }
        if (mapped.isEmpty()) return
        _state.update { old ->
            val merged = old.quotes.toMutableMap()
            mapped.forEach { quote ->
                val previous = merged[quote.instrument.symbol]
                if (previous == null || quote.timestampEpochMillis >= previous.timestampEpochMillis) {
                    merged[quote.instrument.symbol] = quote
                }
            }
            old.copy(
                quotes = merged,
                lastServerTimeEpochMillis = serverTimeMs ?: old.lastServerTimeEpochMillis,
                lastError = null,
            )
        }
    }

    private fun refreshFreshness() {
        val now = nowMillis()
        _state.update { old ->
            old.copy(
                quotes = old.quotes.mapValues { (_, quote) ->
                    quote.copy(isStale = isQuoteStale(quote.source, quote.timestampEpochMillis, now))
                },
            )
        }
    }
}

internal fun webSocketUrl(baseUrl: HttpUrl): HttpUrl {
    require(baseUrl.isHttps) { "Market stream requires HTTPS/WSS" }
    val resolved = requireNotNull(baseUrl.resolve("ws/prices")) { "Invalid market stream URL" }
    return resolved.newBuilder().scheme("wss").build()
}

internal fun WireQuoteDto.toDomain(nowMs: Long): MarketQuote? {
    val normalizedSymbol = symbol?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
    val normalizedPrice = price?.takeIf { it > 0 }
        ?: if (bid != null && ask != null && bid > 0 && ask > 0) (bid + ask) / 2 else null
        ?: return null
    val timestamp = ts ?: receivedAtMs ?: 0L
    val sourceKind = when {
        source.orEmpty().contains("finnhub", ignoreCase = true) -> QuoteSource.FINNHUB
        source.orEmpty().contains("lbank", ignoreCase = true) -> QuoteSource.LBANK
        else -> QuoteSource.UNKNOWN
    }
    val marketType = if (normalizedSymbol == "XAUUSD" || normalizedSymbol == "XAGUSD") {
        MarketType.FOREX
    } else {
        MarketType.CRYPTO
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
