package com.coinepro.core.marketintel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `market-intelligence` body, read as a body.
 *
 * Every other wire-facing module in this repository has a test like this one and `core:marketintel`
 * did not, which is how two rounds of work went by without either failure below being seen. Both
 * were reproduced against the real reader before this file was written, and both are the kind that
 * leave an HTTP 200 in the request log and nothing else anywhere.
 */
class MarketIntelWireTest {

    @Test
    fun `the contract body from the request document maps intact`() {
        // Copied from `docs/NEWS_REQUEST_TRADEYAR.md`, which is what TradeYar were asked to serve.
        val snapshot = snapshotOf(
            """
            {
              "server_time": "2026-08-25T09:14:00Z",
              "news": [
                {
                  "id": "ty-2026-08-25-001",
                  "title": "بایننس لیست شدن توکن X را اعلام کرد",
                  "summary": "معاملات از ساعت ۱۲ به وقت گرینویچ آغاز می‌شود.",
                  "source": "CoinDesk",
                  "url": "https://www.coindesk.com/a",
                  "published_at": "2026-08-25T08:30:00Z",
                  "sentiment": "bullish",
                  "impact": "medium",
                  "relevance": ["crypto"],
                  "stale": false
                }
              ],
              "calendar": []
            }
            """,
            route = NetworkMarketIntelGateway.CRYPTO_ROUTE,
        )

        val story = snapshot.news.single()
        assertEquals("ty-2026-08-25-001", story.id)
        assertEquals("CoinDesk", story.source)
        assertEquals(MarketImpact.MEDIUM, story.impact)
        assertEquals(NewsSentiment.BULLISH, story.sentiment)
        assertEquals(setOf(MarketRelevance.CRYPTO), story.relevance)
        assertEquals("api/mobile/v1/market-intelligence", snapshot.newsSource?.route)
        assertEquals(1, snapshot.newsSource?.kept)
    }

    @Test
    fun `a row without relevance no longer takes the whole snapshot down`() {
        // The measured failure. The DTOs declared `relevance: List<String> = emptyList()`, and a
        // Kotlin default does not survive Gson: with any constructor parameter lacking a default
        // there is no no-arg constructor, so Gson allocates through `Unsafe` and the initialiser
        // never runs. One row without the key left a null in a non-null field and `parseRelevance`
        // threw `NullPointerException: Parameter specified as non-null is null` — which
        // `MarketIntelController` catches, keeping the list already on screen and setting `error`.
        // A feed that never changes and an error nobody can explain, from one optional field.
        val snapshot = snapshotOf(
            """
            {"server_time":"2026-08-30T13:00:00Z",
             "news":[{"id":"n1","title":"عنوان","source":"CoinDesk",
                      "published_at":"2026-08-30T12:00:00Z"}],
             "calendar":[]}
            """,
        )
        assertEquals(1, snapshot.news.size)
        assertEquals(emptySet<MarketRelevance>(), snapshot.news.single().relevance)
        // Nothing said it was fresh, so nothing here claims it is.
        assertTrue(snapshot.news.single().isStale)
    }

    @Test
    fun `an event without relevance no longer takes the whole snapshot down either`() {
        val snapshot = snapshotOf(
            """
            {"news":[],"calendar":[{"id":"e1","title":"CPI","country":"US","currency":"USD",
             "scheduled_at":"2026-08-31T12:30:00Z","impact":"high"}]}
            """,
        )
        assertEquals(1, snapshot.calendar.size)
        assertEquals(MarketImpact.HIGH, snapshot.calendar.single().impact)
    }

    @Test
    fun `a body with no news key is an empty snapshot with its envelope recorded, not a crash`() {
        val snapshot = snapshotOf("""{"server_time":"2026-08-30T13:00:00Z"}""")
        assertEquals(emptyList<MarketNewsItem>(), snapshot.news)
        assertEquals(0, snapshot.newsSource?.received)
        // The envelope is what closes the case in one export: an array the app looked for under
        // `news` and the server put under `articles` is invisible in every other field.
        assertEquals("server_time", snapshot.newsSource?.envelope)
    }

