package com.coinepro.feature.news

import com.coinepro.core.marketintel.MarketImpact
import com.coinepro.core.marketintel.MarketNewsItem
import com.coinepro.core.marketintel.MarketRelevance
import com.coinepro.core.marketintel.NewsSentiment
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the reading page offers next, which is the whole of whether anybody stays in this screen.
 *
 * The rule being pinned is not the length of the list — that is a product decision and a test that
 * fixed it would be a test arguing with the designer. What is pinned is that the suggestion is
 * *about the same market*, that a story never suggests itself, and that a general-market headline
 * still gets somewhere to go instead of a blank space.
 */
class RelatedStoriesTest {

    private var clock = 1_756_000_000L

    private fun story(id: String, vararg relevance: MarketRelevance): MarketNewsItem {
        clock += 600
        return MarketNewsItem(
            id = id,
            title = "خبر $id",
            summary = null,
            source = "ForexLive",
            url = null,
            publishedAt = Instant.ofEpochSecond(clock),
            sentiment = NewsSentiment.UNKNOWN,
            impact = MarketImpact.UNKNOWN,
            relevance = relevance.toSet(),
            isStale = false,
        )
    }

    @Test
    fun `a gold story suggests gold stories and not the crypto feed`() {
        val subject = story("gold-1", MarketRelevance.GOLD)
        val feed = listOf(
            subject,
            story("gold-2", MarketRelevance.GOLD),
            story("crypto-1", MarketRelevance.CRYPTO),
            story("silver-1", MarketRelevance.SILVER),
        )
        val related = relatedTo(subject, feed).map(MarketNewsItem::id)
        assertEquals(listOf("gold-2"), related)
    }

    @Test
    fun `a story never suggests itself`() {
        val subject = story("gold-1", MarketRelevance.GOLD)
        val feed = listOf(subject, story("gold-2", MarketRelevance.GOLD))
        assertFalse("gold-1" in relatedTo(subject, feed).map(MarketNewsItem::id))
    }

    @Test
    fun `a story tagged with two markets matches on either of them`() {
        val subject = story("both", MarketRelevance.GOLD, MarketRelevance.SILVER)
        val feed = listOf(
            subject,
            story("silver-1", MarketRelevance.SILVER),
            story("crypto-1", MarketRelevance.CRYPTO),
        )
        assertEquals(listOf("silver-1"), relatedTo(subject, feed).map(MarketNewsItem::id))
    }

    @Test
    fun `a general market headline is offered the rest of the feed rather than nothing`() {
        val subject = story("general")
        val feed = listOf(subject, story("gold-1", MarketRelevance.GOLD))
        assertEquals(listOf("gold-1"), relatedTo(subject, feed).map(MarketNewsItem::id))
    }

    @Test
    fun `the newest matching stories come first`() {
        val subject = story("gold-1", MarketRelevance.GOLD)
        val older = story("gold-old", MarketRelevance.GOLD)
        val newer = story("gold-new", MarketRelevance.GOLD)
        // Handed to it in the wrong order on purpose: the feed's own order is not a guarantee.
        val related = relatedTo(subject, listOf(subject, older, newer)).map(MarketNewsItem::id)
        assertEquals(listOf("gold-new", "gold-old"), related)
    }

    @Test
    fun `a story with nothing to suggest gets an empty list rather than a stray entry`() {
        val subject = story("gold-1", MarketRelevance.GOLD)
        val feed = listOf(subject, story("crypto-1", MarketRelevance.CRYPTO))
        assertTrue(relatedTo(subject, feed).isEmpty())
    }
}
