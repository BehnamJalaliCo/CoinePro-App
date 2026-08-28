package com.coinepro.core.signals

import com.coinepro.core.model.MarketType
import com.coinepro.core.model.QuoteSource
import com.coinepro.core.common.UiMessage
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
    val hit: Boolean?,
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
    /**
     * How many the server says exist under the current filter, which is not always how many are in
     * [items].
     *
     * Neither backend's list route accepts an offset — there is a `limit`, capped server-side at a
     * hundred, and nothing else. So this list genuinely cannot page, and the previous behaviour was
     * to ask for fifty and say nothing: a reader with more than fifty saw fifty, with no mark
     * anywhere that the rest existed. A silent truncation is the worst of the three options.
     *
     * The list now asks for the server's real ceiling and reports the shortfall, which is honest
     * and is also the thing that would make a paging route worth asking for.
     */
    val total: Int = 0,
    val loading: Boolean = false,
    val membershipRequired: Boolean = false,
    /** The server's own explanation of how to subscribe, shown as written when it gave one. */
    val membershipMessage: String? = null,
    val error: UiMessage? = null,
) {
    /** How many the server has that this screen is not showing. Zero whenever the list is whole. */
    val notShown: Int get() = (total - items.size).coerceAtLeast(0)
}

data class SignalDetailState(
    val signalId: Long? = null,
    val signal: TradingSignal? = null,
    val loading: Boolean = false,
    val membershipRequired: Boolean = false,
    /** The server's own explanation of how to subscribe, shown as written when it gave one. */
    val membershipMessage: String? = null,
    val error: UiMessage? = null,
)

/**
 * The reader is signed in but has no subscription.
 *
 * [serverMessage] is the deployment's own explanation of how to get one, where it gave one. Shown
 * as written: how someone subscribes differs per platform and changes without the app being
 * rebuilt, so the app's own copy is only a fallback for a server that said nothing.
 */
class SignalMembershipRequiredException(
    val serverMessage: String? = null,
) : Exception(serverMessage ?: "Signal membership required")
