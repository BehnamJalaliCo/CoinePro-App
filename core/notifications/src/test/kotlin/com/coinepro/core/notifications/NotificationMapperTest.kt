package com.coinepro.core.notifications

import com.coinepro.core.model.MarketPlatform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationMapperTest {
    @Test
    fun `notification timestamp is normalized to milliseconds`() {
        val mapped = NotificationDto(
            kind = "new_signal",
            title = "New signal",
            body = "XAUUSD",
            data = mapOf("signal_id" to "42"),
            ts = 1_700_000_000L,
        ).toDomain()!!

        assertEquals(1_700_000_000_000L, mapped.timestampEpochMillis)
        assertEquals(42L, mapped.signalId)
    }

    @Test
    fun `invalid alert contract is rejected instead of guessed`() {
        assertNull(
            PriceAlertDto(
                id = "a1",
                symbol = "XAUUSD",
                condition = "magic",
                value = 2500.0,
                trigger = "once",
            ).toDomain(MarketPlatform.COINEPRO_FX),
        )
    }

    @Test
    fun `valid alert preserves server state`() {
        val alert = PriceAlertDto(
            id = "a1",
            market = "forex",
            symbol = "XAUUSD",
            condition = "cross_up",
            value = 2500.0,
            trigger = "recurring",
            active = true,
            createdAtMs = 123L,
        ).toDomain(MarketPlatform.COINEPRO_FX)!!

        assertEquals(PriceAlertCondition.CROSS_UP, alert.condition)
        assertEquals(PriceAlertTrigger.RECURRING, alert.trigger)
        assertEquals(true, alert.active)
    }

    @Test
    fun `alert symbols stay inside the platform that is on screen`() {
        val fx = MarketPlatform.COINEPRO_FX
        val crypto = MarketPlatform.TRADEYAR

        assertEquals("XAUUSD", normalizeProductAlertSymbol("xau/usd", fx))
        assertEquals("BTCUSDT", normalizeProductAlertSymbol("btc-usdt", crypto))

        // The two markets live on separate backends with separate accounts, so a symbol belonging
        // to one is not merely unsupported on the other — it is the mixing bug this app is built
        // to prevent, arriving through a form instead of through a watchlist.
        assertNull("A crypto pair must not be alertable on the forex platform",
            normalizeProductAlertSymbol("BTCUSDT", fx))
        assertNull("Gold must not be alertable on the crypto platform",
            normalizeProductAlertSymbol("XAUUSD", crypto))

        assertNull(normalizeProductAlertSymbol("EURUSD", fx))
        assertNull(normalizeProductAlertSymbol("BTCUSD", crypto))
    }

    @Test
    fun `an alert naming the other platform's market is dropped on the way in too`() {
        // A row stored by an older build, or a server that answered too broadly, must not reach a
        // screen scoped to the other market.
        assertNull(
            PriceAlertDto(
                id = "a1",
                symbol = "BTCUSDT",
                condition = "above",
                value = 60000.0,
                trigger = "once",
            ).toDomain(MarketPlatform.COINEPRO_FX),
        )
    }

    @Test
    fun `out of scope server alert is rejected`() {
        assertNull(
            PriceAlertDto(
                id = "a1",
                market = "forex",
                symbol = "EURUSD",
                condition = "cross_up",
                value = 1.1,
                trigger = "once",
            ).toDomain(MarketPlatform.COINEPRO_FX),
        )
    }

    @Test
    fun `non finite alert payload is rejected`() {
        assertNull(
            PriceAlertDto(
                id = "a1",
                symbol = "XAUUSD",
                condition = "cross_up",
                value = Double.NaN,
                trigger = "once",
            ).toDomain(MarketPlatform.COINEPRO_FX),
        )
    }
}
