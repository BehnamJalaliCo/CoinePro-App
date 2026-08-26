package com.coinepro.core.symbols

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketHoursTest {

    private fun at(utc: String): Clock = Clock.fixed(Instant.parse(utc), ZoneOffset.UTC)

    // 2026-08-22 is a Saturday, so the week either side of it is unambiguous.
    private val saturday = at("2026-08-22T12:00:00Z")
    private val fridayMorning = at("2026-08-21T09:00:00Z")
    private val fridayNight = at("2026-08-21T23:00:00Z")
    private val sundayAfternoon = at("2026-08-23T15:00:00Z")
    private val sundayNight = at("2026-08-23T23:00:00Z")
    private val wednesday = at("2026-08-19T12:00:00Z")

    @Test
    fun `forex is closed all Saturday and either side of the weekend`() {
        assertFalse(MarketHours.isForexOpen(saturday))
        assertFalse(MarketHours.isForexOpen(fridayNight))
        assertFalse(MarketHours.isForexOpen(sundayAfternoon))
        assertTrue(MarketHours.isForexOpen(fridayMorning))
        assertTrue(MarketHours.isForexOpen(sundayNight))
        assertTrue(MarketHours.isForexOpen(wednesday))
    }

    @Test
    fun `crypto never closes`() {
        assertEquals(
            MarketStatus(open = true, weekend = false),
            MarketHours.statusOf("BTCUSDT", clock = saturday),
        )
    }

    @Test
    fun `the client overrules a server that says gold is open on Saturday`() {
        // The bug this exists for: one market_open boolean covers a feed with several calendars, so
        // it read true all weekend whenever the crypto side was up.
        val status = MarketHours.statusOf("XAUUSD", serverOpen = true, clock = saturday)
        assertFalse(status.open)
        assertTrue(status.weekend)
    }

    @Test
    fun `inside the week the server is believed about a halt`() {
        val status = MarketHours.statusOf("EURUSD", serverOpen = false, clock = wednesday)
        assertFalse(status.open)
        // Not the weekend — worth saying differently, because it will not simply pass by Monday.
        assertFalse(status.weekend)
    }

    @Test
    fun `no word from the server inside the week means open`() {
        assertTrue(MarketHours.statusOf("EURUSD", serverOpen = null, clock = wednesday).open)
    }
}
