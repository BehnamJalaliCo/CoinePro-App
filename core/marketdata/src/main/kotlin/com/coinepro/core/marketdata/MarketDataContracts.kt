package com.coinepro.core.marketdata

import com.coinepro.core.model.MarketQuote

enum class MarketConnectionState {
    IDLE,
    CONNECTING,
    LIVE,
    DEGRADED,
    OFFLINE,
}

data class MarketDataState(
    val connection: MarketConnectionState = MarketConnectionState.IDLE,
    val quotes: Map<String, MarketQuote> = emptyMap(),
    val lastServerTimeEpochMillis: Long? = null,
    val lastError: String? = null,
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
