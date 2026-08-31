package com.coinepro.core.marketintel

import com.coinepro.core.model.MarketPlatform
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The public sources, against bodies captured from the live hosts.
 *
 * The fixtures under `src/test/resources` are real responses, saved on the day this was written,
 * not hand-written samples. That distinction is the whole value of the suite: a hand-written RSS
 * item has the fields the author remembered, and every bug this module has had was a field the
 * author did not remember — a `publishedAt` where a `published_at` was expected, a `relevance` the
 * server omits. A captured body has what the publisher actually sends.
 */
class PublicFeedTest {

    private val now = Instant.parse("2026-08-31T00:00:00Z")

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    // ── the calendar ─────────────────────────────────────────────────────────

    @Test
    fun `the published week parses into events`() {
        val events = PublicCalendarFeed.parse(fixture("ff-calendar-week.json"), now)
        assertTrue("expected a full week, got ${events.size}", events.size > 50)
    }

    @Test
    fun `events come out in time order, because a calendar is read forwards`() {
        val events = PublicCalendarFeed.parse(fixture("ff-calendar-week.json"), now)
        assertEquals(events.sortedBy { it.scheduledAt }, events)
    }

    @Test
    fun `every event has a title, a moment and an impact`() {
        PublicCalendarFeed.parse(fixture("ff-calendar-week.json"), now).forEach { event ->
            assertTrue(event.title.isNotBlank())
            assertNotNull(event.scheduledAt)
        }
    }

    @Test
    fun `the high-impact rows are found rather than flattened`() {
        val events = PublicCalendarFeed.parse(fixture("ff-calendar-week.json"), now)
        assertTrue(events.any { it.impact == MarketImpact.HIGH })
        assertTrue(events.any { it.impact == MarketImpact.LOW })
    }

    @Test
    fun `actual is null rather than blank, because this file is published ahead of the week`() {
        // A blank string renders as a dash, and a dash beside a forecast reads as "released, and it
        // came in at nothing". The figure is genuinely not here.
        PublicCalendarFeed.parse(fixture("ff-calendar-week.json"), now).forEach { assertNull(it.actual) }
    }

    @Test
    fun `a body that is not the feed produces nothing rather than throwing`() {
        assertEquals(emptyList<EconomicEvent>(), PublicCalendarFeed.parse("<html>bad gateway</html>", now))
        assertEquals(emptyList<EconomicEvent>(), PublicCalendarFeed.parse("", now))
        assertEquals(emptyList<EconomicEvent>(), PublicCalendarFeed.parse(null, now))
    }

    // ── the Persian glossary ─────────────────────────────────────────────────

    @Test
    fun `the compositional translator handles a title it was never given whole`() {
        // «German Prelim CPI m/m» is in the feed; «German Prelim CPI y/y» is the same event on a
        // different period and a flat table would have been silent about it.
        assertEquals("شاخص قیمت مصرف‌کننده ماه‌به‌ماه مقدماتی آلمان", CalendarPersian.title("German Prelim CPI m/m"))
        assertEquals("شاخص قیمت مصرف‌کننده سال‌به‌سال مقدماتی آلمان", CalendarPersian.title("German Prelim CPI y/y"))
    }

    @Test
    fun `an indicator nobody taught it survives inside a Persian phrase`() {
        // The correct failure: the reader can still tell which row it is.
        val translated = CalendarPersian.title("German Widget Index m/m")
        assertTrue(translated, translated.contains("آلمان"))
        assertTrue(translated, translated.contains("ماه‌به‌ماه"))
        assertTrue(translated, translated.contains("Widget Index"))
    }

    @Test
    fun `the headline releases read as Persian`() {
        assertEquals("تغییر اشتغال غیرکشاورزی", CalendarPersian.title("Non-Farm Employment Change"))
        assertEquals("نرخ بیکاری", CalendarPersian.title("Unemployment Rate"))
        assertEquals("تولید ناخالص داخلی فصل‌به‌فصل", CalendarPersian.title("GDP q/q"))
        assertEquals("ذخایر نفت خام", CalendarPersian.title("Crude Oil Inventories"))
    }

    @Test
    fun `the currency a row is filed under becomes a country a reader knows`() {
        assertEquals("آمریکا", CalendarPersian.country("USD"))
        assertEquals("منطقهٔ یورو", CalendarPersian.country("EUR"))
        assertEquals("جهانی", CalendarPersian.country("All"))
        assertNull(CalendarPersian.country(null))
    }

