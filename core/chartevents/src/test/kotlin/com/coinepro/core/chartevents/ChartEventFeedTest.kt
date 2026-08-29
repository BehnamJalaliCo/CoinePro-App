package com.coinepro.core.chartevents

import com.coinepro.core.chart.ChartEvent
import com.coinepro.core.chart.EventKind
import com.coinepro.core.chart.Importance
import com.coinepro.core.marketintel.EconomicEvent
import com.coinepro.core.marketintel.MarketImpact
import com.coinepro.core.marketintel.MarketIntelSnapshot
import com.coinepro.core.marketintel.MarketNewsItem
import com.coinepro.core.marketintel.MarketRelevance
import com.coinepro.core.marketintel.NewsSentiment
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartEventFeedTest {

    private fun news(
        id: String,
        at: String,
        relevance: Set<MarketRelevance>,
        impact: MarketImpact = MarketImpact.HIGH,
        summary: String? = "متن کامل",
    ) = MarketNewsItem(
        id = id,
        title = "تیتر $id",
        summary = summary,
        source = "رویترز",
        url = null,
        publishedAt = Instant.parse(at),
        sentiment = NewsSentiment.NEUTRAL,
        impact = impact,
        relevance = relevance,
        isStale = false,
    )

    private fun release(
        id: String,
        at: String,
        impact: MarketImpact = MarketImpact.MEDIUM,
        actual: String? = "3.2%",
        forecast: String? = "3.1%",
        previous: String? = "3.0%",
    ) = EconomicEvent(
        id = id,
        title = "شاخص $id",
        country = "آمریکا",
        currency = "USD",
        scheduledAt = Instant.parse(at),
        impact = impact,
        actual = actual,
        forecast = forecast,
        previous = previous,
        relevance = setOf(MarketRelevance.GOLD),
        isStale = false,
    )

    private fun snapshot(
        news: List<MarketNewsItem> = emptyList(),
        calendar: List<EconomicEvent> = emptyList(),
    ) = MarketIntelSnapshot(news = news, calendar = calendar, serverTime = null)

    @Test
    fun `a headline becomes a news event at the second it was published, with who said it`() {
        val item = news("n1", "2026-08-23T10:00:00Z", setOf(MarketRelevance.GOLD))

        val event = item.toChartEvent()

        assertEquals(Instant.parse("2026-08-23T10:00:00Z").epochSecond, event.at)
        assertEquals(EventKind.NEWS, event.kind)
        assertEquals("رویترز", event.source)
        assertEquals("متن کامل", event.detail)
    }

    @Test
    fun `a release carries its figures as the source published them`() {
        val event = release("CPI", "2026-08-23T12:30:00Z").toChartEvent()

        assertEquals(EventKind.ECONOMIC, event.kind)
        // Latin digits, untouched: these are market figures, and reformatting one would risk
        // printing a number nobody published.
        assertEquals("واقعی 3.2% · پیش‌بینی 3.1% · قبلی 3.0%", event.detail)
        assertEquals("آمریکا · USD", event.source)
    }

    @Test
    fun `a release with no figure yet has no figure line rather than a row of dashes`() {
        val event = release("CPI", "2026-08-23T12:30:00Z", actual = null, forecast = null, previous = null)
            .toChartEvent()

        assertNull(event.detail)
    }

    @Test
    fun `an impact the source never declared is drawn at the quietest weight, never a louder one`() {
        val event = release("CPI", "2026-08-23T12:30:00Z", impact = MarketImpact.UNKNOWN).toChartEvent()

        assertEquals(Importance.LOW, event.importance)
    }

    @Test
    fun `news tagged for another market stays off this instrument's chart`() {
        val document = snapshot(
            news = listOf(
                news("gold", "2026-08-23T10:00:00Z", setOf(MarketRelevance.GOLD)),
                news("coin", "2026-08-23T11:00:00Z", setOf(MarketRelevance.CRYPTO)),
            ),
        )

        val onGold = document.chartEventsFor("XAUUSD").map(ChartEvent::title)

        assertEquals(listOf("تیتر gold"), onGold)
    }

    @Test
    fun `news the server tagged with no market at all belongs to every chart`() {
        val document = snapshot(news = listOf(news("general", "2026-08-23T10:00:00Z", emptySet())))

        assertTrue(document.chartEventsFor("XAUUSD").isNotEmpty())
        assertTrue(document.chartEventsFor("BTCUSDT").isNotEmpty())
        // Even an instrument the app has no market tag for: general news is general.
        assertTrue(document.chartEventsFor("EURUSD").isNotEmpty())
    }

    @Test
    fun `the calendar is not filtered by instrument, because macro moves both markets`() {
        val document = snapshot(calendar = listOf(release("CPI", "2026-08-23T12:30:00Z")))

        assertEquals(1, document.chartEventsFor("BTCUSDT").size)
        assertEquals(1, document.chartEventsFor("XAUUSD").size)
    }

    @Test
    fun `everything a snapshot yields comes back in the order it happened`() {
        val document = snapshot(
            news = listOf(news("late", "2026-08-23T15:00:00Z", emptySet())),
            calendar = listOf(release("CPI", "2026-08-23T12:30:00Z")),
        )

        val times = document.chartEventsFor("XAUUSD").map(ChartEvent::at)

        assertEquals(times.sorted(), times)
    }

    @Test
    fun `only the kinds a backend actually publishes are offered as filters`() {
        assertEquals(setOf(EventKind.NEWS, EventKind.ECONOMIC), SERVED_EVENT_KINDS)
    }

    @Test
    fun `each market opens its own reference instrument, and an untagged story opens none`() {
        assertEquals("XAUUSD", ChartEventSymbols.symbolFor(setOf(MarketRelevance.GOLD)))
        assertEquals("XAGUSD", ChartEventSymbols.symbolFor(setOf(MarketRelevance.SILVER)))
        assertEquals("BTCUSDT", ChartEventSymbols.symbolFor(setOf(MarketRelevance.CRYPTO)))
        assertNull(ChartEventSymbols.symbolFor(emptySet()))
        // A story tagged for two markets always opens the same one of them, not whichever the set
        // happened to iterate first.
        assertEquals(
            "XAUUSD",
            ChartEventSymbols.symbolFor(setOf(MarketRelevance.CRYPTO, MarketRelevance.GOLD)),
        )
    }
}
