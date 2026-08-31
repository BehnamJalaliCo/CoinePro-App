package com.coinepro.core.marketintel

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a public source is allowed to fill a section, and when the server's refusal still wins.
 *
 * This is the half of the news-and-calendar work that is a *decision* rather than a parser, and it
 * is the half that was wrong for five rounds. Every earlier fix was to the reading of a wire that
 * turned out to have nothing on it; nothing asked what the app should do about a section the server
 * had left empty, and the answer it was using — show an empty screen — is what the reader kept
 * reporting.
 */
class FallbackPolicyTest {

    private val empty = MarketIntelSnapshot(news = emptyList(), calendar = emptyList(), serverTime = null)

    private fun story(title: String) = MarketNewsItem(
        id = title,
        title = title,
        summary = null,
        source = "Investing.com",
        url = null,
        publishedAt = Instant.parse("2026-08-30T12:00:00Z"),
        sentiment = NewsSentiment.UNKNOWN,
        impact = MarketImpact.UNKNOWN,
        relevance = emptySet(),
        isStale = false,
    )

    private fun event(title: String) = EconomicEvent(
        id = title,
        title = title,
        country = "آمریکا",
        currency = "USD",
        scheduledAt = Instant.parse("2026-09-04T12:30:00Z"),
        impact = MarketImpact.HIGH,
        actual = null,
        forecast = "75K",
        previous = "73K",
        relevance = emptySet(),
        isStale = false,
    )

    // ── news ─────────────────────────────────────────────────────────────────

    @Test
    fun `the server's own stories are never replaced`() {
        // The rule the whole design rests on. The backends serve Persian and a wire does not; the
        // day either starts publishing, its answer must win with no code change and no setting.
        val served = empty.copy(news = listOf(story("خبر سرور")))
        assertEquals(served, served.withPublicNews(listOf(story("a wire story"))))
    }

    @Test
    fun `an empty section is filled from the wires`() {
        val filled = empty.withPublicNews(listOf(story("one"), story("two")))
        assertEquals(2, filled.news.size)
        assertEquals(NetworkMarketIntelGateway.PUBLIC_NEWS_ROUTE, filled.newsSource?.route)
    }

    @Test
    fun `nothing from the wires either leaves the snapshot alone`() {
        assertEquals(empty, empty.withPublicNews(emptyList()))
    }

    @Test
    fun `the outcome says nothing was dropped, because nothing was`() {
        // `feedUnreadable` in feature:news shows «هیچ خبری خوانده نشد» on received > 0 && kept == 0,
        // and `feedShortfall` warns when they differ. Neither line belongs on a full screen.
        val source = empty.withPublicNews(listOf(story("one"))).newsSource!!
        assertEquals(source.received, source.kept)
        assertEquals(0, source.dropped)
        assertNull(source.failure)
    }

    // ── the calendar ─────────────────────────────────────────────────────────

    @Test
    fun `the server's own events are never replaced`() {
        val served = empty.copy(calendar = listOf(event("رویداد سرور")))
        assertEquals(served, served.withPublicCalendar(listOf(event("a published event"))))
    }

    @Test
    fun `an empty calendar is filled from the published week`() {
        val filled = empty.withPublicCalendar(listOf(event("Non-Farm")))
        assertEquals(1, filled.calendar.size)
        assertEquals(PublicCalendarFeed.URL, filled.calendarSource?.route)
    }

    @Test
    fun `when nothing has a calendar, the academy's own account of why is kept`() {
        // The evidence the diagnostics screen exists to show. An empty calendar that cannot say
        // which route answered is the thing this module was written to stop producing.
        val academy = CalendarSourceOutcome(received = 0, route = "academy/bn/calendar")
        val result = empty.withPublicCalendar(emptyList(), academy)
        assertTrue(result.calendar.isEmpty())
        assertEquals("academy/bn/calendar", result.calendarSource?.route)
    }

    // ── the refusal ──────────────────────────────────────────────────────────

    @Test
    fun `a refusal with stories behind it is not the reader's business`() {
        // The guest case, and the expired-token case, which are the same case. Both
        // `market-intelligence` routes answer 401 to a reader who is not signed in; throwing there
        // meant the fallbacks were never reached and the one reader who has not yet decided to trust
        // the product got an outage message over content that is public everywhere else.
        assertFalse(empty.copy(news = listOf(story("wire"))).shouldRethrow())
        assertFalse(empty.copy(calendar = listOf(event("Non-Farm"))).shouldRethrow())
    }

    @Test
    fun `a refusal with nothing behind it is exactly what to say`() {
        // The other half, and it must stay true: when there is genuinely nothing to show, the
        // reader gets the server's own words and the request table gets the code. Swallowing that
        // would trade one silent screen for another.
        assertTrue(empty.shouldRethrow())
    }
}