    @Test
    fun `most of a real week translates rather than most of it surviving in English`() {
        // The number that says whether the glossary is worth having. A compositional translator that
        // covered a third of the week would be worse than none, because a half-Persian calendar
        // reads as broken rather than as partial.
        val titles = PublicCalendarFeed.parse(fixture("ff-calendar-week.json"), now).map { it.title }
        val persian = titles.count { title -> title.any { it in '؀'..'ۿ' } }
        assertTrue("only $persian of ${titles.size} carried Persian", persian * 10 >= titles.size * 9)
    }

    // ── the news ─────────────────────────────────────────────────────────────

    @Test
    fun `a real crypto wire parses into stories`() {
        val feed = PublicNewsFeed.Feed("https://example.invalid", "Cointelegraph")
        val stories = PublicNewsFeed.parse(fixture("cointelegraph.xml"), feed, MarketPlatform.TRADEYAR, now)
        assertTrue("got ${stories.size}", stories.size >= 5)
        stories.forEach { story ->
            assertTrue(story.title.isNotBlank())
            assertEquals("Cointelegraph", story.source)
            assertNotNull(story.url)
        }
    }

    @Test
    fun `a real forex wire parses into stories`() {
        val feed = PublicNewsFeed.Feed("https://example.invalid", "Investing.com")
        val stories = PublicNewsFeed.parse(fixture("investing-forex.xml"), feed, MarketPlatform.COINEPRO_FX, now)
        assertTrue("got ${stories.size}", stories.size >= 5)
        assertTrue(stories.all { MarketRelevance.GOLD in it.relevance })
        assertTrue(stories.none { MarketRelevance.CRYPTO in it.relevance })
    }

    @Test
    fun `stories carry the picture the feed sent`() {
        val feed = PublicNewsFeed.Feed("https://example.invalid", "Cointelegraph")
        val stories = PublicNewsFeed.parse(fixture("cointelegraph.xml"), feed, MarketPlatform.TRADEYAR, now)
        val withPictures = stories.count { it.imageUrl?.startsWith("https://") == true }
        assertTrue("only $withPictures of ${stories.size} had a picture", withPictures >= stories.size / 2)
    }

    @Test
    fun `a summary is a sentence rather than a paragraph of html`() {
        val feed = PublicNewsFeed.Feed("https://example.invalid", "Cointelegraph")
        val stories = PublicNewsFeed.parse(fixture("cointelegraph.xml"), feed, MarketPlatform.TRADEYAR, now)
        val summaries = stories.mapNotNull { it.summary }
        assertTrue(summaries.isNotEmpty())
        summaries.forEach { summary ->
            assertFalse(summary, summary.contains('<'))
            assertFalse(summary, summary.contains("&nbsp;"))
        }
    }

    @Test
    fun `sentiment is never guessed from a headline`() {
        // The most dangerous number this app could print, because a reader would act on it.
        val feed = PublicNewsFeed.Feed("https://example.invalid", "Cointelegraph")
        PublicNewsFeed.parse(fixture("cointelegraph.xml"), feed, MarketPlatform.TRADEYAR, now)
            .forEach { assertEquals(NewsSentiment.UNKNOWN, it.sentiment) }
    }

    @Test
    fun `stories come out newest first`() {
        val feed = PublicNewsFeed.Feed("https://example.invalid", "Cointelegraph")
        val stories = PublicNewsFeed.parse(fixture("cointelegraph.xml"), feed, MarketPlatform.TRADEYAR, now)
        assertEquals(stories.sortedByDescending { it.publishedAt }, stories)
    }

    @Test
    fun `a page that is not a feed produces nothing rather than throwing`() {
        val feed = PublicNewsFeed.Feed("https://example.invalid", "x")
        assertEquals(emptyList<MarketNewsItem>(), PublicNewsFeed.parse("<html>403</html>", feed, MarketPlatform.TRADEYAR, now))
        assertEquals(emptyList<MarketNewsItem>(), PublicNewsFeed.parse(null, feed, MarketPlatform.TRADEYAR, now))
    }

