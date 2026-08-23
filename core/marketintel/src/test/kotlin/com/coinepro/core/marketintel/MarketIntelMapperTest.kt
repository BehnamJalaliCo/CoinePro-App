package com.coinepro.core.marketintel

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketIntelMapperTest {
    @Test
    fun `unknown impact remains unknown`() {
        assertEquals(MarketImpact.UNKNOWN, parseImpact("surprise"))
        assertEquals(MarketImpact.UNKNOWN, parseImpact(null))
    }

    @Test
    fun `timestamps normalize to UTC instant and invalid timestamps are rejected`() {
        assertEquals(
            Instant.parse("2026-08-23T10:00:00Z"),
            parseInstant("2026-08-23T12:00:00+02:00"),
        )
        assertNull(parseInstant("tomorrow morning"))
    }

    @Test
    fun `missing stale flag is treated as stale rather than fresh`() {
        val item = MarketNewsDto(
            id = "n1",
            title = "Gold reacts to macro data",
            summary = null,
            source = "Provider",
            url = "https://example.com/item",
            publishedAt = "2026-08-23T10:00:00Z",
            sentiment = "bullish",
            impact = "high",
            relevance = listOf("gold"),
            stale = null,
        ).toDomain()

        assertTrue(requireNotNull(item).isStale)
    }

    @Test
    fun `only https article links survive mapping`() {
        assertEquals("https://example.com/a", safeHttpsUrl("https://example.com/a"))
        assertNull(safeHttpsUrl("http://example.com/a"))
        assertNull(safeHttpsUrl("javascript:alert(1)"))
    }

    @Test
    fun `high impact warning requires exact impact relevance freshness and time window`() {
        val now = Instant.parse("2026-08-23T10:00:00Z")
        val matching = event(
            id = "high-gold",
            impact = MarketImpact.HIGH,
            relevance = setOf(MarketRelevance.GOLD),
            scheduledAt = Instant.parse("2026-08-23T12:00:00Z"),
        )
        val unknown = event(
            id = "unknown-gold",
            impact = MarketImpact.UNKNOWN,
            relevance = setOf(MarketRelevance.GOLD),
            scheduledAt = Instant.parse("2026-08-23T12:00:00Z"),
        )
        val stale = event(
            id = "stale-gold",
            impact = MarketImpact.HIGH,
            relevance = setOf(MarketRelevance.GOLD),
            scheduledAt = Instant.parse("2026-08-23T12:00:00Z"),
            stale = true,
        )
        val crypto = event(
            id = "crypto",
            impact = MarketImpact.HIGH,
            relevance = setOf(MarketRelevance.CRYPTO),
            scheduledAt = Instant.parse("2026-08-23T12:00:00Z"),
        )

        val warnings = listOf(matching, unknown, stale, crypto).highImpactWarningsFor("XAUUSD", now)
        assertEquals(listOf("high-gold"), warnings.map(EconomicEvent::id))
        assertFalse(listOf(matching).highImpactWarningsFor("EURUSD", now).isNotEmpty())
    }

    private fun event(
        id: String,
        impact: MarketImpact,
        relevance: Set<MarketRelevance>,
        scheduledAt: Instant,
        stale: Boolean = false,
    ) = EconomicEvent(
        id = id,
        title = "Macro event",
        country = "US",
        currency = "USD",
        scheduledAt = scheduledAt,
        impact = impact,
        actual = null,
        forecast = null,
        previous = null,
        relevance = relevance,
        isStale = stale,
    )
}
