package com.coinepro.feature.chart

import com.coinepro.core.chart.ChartHistory
import com.coinepro.core.marketdata.ArchiveSpan
import com.coinepro.core.marketdata.CandleArchive
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.HISTORY_PAGE_BARS
import com.coinepro.core.marketdata.InMemoryCandleArchive
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The archive on the chart: the depth the owner asked for, and what it is honestly worth.
 *
 * Item 7 was worded as a number — twenty to fifty thousand candles — and the number on its own is
 * the easy half. `CandleArchive.MAX_BARS_PER_SERIES` is fifty thousand and always was going to be;
 * what makes it mean anything is that a page-back **accumulates** rather than being thrown away at
 * the end of the session, that the second walk through a week is served from disk, and that a chart
 * says how deep it actually is rather than reading a constant out loud. That is what is tested
 * here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartArchiveTest {

    private fun bars(count: Int, from: Long, base: Double) = (0 until count).map { index ->
        val price = base + index
        OhlcBar(
            t = from + index * 3600L,
            o = price,
            h = price + 1,
            l = price - 1,
            c = price,
            v = 1.0,
        )
    }

    /** Answers the live edge and nothing behind it — a venue with one page of history. */
    private class OnePage(private val page: List<OhlcBar>) : CandleGateway {
        var calls = 0
        override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?): CandlePage {
            calls++
            // Nothing older, which is the whole point: a page-back that reaches this gateway comes
            // back empty, so anything the chart gains had to have come from the archive.
            val candles = if (before == null) page else emptyList()
            return CandlePage(symbol = symbol, timeframe = timeframe, candles = candles)
        }
    }

    private val hourly = ChartInterval.Preset(Timeframe.H1)

    @Test
    fun `paging back is served from the archive rather than the venue`() = runTest {
        val newest = bars(60, from = 1_700_000_000L, base = 100.0)
        val older = bars(200, from = 1_700_000_000L - 200 * 3600L, base = 50.0)
        val archive = InMemoryCandleArchive()
        archive.write("BTCUSDT", hourly, older)
        val gateway = OnePage(newest)

        val controller = ChartController("BTCUSDT", gateway, this, cache = archiveless(), archive = archive)
        controller.start()
        advanceUntilIdle()
        // The venue answered one page of sixty. The two hundred behind it came off the archive and
        // are on the chart at open rather than after a drag — see `deepenResident`, which is what
        // «باید ۵ الی ۵۰ هزار کندل باشه» asked for: what the device holds is what the chart holds.
        assertEquals(260, controller.state.value.series.size)

        controller.loadMore()
        advanceUntilIdle()

        // Nothing more to have, and that is the proof: [OnePage] answers every page-back empty, so
        // the two hundred older bars can only have come from the archive.
        assertEquals(260, controller.state.value.series.size)
        // Oldest first, and no bar doubled where the two halves meet.
        val times = controller.state.value.series.time.toList()
        assertEquals(times.size, times.distinct().size)
        assertTrue(times.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `a fetched page is kept, so the next session starts deeper`() = runTest {
        val archive = InMemoryCandleArchive()
        val controller = ChartController(
            "XAUUSD",
            OnePage(bars(120, from = 1_700_000_000L, base = 10.0)),
            this,
            cache = archiveless(),
            archive = archive,
        )
        controller.start()
        advanceUntilIdle()

        assertEquals(120, archive.span("XAUUSD", hourly)?.count)
        // And the chart says what it holds, which is a fact rather than the ceiling.
        assertEquals(120, controller.state.value.archivedBars)
    }

    @Test
    fun `a venue with one page is not asked for a second one twice`() = runTest {
        val archive = InMemoryCandleArchive()
        val controller = ChartController(
            "XAUUSD",
            OnePage(bars(80, from = 1_700_000_000L, base = 10.0)),
            this,
            cache = archiveless(),
            archive = archive,
        )
        controller.start()
        advanceUntilIdle()

        // The backward fill walked to the end of what this venue has, established it, and said so.
        // That is the honest basis for the chart no longer offering «بیشتر» — not a short page.
        assertTrue(controller.state.value.venueExhausted)
        assertTrue(!controller.state.value.hasMore)
    }

    @Test
    fun `an archive that throws leaves the chart alone`() = runTest {
        val angry = object : CandleArchive {
            override suspend fun read(
                symbol: String,
                interval: ChartInterval,
                limit: Int,
                before: Long?,
            ): List<OhlcBar> = error("disk is on fire")

            override suspend fun write(symbol: String, interval: ChartInterval, bars: List<OhlcBar>): Int =
                error("disk is still on fire")

            override suspend fun span(symbol: String, interval: ChartInterval): ArchiveSpan? =
                error("and the index with it")

            override suspend fun clear() = Unit
        }
        val controller = ChartController(
            "BTCUSDT",
            OnePage(bars(40, from = 1_700_000_000L, base = 7.0)),
            this,
            cache = archiveless(),
            archive = angry,
        )
        controller.start()
        advanceUntilIdle()

        assertEquals(40, controller.state.value.series.size)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `paging back stops at the ceiling instead of growing without bound`() = runTest {
        // The regression this guards. Reading from disk removed the network's brake, and the first
        // build with the archive grew the series as fast as frames rendered — which the owner
        // reported as a chart that had become very slow to the point of being unusable.
        val newest = bars(60, from = 1_700_000_000L, base = 100.0)
        val deep = bars(
            ChartHistory.MAX_RESIDENT_BARS + 500,
            from = 1_700_000_000L - (ChartHistory.MAX_RESIDENT_BARS + 500) * 3600L,
            base = 50.0,
        )
        val archive = InMemoryCandleArchive()
        archive.write("BTCUSDT", hourly, deep)

        val controller = ChartController(
            "BTCUSDT",
            OnePage(newest),
            this,
            cache = archiveless(),
            archive = archive,
        )
        controller.start()
        advanceUntilIdle()

        // Drag at the left edge for far longer than any reader would.
        repeat(200) {
            controller.loadMore()
            advanceUntilIdle()
        }

        // The ceiling plus at most one page, which is the real contract: the guard refuses to
        // *start* a page once the series has reached the ceiling, so the page that carried it over
        // still lands whole. Trimming that page instead would cut a hole at the left edge of a
        // chart the reader is looking at, to save a few hundred bars out of twelve thousand.
        val size = controller.state.value.series.size
        assertTrue(
            "series grew to " + size,
            size <= ChartHistory.MAX_RESIDENT_BARS + HISTORY_PAGE_BARS,
        )
        // Two hundred drags and it stopped growing long before the end of them.
        assertTrue(size >= ChartHistory.MAX_RESIDENT_BARS)
        // And it is held rather than declared finished: the history is real and still on disk, so
        // saying there is no more would be a lie the «بیشتر» affordance is built on.
    }

    @Test
    fun `history on disk means more, whatever ended the fill`() = runTest {
        // The regression the fifty-thousand ceiling exposed. `hasMore` used to consult the archive
        // only when the *venue* was exhausted, and let the venue's own answer stand otherwise —
        // but a fill that stops because the archive is **full** reports `venueExhausted = false`,
        // which is the state with the most history behind it. So a chart over a full archive kept
        // the venue's `hasMore = false` and refused to walk back through any of it.
        val newest = bars(60, from = 1_700_000_000L, base = 100.0)
        val full = bars(
            ChartHistory.MAX_RESIDENT_BARS,
            from = 1_700_000_000L - ChartHistory.MAX_RESIDENT_BARS * 3600L,
            base = 50.0,
        )
        val archive = InMemoryCandleArchive()
        archive.write("BTCUSDT", hourly, full)

        val controller = ChartController(
            "BTCUSDT",
            OnePage(newest),
            this,
            cache = archiveless(),
            archive = archive,
        )
        controller.start()
        advanceUntilIdle()

        assertTrue("the archive is full and the chart says there is nothing more", controller.state.value.hasMore)
        controller.loadMore()
        advanceUntilIdle()
        assertTrue(controller.state.value.series.size > 60)
    }

    @Test
    fun `a page-back off the disk is not sized like a page off a network`() = runTest {
        // `HISTORY_PAGE_BARS` is five hundred because that is the smallest of the three venues'
        // page caps. It is a fact about a server, and it had no business deciding how much of our
        // own archive one pull reads: at five hundred a time, walking back over a year of hourly
        // candles the device already holds is ninety passes through `loadMore`, ninety series
        // rebuilds and ninety recompositions. The owner wants a chart he can back-test on, which
        // means walking to the far end of the archive.
        val newest = bars(60, from = 1_700_000_000L, base = 100.0)
        val deep = bars(
            ChartHistory.MAX_RESIDENT_BARS + 500,
            from = 1_700_000_000L - (ChartHistory.MAX_RESIDENT_BARS + 500) * 3600L,
            base = 50.0,
        )
        val archive = InMemoryCandleArchive()
        archive.write("BTCUSDT", hourly, deep)

        val controller = ChartController(
            "BTCUSDT",
            OnePage(newest),
            this,
            cache = archiveless(),
            archive = archive,
        )
        controller.start()
        advanceUntilIdle()
        controller.loadMore()
        advanceUntilIdle()

        val afterOne = controller.state.value.series.size
        assertTrue(
            "one pull off the disk brought $afterOne bars, no better than a network page",
            afterOne > 60 + HISTORY_PAGE_BARS,
        )

        // And it lands *on* the ceiling rather than a page past it. The request is clamped to the
        // room left, so the last pull is a partial one — a bound a chart overshoots by five
        // thousand bars is not a bound.
        repeat(20) {
            controller.loadMore()
            advanceUntilIdle()
        }
        assertEquals(ChartHistory.MAX_RESIDENT_BARS, controller.state.value.series.size)
    }

    /** No cache, so every one of these tests is measuring the archive and not the other store. */
    private fun archiveless() = com.coinepro.core.marketdata.NoOpCandleCache
}