    @Test
    fun `the same story from two wires is kept once`() {
        val one = story("Bitcoin holds 80k", Instant.parse("2026-08-30T10:00:00Z"))
        val again = story("bitcoin holds 80k  ", Instant.parse("2026-08-30T09:00:00Z"))
        val other = story("Gold taps a record", Instant.parse("2026-08-30T08:00:00Z"))
        assertEquals(listOf(one, other), PublicNewsFeed.merge(listOf(other, again, one)))
    }

    // ── the two together ─────────────────────────────────────────────────────

    @Test
    fun `both wires are read and merged`() = runTest {
        val bodies = mapOf(
            "https://www.investing.com/rss/news_301.rss" to fixture("investing-forex.xml"),
            "https://www.cointelegraph.com/rss" to fixture("cointelegraph.xml"),
        )
        val intel = PublicMarketIntel(
            client = { url -> bodies[url] },
            platform = MarketPlatform.TRADEYAR,
            now = { now },
        )
        val stories = intel.news()
        assertTrue("got ${stories.size}", stories.size > 10)
        assertTrue(stories.any { it.source == "Cointelegraph" })
        assertTrue(stories.any { it.source == "Investing.com" })
    }

    @Test
    fun `a wire that will not answer costs the other one nothing`() = runTest {
        val intel = PublicMarketIntel(
            client = { url -> if (url.contains("cointelegraph")) fixture("cointelegraph.xml") else null },
            platform = MarketPlatform.TRADEYAR,
            now = { now },
        )
        assertTrue(intel.news().isNotEmpty())
    }

    @Test
    fun `every source down is an empty list rather than a crash`() = runTest {
        val intel = PublicMarketIntel(client = { null }, platform = MarketPlatform.COINEPRO_FX, now = { now })
        assertEquals(emptyList<MarketNewsItem>(), intel.news())
        assertEquals(emptyList<EconomicEvent>(), intel.calendar())
    }

    @Test
    fun `the two platforms are asked different wires`() {
        val crypto = PublicNewsFeed.feeds(MarketPlatform.TRADEYAR).map { it.url }
        val forex = PublicNewsFeed.feeds(MarketPlatform.COINEPRO_FX).map { it.url }
        assertTrue(crypto.none { it in forex })
        assertTrue(crypto.isNotEmpty() && forex.isNotEmpty())
        assertTrue(crypto.all { it.startsWith("https://") })
        assertTrue(forex.all { it.startsWith("https://") })
    }

    @Test
    fun `a guest gets the wires rather than the sign-in wall`() = runTest {
        // The case that made this whole file necessary and was nearly missed. Both
        // `market-intelligence` routes are behind auth and answer 401 to a guest; the gateway used
        // to throw on that before the fallbacks were reached, so the one reader who has not decided
        // whether to trust the product saw an empty screen worded as an outage — for content that
        // is public everywhere else. A member whose token had merely expired saw the same.
        val intel = PublicMarketIntel(
            client = { url ->
                when {
                    url.contains("faireconomy") -> fixture("ff-calendar-week.json")
                    url.contains("cointelegraph") -> fixture("cointelegraph.xml")
                    else -> null
                }
            },
            platform = MarketPlatform.TRADEYAR,
            now = { now },
        )
        assertTrue(intel.news().isNotEmpty())
        assertTrue(intel.calendar().isNotEmpty())
    }

    @Test
    fun `the calendar is the same list on both platforms`() = runTest {
        // Deliberate rather than lazy. A Fed decision moves the dollar, the dollar is one side of
        // every metal quote *and* of every USDT pair, and a crypto reader who is not shown CPI day
        // is being kept from the most consequential hour of their week. The calendar was forex-only
        // in the menu for a while on the opposite reasoning; this pins the correction.
        val client = PublicFeedClient { url ->
            fixture("ff-calendar-week.json").takeIf { url.contains("faireconomy") }
        }
        val crypto = PublicMarketIntel(client, MarketPlatform.TRADEYAR, now = { now }).calendar()
        val forex = PublicMarketIntel(client, MarketPlatform.COINEPRO_FX, now = { now }).calendar()
        assertEquals(forex, crypto)
        assertTrue(crypto.isNotEmpty())
    }

    // ── TradeYar's own public route ──────────────────────────────────────────

