package com.coinepro.core.chart

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dates along the bottom.
 *
 * The defect these pin, in one sentence: the time axis divided the visible *bar count* into five
 * and printed whatever timestamp fell out, which is the exact thing the price axis was fixed for a
 * wave earlier — arbitrary labels that churn under a pan instead of sliding.
 */
class TimeScaleTest {

    private val tehran = CHART_ZONE

    /** Hourly bars across four days, starting at midnight Tehran. */
    private fun hourly(days: Int, from: ZonedDateTime = ZonedDateTime.of(2026, 3, 2, 0, 0, 0, 0, tehran)) =
        LongArray(days * 24) { from.plusHours(it.toLong()).toEpochSecond() }

    @Test
    fun `every label lands on a calendar boundary rather than on a fraction of the window`() {
        val times = hourly(days = 4)
        val ticks = TimeScale.ticks(times, 0, times.lastIndex, tehran.asChartZone(), minGapBars = 6, maxTicks = 6)
        assertTrue("the axis must carry labels", ticks.isNotEmpty())
        ticks.forEach { tick ->
            assertNotNull("a dated axis never places a tick on nothing", tick.unit)
            // Every tick is a real boundary against the bar before it, which is what makes the
            // labels stable: a boundary belongs to the bar, not to where the view happens to start.
            assertEquals(tick.unit, TimeScale.boundaryOf(tick.time, times[tick.index - 1], tehran.asChartZone()))
        }
    }

    @Test
    fun `panning slides the labels instead of renumbering them`() {
        // The complaint this answers: dragging through history used to churn all five dates every
        // frame, because they were measured from the first visible bar and the first visible bar
        // moves. The bars a label sits on must not change when the window does.
        val times = hourly(days = 10)
        val first = TimeScale.ticks(times, 40, 160, tehran.asChartZone(), minGapBars = 8, maxTicks = 6)
        val second = TimeScale.ticks(times, 44, 164, tehran.asChartZone(), minGapBars = 8, maxTicks = 6)
        val shared = first.map { it.index }.intersect(second.map { it.index }.toSet())
        assertTrue(
            "a four-bar pan must not rebuild the whole axis, it moved $first to $second",
            shared.size >= first.size - 1,
        )
    }

    @Test
    fun `the coarsest boundary in view is labelled first`() {
        // A window holding one month boundary and eighty day boundaries labels the month. It is the
        // one a reader navigates by, and a ladder that filled up on days would spend every slot
        // before reaching it.
        val start = ZonedDateTime.of(2026, 3, 28, 0, 0, 0, 0, tehran)
        val times = hourly(days = 8, from = start)
        val ticks = TimeScale.ticks(times, 0, times.lastIndex, tehran.asChartZone(), minGapBars = 4, maxTicks = 3)
        assertTrue(
            "the month boundary must be on the axis, got ${ticks.map { it.unit }}",
            ticks.any { it.unit == TimeTickUnit.MONTH },
        )
    }

    @Test
    fun `labels never collide`() {
        val times = hourly(days = 6)
        val gap = 11
        val ticks = TimeScale.ticks(times, 0, times.lastIndex, tehran.asChartZone(), minGapBars = gap, maxTicks = 8)
        ticks.zipWithNext { a, b ->
            assertTrue("labels ${a.index} and ${b.index} are too close", b.index - a.index >= gap)
        }
    }

    @Test
    fun `a window with no boundary at all still gets an axis`() {
        // Second-resolution bars inside one minute: nothing opens anything. An axis with no labels
        // is worse than one with arbitrary labels — the reader loses the ability to place anything
        // at all — so this is the one case that falls back to an even spread.
        val base = ZonedDateTime.of(2026, 3, 2, 9, 0, 0, 0, tehran).toEpochSecond()
        val times = LongArray(20) { base + it }
        val ticks = TimeScale.ticks(times, 0, times.lastIndex, tehran.asChartZone(), minGapBars = 4, maxTicks = 5)
        assertTrue(ticks.isNotEmpty())
        assertTrue("the fallback places ticks with no boundary", ticks.all { it.unit == null })
    }

