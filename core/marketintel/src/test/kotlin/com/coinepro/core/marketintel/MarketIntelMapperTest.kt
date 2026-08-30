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
    fun `a picture and a body survive the mapping, and a cleartext picture does not`() {
        val item = requireNotNull(
            newsDto(
                imageUrl = "https://cdn.example.com/gold.jpg",
                body = "بند اول.\n\nبند دوم.",
            ).toDomain(),
        )
        assertEquals("https://cdn.example.com/gold.jpg", item.imageUrl)
        assertEquals("بند اول.\n\nبند دوم.", item.body)

        // The same rule the article link has, for the same reason: a cleartext fetch is the one
        // request in the app anybody on the path can rewrite, and a swapped picture is worse than
        // no picture because the reader trusts it precisely because the app drew it.
        assertNull(newsDto(imageUrl = "http://cdn.example.com/gold.jpg").toDomain()?.imageUrl)
    }

    @Test
    fun `a body that is only the summary again is not a body`() {
        // The likeliest first version of the route: an adapter mapping `summary_fa` into both
        // fields. Taken at face value the reading page would print the same paragraph twice, the
        // second time under a heading claiming it was more.
        val summary = "کمیتهٔ بازار باز رأی به توقف داد."
        assertNull(newsDto(summary = summary, body = summary).toDomain()?.body)
        assertNull(newsDto(summary = summary, body = "  $summary  ").toDomain()?.body)
    }

    @Test
    fun `a body arriving as markup is refused rather than printed with its tags`() {
        assertNull(newsDto(body = "<p>بند اول.</p><p>بند دوم.</p>").toDomain()?.body)
        // A lone angle bracket is not markup. Persian prose is allowed to contain one, and refusing
        // the whole story over it would lose real text to a rule aimed at HTML.
        assertEquals("نرخ < ۲ درصد ماند.", newsDto(body = "نرخ < ۲ درصد ماند.").toDomain()?.body)
    }

    @Test
    fun `a body written on Windows and padded with blank lines is normalised, not rejected`() {
        val item = newsDto(body = "بند اول.\r\n\r\n\r\n  \r\nبند دوم.\r\n").toDomain()
        assertEquals("بند اول.\n\nبند دوم.", item?.body)
    }

    @Test
    fun `a story with neither picture nor body maps to a story that has neither`() {
        val item = requireNotNull(newsDto().toDomain())
        assertNull(item.imageUrl)
        assertNull(item.body)
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

    private fun newsDto(
        summary: String? = null,
        imageUrl: String? = null,
        body: String? = null,
    ) = MarketNewsDto(
        id = "n1",
        title = "Gold reacts to macro data",
        summary = summary,
        source = "Provider",
        url = "https://example.com/item",
        publishedAt = "2026-08-23T10:00:00Z",
        sentiment = "bullish",
        impact = "high",
        relevance = listOf("gold"),
        stale = false,
        imageUrl = imageUrl,
        body = body,
    )

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
