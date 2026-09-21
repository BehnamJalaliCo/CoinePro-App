package com.coinepro.core.chart

import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where the day started.
 *
 * "How much is it up today" is a question about a distance on the chart, and until this line existed
 * there was nothing on the plot to measure that distance from — the session change lived only in the
 * page header, as a number, which is the one form of the answer a chart is bad at.
 */
class PreviousCloseTest {

    private val tehran = CHART_ZONE

    /** Hourly bars from midnight Tehran, each closing at its own index so a close names its bar. */
    private fun hourly(hours: Int, from: ZonedDateTime = ZonedDateTime.of(2026, 3, 2, 0, 0, 0, 0, tehran)) =
        CandleSeries(
            (0 until hours).map { index ->
                val time = from.plusHours(index.toLong()).toEpochSecond()
                Candle(time, 100.0, 101.0, 99.0, 100.0 + index)
            },
        )

    @Test
    fun `the reference is the last close of the previous calendar day`() {
        val series = hourly(hours = 30)
        // Bar 29 is 05:00 on the third; the previous day's last bar is 23:00 on the second, index 23.
        assertEquals(100.0 + 23, previousSessionClose(series, 29, tehran)!!, 1e-9)
    }

    @Test
    fun `every bar of one session shares the same reference`() {
        // The line must not move as the day goes on. A reference that slid bar by bar would be a
        // second live price, which is exactly what it is drawn faint to avoid being read as.
        val series = hourly(hours = 40)
        val reference = previousSessionClose(series, 24, tehran)
        (24 until 40).forEach { index ->
            assertEquals(reference, previousSessionClose(series, index, tehran))
        }
    }

    @Test
    fun `a chart that has not reached a previous day has no reference to draw`() {
        // Null rather than the first bar's open. A line at a number the session never closed at is
        // the class of plausible wrong answer this chart cannot afford.
        val series = hourly(hours = 6)
        assertNull(previousSessionClose(series, 5, tehran))
    }

    @Test
    fun `a daily chart draws nothing, because the previous close is the candle next door`() {
        val daily = CandleSeries(
            (0 until 20).map { index ->
                val time = ZonedDateTime.of(2026, 3, 2, 0, 0, 0, 0, tehran).plusDays(index.toLong()).toEpochSecond()
                Candle(time, 100.0, 101.0, 99.0, 100.0 + index)
            },
        )
        assertNull(previousSessionClose(daily, 19, tehran))
    }

    @Test
    fun `the session boundary is the reader's own midnight and not the device's`() {
        // Bars either side of Tehran's midnight are the same UTC day, so the two zones disagree about
        // which session a bar belongs to — and the line would sit on a different price in each.
        val series = hourly(hours = 30)
        val utc = java.time.ZoneId.of("UTC")
        assertEquals(100.0 + 23, previousSessionClose(series, 29, tehran)!!, 1e-9)
        // Tehran is UTC+3:30, so its midnight is 20:30 the day before in UTC and the UTC day turns
        // over four bars later: the boundary — and therefore the reference — lands on a different
        // bar, four hours' worth of price away from the right answer.
        val inUtc = previousSessionClose(series, 29, utc)
        assertEquals(100.0 + 27, inUtc!!, 1e-9)
    }

    @Test
    fun `a series too short to have a bar interval has no reference`() {
        assertNull(previousSessionClose(CandleSeries.EMPTY, 0, tehran))
        assertNull(previousSessionClose(hourly(hours = 1), 0, tehran))
    }
}