    @Test
    fun `a price-driven type is numbered rather than dated`() {
        // Renko and its neighbours carry synthetic timestamps, so a date on that axis is a
        // fabricated one.
        val times = hourly(days = 3)
        val ticks = TimeScale.ticks(times, 0, times.lastIndex, tehran.asChartZone(), 4, 5, dated = false)
        assertTrue(ticks.isNotEmpty())
        assertTrue(ticks.all { it.unit == null })
    }

    @Test
    fun `a boundary is read in the reader's own zone and not the device's`() {
        val utc = ZoneId.of("UTC")
        // 00:30 on the first of April in Tehran is still the thirty-first of March in UTC.
        val after = ZonedDateTime.of(2026, 4, 1, 0, 30, 0, 0, tehran).toEpochSecond()
        val before = ZonedDateTime.of(2026, 3, 31, 23, 0, 0, 0, tehran).toEpochSecond()
        assertEquals(TimeTickUnit.MONTH, TimeScale.boundaryOf(after, before, tehran.asChartZone()))
        assertEquals(TimeTickUnit.HOUR, TimeScale.boundaryOf(after, before, utc.asChartZone()))
    }

    @Test
    fun `a window straddling a daylight-saving change is still read correctly`() {
        // The fast path takes one zone offset for the whole window. It has to abandon that when the
        // ends disagree, or the axis would be an hour out twice a year — on the one row of the
        // chart whose job is to say when.
        val newYork = ZoneId.of("America/New_York")
        val from = ZonedDateTime.of(2026, 3, 7, 0, 0, 0, 0, newYork)
        val times = LongArray(4 * 24) { from.plusHours(it.toLong()).toEpochSecond() }
        val ticks = TimeScale.ticks(times, 0, times.lastIndex, newYork.asChartZone(), minGapBars = 6, maxTicks = 6)
        ticks.forEach { tick ->
            assertEquals(
                "tick ${tick.index} must agree with a full calendar reading",
                TimeScale.boundaryOf(tick.time, times[tick.index - 1], newYork.asChartZone()),
                tick.unit,
            )
        }
    }

