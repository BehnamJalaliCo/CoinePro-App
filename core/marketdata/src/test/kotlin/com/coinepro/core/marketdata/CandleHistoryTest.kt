package com.coinepro.core.marketdata

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paging backwards until something stops it, and being able to say which something.
 *
 * The four ends are the whole subject. Three of them are not failures — the venue runs out, the
 * ceiling is reached, the call's budget is spent — and the fourth, a page that adds nothing, is the
 * only way to discover a route that takes no `before` at all. TradeYar's public route is exactly
 * that route and it is what a guest's chart is reading, so "keeps handing back the newest page" is
 * a live production behaviour rather than a hypothetical: without the stop, a fill against it is an
 * infinite loop of identical requests against a server answering perfectly.
 */
class CandleHistoryTest {

    /**
     * A venue with a finite history and honest `before` paging.
     *
     * [bars] bars of one hour, oldest at `firstAt`. `before` is exclusive, which is the promise
     * TradeYar's mobile route makes and the one this app's gateways normalise CoinePro-FX onto.
     */
    private class FiniteVenue(
        private val bars: Int,
        private val firstAt: Long = 1_600_000_000L,
        private val step: Long = 3_600L,
        override val nativeTimeframes: List<Timeframe> = SERVER_NATIVE_TIMEFRAMES,
    ) : CandleGateway {
        var requests = 0

        private fun barAt(index: Int) = OhlcBar(
            t = firstAt + index * step,
            o = 1.0 + index,
            h = 2.0 + index,
            l = index.toDouble(),
            c = 1.5 + index,
            v = 1.0,
        )

        override suspend fun load(
            symbol: String,
            timeframe: Timeframe,
            limit: Int,
            before: Long?,
        ): CandlePage {
            requests++
            val end = if (before == null) bars else ((before - firstAt) / step).toInt().coerceIn(0, bars)
            val start = (end - limit).coerceAtLeast(0)
            val page = (start until end).map(::barAt)
            return CandlePage(
                symbol = symbol,
                timeframe = timeframe,
                candles = page,
                oldest = page.firstOrNull()?.t,
                hasMore = start > 0,
            )
        }
    }

    /** The public route: it takes no `before`, so every page is the newest page. */
    private class NoPagingVenue(private val bars: Int) : CandleGateway {
        var requests = 0

        override suspend fun load(
            symbol: String,
            timeframe: Timeframe,
            limit: Int,
            before: Long?,
        ): CandlePage {
            requests++
            val page = (0 until minOf(bars, limit)).map { index ->
                OhlcBar(t = 1_600_000_000L + index * 3_600L, o = 1.0, h = 2.0, l = 0.5, c = 1.5, v = 1.0)
            }
            return CandlePage(symbol = symbol, timeframe = timeframe, candles = page, hasMore = true)
        }
    }

    private val hourly = ChartInterval.Preset(Timeframe.H1)

    @Test
    fun `a fill walks backwards to the end of the venue and says that is why it stopped`() = runTest {
        val venue = FiniteVenue(bars = 1_918)
        val archive = InMemoryCandleArchive()

        val depth = venue.fillHistory("BTCUSDT", hourly, archive, pageBars = 500, maxPages = 12)

        assertEquals(HistoryStop.VENUE_EXHAUSTED, depth.stop)
        assertEquals(1_918, depth.bars)
        assertTrue(depth.venueExhausted)
        assertFalse(depth.reachedCeiling)
        assertEquals(1_600_000_000L, depth.oldest)
        // Three full pages and a short fourth, which is what 1,918 bars at 500 a page comes to —
        // and 1,918 is what TradeYar measured for one hour of BTCUSDT, not a round number chosen
        // to make the arithmetic tidy.
        assertEquals(4, venue.requests)
    }

    @Test
    fun `pages join without a gap and without a repeated bar`() = runTest {
        val venue = FiniteVenue(bars = 1_200)
        val archive = InMemoryCandleArchive()

        venue.fillHistory("BTCUSDT", hourly, archive, pageBars = 300, maxPages = 12)

        val held = archive.read("BTCUSDT", hourly, limit = 5_000)
        assertEquals(1_200, held.size)
        // Every open time exactly one bar apart, ascending, no duplicates. A repeated bar draws as
        // a spike that never happened and a missing one is a hole the reader cannot account for.
        for (index in 1 until held.size) {
            assertEquals("bar $index", held[index - 1].t + 3_600L, held[index].t)
        }
    }

    @Test
    fun `the ceiling stops the fill before the venue does`() = runTest {
        val venue = FiniteVenue(bars = 100_000)
        val archive = InMemoryCandleArchive()

        val depth = venue.fillHistory("BTCUSDT", hourly, archive, target = 900, pageBars = 300, maxPages = 12)

        assertEquals(HistoryStop.CEILING, depth.stop)
        assertTrue("stopped at ${depth.bars}", depth.bars >= 900)
        assertTrue(depth.reachedCeiling)
        // Stopped because we stopped asking, not because there is nothing older. A caller must not
        // tell a reader this is all there is.
        assertFalse(depth.venueExhausted)
    }

    @Test
    fun `the budget bounds one call, and the next call carries on from where it stopped`() = runTest {
        val venue = FiniteVenue(bars = 10_000)
        val archive = InMemoryCandleArchive()

        val first = venue.fillHistory("BTCUSDT", hourly, archive, pageBars = 200, maxPages = 3)
        assertEquals(HistoryStop.PAGE_BUDGET, first.stop)
        assertEquals(600, first.bars)
        assertEquals(3, venue.requests)

        val second = venue.fillHistory("BTCUSDT", hourly, archive, pageBars = 200, maxPages = 3)
        assertEquals(1_200, second.bars)
        assertEquals(600, second.added)
        // The second call resumed at the far edge rather than re-reading the live edge, which is
        // what makes the window grow across sessions instead of being re-fetched every time.
        assertTrue("resumed older than it started", second.oldest!! < first.oldest!!)
    }

    @Test
    fun `a route that ignores before is stopped after one wasted page, not looped forever`() = runTest {
        val venue = NoPagingVenue(bars = 300)
        val archive = InMemoryCandleArchive()

        val depth = venue.fillHistory("BTCUSDT", hourly, archive, pageBars = 300, maxPages = 12)

        assertEquals(HistoryStop.NO_PROGRESS, depth.stop)
        assertEquals(300, depth.bars)
        // Two requests: the first filled the archive, the second proved the route has no `before`.
        // A third would have been the same request again, and so would every one after it.
        assertEquals(2, venue.requests)
        assertTrue(depth.venueExhausted)
    }

    @Test
    fun `an interval the venue cannot build is refused rather than paged`() = runTest {
        // Written as a venue with the forex list rather than as a wrapper: interface delegation
        // would hand the default `load(interval)` back to the delegate, which reads *its* list, and
        // the test would then pass for the wrong reason.
        val venue = FiniteVenue(bars = 100, nativeTimeframes = ACADEMY_NATIVE_TIMEFRAMES)
        val archive = InMemoryCandleArchive()

        val thrown = runCatching { venue.fillHistory("XAUUSD", ChartInterval.Preset(Timeframe.M1), archive) }
            .exceptionOrNull()

        assertTrue("a fill must refuse what a load refuses: $thrown", thrown is CandleIntervalUnavailableException)
        assertEquals(0, archive.totalBars())
    }
}
