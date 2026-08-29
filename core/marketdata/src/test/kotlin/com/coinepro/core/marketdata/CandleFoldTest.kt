package com.coinepro.core.marketdata

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Folding finer bars into coarser ones, which is how seven of the fifteen presets and every custom
 * interval exist at all.
 *
 * The fixtures are hand-built rather than generated, because the failures worth catching here are
 * the ones a generator would reproduce: an open taken from `max` instead of from the first bar, a
 * month sized by arithmetic instead of by a calendar, an empty bucket filled in rather than left
 * out. Every calendar instant is written as a date in Tehran and converted, for the same reason
 * [TimeframeTest] does it — a hand-computed epoch constant would agree with the same mistake in the
 * implementation.
 */
class CandleFoldTest {

    private val tehran = ZoneId.of("Asia/Tehran")

    private fun bar(
        t: Long,
        o: Double,
        h: Double,
        l: Double,
        c: Double,
        v: Double = 0.0,
        closed: Boolean = true,
    ) = OhlcBar(t = t, o = o, h = h, l = l, c = c, v = v, closed = closed)

    private fun dayStart(date: String): Long =
        LocalDate.parse(date).atStartOfDay(tehran).toEpochSecond()

    // ── the shape of a folded bar ─────────────────────────────────────────────────────

    @Test
    fun `two one-minute bars become one two-minute bar with the outer high and low`() {
        val folded = foldBars(
            listOf(
                bar(t = 0, o = 10.0, h = 12.0, l = 9.0, c = 11.0, v = 5.0),
                bar(t = 60, o = 11.0, h = 15.0, l = 8.0, c = 13.0, v = 7.0),
            ),
            ChartInterval.Preset(Timeframe.M2),
        )

        assertEquals(1, folded.size)
        assertEquals(0L, folded[0].t)
        assertEquals(10.0, folded[0].o, 1e-9)
        assertEquals(15.0, folded[0].h, 1e-9)
        assertEquals(8.0, folded[0].l, 1e-9)
        assertEquals(13.0, folded[0].c, 1e-9)
    }

    @Test
    fun `a four minute page folds into two two-minute bars on the right boundaries`() {
        val folded = foldBars(
            listOf(
                bar(t = 0, o = 10.0, h = 12.0, l = 9.0, c = 11.0),
                bar(t = 60, o = 11.0, h = 15.0, l = 8.0, c = 13.0),
                bar(t = 120, o = 13.0, h = 14.0, l = 12.0, c = 13.5),
                bar(t = 180, o = 13.5, h = 16.0, l = 13.0, c = 15.0),
            ),
            ChartInterval.Preset(Timeframe.M2),
        )

        assertEquals(listOf(0L, 120L), folded.map { it.t })
        assertEquals(13.0, folded[1].o, 1e-9)
        assertEquals(15.0, folded[1].c, 1e-9)
    }

    @Test
    fun `two hourly bars become one two-hour bar`() {
        val folded = foldBars(
            listOf(
                bar(t = 0, o = 100.0, h = 101.0, l = 99.0, c = 100.5),
                bar(t = 3_600, o = 100.5, h = 104.0, l = 100.0, c = 103.0),
                bar(t = 7_200, o = 103.0, h = 103.5, l = 97.0, c = 98.0),
                bar(t = 10_800, o = 98.0, h = 99.0, l = 90.0, c = 91.0),
            ),
            ChartInterval.Preset(Timeframe.H2),
        )

        assertEquals(listOf(0L, 7_200L), folded.map { it.t })
        assertEquals(100.0, folded[0].o, 1e-9)
        assertEquals(104.0, folded[0].h, 1e-9)
        assertEquals(99.0, folded[0].l, 1e-9)
        assertEquals(103.0, folded[0].c, 1e-9)
        assertEquals(103.0, folded[1].o, 1e-9)
        assertEquals(90.0, folded[1].l, 1e-9)
        assertEquals(91.0, folded[1].c, 1e-9)
    }