    @Test
    fun `a label names the boundary it opens and nothing more`() {
        val newYear = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, tehran).toEpochSecond()
        val month = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, tehran).toEpochSecond()
        val day = ZonedDateTime.of(2026, 3, 12, 0, 0, 0, 0, tehran).toEpochSecond()
        val hour = ZonedDateTime.of(2026, 3, 12, 14, 0, 0, 0, tehran).toEpochSecond()
        val span = 0L
        // A year boundary reads as the year. It used to read «1 Jan», which is the date of the
        // boundary rather than the thing the boundary is.
        assertEquals("2026", formatTimeTick(TimeTick(0, newYear, TimeTickUnit.YEAR), span, tehran))
        assertEquals("Mar", formatTimeTick(TimeTick(0, month, TimeTickUnit.MONTH), span, tehran))
        assertEquals("12 Mar", formatTimeTick(TimeTick(0, day, TimeTickUnit.DAY), span, tehran))
        assertEquals("14:00", formatTimeTick(TimeTick(0, hour, TimeTickUnit.HOUR), span, tehran))
    }

    @Test
    fun `a Persian reader gets the same label in their own calendar`() {
        // The chart was the last surface in the app on a Gregorian calendar. Every other date a
        // reader sees — the economic calendar's rows, a headline's timestamp, the activity log —
        // is Solar Hijri, so lining a CPI release up against the candle it moved meant converting
        // a calendar in one's head on the one screen whose whole point is lining two things up.
        //
        // These are conversions, not transliterations: 12 March 2026 in Tehran *is* 21 Esfand
        // 1404, eleven days from where a rendered-in-Persian Gregorian month would have put it —
        // which is the mistake the old comment on `formatTimeTick` was right to refuse.
        val newYear = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, tehran).toEpochSecond()
        val month = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, tehran).toEpochSecond()
        val day = ZonedDateTime.of(2026, 3, 12, 0, 0, 0, 0, tehran).toEpochSecond()
        val hour = ZonedDateTime.of(2026, 3, 12, 14, 0, 0, 0, tehran).toEpochSecond()
        val span = 0L
        fun fa(tick: TimeTick) = formatTimeTick(tick, span, tehran, jalali = true)
        assertEquals("۱۴۰۴", fa(TimeTick(0, newYear, TimeTickUnit.YEAR)))
        assertEquals("اسفند", fa(TimeTick(0, month, TimeTickUnit.MONTH)))
        assertEquals("۲۱ اسفند", fa(TimeTick(0, day, TimeTickUnit.DAY)))
        // The clock is untouched. It is a market figure read against MetaTrader and LBank, and
        // this is the same line `PersianDateTime` draws for the whole app.
        assertTrue(
            "the clock must stay Latin",
            fa(TimeTick(0, hour, TimeTickUnit.HOUR)).contains("14:00"),
        )
    }

    @Test
    fun `only a month and a year are set in bold`() {
        assertTrue(TimeTick(0, 0L, TimeTickUnit.YEAR).isBoundary())
        assertTrue(TimeTick(0, 0L, TimeTickUnit.MONTH).isBoundary())
        listOf(TimeTickUnit.WEEK, TimeTickUnit.DAY, TimeTickUnit.HOUR, TimeTickUnit.MINUTE).forEach {
            assertTrue("$it is not a wayfinding boundary", !TimeTick(0, 0L, it).isBoundary())
        }
    }

    @Test
    fun `a minute axis is labelled on round minutes`() {
        // The one unit whose members are not all worth naming. On a one-minute chart every bar opens
        // a minute, so without this the axis reads «09:37 09:41 09:46» — true times nobody says out
        // loud, and the exact arbitrary-label failure the price axis was fixed for.
        val from = ZonedDateTime.of(2026, 3, 2, 9, 0, 0, 0, tehran)
        val times = LongArray(80) { from.plusMinutes(it.toLong()).toEpochSecond() }
        val ticks = TimeScale.ticks(times, 0, times.lastIndex, tehran.asChartZone(), minGapBars = 8, maxTicks = 6)
        val minutes = ticks.filter { it.unit == TimeTickUnit.MINUTE }
        assertTrue("there should be minute labels to check", minutes.isNotEmpty())
        minutes.forEach { tick ->
            val minute = ZonedDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(tick.time),
                tehran,
            ).minute
            assertEquals("$minute past the hour is not a round time", 0, minute % 5)
        }
    }

    @Test
    fun `an hourly axis is left alone by the minute rule`() {
        // Every boundary on it is already round, so thinning could only ever throw labels away.
        val times = hourly(days = 2)
        val ticks = TimeScale.ticks(times, 0, times.lastIndex, tehran.asChartZone(), minGapBars = 5, maxTicks = 6)
        assertTrue(ticks.none { it.unit == TimeTickUnit.MINUTE })
        assertTrue(ticks.size >= 3)
    }
}

/**
 * Run F's two rules for the axis: the hours it names are round ones, and the label names the
 * **boundary** the bar opens rather than the bar's own stamp.
 *
 * The complaint behind both, from the owner's read of a shipped frame: «۱۰:۲۳ زیر هر روز تکرار
 * می‌شود ← تیک‌ها روی فاصله‌ی ثابت کندل گذاشته شده‌اند نه روی زمان‌های رند». Two separate
 * defects wearing one symptom — an hour ladder that took whichever hours survived the collision
 * gap, and a label printing a stamp that is only round on a feed whose bars are aligned.
 */
class RoundTimeAxisTest {

