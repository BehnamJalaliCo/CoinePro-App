package com.coinepro.core.database

import com.coinepro.core.model.Instrument
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.QuoteSource
import com.coinepro.core.model.SignalDirection
import com.coinepro.core.signals.SignalResult
import com.coinepro.core.signals.SignalTarget
import com.coinepro.core.signals.TradingSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomReadCacheMapperTest {
    @Test
    fun `cached quote is always restored stale`() {
        val quote = MarketQuote(
            instrument = Instrument("XAUUSD", "Gold", MarketType.FOREX),
            price = 2500.0,
            bid = 2499.5,
            ask = 2500.5,
            timestampEpochMillis = 1000L,
            source = QuoteSource.FINNHUB,
            isStale = false,
        )

        val restored = quote.toEntity()!!.toDomain()!!

        assertEquals("XAUUSD", restored.instrument.symbol)
        assertEquals(2500.0, restored.price, 0.0)
        assertTrue(restored.isStale)
    }

    @Test
    fun `unsupported or invalid quote cannot enter cache`() {
        val invalid = MarketQuote(
            instrument = Instrument("XAUUSD", "Gold", MarketType.FOREX),
            price = Double.NaN,
            timestampEpochMillis = 1000L,
            source = QuoteSource.FINNHUB,
            isStale = true,
        )
        assertNull(invalid.toEntity())
    }

    @Test
    fun `closed signal cache never restores a live quote`() {
        val signal = TradingSignal(
            id = 42,
            market = MarketType.CRYPTO,
            symbol = "BTCUSDT",
            direction = SignalDirection.BUY,
            status = "closed",
            timeframe = "H1",
            strategy = "trend",
            confidence = 80,
            entry = 100.0,
            entryZone = null,
            stopLoss = 95.0,
            targets = listOf(SignalTarget(1, 110.0, true), SignalTarget(2, 120.0, null)),
            riskRewardTp1 = 2.0,
            currentQuote = null,
            livePnlPercent = null,
            hitTarget = "TP1",
            closeReason = "TP1",
            result = SignalResult(10.0, "server"),
            createdAt = "2026-08-20T10:00:00Z",
            closedAt = "2026-08-20T11:00:00Z",
        )

        val entity = signal.toEntity()!!
        val targets = signal.targets.mapNotNull { it.toEntity(signal.id) }
        val restored = entity.toDomain(targets)!!

        assertEquals(42L, restored.id)
        assertEquals(2, restored.targets.size)
        assertEquals(true, restored.targets.first().hit)
        assertNull(restored.targets.last().hit)
        assertNull(restored.currentQuote)
        assertNull(restored.livePnlPercent)
        assertEquals(10.0, restored.result?.pnlUsd ?: error("missing pnl"), 0.0)
    }

    @Test
    fun `cache rejects product scope violations`() {
        val signal = TradingSignal(
            id = 1,
            market = MarketType.FOREX,
            symbol = "EURUSD",
            direction = SignalDirection.BUY,
            status = "closed",
            timeframe = null,
            strategy = null,
            confidence = null,
            entry = null,
            entryZone = null,
            stopLoss = null,
            targets = emptyList(),
            riskRewardTp1 = null,
            currentQuote = null,
            livePnlPercent = null,
            hitTarget = null,
            createdAt = null,
            closedAt = null,
        )
        assertNull(signal.toEntity())
    }
}