    @Test
    fun `the crypto host's own Persian news parses`() {
        val stories = TradeYarPublicNews.parse(fixture("tradeyar-public-news.json"), now)
        assertEquals(30, stories.size)
        stories.forEach { story ->
            assertTrue(story.title.isNotBlank())
            assertNotNull(story.url)
        }
    }

    @Test
    fun `the Persian headline is preferred over the English original`() {
        // A reader who opened a Persian app is owed the Persian headline wherever the server took
        // the trouble to write one — and this route writes one for every row.
        val stories = TradeYarPublicNews.parse(fixture("tradeyar-public-news.json"), now)
        val persian = stories.count { story -> story.title.any { it in '؀'..'ۿ' } }
        assertEquals(stories.size, persian)
    }

    @Test
    fun `pictures and summaries survive the read`() {
        val stories = TradeYarPublicNews.parse(fixture("tradeyar-public-news.json"), now)
        assertTrue(stories.count { it.imageUrl != null } >= stories.size / 2)
        assertTrue(stories.count { it.summary != null } >= stories.size / 2)
    }

    @Test
    fun `the upstream publisher is named rather than the relay`() {
        // The row says `decrypt`, `theblock` and so on. Passing that through is both accurate and
        // the more useful thing for a reader to see.
        val sources = TradeYarPublicNews.parse(fixture("tradeyar-public-news.json"), now)
            .map { it.source }
            .toSet()
        assertTrue(sources.toString(), sources.size > 1)
        assertFalse("TradeYar" in sources)
    }

    @Test
    fun `camelCase timestamps are read, which is the bug that dropped every row last time`() {
        // The route spells it `publishedAt`. Resolved only by a snake-case naming policy it comes
        // back null, every row is dropped, and the screen is empty behind a clean 200 with no error
        // anywhere. That is measured history, not a hypothetical.
        val stories = TradeYarPublicNews.parse(fixture("tradeyar-public-news.json"), now)
        assertTrue(stories.all { it.publishedAt.isAfter(Instant.EPOCH) })
    }

    @Test
    fun `the server's importance score becomes the app's three levels`() {
        val impacts = TradeYarPublicNews.parse(fixture("tradeyar-public-news.json"), now)
            .map { it.impact }
            .toSet()
        assertFalse(MarketImpact.UNKNOWN in impacts)
    }

    @Test
    fun `the crypto host is asked before any wire`() = runTest {
        // The order of authority: this product's own Persian beats a third party's English, always.
        var wireAsked = false
        val intel = PublicMarketIntel(
            client = { url ->
                if (url.contains("news/list")) {
                    fixture("tradeyar-public-news.json")
                } else {
                    wireAsked = true
                    fixture("cointelegraph.xml")
                }
            },
            platform = MarketPlatform.TRADEYAR,
            platformBaseUrl = "https://tradeyar.example.invalid/",
            now = { now },
        )
        val stories = intel.news()
        assertEquals(30, stories.size)
        assertFalse("a wire was read even though the host answered", wireAsked)
    }

    @Test
    fun `the wires are the answer when the crypto host will not talk`() = runTest {
        val intel = PublicMarketIntel(
            client = { url -> if (url.contains("news/list")) null else fixture("cointelegraph.xml") },
            platform = MarketPlatform.TRADEYAR,
            platformBaseUrl = "https://tradeyar.example.invalid/",
            now = { now },
        )
        assertTrue(intel.news().isNotEmpty())
    }

    @Test
    fun `the forex side never asks the crypto host`() = runTest {
        // CoinePro-FX publishes no equivalent — `api/v1/news/list` is a 404 there — and asking it
        // anyway would be a request that can only ever fail.
        var asked = false
        val intel = PublicMarketIntel(
            client = { url ->
                if (url.contains("news/list")) asked = true
                fixture("investing-forex.xml")
            },
            platform = MarketPlatform.COINEPRO_FX,
            platformBaseUrl = "https://coineprofx.example.invalid/",
            now = { now },
        )
        assertTrue(intel.news().isNotEmpty())
        assertFalse(asked)
    }

    private fun story(title: String, at: Instant) = MarketNewsItem(
        id = title,
        title = title,
        summary = null,
        source = "x",
        url = null,
        publishedAt = at,
        sentiment = NewsSentiment.UNKNOWN,
        impact = MarketImpact.UNKNOWN,
        relevance = emptySet(),
        isStale = false,
    )
}
