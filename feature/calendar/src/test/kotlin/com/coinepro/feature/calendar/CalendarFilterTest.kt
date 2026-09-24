package com.coinepro.feature.calendar

import com.coinepro.core.marketintel.EconomicEvent
import com.coinepro.core.marketintel.MarketImpact
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The old terminal's calendar filters and its «next» marker (5.15.0). */
class CalendarFilterTest {

    private fun event(id: String, currency: String?, at: String, title: String = id, impact: MarketImpact = MarketImpact.HIGH) =
        EconomicEvent(
            id = id,
            title = title,
            country = currency?.take(2),
            currency = currency,
            scheduledAt = Instant.parse(at),
            impact = impact,
            actual = null,
            forecast = null,
            previous = null,
            relevance = emptySet(),
            isStale = false,
        )

    private val week = listOf(
        event("nfp", "USD", "2026-09-04T12:30:00Z", title = "Non-Farm Payrolls"),
        event("cpi", "USD", "2026-09-10T12:30:00Z", title = "CPI"),
        event("ecb", "EUR", "2026-09-11T12:15:00Z", title = "ECB rate decision", impact = MarketImpact.MEDIUM),
        event("gdp", "GBP", "2026-09-12T06:00:00Z", title = "GDP"),
    )

    @Test
    fun `a currency keeps its releases and nothing else`() {
        assertEquals(listOf("nfp", "cpi"), CalendarFilter.apply(week, null, "USD", "").map { it.id })
        assertEquals(listOf("ecb"), CalendarFilter.apply(week, null, "eur", "").map { it.id })
        assertEquals(emptyList<String>(), CalendarFilter.apply(week, MarketImpact.HIGH, "EUR", "").map { it.id })
    }

    @Test
    fun `a word finds a release by title or country`() {
        assertEquals(listOf("ecb"), CalendarFilter.apply(week, null, null, "rate").map { it.id })
        assertEquals(listOf("gdp"), CalendarFilter.apply(week, null, null, "gb").map { it.id })
    }

    @Test
    fun `the currencies come most releases first, and the next release is the first still to come`() {
        assertEquals(listOf("USD", "EUR", "GBP"), CalendarFilter.currencies(week))
        assertEquals("ecb", CalendarFilter.next(week, Instant.parse("2026-09-11T00:00:00Z"))!!.id)
        assertNull(CalendarFilter.next(week, Instant.parse("2026-10-01T00:00:00Z")))
    }
}
