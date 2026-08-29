package com.coinepro.feature.chart

import com.coinepro.core.chart.Candle
import com.coinepro.core.common.JalaliDate
import com.coinepro.core.marketdata.CHART_TIME_ZONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Resolving a typed Jalali date onto a bar.
 *
 * The failure this guards is a calendar conversion that is off by a day, which on a daily chart
 * puts the reader on the wrong candle and looks entirely correct while doing it.
 */
class ReplayGoToDateTest {

    /** Seven daily bars from 1403/05/09, cut at midnight in Tehran like every daily bar here. */
    private val bars: List<Candle> = (0 until 7).map { offset ->
        val day = JalaliDate(1403, 5, 9 + offset)
        val seconds = day.toGregorian().atStartOfDay(CHART_TIME_ZONE).toEpochSecond()
        Candle(t = seconds, o = 100.0, h = 101.0, l = 99.0, c = 100.0)
    }

    @Test
    fun `a date typed in Persian digits lands on the bar for that day`() {
        // The fourth bar of seven: 1403/05/09 plus three days.
        assertEquals(3, indexOfTypedDate("۱۴۰۳/۰۵/۱۲", bars))
    }

    @Test
    fun `the same date in Latin digits lands on the same bar`() {
        assertEquals(indexOfTypedDate("۱۴۰۳/۰۵/۱۲", bars), indexOfTypedDate("1403/05/12", bars))
        assertEquals(indexOfTypedDate("1403/05/12", bars), indexOfTypedDate("1403-05-12", bars))
    }

    @Test
    fun `a date the snapshot does not cover resolves to its nearest bar, not to nothing`() {
        // Long before the first bar. "Around there" is what somebody typing it means, and a
        // disabled button would leave them guessing which nearby day the feed has.
        assertEquals(0, indexOfTypedDate("1400/01/01", bars))
        assertEquals(bars.lastIndex, indexOfTypedDate("1410/01/01", bars))
    }

    @Test
    fun `half a date is not an error, it is somebody still typing`() {
        assertNull(indexOfTypedDate("", bars))
        assertNull(indexOfTypedDate("1403/05", bars))
        assertNull(indexOfTypedDate("سلام", bars))
    }

    @Test
    fun `a month or day outside the calendar is refused rather than clamped`() {
        assertNull(indexOfTypedDate("1403/13/01", bars))
        assertNull(indexOfTypedDate("1403/05/32", bars))
    }

    @Test
    fun `an empty chart has no bar to go to`() {
        assertNull(indexOfTypedDate("1403/05/12", emptyList()))
    }
}