    @Test
    fun `camel case is read, because this server already serves camel case next door`() {
        // `GET https://tradeyar.trade-future.ir/api/v1/news/list` — the public headline route on the
        // same host — answers `titleFa`, `summaryFa`, `sourceUrl`, `sourceImageUrl` and
        // `publishedAt`, with a numeric `id`. An adapter written beside that serializer spells
        // things this way. Under the old reader every one of these rows parsed with a null date and
        // was dropped: thirty rows in, zero rows out, HTTP 200, no error anywhere.
        val snapshot = snapshotOf(
            """
            {"serverTime":"2026-08-30T19:00:00Z","calendar":[],"news":[
              {"id":35414,
               "slug":"bitcoins-7-million-coin-quantum-problem-87263b",
               "source":"cryptoslate",
               "sourceUrl":"https://cryptoslate.com/a/",
               "sourceImageUrl":"https://cryptoslate.com/wp-content/uploads/2026/08/t.jpg",
               "titleFa":"چالش کوانتومی ۷ میلیون بیت‌کوین",
               "summaryFa":"خزانه‌داری آمریکا وارد بحث شد.",
               "publishedAt":"2026-08-30T18:37:58.177790+00:00"}
            ]}
            """,
        )

        val story = snapshot.news.single()
        assertEquals("35414", story.id)
        assertEquals("چالش کوانتومی ۷ میلیون بیت‌کوین", story.title)
        assertEquals("خزانه‌داری آمریکا وارد بحث شد.", story.summary)
        assertEquals("https://cryptoslate.com/a/", story.url)
        assertEquals("https://cryptoslate.com/wp-content/uploads/2026/08/t.jpg", story.imageUrl)
        assertNotNull(story.publishedAt)
        assertNotNull(snapshot.serverTime)
    }

    @Test
    fun `an epoch date is read rather than dropping the row it belongs to`() {
        val seconds = snapshotOf(
            """{"news":[{"id":"a","title":"t","source":"s","published_at":"1787000000"}],"calendar":[]}""",
        )
        val millis = snapshotOf(
            """{"news":[{"id":"b","title":"t","source":"s","published_at":1787000000000}],"calendar":[]}""",
        )
        assertEquals(seconds.news.single().publishedAt, millis.news.single().publishedAt)
    }

    @Test
    fun `rows the app cannot read are counted, not silently discarded`() {
        // The whole point of the probe. Two rows arrive, one is unreadable, and the difference is
        // what says "the shape is wrong" rather than "the server has stopped publishing".
        val snapshot = snapshotOf(
            """
            {"calendar":[],"news":[
              {"id":"good","title":"t","source":"s","published_at":"2026-08-30T12:00:00Z"},
              {"id":"bad","title":"t","source":"s","published_at":"30/08/2026 12:00"}
            ]}
            """,
        )
        val probe = requireNotNull(snapshot.newsSource)
        assertEquals(2, probe.received)
        assertEquals(1, probe.kept)
        assertEquals(1, probe.dropped)
        // Untouched, in the server's own order — the string that has to be shown to their team.
        assertEquals("2026-08-30T12:00:00Z", probe.firstPublished)
        assertEquals("30/08/2026 12:00", probe.lastPublished)
        assertEquals("id,title,source,published_at", probe.sampleKeys)
        assertEquals(200, probe.status)
    }

    @Test
    fun `a body that is not an object reports why rather than throwing`() {
        val probe = requireNotNull(snapshotOf("[]").newsSource)
        assertEquals("body is not an object", probe.failure)
        assertEquals(0, probe.kept)
    }

    @Test
    fun `an importance grade is never read as an impact`() {
        // TradeYar's public feed grades a story `importance: 7` on a ten-point scale while
        // `parseImpact` reads `3` as HIGH. Aliasing one onto the other would print a mild story as
        // market-moving, so an unmapped grade stays unknown and the contract's ask stands.
        val snapshot = snapshotOf(
            """{"calendar":[],"news":[{"id":"a","title":"t","source":"s",
               "published_at":"2026-08-30T12:00:00Z","importance":7}]}""",
        )
        assertEquals(MarketImpact.UNKNOWN, snapshot.news.single().impact)
    }

    @Test
    fun `an empty news array is distinguishable from an unread one`() {
        val published = snapshotOf("""{"news":[],"calendar":[]}""")
        assertEquals(0, published.newsSource?.received)
        assertEquals("news,calendar", published.newsSource?.envelope)
        assertNull(published.newsSource?.failure)
    }
}
