package com.coinepro.core.marketdata

import com.coinepro.core.model.MarketQuote

enum class MarketConnectionState {
    IDLE,
    CONNECTING,
    LIVE,
    DEGRADED,
    OFFLINE,
}

enum class MarketDataOrigin {
    NONE,
    CACHE,
    NETWORK,
}

data class MarketDataState(
    val connection: MarketConnectionState = MarketConnectionState.IDLE,
    val quotes: Map<String, MarketQuote> = emptyMap(),
    val lastServerTimeEpochMillis: Long? = null,
    val lastError: String? = null,
    val origin: MarketDataOrigin = MarketDataOrigin.NONE,
    val cacheStoredAtEpochMillis: Long? = null,
)

object MarketDataSymbols {
    val default: List<String> = listOf(
        "XAUUSD",
        "XAGUSD",
        "BTCUSDT",
        "ETHUSDT",
        "BNBUSDT",
        "SOLUSDT",
        "XRPUSDT",
        "ADAUSDT",
        "DOGEUSDT",
        "TRXUSDT",
    )
}