    private val tehran = CHART_ZONE
    private val zone = tehran.asChartZone()

    /** Hourly bars from midnight Tehran, offset into the hour by [pastTheHour] seconds. */
    private fun hourly(hours: Int, pastTheHour: Long = 0L): LongArray {
        val midnight = ZonedDateTime.of(2026, 3, 2, 0, 0, 0, 0, tehran).toEpochSecond()
        return LongArray(hours) { midnight + it * 3_600L + pastTheHour }
    }

    private fun hourOf(seconds: Long): Int =
        java.time.Instant.ofEpochSecond(seconds).atZone(tehran).hour

    @Test
    fun `an hourly chart is labelled at round hours of the day`() {
        val times = hourly(hours = 72)
        val ticks = TimeScale.ticks(times, 0, times.lastIndex, zone, minGapBars = 4, maxTicks = 8)
        val hours = ticks.filter { it.unit == TimeTickUnit.HOUR }.map { hourOf(it.boundaryTime(zone)) }
        assertTrue("the axis must name some hours: $ticks", hours.isNotEmpty())
        // Whatever step survived — six on this window — every hour named divides the day by it,
        // so the ladder repeats tomorrow instead of being re-chosen.
        val step = hours.toSet().minOf { if (it == 0) 24 else it }
        hours.forEach { hour ->
            assertEquals("«$hour:00» is not a round hour on a step of $step", 0, hour % step)
        }
    }

    @Test
    fun `a feed whose bars open past the hour is still labelled on the hour`() {
        // The screenshot's own case: bars stamped at 23 minutes and 20 seconds past. The bar is
        // where it is; the label is the hour it opened, which is what an axis is for.
        val times = hourly(hours = 72, pastTheHour = 23 * 60L + 20L)
        val ticks = TimeScale.ticks(times, 0, times.lastIndex, zone, minGapBars = 4, maxTicks = 8)
        ticks.filter { it.unit == TimeTickUnit.HOUR }.forEach { tick ->
            val at = java.time.Instant.ofEpochSecond(tick.boundaryTime(zone)).atZone(tehran)
            assertEquals("minutes must be zeroed, was $at", 0, at.minute)
            assertEquals("seconds must be zeroed, was $at", 0, at.second)
            // And it is still the *bar's own* hour: nothing is moved, only truncated.
            assertEquals(hourOf(tick.time), at.hour)
        }
    }

    @Test
    fun `a day boundary names the day it opens, whatever o'clock the session started`() {
        val times = hourly(hours = 96, pastTheHour = 23 * 60L + 20L)
        val ticks = TimeScale.ticks(times, 0, times.lastIndex, zone, minGapBars = 4, maxTicks = 8)
        ticks.filter { it.unit == TimeTickUnit.DAY || it.unit == TimeTickUnit.WEEK }.forEach { tick ->
            val at = java.time.Instant.ofEpochSecond(tick.boundaryTime(zone)).atZone(tehran)
            assertEquals("a date label stands on midnight, was $at", 0, at.hour)
            assertEquals(0, at.minute)
        }
    }

    @Test
    fun `a window too short for a round ladder keeps the hours it has`() {
        // Four hourly bars: there is no step of the day that leaves three labels, and an axis with
        // no labels is worse than one with unround ones. Nothing is thinned away.
        val times = hourly(hours = 4)
        val ticks = TimeScale.ticks(times, 0, times.lastIndex, zone, minGapBars = 1, maxTicks = 6)
        assertTrue("the short window must keep its labels: $ticks", ticks.size >= 3)
    }

    @Test
    fun `a tick with no calendar under it reports its own time`() {
        // A Renko bar's stamp is synthetic and the even spread gives `unit = null`; there is no
        // boundary to floor to and inventing one would print a date the series does not have.
        val tick = TimeTick(index = 3, time = 1_760_000_123L, unit = null)
        assertEquals(1_760_000_123L, tick.boundaryTime(zone))
    }
}
