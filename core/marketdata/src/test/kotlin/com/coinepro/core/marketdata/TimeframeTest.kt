package com.coinepro.core.marketdata

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The interval table, its two spellings, and the boundaries a bar actually opens on.
 *
 * The bar-boundary tests are the load-bearing ones. Tehran is UTC+03:30, so every instant used here
 * is written as an ISO string and converted, rather than as a magic epoch number: the failures this
 * file is meant to catch are exactly the ones where a plausible-looking constant is half an hour or
 * one day out, and a hand-computed constant in the expectation would agree with the same mistake in
 * the implementation.
 */
class TimeframeTest {

    private val tehran = ZoneId.of("Asia/Tehran")

    /** Late evening UTC on a Thursday, which is already Friday the 28th in Tehran. */
    private val lateEveningUtc = Instant.parse("2026-08-27T21:00:00Z").epochSecond

    // ── the table ─────────────────────────────────────────────────────────────────────

    @Test
    fun `every timeframe carries the number of seconds its name claims`() {
        assertEquals(60L, Timeframe.M1.seconds)
        assertEquals(120L, Timeframe.M2.seconds)
        assertEquals(180L, Timeframe.M3.seconds)
        assertEquals(300L, Timeframe.M5.seconds)
        assertEquals(600L, Timeframe.M10.seconds)
        assertEquals(900L, Timeframe.M15.seconds)
        assertEquals(1_800L, Timeframe.M30.seconds)
        assertEquals(2_700L, Timeframe.M45.seconds)
        assertEquals(3_600L, Timeframe.H1.seconds)
        assertEquals(7_200L, Timeframe.H2.seconds)
        assertEquals(10_800L, Timeframe.H3.seconds)
        assertEquals(14_400L, Timeframe.H4.seconds)
        assertEquals(86_400L, Timeframe.D1.seconds)
        assertEquals(604_800L, Timeframe.W1.seconds)
        assertEquals(2_592_000L, Timeframe.MN1.seconds)
    }

    @Test
    fun `the table is ordered from the shortest bar to the longest`() {
        // The picker renders the entries in declaration order, and a shortcut key is bound to an
        // index. An entry inserted in the wrong place moves a control under a reader's finger.
        val seconds = Timeframe.entries.map { it.seconds }
        assertEquals(seconds.sorted(), seconds)
    }

    @Test
    fun `every label is written in Persian digits`() {
        for (frame in Timeframe.entries) {
            assertTrue(frame.name, frame.label.none { it in '0'..'9' })
            assertTrue(frame.name, frame.label.any { it in '۰'..'۹' })
        }
        assertEquals("۴۵ دقیقه", Timeframe.M45.label)
        assertEquals("۳ ساعت", Timeframe.H3.label)
        assertEquals("۱ ماه", Timeframe.MN1.label)
    }

    // ── spellings ─────────────────────────────────────────────────────────────────────

    @Test
    fun `both spellings of a timeframe still resolve to the same one`() {
        assertEquals(Timeframe.M15, Timeframe.of("M15"))
        assertEquals(Timeframe.M15, Timeframe.of("15M"))
        assertEquals(Timeframe.M15, Timeframe.of("15m"))
        assertEquals(Timeframe.H1, Timeframe.of("H1"))
        assertEquals(Timeframe.H1, Timeframe.of("1H"))
        assertEquals(Timeframe.H1, Timeframe.of("1h"))
        assertEquals(Timeframe.D1, Timeframe.of("1d"))
        assertEquals(Timeframe.W1, Timeframe.of("1w"))
    }

    @Test
    fun `the new presets answer to both spellings too`() {
        assertEquals(Timeframe.M2, Timeframe.of("M2"))
        assertEquals(Timeframe.M2, Timeframe.of("2m"))
        assertEquals(Timeframe.M10, Timeframe.of("10M"))
        assertEquals(Timeframe.M45, Timeframe.of("45m"))
        assertEquals(Timeframe.H2, Timeframe.of("2h"))
        assertEquals(Timeframe.H3, Timeframe.of("H3"))
        assertEquals(Timeframe.MN1, Timeframe.of("MN1"))
        assertEquals(Timeframe.MN1, Timeframe.of("1mn"))
    }

