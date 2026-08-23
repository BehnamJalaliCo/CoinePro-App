package com.coinepro.core.signals

import com.coinepro.core.model.MarketType
import com.coinepro.core.model.SignalDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalPerformanceTest {
    @Test
    fun `win rate uses only finite explicit pnl evidence`() {
        val signals = listOf(
            signal(1, pnl = 10.0),
            signal(2, pnl = -5.0),
            signal(3, pnl = 0.0),
            signal(4, pnl = null),
            signal(5, pnl = Double.NaN),
        )

        val summary = summarizeSignalPerformance(signals)

        assertEquals(1, summary.wins)
        assertEquals(1, summary.losses)
        assertEquals(1, summary.breakeven)
        assertEquals(2, summary.unknownPnl)
        assertEquals(3, summary.winRate.denominator)
        assertEquals(33.333333, requireNotNull(summary.winRate.percent), 0.00001)
    }

    @Test
    fun `zero records produces missing rates not fake zero`() {
        val summary = summarizeSignalPerformance(emptyList())
        assertNull(summary.winRate.percent)
        assertNull(summary.tpHitRate.percent)
        assertNull(summary.stopLossRate.percent)
        assertNull(summary.averagePlannedRiskReward)
        assertEquals(0, summary.riskRewardDenominator)
    }

    @Test
    fun `tp hit denominator requires target evidence`() {
        val signals = listOf(
            signal(1, targets = listOf(SignalTarget(1, 101.0, true))),
            signal(2, targets = listOf(SignalTarget(1, 101.0, false))),
            signal(3, targets = emptyList()),
        )
        val summary = summarizeSignalPerformance(signals)
        assertEquals(1, summary.tpHitRate.hits)
        assertEquals(2, summary.tpHitRate.denominator)
        assertEquals(50.0, requireNotNull(summary.tpHitRate.percent), 0.000001)
    }

    @Test
    fun `stop loss rate only recognizes explicit stop loss codes`() {
        val signals = listOf(
            signal(1, closeReason = "SL"),
            signal(2, closeReason = "stop_loss"),
            signal(3, closeReason = "tp1"),
            signal(4, closeReason = "manual"),
            signal(5, closeReason = null),
        )
        val summary = summarizeSignalPerformance(signals)
        assertEquals(2, summary.stopLossRate.hits)
        assertEquals(4, summary.stopLossRate.denominator)
        assertEquals(50.0, requireNotNull(summary.stopLossRate.percent), 0.000001)
        assertTrue(isExplicitStopLossReason("stop-loss"))
        assertFalse(isExplicitStopLossReason("stopped manually"))
    }

    @Test
    fun `average rr uses only finite positive planned tp1 rr`() {
        val signals = listOf(
            signal(1, rr = 2.0),
            signal(2, rr = 3.0),
            signal(3, rr = 0.0),
            signal(4, rr = -1.0),
            signal(5, rr = Double.POSITIVE_INFINITY),
            signal(6, rr = null),
        )
        val summary = summarizeSignalPerformance(signals)
        assertEquals(2, summary.riskRewardDenominator)
        assertEquals(2.5, requireNotNull(summary.averagePlannedRiskReward), 0.000001)
    }

    @Test
    fun `history filters market symbol and explicit result without guessing unknown`() {
        val signals = listOf(
            signal(1, market = MarketType.FOREX, symbol = "XAUUSD", pnl = 10.0),
            signal(2, market = MarketType.FOREX, symbol = "XAGUSD", pnl = -2.0),
            signal(3, market = MarketType.CRYPTO, symbol = "BTCUSDT", pnl = null),
        )

        assertEquals(listOf(1L), signals.filterHistory(MarketType.FOREX, "xauusd", PerformanceResultFilter.WIN).map { it.id })
        assertEquals(listOf(2L), signals.filterHistory(result = PerformanceResultFilter.LOSS).map { it.id })
        assertEquals(listOf(3L), signals.filterHistory(result = PerformanceResultFilter.UNKNOWN).map { it.id })
    }

    @Test
    fun `summary preserves incomplete coverage truth`() {
        val signals = listOf(signal(1, pnl = 1.0), signal(2, pnl = -1.0))
        val summary = summarizeSignalPerformance(signals, expectedTotal = 20, coverageComplete = false)
        assertEquals(2, summary.totalLoaded)
        assertEquals(20, summary.expectedTotal)
        assertFalse(summary.coverageComplete)
    }

    private fun signal(
        id: Long,
        market: MarketType = MarketType.FOREX,
        symbol: String = "XAUUSD",
        pnl: Double? = null,
        rr: Double? = null,
        closeReason: String? = null,
        targets: List<SignalTarget> = emptyList(),
    ) = TradingSignal(
        id = id,
        market = market,
        symbol = symbol,
        direction = SignalDirection.BUY,
        status = "closed",
        timeframe = "H1",
        strategy = null,
        confidence = null,
        entry = 100.0,
        entryZone = null,
        stopLoss = 99.0,
        targets = targets,
        riskRewardTp1 = rr,
        currentQuote = null,
        livePnlPercent = null,
        hitTarget = null,
        rationale = null,
        scoreBreakdown = null,
        closeReason = closeReason,
        result = pnl?.let { SignalResult(it, "server") },
        createdAt = "2026-08-20T10:00:00Z",
        closedAt = "2026-08-20T11:00:00Z",
    )
}
