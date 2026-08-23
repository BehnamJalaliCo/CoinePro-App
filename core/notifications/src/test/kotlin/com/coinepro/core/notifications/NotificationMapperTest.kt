package com.coinepro.core.notifications

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
            ).toDomain(),
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
        ).toDomain()!!

        assertEquals(PriceAlertCondition.CROSS_UP, alert.condition)
        assertEquals(PriceAlertTrigger.RECURRING, alert.trigger)
        assertEquals(true, alert.active)
    }

    @Test
    fun `alert symbol normalization stays inside product scope`() {
        assertEquals("XAUUSD", normalizeProductAlertSymbol("xau/usd"))
        assertEquals("BTCUSDT", normalizeProductAlertSymbol("btc-usdt"))
        assertNull(normalizeProductAlertSymbol("EURUSD"))
        assertNull(normalizeProductAlertSymbol("BTCUSD"))
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
            ).toDomain(),
        )
    }
}