    @Test
    fun `one M is one minute and not one month`() {
        // TradingView reads `1M` as a month. Every caller in this app that has ever sent it meant a
        // minute, and a saved layout carrying it predates the monthly bar entirely, so the reversed
        // spelling keeps meaning what it always meant. A month is `MN1` or `1MN`.
        assertEquals(Timeframe.M1, Timeframe.of("1M"))
    }

    @Test
    fun `an unknown spelling is still null rather than a silent default`() {
        for (junk in listOf("", "   ", "H7", "M4", "banana", "0m", "MN2", "15")) {
            assertNull(junk, Timeframe.of(junk))
        }
        assertNull(Timeframe.of(null))
    }

    // ── custom intervals ──────────────────────────────────────────────────────────────

    @Test
    fun `a bare number is read as a custom interval in minutes`() {
        assertEquals(CustomInterval(205), customOf("205"))
        assertEquals(205, customOf("205")?.minutes)
        assertEquals(12_300L, customOf("205")?.seconds)
        assertEquals(CustomInterval(1440), customOf(" 1440 "))
        assertEquals(CustomInterval(1), customOf("1"))
    }

    @Test
    fun `a Persian-typed number is read as the same interval`() {
        assertEquals(CustomInterval(205), customOf("۲۰۵"))
    }

    @Test
    fun `an interval outside one to fourteen forty is refused`() {
        assertNull(customOf("1441"))
        assertNull(customOf("0"))
        assertNull(customOf("-5"))
        assertNull(customOf("12.5"))
        assertNull(customOf("15m"))
        assertNull(customOf(""))
        assertNull(customOf(null))
        assertThrows(IllegalArgumentException::class.java) { CustomInterval(0) }
        assertThrows(IllegalArgumentException::class.java) { CustomInterval(1441) }
    }

    @Test
    fun `a custom interval is captioned in Persian digits`() {
        assertEquals("۲۰۵ دقیقه", CustomInterval(205).label)
        assertEquals("۷ دقیقه", CustomInterval(7).label)
        assertEquals("۲ ساعت", CustomInterval(120).label)
        assertEquals("۲۴ ساعت", CustomInterval(1440).label)
        assertEquals("205", CustomInterval(205).wire)
    }

    // ── one type over both ────────────────────────────────────────────────────────────

    @Test
    fun `a preset wins over a number that looks like one`() {
        assertEquals(ChartInterval.Preset(Timeframe.M15), ChartInterval.of("15M"))
        assertEquals(ChartInterval.Preset(Timeframe.MN1), ChartInterval.of("MN1"))
        assertEquals(ChartInterval.Custom(CustomInterval(205)), ChartInterval.of("205"))
        assertNull(ChartInterval.of("banana"))
        assertNull(ChartInterval.of("1441"))
    }

    @Test
    fun `either kind of interval answers the same three questions`() {
        val preset = ChartInterval.of("H4")!!
        assertEquals("H4", preset.wire)
        assertEquals(14_400L, preset.seconds)
        assertEquals("۴ ساعت", preset.label)

        val custom = ChartInterval.of("205")!!
        assertEquals("205", custom.wire)
        assertEquals(12_300L, custom.seconds)
        assertEquals("۲۰۵ دقیقه", custom.label)
    }

    // ── bar boundaries ────────────────────────────────────────────────────────────────

    @Test
    fun `an intraday bar opens on the epoch, where the servers put it`() {
        val noon = Instant.parse("2026-08-27T12:10:00Z").epochSecond
        assertEquals(Instant.parse("2026-08-27T12:00:00Z").epochSecond, Timeframe.H1.bucketStart(noon))
        assertEquals(Instant.parse("2026-08-27T12:00:00Z").epochSecond, Timeframe.H4.bucketStart(noon))
        assertEquals(Instant.parse("2026-08-27T12:10:00Z").epochSecond, Timeframe.M5.bucketStart(noon))
        assertEquals(Instant.parse("2026-08-27T12:00:00Z").epochSecond, Timeframe.M45.bucketStart(noon))
    }

