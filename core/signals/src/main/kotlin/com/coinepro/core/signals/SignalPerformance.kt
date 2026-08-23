package com.coinepro.core.signals

import com.coinepro.core.model.MarketType

data class SignalHistoryState(
    val loading: Boolean = false,
    val items: List<TradingSignal> = emptyList(),
    val expectedTotal: Int = 0,
    val coverageComplete: Boolean = true,
    val membershipRequired: Boolean = false,
    val error: String? = null,
    val fromCache: Boolean = false,
    val cacheStoredAtEpochMillis: Long? = null,
)

enum class PerformanceResultFilter {
    ALL,
    WIN,
    LOSS,
    BREAKEVEN,
    UNKNOWN,
}

data class RateMetric(
    val hits: Int,
    val denominator: Int,
    val percent: Double?,
)

data class SignalPerformanceSummary(
    val totalLoaded: Int,
    val expectedTotal: Int,
    val coverageComplete: Boolean,
    val wins: Int,
    val losses: Int,
    val breakeven: Int,
    val unknownPnl: Int,
    val winRate: RateMetric,
    val tpHitRate: RateMetric,
    val stopLossRate: RateMetric,
    val averagePlannedRiskReward: Double?,
    val riskRewardDenominator: Int,
)

fun List<TradingSignal>.filterHistory(
    market: MarketType? = null,
    symbol: String? = null,
    result: PerformanceResultFilter = PerformanceResultFilter.ALL,
): List<TradingSignal> {
    val safeSymbol = symbol?.trim()?.uppercase()?.takeIf(String::isNotEmpty)
    return filter { signal ->
        (market == null || signal.market == market) &&
            (safeSymbol == null || signal.symbol == safeSymbol) &&
            matchesResult(signal, result)
    }
}

fun summarizeSignalPerformance(
    signals: List<TradingSignal>,
    expectedTotal: Int = signals.size,
    coverageComplete: Boolean = true,
): SignalPerformanceSummary {
    val pnlEvidence = signals.mapNotNull { signal ->
        signal.result?.pnlUsd?.takeIf(Double::isFinite)
    }
    val wins = pnlEvidence.count { it > 0.0 }
    val losses = pnlEvidence.count { it < 0.0 }
    val breakeven = pnlEvidence.count { it == 0.0 }
    val unknownPnl = signals.size - pnlEvidence.size

    val targetEvidence = signals.filter { signal ->
        signal.targets.any { target -> target.hit != null }
    }
    val targetHits = targetEvidence.count { signal ->
        signal.targets.any { target -> target.hit == true }
    }

    val closeReasonEvidence = signals.filter { !it.closeReason.isNullOrBlank() }
    val stopLossHits = closeReasonEvidence.count { isExplicitStopLossReason(it.closeReason) }

    val plannedRiskRewards = signals.mapNotNull { signal ->
        signal.riskRewardTp1?.takeIf { it.isFinite() && it > 0.0 }
    }

    return SignalPerformanceSummary(
        totalLoaded = signals.size,
        expectedTotal = expectedTotal.coerceAtLeast(signals.size),
        coverageComplete = coverageComplete,
        wins = wins,
        losses = losses,
        breakeven = breakeven,
        unknownPnl = unknownPnl,
        winRate = rate(wins, pnlEvidence.size),
        tpHitRate = rate(targetHits, targetEvidence.size),
        stopLossRate = rate(stopLossHits, closeReasonEvidence.size),
        averagePlannedRiskReward = plannedRiskRewards.takeIf(List<Double>::isNotEmpty)?.average(),
        riskRewardDenominator = plannedRiskRewards.size,
    )
}

fun performanceResult(signal: TradingSignal): PerformanceResultFilter {
    val pnl = signal.result?.pnlUsd?.takeIf(Double::isFinite) ?: return PerformanceResultFilter.UNKNOWN
    return when {
        pnl > 0.0 -> PerformanceResultFilter.WIN
        pnl < 0.0 -> PerformanceResultFilter.LOSS
        else -> PerformanceResultFilter.BREAKEVEN
    }
}

fun isExplicitStopLossReason(reason: String?): Boolean {
    val normalized = reason?.trim()?.uppercase()?.replace('-', '_')?.replace(' ', '_') ?: return false
    return normalized in setOf("SL", "STOP_LOSS", "STOPLOSS")
}

private fun matchesResult(signal: TradingSignal, filter: PerformanceResultFilter): Boolean = when (filter) {
    PerformanceResultFilter.ALL -> true
    else -> performanceResult(signal) == filter
}

private fun rate(hits: Int, denominator: Int): RateMetric = RateMetric(
    hits = hits,
    denominator = denominator,
    percent = if (denominator == 0) null else hits.toDouble() / denominator.toDouble() * 100.0,
)
