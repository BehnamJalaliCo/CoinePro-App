package com.coinepro.core.signals

import com.coinepro.core.model.MarketType
import com.coinepro.core.model.QuoteSource
import com.coinepro.core.model.SignalDirection

enum class SignalMarketFilter(val wireValue: String) {
    FOREX("forex"),
    CRYPTO("crypto"),
}

enum class SignalStatusFilter(val wireValue: String) {
    ACTIVE("active"),
    RECENT("recent"),
    CLOSED("closed"),
}

data class SignalTarget(
    val level: Int,
    val price: Double?,
    val hit: Boolean,
)

data class SignalEntryZone(
    val low: Double?,
    val high: Double?,
)

data class SignalLiveQuote(
    val price: Double,
    val bid: Double?,
    val ask: Double?,
    val timestampEpochMillis: Long?,
    val source: QuoteSource,
    val isStale: Boolean,
)

data class SignalScoreBreakdown(
    val technical: Double?,
    val pattern: Double?,
    val ml: Double?,
)

data class SignalResult(
    val pnlUsd: Double?,
    val source: String?,
)

data class TradingSignal(
    val id: Long,
    val market: MarketType,
    val symbol: String,
    val direction: SignalDirection,
    val status: String,
    val timeframe: String?,
    val strategy: String?,
    val confidence: Int?,
    val entry: Double?,
    val entryZone: SignalEntryZone?,
    val stopLoss: Double?,
    val targets: List<SignalTarget>,
    val riskRewardTp1: Double?,
    val currentQuote: SignalLiveQuote?,
    val livePnlPercent: Double?,
    val hitTarget: String?,
    val rationale: String? = null,
    val scoreBreakdown: SignalScoreBreakdown? = null,
    val closeReason: String? = null,
    val result: SignalResult? = null,
    val createdAt: String?,
    val closedAt: String?,
)

data class SignalPage(
    val items: List<TradingSignal>,
    val total: Int,
    val serverTimeEpochMillis: Long?,
)

data class SignalsState(
    val market: SignalMarketFilter = SignalMarketFilter.FOREX,
    val status: SignalStatusFilter = SignalStatusFilter.ACTIVE,
    val items: List<TradingSignal> = emptyList(),
    val loading: Boolean = false,
    val membershipRequired: Boolean = false,
    val error: String? = null,
)

data class SignalDetailState(
    val signalId: Long? = null,
    val signal: TradingSignal? = null,
    val loading: Boolean = false,
    val membershipRequired: Boolean = false,
    val error: String? = null,
)

class SignalMembershipRequiredException : Exception("Signal membership required")