    @Test
    fun `the daily bar opens at midnight in Tehran, not at midnight in UTC`() {
        // 21:00 UTC on the 27th is already 00:30 on the 28th in Tehran, so the bar that contains it
        // is the 28th's — and it opened at 20:30 UTC, half an hour earlier. Dividing the epoch by
        // 86_400 answers the 27th, which is the wrong day and the wrong instant.
        assertEquals(
            Instant.parse("2026-08-27T20:30:00Z").epochSecond,
            Timeframe.D1.bucketStart(lateEveningUtc, tehran),
        )
        assertEquals(
            Instant.parse("2026-08-27T00:00:00Z").epochSecond,
            lateEveningUtc / 86_400L * 86_400L,
        )
    }

    @Test
    fun `the weekly bar opens on Saturday in Tehran`() {
        // Friday the 28th in Tehran belongs to the week that opened on Saturday the 22nd, which
        // began at 20:30 UTC on the 21st. An ISO Monday would answer the 24th and cut the Iranian
        // week in half.
        assertEquals(
            Instant.parse("2026-08-21T20:30:00Z").epochSecond,
            Timeframe.W1.bucketStart(lateEveningUtc, tehran),
        )
    }

    @Test
    fun `the monthly bar opens on the first of the month, not thirty days back`() {
        val start = Timeframe.MN1.bucketStart(lateEveningUtc, tehran)
        assertEquals(Instant.parse("2026-07-31T20:30:00Z").epochSecond, start)
        // Thirty days back from the instant would land on 29 July, mid-month and mid-afternoon.
        assertTrue(start != lateEveningUtc - Timeframe.MN1.seconds)
        val local = Instant.ofEpochSecond(start).atZone(tehran)
        assertEquals(1, local.dayOfMonth)
        assertEquals(0, local.hour)
        assertEquals(0, local.minute)
    }

    @Test
    fun `a month is measured by the calendar, so consecutive months are different lengths`() {
        val february = Timeframe.MN1.bucketStart(Instant.parse("2026-02-14T09:00:00Z").epochSecond, tehran)
        val march = Timeframe.MN1.bucketStart(Instant.parse("2026-03-14T09:00:00Z").epochSecond, tehran)
        val april = Timeframe.MN1.bucketStart(Instant.parse("2026-04-14T09:00:00Z").epochSecond, tehran)
        assertEquals(28L * 86_400L, march - february)
        assertEquals(31L * 86_400L, april - march)
    }

    @Test
    fun `a custom interval counts from the reader's own midnight`() {
        val dayStart = Instant.parse("2026-08-27T20:30:00Z").epochSecond
        val threeHoursIn = dayStart + 3 * 3_600L
        // 205 minutes is 12_300 seconds, so the instant three hours into the day is still inside the
        // day's first bar.
        assertEquals(dayStart, CustomInterval(205).bucketStart(threeHoursIn, tehran))
        assertEquals(dayStart + 12_300L, CustomInterval(205).bucketStart(dayStart + 12_301L, tehran))
        // A whole day as a custom interval agrees with the daily preset rather than with UTC.
        assertEquals(dayStart, CustomInterval(1440).bucketStart(lateEveningUtc, tehran))
        assertEquals(
            Timeframe.D1.bucketStart(lateEveningUtc, tehran),
            CustomInterval(1440).bucketStart(lateEveningUtc, tehran),
        )
    }

    @Test
    fun `an interval hands its own bucket rule to whoever holds it`() {
        assertEquals(
            Timeframe.D1.bucketStart(lateEveningUtc, tehran),
            ChartInterval.of("D1")!!.bucketStart(lateEveningUtc, tehran),
        )
        assertEquals(
            CustomInterval(205).bucketStart(lateEveningUtc, tehran),
            ChartInterval.of("205")!!.bucketStart(lateEveningUtc, tehran),
        )
    }

    @Test
    fun `the default zone is Tehran, so a caller that passes none still gets the Iranian day`() {
        assertEquals(
            Timeframe.D1.bucketStart(lateEveningUtc, tehran),
            Timeframe.D1.bucketStart(lateEveningUtc),
        )
        assertEquals("Asia/Tehran", CHART_TIME_ZONE.id)
    }
}