    @Test
    fun `the open is the first bar's open and the close is the last bar's close, never an extreme`() {
        // Chosen so that every wrong answer is a *different* number from the right one: the largest
        // open is the second bar's, and the largest close is the first bar's. A fold that reached
        // for `max` or `min` on either would be caught.
        val folded = foldBars(
            listOf(
                bar(t = 0, o = 100.0, h = 110.0, l = 95.0, c = 98.0),
                bar(t = 60, o = 105.0, h = 109.0, l = 80.0, c = 85.0),
            ),
            ChartInterval.Preset(Timeframe.M2),
        )

        assertEquals(1, folded.size)
        assertEquals(100.0, folded[0].o, 1e-9)
        assertEquals(85.0, folded[0].c, 1e-9)
        assertEquals(110.0, folded[0].h, 1e-9)
        assertEquals(80.0, folded[0].l, 1e-9)
    }

    @Test
    fun `volume is the sum of the source bars and not one of them`() {
        val folded = foldBars(
            listOf(
                bar(t = 0, o = 1.0, h = 1.0, l = 1.0, c = 1.0, v = 12.5),
                bar(t = 60, o = 1.0, h = 1.0, l = 1.0, c = 1.0, v = 7.5),
                bar(t = 120, o = 1.0, h = 1.0, l = 1.0, c = 1.0, v = 4.0),
                bar(t = 180, o = 1.0, h = 1.0, l = 1.0, c = 1.0, v = 6.0),
            ),
            ChartInterval.Preset(Timeframe.M2),
        )

        assertEquals(20.0, folded[0].v, 1e-9)
        assertEquals(10.0, folded[1].v, 1e-9)
    }

    @Test
    fun `a non-finite volume is skipped rather than poisoning the folded bar`() {
        val folded = foldBars(
            listOf(
                bar(t = 0, o = 1.0, h = 1.0, l = 1.0, c = 1.0, v = 3.0),
                bar(t = 60, o = 1.0, h = 1.0, l = 1.0, c = 1.0, v = Double.NaN),
            ),
            ChartInterval.Preset(Timeframe.M2),
        )

        assertEquals(3.0, folded[0].v, 1e-9)
    }

    @Test
    fun `a folded bar is open while any source bar in it is still forming`() {
        val folded = foldBars(
            listOf(
                bar(t = 0, o = 1.0, h = 1.0, l = 1.0, c = 1.0, closed = true),
                bar(t = 60, o = 1.0, h = 1.0, l = 1.0, c = 1.0, closed = true),
                bar(t = 120, o = 1.0, h = 1.0, l = 1.0, c = 1.0, closed = true),
                bar(t = 180, o = 1.0, h = 1.0, l = 1.0, c = 1.0, closed = false),
            ),
            ChartInterval.Preset(Timeframe.M2),
        )

        assertTrue(folded[0].closed)
        assertFalse(folded[1].closed)
    }

    // ── gaps ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a bucket with no source bars is omitted and never emitted as a flat bar`() {
        // Eight minutes of silence between the two-minute bucket at 0 and the one at 600. A fold
        // that filled them would put four dojis on the chart at a price nobody traded at.
        val folded = foldBars(
            listOf(
                bar(t = 0, o = 10.0, h = 11.0, l = 9.0, c = 10.5),
                bar(t = 60, o = 10.5, h = 12.0, l = 10.0, c = 11.0),
                bar(t = 600, o = 20.0, h = 21.0, l = 19.0, c = 20.5),
                bar(t = 660, o = 20.5, h = 22.0, l = 20.0, c = 21.0),
            ),
            ChartInterval.Preset(Timeframe.M2),
        )

