package com.coinepro.core.chart

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The session bands, the period rules and the prior period's levels (5.12.0).
 *
 * The daylight-saving rules in [Sessions] are written out by hand because the module has no
 * time-zone database on every target; here, on the JVM, there is one, and every hour of three
 * years is checked against it.
 */
class SessionsTest {

    private val cities = mapOf(
        Sessions.Session.SYDNEY to ZoneId.of("Australia/Sydney"),
        Sessions.Session.TOKYO to ZoneId.of("Asia/Tokyo"),
        Sessions.Session.LONDON to ZoneId.of("Europe/London"),
        Sessions.Session.NEW_YORK to ZoneId.of("America/New_York"),
    )

    @Test
    fun `the hand-written daylight saving agrees with the time-zone database every hour for three years`() {
        val start = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")).toEpochSecond()
        for ((session, zone) in cities) {
            var t = start
            while (t < start + 3 * 366 * 86_400L) {
                val expected = zone.rules.getOffset(java.time.Instant.ofEpochSecond(t)).totalSeconds.toLong()
                assertEquals("$session at $t", expected, Sessions.offsetSeconds(session, t))
                t += 3_600L
            }
        }
    }

    private fun hourly(fromUtc: ZonedDateTime, hours: Int) = CandleSeries(
        (0 until hours).map { index ->
            val t = fromUtc.toEpochSecond() + index * 3_600L
            val price = 100.0 + (index % 24) * 0.1 + index * 0.01
            Candle(t, price, price + 0.5, price - 0.5, price + 0.2, 100.0)
        },
    )

    @Test
    fun `london opens at eight local in winter and in summer`() {
        for (month in listOf(1, 7)) {
            val monday = ZonedDateTime.of(2026, month, if (month == 1) 5 else 6, 0, 0, 0, 0, ZoneId.of("UTC"))
            val bands = Sessions.sessionBands(hourly(monday, 48))
            val london = bands.first { it.label == "LDN" }
            val local = java.time.Instant.ofEpochSecond(london.from).atZone(ZoneId.of("Europe/London"))
            assertEquals(8, local.hour)
            assertEquals(9 * 3_600L, london.to - london.from)
        }
    }

    @Test
    fun `the weekend has no session and the overlap sits inside both of its parents`() {
        val saturday = ZonedDateTime.of(2026, 3, 14, 0, 0, 0, 0, ZoneId.of("UTC"))
        val weekend = Sessions.sessionBands(hourly(saturday, 24))
        assertTrue("sessions drawn on a Saturday: $weekend", weekend.none { it.label == "LDN" || it.label == "NY" })

        val tuesday = ZonedDateTime.of(2026, 3, 17, 0, 0, 0, 0, ZoneId.of("UTC"))
        val bands = Sessions.sessionBands(hourly(tuesday, 24))
        val london = bands.first { it.label == "LDN" }
        val newYork = bands.first { it.label == "NY" }
        val overlap = bands.first { it.label == null }
        assertTrue(overlap.from >= london.from && overlap.from >= newYork.from)
        assertTrue(overlap.to <= london.to && overlap.to <= newYork.to)
    }

    @Test
    fun `a daily chart gets no session bands and month rules`() {
        val start = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")).toEpochSecond()
        val daily = CandleSeries((0 until 120).map { Candle(start + it * 86_400L, 1.0, 2.0, 0.5, 1.5) })
        assertTrue(Sessions.sessionBands(daily).isEmpty())
        assertEquals(Sessions.Period.MONTH, Sessions.separatorPeriod(daily))
        val rules = Sessions.periodSeparators(daily)
        // February, March and April begin inside a hundred and twenty days from the first of January.
        assertEquals(3, rules.size)
        assertTrue(rules.all { it.isRule })
    }

    @Test
    fun `yesterday's levels are yesterday's, and the first day has none`() {
        val start = ZonedDateTime.of(2026, 3, 16, 0, 0, 0, 0, ZoneId.of("UTC"))
        val series = hourly(start, 72)
        val levels = assertNotNullAndGet(Sessions.previousPeriodLevels(series))
        assertNull(levels.high[5])
        // Bar 30 is on the second day; its level is the first day's high.
        val firstDayHigh = (0 until 24).maxOf { series.high[it] }
        val firstDayLow = (0 until 24).minOf { series.low[it] }
        assertEquals(firstDayHigh, levels.high[30]!!, 1e-9)
        assertEquals(firstDayLow, levels.low[30]!!, 1e-9)
        assertEquals(series.close[23], levels.close[30]!!, 1e-9)
        assertEquals(series.open[24], levels.open[30]!!, 1e-9)
        // Broken at the boundary, so the step is a gap and not a stroke.
        assertNull(levels.high[48])
        assertFalse(levels.high[49] == null)
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
