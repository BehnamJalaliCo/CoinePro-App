package com.coinepro.core.marketdata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bars shorter than a minute, which no server this app talks to has.
 *
 * «ما تایم‌فریم ۱۰ ثانیه تا ۵۰ ثانیه هم باید داشته باشیم.» TradeYar's candle route starts at `1m`
 * and CoinePro-FX's at `M5`, and a minute candle cannot be cut into six — so these are built on the
 * phone out of the price feed and kept in the archive between sittings. What is tested here is the
 * three things the rest of the app depends on being true of them: they round-trip through a saved
 * layout, they bucket on a grid, and **no request is ever built for one**.
 */
class SecondsIntervalTest {

    @Test
    fun `every offered length round-trips through a saved layout`() {
        SECONDS_KEYS.forEach { count ->
            val interval = ChartInterval.Seconds(count)
            assertEquals("${count}S", interval.wire)
            assertEquals(interval, ChartInterval.of(interval.wire))
            assertEquals(count.toLong(), interval.seconds)
        }
    }

    @Test
    fun `a preset still wins over a seconds spelling and over a minute count`() {
        // Order in `ChartInterval.of` is presets, then seconds, then a bare number. Anything else
        // and `1M` becomes a one-minute *custom* interval that caches under a different key, or
        // `10S` becomes ten minutes.
        assertEquals(ChartInterval.Preset(Timeframe.M1), ChartInterval.of("M1"))
        assertEquals(ChartInterval.Preset(Timeframe.M15), ChartInterval.of("15M"))
        assertEquals(ChartInterval.Seconds(15), ChartInterval.of("15S"))
        assertEquals(ChartInterval.Custom(CustomInterval(205)), ChartInterval.of("205"))
    }

    @Test
    fun `a length this build does not offer is null rather than the nearest one`() {
        // A saved layout naming `7S` should fall back to a default, not silently become ten
        // seconds — a chart drawn at a bar length nobody asked for is worse than a chart reset.
        assertNull(secondsOf("7S"))
        assertNull(secondsOf("60S"))
        assertNull(secondsOf("S"))
        assertNull(secondsOf(""))
        assertNull(secondsOf(null))
        assertNull(secondsOf("10"))
    }

    @Test
    fun `either case of the suffix is read`() {
        assertEquals(ChartInterval.Seconds(30), secondsOf("30s"))
        assertEquals(ChartInterval.Seconds(30), secondsOf(" 30S "))
    }

    @Test
    fun `buckets are laid on the epoch and are exactly one length apart`() {
        val ten = ChartInterval.Seconds(10)
        assertEquals(1_700_000_000L, ten.bucketStart(1_700_000_000L))
        assertEquals(1_700_000_000L, ten.bucketStart(1_700_000_009L))
        assertEquals(1_700_000_010L, ten.bucketStart(1_700_000_010L))
        // And before the epoch, where plain integer division rounds towards zero and would hand
        // back the *next* bar's open.
        assertEquals(-10L, ten.bucketStart(-1L))
    }

    @Test
    fun `a fifty-second bar drifts against the minute, and that is not a defect`() {
        val fifty = ChartInterval.Seconds(50)
        val first = fifty.bucketStart(0L)
        val second = fifty.bucketStart(60L)
        assertEquals(0L, first)
        assertEquals(50L, second)
        assertEquals(50L, second - first)
    }

    @Test
    fun `no venue is ever asked for a seconds bar`() {
        // This is the one that would break something visible. `sourceTimeframeFor` answering a
        // timeframe here would send a request for a length no server recognises — or worse, get a
        // page of minutes back and draw it under a ten-second label.
        SECONDS_KEYS.forEach { count ->
            assertNull(sourceTimeframeFor(ChartInterval.Seconds(count)))
        }
        val plan = resolveCandleRequest(ChartInterval.Seconds(10))
        assertTrue("a seconds plan must not claim a feed", !plan.available)
    }

    @Test
    fun `the label is Persian prose with Persian digits`() {
        // A caption is prose. Market figures stay Latin; this is not one.
        assertEquals("۱۰ ثانیه", ChartInterval.Seconds(10).label)
        assertEquals("۴۵ ثانیه", ChartInterval.Seconds(45).label)
    }

    @Test
    fun `the offered set is the range the owner named`() {
        assertEquals(listOf(10, 15, 20, 30, 45, 50), SECONDS_KEYS)
        // Constructing one outside the set is a programming error, not a fallback.
        SECONDS_KEYS.forEach { ChartInterval.Seconds(it) }
        runCatching { ChartInterval.Seconds(7) }.let { assertTrue(it.isFailure) }
    }
}
