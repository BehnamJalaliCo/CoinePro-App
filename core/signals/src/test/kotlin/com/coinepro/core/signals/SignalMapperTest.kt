package com.coinepro.core.signals

import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.QuoteSource
import com.coinepro.core.model.SignalDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalMapperTest {
    @Test
    fun `maps server signal without inventing missing values`() {
        val dto = SignalDto(
            id = 42,
            market = "forex",
            symbol = "xauusd",
            direction = "BUY",
            status = "active",
            confidence = 84,
            entry = 2500.0,
            stopLoss = 2490.0,
            targets = listOf(SignalTargetDto(1, 2520.0, false)),
            rationale = null,
        )

        val signal = dto.toDomain(nowMs = 10_000L, MarketPlatform.COINEPRO_FX)!!
        assertEquals(42L, signal.id)
        assertEquals(MarketType.FOREX, signal.market)
        assertEquals("XAUUSD", signal.symbol)
        assertEquals(SignalDirection.BUY, signal.direction)
        assertNull(signal.rationale)
    }

    @Test
    fun `persisted signal id must be positive`() {
        assertNull(SignalDto(id = 0, market = "forex", symbol = "XAUUSD", direction = "BUY").toDomain(0L, MarketPlatform.COINEPRO_FX))
        assertNull(SignalDto(id = -1, market = "forex", symbol = "XAUUSD", direction = "BUY").toDomain(0L, MarketPlatform.COINEPRO_FX))
    }

    @Test
    fun `quote freshness follows market-specific thresholds`() {
        val crypto = SignalQuoteDto(100.0, timestampMs = 90_000L, source = "lbank_futures_ws")
            .toDomain(MarketType.CRYPTO, nowMs = 100_000L)!!
        val forex = SignalQuoteDto(2500.0, timestampMs = 20_000L, source = "finnhub")
            .toDomain(MarketType.FOREX, nowMs = 100_000L)!!

        assertFalse(crypto.isStale)
        assertFalse(forex.isStale)
        assertEquals(QuoteSource.LBANK, crypto.source)
        assertEquals(QuoteSource.FINNHUB, forex.source)

        val staleCrypto = SignalQuoteDto(100.0, timestampMs = 70_000L, source = "lbank")
            .toDomain(MarketType.CRYPTO, nowMs = 100_000L)!!
        assertTrue(staleCrypto.isStale)
    }

    @Test
    fun `unknown market is rejected instead of guessed`() {
        assertNull(SignalDto(id = 1, market = "stocks", symbol = "AAPL", direction = "BUY").toDomain(0L, MarketPlatform.COINEPRO_FX))
    }

    @Test
    fun `non actionable direction is rejected instead of displayed as a trade`() {
        assertNull(SignalDto(id = 1, market = "forex", symbol = "XAUUSD", direction = "neutral").toDomain(0L, MarketPlatform.COINEPRO_FX))
    }

    @Test
    fun `forex signal outside gold and silver scope is rejected`() {
        assertNull(SignalDto(id = 1, market = "forex", symbol = "EURUSD", direction = "BUY").toDomain(0L, MarketPlatform.COINEPRO_FX))
        assertTrue(isProductSignalSymbol(MarketType.FOREX, "XAUUSD"))
        assertTrue(isProductSignalSymbol(MarketType.FOREX, "XAGUSD"))
    }

    @Test
    fun `crypto signal must use lbank style usdt pair`() {
        assertNull(SignalDto(id = 1, market = "crypto", symbol = "BTCUSD", direction = "BUY").toDomain(0L, MarketPlatform.COINEPRO_FX))
        assertTrue(isProductSignalSymbol(MarketType.CRYPTO, "BTCUSDT"))
        assertFalse(isProductSignalSymbol(MarketType.CRYPTO, "USDT"))
    }
}
