package com.coinepro.core.model

enum class MarketType { FOREX, CRYPTO }

enum class SignalDirection { BUY, SELL, NEUTRAL }

enum class QuoteSource { FINNHUB, LBANK, UNKNOWN }

data class Instrument(
    val symbol: String,
    val displayName: String,
    val marketType: MarketType,
)

data class MarketQuote(
    val instrument: Instrument,
    val price: Double,
    val bid: Double? = null,
    val ask: Double? = null,
    val changePercent: Double? = null,
    val timestampEpochMillis: Long,
    val source: QuoteSource = QuoteSource.UNKNOWN,
    val isStale: Boolean = true,
)
