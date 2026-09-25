package com.coinepro.feature.news

import com.coinepro.core.marketintel.MarketImpact
import com.coinepro.core.marketintel.NewsSentiment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The news filters (5.17.0): each narrows on its own, they combine, and a ticker finds its market's words. */
class NewsFilterTest {

    private val stories = listOf(
        NewsStory("1", "Gold climbs as the dollar slips", null, source = "Reuters", impact = MarketImpact.HIGH, sentiment = NewsSentiment.BULLISH),
        NewsStory("2", "طلا در محدوده‌ی حمایت", null, source = "Reuters", impact = MarketImpact.LOW, sentiment = NewsSentiment.NEUTRAL),
        NewsStory("3", "Bitcoin ETF outflows deepen", null, source = "CoinDesk", impact = MarketImpact.HIGH, sentiment = NewsSentiment.BEARISH),
        NewsStory("4", "Euro steady before ECB", null, source = null),
    )

    @Test
    fun `an empty filter is not active and passes everything`() {
        assertFalse(NewsFilter().active)
        assertEquals(stories, NewsFilter().apply(stories))
    }

    @Test
    fun `impact, sentiment and source each narrow and combine`() {
        assertEquals(listOf("1", "3"), NewsFilter(highImpact = true).apply(stories).map { it.id })
        assertEquals(listOf("3"), NewsFilter(sentiment = NewsSentiment.BEARISH).apply(stories).map { it.id })
        assertEquals(listOf("1", "2"), NewsFilter(source = "reuters").apply(stories).map { it.id })
        assertEquals(listOf("1"), NewsFilter(highImpact = true, source = "Reuters").apply(stories).map { it.id })
    }

    @Test
    fun `a ticker finds the stories that name its market in either language`() {
        assertEquals(listOf("1", "2"), NewsFilter(query = "xau/usd").apply(stories).map { it.id })
        assertEquals(listOf("3"), NewsFilter(query = "BTCUSDT").apply(stories).map { it.id })
        assertEquals(listOf("4"), NewsFilter(query = "ecb").apply(stories).map { it.id })
    }

    @Test
    fun `sources are ranked by count and the guest feed reads as unclassified`() {
        assertEquals(listOf("Reuters", "CoinDesk"), NewsFilter.sourcesOf(stories))
        assertTrue(NewsFilter.classified(stories))
        assertFalse(NewsFilter.classified(listOf(stories[3])))
    }
}
