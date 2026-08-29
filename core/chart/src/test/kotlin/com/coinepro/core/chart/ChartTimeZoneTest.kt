package com.coinepro.core.chart

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One zone for the whole time axis.
 *
 * The bug: the bars are cut into buckets in [CHART_ZONE] while both the label under them and the
 * bold month boundary were read in `ZoneId.systemDefault()`. On any device not set to Tehran the
 * label and the boundary could name different days for the same bar — and nothing on screen said
 * so, which is the worst kind of wrong a time axis can be.
 */
class ChartTimeZoneTest {

    private val tehran = CHART_ZONE
    private val utc = ZoneId.of("UTC")

    /** 00:30 on the first of April in Tehran, which is still the thirty-first of March in UTC. */
    private val afterMidnight = ZonedDateTime.of(2026, 4, 1, 0, 30, 0, 0, tehran).toEpochSecond()

    /** And an hour and a half before it, which is March in both zones. */
    private val beforeMidnight = ZonedDateTime.of(2026, 3, 31, 23, 0, 0, 0, tehran).toEpochSecond()

    @Test
    fun `the chart reads times in the same zone the bars are bucketed in`() {
        assertEquals("Asia/Tehran", CHART_ZONE.id)
    }

    @Test
    fun `a month boundary is decided in the zone it is asked for`() {
        assertTrue(
            "in Tehran the label opens April",
            startsAPeriod(afterMidnight, beforeMidnight, tehran),
        )
        assertFalse(
            "and in UTC the same pair of bars are both still March",
            startsAPeriod(afterMidnight, beforeMidnight, utc),
        )
    }

    @Test
    fun `the first label of a view is never a boundary`() {
        assertFalse("there is nothing before it to be a boundary against", startsAPeriod(afterMidnight, null, tehran))
        assertFalse(startsAPeriod(null, beforeMidnight, tehran))
    }

    @Test
    fun `two bars in the same month are not a boundary in any zone`() {
        val earlier = ZonedDateTime.of(2026, 4, 10, 9, 0, 0, 0, tehran).toEpochSecond()
        val later = ZonedDateTime.of(2026, 4, 17, 9, 0, 0, 0, tehran).toEpochSecond()
        assertFalse(startsAPeriod(later, earlier, tehran))
        assertFalse(startsAPeriod(later, earlier, utc))
    }

    @Test
    fun `a year turning over is a boundary as much as a month is`() {
        val december = ZonedDateTime.of(2025, 12, 30, 12, 0, 0, 0, tehran).toEpochSecond()
        val january = ZonedDateTime.of(2026, 1, 2, 12, 0, 0, 0, tehran).toEpochSecond()
        assertTrue(startsAPeriod(january, december, tehran))
    }

    @Test
    fun `the label and the boundary read the same clock`() {
        // The label prints the hour in whichever zone it is handed, which is the half of the pair
        // that used to be read separately.
        val moment = ZonedDateTime.of(2026, 3, 4, 13, 15, 0, 0, tehran).toEpochSecond()
        assertEquals("13:15", formatTime(moment, spanSeconds = 0L, zone = tehran))
        assertEquals("09:45", formatTime(moment, spanSeconds = 0L, zone = utc))
    }
}
