package com.coinepro.feature.news

import com.coinepro.core.guest.GuestHeadline
import com.coinepro.core.marketintel.MarketImpact
import com.coinepro.core.marketintel.MarketNewsItem
import com.coinepro.core.marketintel.MarketRelevance
import com.coinepro.core.marketintel.NewsSentiment
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two feeds becoming one story, and the text becoming paragraphs.
 *
 * This is the seam the whole reader stands on: one screen now draws a member's story and a guest's,
 * and everything that makes that safe is a mapping rule rather than a layout. What is pinned here
 * is that nothing is invented on the way in — no publisher's name for a story that arrived without
 * one, no date for a string that would not parse — and that a picture and a body survive the trip,
 * since a field dropped in a mapper is exactly the failure that produced this work.
 */
class NewsStoryTest {

    private fun item(
        imageUrl: String? = null,
        body: String? = null,
    ) = MarketNewsItem(
        id = "fx-1",
        title = "فدرال رزرو نرخ بهره را بدون تغییر نگه داشت",
        summary = "کمیتهٔ بازار باز رأی به توقف داد.",
        source = "ForexLive",
        url = "https://example.com/a",
        publishedAt = Instant.ofEpochSecond(1_756_000_000),
        sentiment = NewsSentiment.BULLISH,
        impact = MarketImpact.HIGH,
        relevance = setOf(MarketRelevance.GOLD),
        isStale = false,
        imageUrl = imageUrl,
        body = body,
    )

    @Test
    fun `a members story arrives whole, picture and text included`() {
        val story = item(imageUrl = "https://cdn.example.com/gold.jpg", body = "بند اول.").asStory()

        assertEquals("https://cdn.example.com/gold.jpg", story.imageUrl)
        assertEquals("بند اول.", story.body)
        assertEquals("ForexLive", story.source)
        assertEquals(Instant.ofEpochSecond(1_756_000_000), story.publishedAt)
        assertEquals(MarketImpact.HIGH, story.impact)
        assertEquals(setOf(MarketRelevance.GOLD), story.relevance)
    }

    @Test
    fun `a story with no picture and no body says so rather than carrying empty text`() {
        val story = item().asStory()
        assertNull(story.imageUrl)
        assertNull(story.body)
    }

    @Test
    fun `a guest headline keeps what the public route sends and invents nothing else`() {
        val story = GuestHeadline(
            slug = "ty-2026-08-25-001",
            title = "بایننس لیست شدن توکن X را اعلام کرد",
            summary = "معاملات از ساعت ۱۲ به وقت گرینویچ آغاز می‌شود.",
            source = "CoinDesk",
            publishedAt = "2026-08-25T08:30:00Z",
            url = "https://www.coindesk.com/a",
        ).asStory()

        assertEquals("ty-2026-08-25-001", story.id)
        assertEquals("CoinDesk", story.source)
        assertEquals(Instant.parse("2026-08-25T08:30:00Z"), story.publishedAt)
        // The route has always sent this and the mapping used to drop it, which is why a guest's
        // story had no way through to the publisher while a member's did.
        assertEquals("https://www.coindesk.com/a", story.url)
        // The public route sends none of these, and none of them is guessed from `importance`.
        assertNull(story.imageUrl)
        assertNull(story.body)
        assertEquals(MarketImpact.UNKNOWN, story.impact)
        assertEquals(NewsSentiment.UNKNOWN, story.sentiment)
        assertTrue(story.relevance.isEmpty())
    }

    @Test
    fun `a guest link is carried as sent and its scheme is judged where it is used`() {
        // `core:guest` passes `sourceUrl` through raw, unlike the signed-in gateway which has
        // already applied `safeHttpsUrl`. So the mapping keeps whatever arrived and the refusal
        // happens at the point of use — which is the check that has to exist anyway for an address
        // restored from a saved record written by an older build.
        val cleartext = GuestHeadline(
            slug = "ty-3",
            title = "عنوان",
            summary = null,
            source = "CoinDesk",
            publishedAt = null,
            url = "http://www.coindesk.com/a",
        ).asStory()

        assertEquals("http://www.coindesk.com/a", cleartext.url)
        // No pill is drawn for it, because this is what the pill is drawn from.
        assertNull(NewsHandoff.safeUrl(cleartext.url))
    }

    @Test
    fun `a publication time that will not parse becomes absent rather than the epoch`() {
        val story = GuestHeadline(
            slug = "ty-2",
            title = "عنوان",
            summary = null,
            source = null,
            publishedAt = "tomorrow morning",
            url = null,
        ).asStory()

        // Absent, so the byline draws a source-only line. The epoch would print as a day in 1970
        // beside a market headline, which is the one wrong answer available here.
        assertNull(story.publishedAt)
        assertNull(story.source)
    }

    @Test
    fun `a story with no publication time cannot be saved`() {
        val story = NewsStory(id = "ty-3", title = "عنوان", summary = null)
        assertNull(story.asSavedArticle(Instant.ofEpochSecond(1_756_000_500)))
    }

    @Test
    fun `a saved story keeps its lede so reopening it is not an empty page`() {
        val saved = item().asStory().asSavedArticle(Instant.ofEpochSecond(1_756_000_500))
        assertEquals("کمیتهٔ بازار باز رأی به توقف داد.", saved?.summary)
        assertEquals("کمیتهٔ بازار باز رأی به توقف داد.", saved?.asStory()?.summary)
    }

    @Test
    fun `a blank line breaks a paragraph and a single newline does not`() {
        val paragraphs = newsParagraphs("بند اول\nهمان بند، ادامه\n\nبند دوم")
        assertEquals(listOf("بند اول\nهمان بند، ادامه", "بند دوم"), paragraphs)
    }

    @Test
    fun `text that is only whitespace produces no paragraphs at all`() {
        assertTrue(newsParagraphs(null).isEmpty())
        assertTrue(newsParagraphs("   \n\n  ").isEmpty())
    }
}
