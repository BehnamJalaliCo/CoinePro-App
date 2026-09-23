package com.coinepro.core.marketintel

import com.coinepro.core.model.MarketPlatform
import java.time.Instant
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How old what is on screen actually is — run Τ2, B6.
 *
 * The controller already keeps a section rather than blanking it when a refresh comes back empty,
 * which is right: the release times have not changed, only this fetch's luck has. What it did not
 * do is **say so**, and a held calendar is indistinguishable from a quiet week. These are the
 * assertions that it now says so, per section, because the two halves are held independently.
 */
class MarketIntelHeldTest {

    private fun news(id: String) = MarketNewsItem(
        id = id,
        title = id,
        summary = null,
        source = "s",
        url = null,
        publishedAt = Instant.ofEpochSecond(1_700_000_000L),
        sentiment = NewsSentiment.NEUTRAL,
        impact = MarketImpact.LOW,
        relevance = emptySet(),
        isStale = false,
    )

    private fun event(id: String) = EconomicEvent(
        id = id,
        title = id,
        country = "US",
        currency = "USD",
        scheduledAt = Instant.ofEpochSecond(1_700_000_000L),
        impact = MarketImpact.HIGH,
        actual = null,
        forecast = null,
        previous = null,
        relevance = emptySet(),
        isStale = false,
    )

    private class Feed(private val answers: MutableList<Result<MarketIntelSnapshot>>) : MarketIntelGateway {
        override suspend fun snapshot(): MarketIntelSnapshot = answers.removeAt(0).getOrThrow()
    }

    private fun snapshot(news: List<MarketNewsItem>, calendar: List<EconomicEvent>) =
        MarketIntelSnapshot(
            news = news,
            calendar = calendar,
            serverTime = null,
            platform = MarketPlatform.TRADEYAR,
        )

    private fun controller(
        answers: MutableList<Result<MarketIntelSnapshot>>,
        clock: () -> Long,
    ): MarketIntelController {
        val scope = TestScope(UnconfinedTestDispatcher())
        return MarketIntelController(gateway = Feed(answers), scope = scope, now = clock)
    }

    @Test
    fun `a fresh fetch is not held`() = runTest {
        var now = 1_000L
        val controller = controller(
            mutableListOf(Result.success(snapshot(listOf(news("a")), listOf(event("e"))))),
            { now },
        )
        controller.refresh()
        val state = controller.state.value
        assertEquals(1_000L, state.newsFetchedAtEpochMillis)
        assertEquals(1_000L, state.calendarFetchedAtEpochMillis)
        assertFalse(state.newsIsHeld)
        assertFalse(state.calendarIsHeld)
    }

    @Test
    fun `a held calendar keeps its own age and says it is held`() = runTest {
        // The assertion this file exists for. The second fetch answered with news and no calendar;
        // the calendar on screen is the first fetch's and must not claim the second's freshness.
        var now = 1_000L
        val controller = controller(
            mutableListOf(
                Result.success(snapshot(listOf(news("a")), listOf(event("e")))),
                Result.success(snapshot(listOf(news("b")), emptyList())),
            ),
            { now },
        )
        controller.refresh()
        now = 60_000L
        controller.refresh()
        val state = controller.state.value
        assertEquals(60_000L, state.newsFetchedAtEpochMillis)
        assertEquals(1_000L, state.calendarFetchedAtEpochMillis)
        assertFalse(state.newsIsHeld)
        assertTrue(state.calendarIsHeld)
    }

    @Test
    fun `a held news list is the same the other way round`() = runTest {
        var now = 1_000L
        val controller = controller(
            mutableListOf(
                Result.success(snapshot(listOf(news("a")), listOf(event("e")))),
                Result.success(snapshot(emptyList(), listOf(event("f")))),
            ),
            { now },
        )
        controller.refresh()
        now = 60_000L
        controller.refresh()
        val state = controller.state.value
        assertTrue(state.newsIsHeld)
        assertFalse(state.calendarIsHeld)
    }

    @Test
    fun `a fetch that threw leaves everything held`() = runTest {
        // The case where saying so matters most: nothing on screen changed, and without this the
        // page looks exactly like a page that had just been refreshed.
        var now = 1_000L
        val controller = controller(
            mutableListOf(
                Result.success(snapshot(listOf(news("a")), listOf(event("e")))),
                Result.failure(IllegalStateException("timeout")),
            ),
            { now },
        )
        controller.refresh()
        now = 60_000L
        controller.refresh()
        val state = controller.state.value
        assertTrue(state.failed)
        assertTrue(state.newsIsHeld)
        assertTrue(state.calendarIsHeld)
        // And the age is the content's own, not the failed attempt's.
        assertEquals(1_000L, state.newsFetchedAtEpochMillis)
    }

    @Test
    fun `a reader who has fetched nothing has no age and nothing held`() {
        val state = MarketIntelState()
        assertNull(state.newsFetchedAtEpochMillis)
        // Not held: there is nothing on screen to be held. An empty first paint is a loading
        // state, and «ذخیره‌شده» over it would be the app describing a cache it does not have.
        assertFalse(state.newsIsHeld)
        assertFalse(state.calendarIsHeld)
    }
}
