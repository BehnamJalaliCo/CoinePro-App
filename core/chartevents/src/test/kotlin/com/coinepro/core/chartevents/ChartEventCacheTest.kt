package com.coinepro.core.chartevents

import com.coinepro.core.chart.ChartEvent
import com.coinepro.core.chart.EventKind
import com.coinepro.core.chart.Importance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChartEventCacheTest {

    private fun event(at: Long) = ChartEvent(
        at = at,
        kind = EventKind.NEWS,
        title = "تیتر",
        detail = null,
        importance = Importance.MEDIUM,
    )

    @Test
    fun `a window inside one already fetched is answered without going back to the feed`() {
        val cache = ChartEventCache()
        cache.put("XAUUSD", fromSeconds = 1_000, toSeconds = 5_000, events = listOf(event(2_000)), now = 100)

        val held = cache.hit("XAUUSD", fromSeconds = 2_000, toSeconds = 4_000, now = 120)

        assertNotNull(held)
        assertEquals(listOf(2_000L), held?.map(ChartEvent::at))
    }

    @Test
    fun `a window reaching past what was fetched is a miss on either side`() {
        val cache = ChartEventCache()
        cache.put("XAUUSD", 1_000, 5_000, listOf(event(2_000)), now = 100)

        assertNull(cache.hit("XAUUSD", 900, 4_000, now = 100))
        assertNull(cache.hit("XAUUSD", 2_000, 5_001, now = 100))
    }

    @Test
    fun `a quiet window is remembered as empty rather than as nothing cached`() {
        // The case the cache is most needed for: refetching a week with no news on every frame of a
        // pan is the same cost as refetching a busy one, and nobody would see it happening.
        val cache = ChartEventCache()
        cache.put("BTCUSDT", 1_000, 5_000, emptyList(), now = 100)

        assertEquals(emptyList<ChartEvent>(), cache.hit("BTCUSDT", 2_000, 3_000, now = 100))
    }

    @Test
    fun `an entry older than its freshness is a miss, so a breaking headline still arrives`() {
        val cache = ChartEventCache(freshnessSeconds = 300)
        cache.put("BTCUSDT", 1_000, 5_000, listOf(event(2_000)), now = 100)

        assertNotNull(cache.hit("BTCUSDT", 2_000, 3_000, now = 399))
        assertNull(cache.hit("BTCUSDT", 2_000, 3_000, now = 400))
    }

    @Test
    fun `one symbol's events never answer for another, whatever the case of the ticker`() {
        val cache = ChartEventCache()
        cache.put("btcusdt", 1_000, 5_000, listOf(event(2_000)), now = 100)

        assertNotNull(cache.hit("BTCUSDT", 2_000, 3_000, now = 100))
        assertNull(cache.hit("XAUUSD", 2_000, 3_000, now = 100))
    }

    @Test
    fun `the oldest symbol is dropped once the cache is full, and the newest is kept`() {
        val cache = ChartEventCache(symbols = 2)
        cache.put("A", 0, 10, listOf(event(1)), now = 0)
        cache.put("B", 0, 10, listOf(event(2)), now = 0)
        cache.put("C", 0, 10, listOf(event(3)), now = 0)

        assertNull(cache.hit("A", 0, 10, now = 0))
        assertNotNull(cache.hit("B", 0, 10, now = 0))
        assertNotNull(cache.hit("C", 0, 10, now = 0))
    }

    @Test
    fun `clearing leaves nothing behind for the next reader`() {
        val cache = ChartEventCache()
        cache.put("XAUUSD", 0, 10, listOf(event(1)), now = 0)

        cache.clear()

        assertNull(cache.hit("XAUUSD", 0, 10, now = 0))
    }
}
