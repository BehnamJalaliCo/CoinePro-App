package com.coinepro.core.model

enum class MarketType { FOREX, CRYPTO }

enum class SignalDirection { BUY, SELL, NEUTRAL }

data class Instrument(
    val symbol: String,
    val displayName: String,
    val marketType: MarketType,
)

data class MarketQuote(
    val instrument: Instrument,
    val price: Double,
    val changePercent: Double?,
    val timestampEpochMillis: Long,
)
