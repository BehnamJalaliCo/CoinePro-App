package com.coinepro.core.marketdata

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The archive: what goes in comes back out, and what would sink a phone never gets in.
 *
 * The bounds are the load-bearing half. A per-series ceiling on its own is not a bound at all — a
 * reader who opens forty symbols on four intervals has a hundred and sixty series, and fifty
 * thousand bars each is eight million rows and half a gigabyte on a device that had none to spare.
 * The total is what actually stops that, and eviction takes whole series rather than slices off
 * each because a series with a hole in the middle draws as a market that was shut and was not.
 */
class CandleArchiveTest {

    private val hourly = ChartInterval.Preset(Timeframe.H1)
    private val custom = ChartInterval.Custom(CustomInterval(205))

    private fun bars(count: Int, firstAt: Long = 1_600_000_000L, step: Long = 3_600L): List<OhlcBar> =
        (0 until count).map { index ->
            OhlcBar(
                t = firstAt + index * step,
                o = 1.0 + index,
                h = 2.0 + index,
                l = index.toDouble(),
                c = 1.5 + index,
                v = 1.0,
            )
        }

    // ── the round trip ────────────────────────────────────────────────────────────────

    @Test
    fun `bars written come back in order, with their prices intact`() = runTest {
        val archive = InMemoryCandleArchive()
        val written = bars(400)

        assertEquals(400, archive.write("btcusdt", hourly, written))

        // Lower case in, upper case out: a symbol is one series however the caller spells it, and
        // two spellings would be two archives that each look half full.
        val held = archive.read("BTCUSDT", hourly, limit = 1_000)
        assertEquals(written, held)
        val span = requireNotNull(archive.span("BTCUSDT", hourly))
        assertEquals(400, span.count)
        assertEquals(written.first().t, span.oldest)
        assertEquals(written.last().t, span.newest)
    }

    @Test
    fun `a read hands back the newest bars, and before selects the page under them`() = runTest {
        val archive = InMemoryCandleArchive()
        val written = bars(1_000)
        archive.write("BTCUSDT", hourly, written)

        val newest = archive.read("BTCUSDT", hourly, limit = 300)
        assertEquals(written.takeLast(300), newest)

        // The same promise the gateway makes: strictly before, so a page-back off disk and a
        // page-back off the network are interchangeable and neither repeats a bar.
        val older = archive.read("BTCUSDT", hourly, limit = 300, before = newest.first().t)
        assertEquals(300, older.size)
        assertTrue(older.last().t < newest.first().t)
        assertEquals(written[699], older.last())
        assertEquals(written[400], older.first())
    }

    @Test
    fun `writing the same page twice adds nothing the second time`() = runTest {
        val archive = InMemoryCandleArchive()
        archive.write("BTCUSDT", hourly, bars(300))

        // The count is what a backward fill reads to tell "the venue has no more" from "this route
        // is handing me the same page forever". A second write of the same bars must answer zero.
        assertEquals(0, archive.write("BTCUSDT", hourly, bars(300)))
        assertEquals(300, archive.totalBars())
    }

    @Test
    fun `a custom interval keeps its own archive rather than borrowing a preset's`() = runTest {
        val archive = InMemoryCandleArchive()
        archive.write("BTCUSDT", hourly, bars(100))
        archive.write("BTCUSDT", custom, bars(50, step = 12_300L))

        assertEquals(100, archive.span("BTCUSDT", hourly)?.count)
        assertEquals(50, archive.span("BTCUSDT", custom)?.count)
    }

    @Test
    fun `a bar with a price that is not a number never enters the archive`() = runTest {
        val archive = InMemoryCandleArchive()
        val poisoned = bars(3) + OhlcBar(t = 1_600_100_000L, o = 1.0, h = Double.NaN, l = 1.0, c = 1.0, v = 1.0)

        assertEquals(3, archive.write("BTCUSDT", hourly, poisoned))
        // One NaN high rescales the price axis to nothing, and in an archive it does so on every
        // open from then on, long after the response that produced it is forgotten.
        assertTrue(archive.read("BTCUSDT", hourly, limit = 100).all { it.h.isFinite() })
    }

    // ── the bounds ────────────────────────────────────────────────────────────────────

    @Test
    fun `one series is trimmed to the ceiling, keeping the newest bars`() = runTest {
        val archive = InMemoryCandleArchive(maxBarsPerSeries = 1_000, maxBarsTotal = 100_000)

        archive.write("BTCUSDT", hourly, bars(1_500))

        val span = requireNotNull(archive.span("BTCUSDT", hourly))
        assertEquals(1_000, span.count)
        // The oldest go, because the ceiling is only reached by walking backwards and those are
        // the bars the reader is walking away from.
        assertEquals(bars(1_500)[500].t, span.oldest)
        assertEquals(bars(1_500).last().t, span.newest)
    }

    @Test
    fun `the total across every series is what stops a phone running out of memory`() = runTest {
        val archive = InMemoryCandleArchive(maxBarsPerSeries = 1_000, maxBarsTotal = 2_500)

        // Six symbols at a thousand bars each is six thousand rows, well past the total. Without
        // this bound nothing at all limits the store: the per-series ceiling is per series.
        for (index in 0 until 6) {
            archive.write("SYM$index", hourly, bars(1_000))
        }

        assertTrue("held ${archive.totalBars()} bars", archive.totalBars() <= 2_500)
        // The first symbols opened are the ones dropped, and the most recent survives whole.
        assertNull(archive.span("SYM0", hourly))
        assertEquals(1_000, archive.span("SYM5", hourly)?.count)
    }

    @Test
    fun `eviction is least recently read, so the chart in front of the reader is not the one dropped`() = runTest {
        val archive = InMemoryCandleArchive(maxBarsPerSeries = 1_000, maxBarsTotal = 2_000)
        archive.write("AAA", hourly, bars(1_000))
        archive.write("BBB", hourly, bars(1_000))

        // Reading AAA makes it the most recent, so the next write must evict BBB and not it.
        archive.read("AAA", hourly, limit = 10)
        archive.write("CCC", hourly, bars(1_000))

        assertNotNull(archive.span("AAA", hourly))
        assertNull(archive.span("BBB", hourly))
        assertNotNull(archive.span("CCC", hourly))
    }

    @Test
    fun `the ceilings are the ones the app actually ships, and they add up to a size worth stating`() {
        assertEquals(50_000, CandleArchive.MAX_BARS_PER_SERIES)
        assertEquals(250_000, CandleArchive.MAX_BARS_TOTAL)
        // Sixteen megabytes, which is the number that has to be sayable out loud when somebody asks
        // what this costs on their phone.
        assertEquals(16_000_000L, CandleArchive.estimatedBytes(CandleArchive.MAX_BARS_TOTAL))
    }

    @Test
    fun `the no-op archive is a working answer rather than a hole`() = runTest {
        assertEquals(0, NoOpCandleArchive.write("BTCUSDT", hourly, bars(10)))
        assertTrue(NoOpCandleArchive.read("BTCUSDT", hourly).isEmpty())
        assertNull(NoOpCandleArchive.span("BTCUSDT", hourly))
    }
}
