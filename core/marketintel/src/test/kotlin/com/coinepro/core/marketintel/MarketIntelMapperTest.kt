package com.coinepro.core.marketintel

import com.google.gson.JsonParser
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
    fun `a missing stale flag is answered by the story's own age, not by a default`() {
        // It used to default to stale, which put a «کهنه» pill on a story published two minutes
        // ago whenever the route left the key out — indistinguishable, to a reader, from the feed
        // having stopped. The app knows when the story was published; that is what staleness means.
        val old = readNews(newsRow(stale = null, publishedAt = "2020-01-01T00:00:00Z"))
        assertTrue("a story from 2020 is stale whoever asks", requireNotNull(old).isStale)

        val fresh = readNews(
            newsRow(stale = null, publishedAt = Instant.now().minusSeconds(600).toString()),
        )
        assertFalse("ten minutes old is not stale", requireNotNull(fresh).isStale)
    }

    @Test
    fun `the server's own flag still wins over the age rule`() {
        // A newsroom that says a fresh row is stale knows something the clock does not — a
        // correction pending, a re-publish of an old wire — and it is still the authority.
        val recent = Instant.now().minusSeconds(60).toString()
        assertTrue(requireNotNull(readNews(newsRow(stale = true, publishedAt = recent))).isStale)
        assertFalse(
            "and a server vouching for an old story is believed too",
            requireNotNull(readNews(newsRow(stale = false, publishedAt = "2020-01-01T00:00:00Z"))).isStale,
        )
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
            readNews(
                newsRow(
                    imageUrl = "https://cdn.example.com/gold.jpg",
                    body = "بند اول.\n\nبند دوم.",
                ),
            ),
        )
        assertEquals("https://cdn.example.com/gold.jpg", item.imageUrl)
        assertEquals("بند اول.\n\nبند دوم.", item.body)

        // The same rule the article link has, for the same reason: a cleartext fetch is the one
        // request in the app anybody on the path can rewrite, and a swapped picture is worse than
        // no picture because the reader trusts it precisely because the app drew it.
        assertNull(readNews(newsRow(imageUrl = "http://cdn.example.com/gold.jpg"))?.imageUrl)
    }

    @Test
    fun `a body that is only the summary again is not a body`() {
        // The likeliest first version of the route: an adapter mapping `summary_fa` into both
        // fields. Taken at face value the reading page would print the same paragraph twice, the
        // second time under a heading claiming it was more.
        val summary = "کمیته‌ی بازار باز رأی به توقف داد."
        assertNull(readNews(newsRow(summary = summary, body = summary))?.body)
        assertNull(readNews(newsRow(summary = summary, body = "  $summary  "))?.body)
    }

    @Test
    fun `a body arriving as markup is refused rather than printed with its tags`() {
        assertNull(readNews(newsRow(body = "<p>بند اول.</p><p>بند دوم.</p>"))?.body)
        // A lone angle bracket is not markup. Persian prose is allowed to contain one, and refusing
        // the whole story over it would lose real text to a rule aimed at HTML.
        assertEquals("نرخ < ۲ درصد ماند.", readNews(newsRow(body = "نرخ < ۲ درصد ماند."))?.body)
    }

    @Test
    fun `a body written on Windows and padded with blank lines is normalised, not rejected`() {
        val item = readNews(newsRow(body = "بند اول.\r\n\r\n\r\n  \r\nبند دوم.\r\n"))
        assertEquals("بند اول.\n\nبند دوم.", item?.body)
    }

    @Test
    fun `a story with neither picture nor body maps to a story that has neither`() {
        val item = requireNotNull(readNews(newsRow()))
        assertNull(item.imageUrl)
        assertNull(item.body)
    }

    @Test
    fun `a story missing any of id title source or a readable date is dropped`() {
        assertNull(readNews(newsRow(id = null)))
        assertNull(readNews(newsRow(title = null)))
        assertNull(readNews(newsRow(source = null)))
        assertNull(readNews(newsRow(publishedAt = "next tuesday")))
    }

    @Test
    fun `the feed is ordered newest first whatever order the server sent it in`() {
        // The owner has reported four times that «اخبار اصلاً آپدیت نمی‌شود، همان چیزی است که از
        // ورژن ۱ بوده». An adapter answering `ORDER BY id`, or ascending, produces exactly that and
        // nothing inside the app could tell it apart from a server that stopped publishing: the
        // request goes out, the response is fresh, the count is right — and the only part of the
        // list anybody reads is the oldest rows, unchanged for ever.
        val snapshot = snapshotOf(
            """
            {"server_time":"2026-08-30T13:00:00Z","calendar":[],"news":[
              ${newsJson(id = "oldest", publishedAt = "2026-08-24T09:00:00Z")},
              ${newsJson(id = "newest", publishedAt = "2026-08-30T12:37:54.012304+00:00")},
              ${newsJson(id = "middle", publishedAt = "2026-08-27T18:30:00+00:00")}
            ]}
            """,
        )

        assertEquals(listOf("newest", "middle", "oldest"), snapshot.news.map(MarketNewsItem::id))

        // And the evidence of the server's own order survives the sort, which is the half that was
        // missing: `first` older than `last` is an ascending adapter, and no amount of sorting here
        // will ever make that visible again once the list has been reordered.
        val probe = requireNotNull(snapshot.newsSource)
        assertEquals("2026-08-24T09:00:00Z", probe.firstPublished)
        assertEquals("2026-08-27T18:30:00+00:00", probe.lastPublished)
        assertEquals(3, probe.received)
        assertEquals(3, probe.kept)
        assertEquals(0, probe.dropped)
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

/** The snapshot a body produces, read exactly as the gateway reads one. */
internal fun snapshotOf(json: String, route: String = "test/route", status: Int = 200): MarketIntelSnapshot =
    readSnapshot(JsonParser.parseString(json.trimIndent()), route, status)

internal fun newsJson(
    summary: String? = null,
    imageUrl: String? = null,
    body: String? = null,
    id: String? = "n1",
    title: String? = "Gold reacts to macro data",
    source: String? = "Provider",
    publishedAt: String? = "2026-08-23T10:00:00Z",
    stale: Boolean? = false,
): String {
    val fields = buildList {
        id?.let { add("\"id\":${quote(it)}") }
        title?.let { add("\"title\":${quote(it)}") }
        summary?.let { add("\"summary\":${quote(it)}") }
        source?.let { add("\"source\":${quote(it)}") }
        add("\"url\":\"https://example.com/item\"")
        publishedAt?.let { add("\"published_at\":${quote(it)}") }
        add("\"sentiment\":\"bullish\"")
        add("\"impact\":\"high\"")
        add("\"relevance\":[\"gold\"]")
        stale?.let { add("\"stale\":$it") }
        imageUrl?.let { add("\"image_url\":${quote(it)}") }
        body?.let { add("\"body\":${quote(it)}") }
    }
    return fields.joinToString(",", "{", "}")
}

internal fun newsRow(
    summary: String? = null,
    imageUrl: String? = null,
    body: String? = null,
    id: String? = "n1",
    title: String? = "Gold reacts to macro data",
    source: String? = "Provider",
    publishedAt: String? = "2026-08-23T10:00:00Z",
    stale: Boolean? = false,
) = JsonParser.parseString(
    newsJson(summary, imageUrl, body, id, title, source, publishedAt, stale),
).asJsonObject

private fun quote(value: String): String = com.google.gson.JsonPrimitive(value).toString()