        assertEquals(listOf(0L, 600L), folded.map { it.t })
        assertTrue(folded.none { it.t in 120L..540L })
        assertEquals(11.0, folded[0].c, 1e-9)
        assertEquals(20.0, folded[1].o, 1e-9)
    }

    @Test
    fun `a page arriving out of order and with a repeated bar still folds once, in time order`() {
        val folded = foldBars(
            listOf(
                bar(t = 60, o = 11.0, h = 15.0, l = 8.0, c = 13.0),
                bar(t = 0, o = 10.0, h = 12.0, l = 9.0, c = 11.0),
                bar(t = 60, o = 11.0, h = 15.0, l = 8.0, c = 13.0),
            ),
            ChartInterval.Preset(Timeframe.M2),
        )

        assertEquals(1, folded.size)
        assertEquals(10.0, folded[0].o, 1e-9)
        assertEquals(13.0, folded[0].c, 1e-9)
    }

    @Test
    fun `an empty page folds to an empty series rather than to one invented bar`() {
        assertTrue(foldBars(emptyList(), ChartInterval.Preset(Timeframe.M2)).isEmpty())
    }

    // ── months, which are the case arithmetic gets wrong ──────────────────────────────

    private fun dailyBarsBetween(first: String, last: String): List<OhlcBar> {
        val start = LocalDate.parse(first)
        val end = LocalDate.parse(last)
        val bars = mutableListOf<OhlcBar>()
        var date = start
        var price = 100.0
        while (!date.isAfter(end)) {
            val t = date.atStartOfDay(tehran).toEpochSecond()
            bars += bar(t = t, o = price, h = price + 2, l = price - 2, c = price + 1, v = 1.0)
            price += 1.0
            date = date.plusDays(1)
        }
        return bars
    }

    @Test
    fun `February and March fold into monthly bars of different lengths, not of thirty days each`() {
        // 2024 is a leap year, so this February is 29 days and the March after it is 31. The
        // nominal `MN1.seconds` is 30 days, which is wrong for both — a fold that divided elapsed
        // seconds by it would open the March bar a day into February and the April bar two days
        // into March.
        val folded = foldBars(
            dailyBarsBetween("2024-02-01", "2024-03-31"),
            ChartInterval.Preset(Timeframe.MN1),
            tehran,
        )

        assertEquals(2, folded.size)
        assertEquals(dayStart("2024-02-01"), folded[0].t)
        assertEquals(dayStart("2024-03-01"), folded[1].t)
        assertEquals(29L * 86_400L, folded[1].t - folded[0].t)
        assertEquals(29.0, folded[0].v, 1e-9)
        assertEquals(31.0, folded[1].v, 1e-9)
    }

    @Test
    fun `a non-leap February folds into a twenty-eight day monthly bar`() {
        val folded = foldBars(
            dailyBarsBetween("2023-02-01", "2023-04-30"),
            ChartInterval.Preset(Timeframe.MN1),
            tehran,
        )

        assertEquals(3, folded.size)
        assertEquals(28L * 86_400L, folded[1].t - folded[0].t)
        assertEquals(31L * 86_400L, folded[2].t - folded[1].t)
        assertEquals(28.0, folded[0].v, 1e-9)
        assertEquals(31.0, folded[1].v, 1e-9)
        assertEquals(30.0, folded[2].v, 1e-9)
    }

    @Test
    fun `a monthly bar opens at the first day's open and closes at the last day's close`() {
        val february = dailyBarsBetween("2024-02-01", "2024-02-29")
        val folded = foldBars(february, ChartInterval.Preset(Timeframe.MN1), tehran)

        assertEquals(1, folded.size)
        assertEquals(february.first().o, folded[0].o, 1e-9)
        assertEquals(february.last().c, folded[0].c, 1e-9)
        assertEquals(february.maxOf { it.h }, folded[0].h, 1e-9)
        assertEquals(february.minOf { it.l }, folded[0].l, 1e-9)
    }

    @Test
    fun `a month whose first trading days are missing still opens on the first of the month`() {
        // The feed's first bar is the 5th; the bucket is still February's, and its timestamp is
        // the first of the month rather than the first bar's own day. A timestamp taken from the
        // source bar would put the monthly candle four days into the month on the axis.
        val folded = foldBars(
            dailyBarsBetween("2024-02-05", "2024-02-29"),
            ChartInterval.Preset(Timeframe.MN1),
            tehran,
        )

        assertEquals(1, folded.size)
        assertEquals(dayStart("2024-02-01"), folded[0].t)
    }

    // ── the source a request is resolved to ───────────────────────────────────────────

    @Test
    fun `every interval the servers serve is fetched at itself and never folded`() {
        for (frame in SERVER_NATIVE_TIMEFRAMES) {
            val plan = resolveCandleRequest(ChartInterval.Preset(frame))
            assertEquals(frame.name, frame, plan.source)
            assertEquals(frame.name, 1, plan.factor)
            assertFalse(frame.name, plan.foldsOnClient)
        }
    }

    @Test
    fun `every interval the servers do not serve resolves to the coarsest divisor they do`() {
        assertEquals(Timeframe.M1, sourceTimeframeFor(ChartInterval.Preset(Timeframe.M2)))
        assertEquals(Timeframe.M1, sourceTimeframeFor(ChartInterval.Preset(Timeframe.M3)))
        assertEquals(Timeframe.M5, sourceTimeframeFor(ChartInterval.Preset(Timeframe.M10)))
        assertEquals(Timeframe.M15, sourceTimeframeFor(ChartInterval.Preset(Timeframe.M45)))
        assertEquals(Timeframe.H1, sourceTimeframeFor(ChartInterval.Preset(Timeframe.H2)))
        assertEquals(Timeframe.H1, sourceTimeframeFor(ChartInterval.Preset(Timeframe.H3)))
        assertEquals(Timeframe.D1, sourceTimeframeFor(ChartInterval.Preset(Timeframe.MN1)))
    }

    @Test
    fun `a month is fetched in days and sized for the longest month rather than the nominal one`() {
        val plan = resolveCandleRequest(ChartInterval.Preset(Timeframe.MN1), limit = 12)
        assertEquals(Timeframe.D1, plan.source)
        assertEquals(31, plan.factor)
        assertEquals(372, plan.requestLimit)
    }

    @Test
    fun `a custom interval is never folded from an hourly bar, however cleanly it divides`() {
        // Sixty minutes divides into H1 perfectly, and H1 is still the wrong source: a custom
        // bucket is anchored to Tehran midnight, which is half an hour off every server's hourly
        // grid, so an H1 bar would straddle two of these buckets.
        val plan = resolveCandleRequest(ChartInterval.Custom(CustomInterval(60)))
        assertEquals(Timeframe.M30, plan.source)
        assertEquals(2, plan.factor)
    }

    @Test
    fun `every source bar boundary nests inside the custom buckets it is folded into`() {
        val interval = ChartInterval.Custom(CustomInterval(205))
        val source = sourceTimeframeFor(interval)
        val start = dayStart("2026-08-29")
        for (step in 0 until 400) {
            val boundary = interval.bucketStart(start + step * 300L, tehran)
            assertEquals(0L, Math.floorMod(boundary, source.seconds))
        }
    }

    // ── how large the request has to be ───────────────────────────────────────────────

    @Test
    fun `folding multiplies the request by the number of source bars in a drawn bar`() {
        val plan = resolveCandleRequest(ChartInterval.Preset(Timeframe.H2), limit = 300)
        assertEquals(Timeframe.H1, plan.source)
        assertEquals(600, plan.requestLimit)
        assertEquals(300, plan.expectedBars)
        assertFalse(plan.truncated)
    }

    @Test
    fun `a two hundred and five minute interval cannot show three hundred bars in one page`() {
        // 205 minutes is forty-one five-minute bars, so three hundred of them would be 12,300
        // source bars and the page cap is a thousand. Twenty-four drawn bars is the honest answer
        // and `truncated` is how the caller learns it is short rather than broken.
        val plan = resolveCandleRequest(ChartInterval.Custom(CustomInterval(205)), limit = 300)
        assertEquals(Timeframe.M5, plan.source)
        assertEquals(41, plan.factor)
        assertEquals(CandleGateway.SOURCE_LIMIT_MAX, plan.requestLimit)
        assertEquals(24, plan.expectedBars)
        assertTrue(plan.truncated)
    }

    @Test
    fun `a caller with a larger page allowance gets a larger request`() {
        val plan = resolveCandleRequest(
            ChartInterval.Custom(CustomInterval(205)),
            limit = 300,
            sourceLimitMax = 3_000,
        )
        assertEquals(3_000, plan.requestLimit)
        assertEquals(73, plan.expectedBars)
        assertTrue(plan.truncated)
    }

    @Test
    fun `a request never asks for zero bars however small the limit`() {
        val plan = resolveCandleRequest(ChartInterval.Preset(Timeframe.M3), limit = 0)
        assertTrue(plan.requestLimit >= 1)
        assertTrue(plan.expectedBars >= 1)
    }
}
