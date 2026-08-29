package com.coinepro.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A date in the Solar Hijri calendar — the calendar this app's reader actually lives in.
 *
 * The economic calendar used to print `EEE, MMM d`: «Wed, Aug 26», in a Persian interface, for a
 * reader whose phone says ۵ شهریور. That is not a translation problem. A Persian reader planning
 * around a data release converts the Gregorian date in their head every single time, and the one
 * they convert it *to* is the one their bank, their broker and their calendar all use.
 *
 * The conversion is the Borkowski algorithm, the same one `jalaali-js` implements and the one
 * Iran's own civil calendar follows: leap years are determined by the 33-year cycle with its
 * documented breaks rather than by a modulus that is right most of the time. A simplified rule
 * disagrees with the printed calendar roughly once a decade, and the day it disagrees is a day the
 * app is showing the wrong date for a release somebody is trading.
 *
 * The Gregorian half is [LocalDate] rather than hand-rolled Julian day arithmetic: `toEpochDay`
 * already is the day number the algorithm needs, and the two thousand lines of civil-calendar edge
 * cases behind it are not worth reimplementing to save an import.
 *
 * Defined for Jalali years −61 to 3177 — 560 to 3798 CE — which is the span the break table covers.
 * Outside it there is no answer to give, and [fromGregorian] throws rather than returning a date
 * that is quietly wrong.
 */
data class JalaliDate(val year: Int, val month: Int, val day: Int) {

    /** «شهریور» — the month's own name, not a number. */
    val monthName: String get() = MONTH_NAMES[month - 1]

    /**
     * «۵ شهریور ۱۴۰۵».
     *
     * Persian digits, because a date in prose is a prose count and not a market figure. The rule
     * everywhere else in this app is the opposite — see [MarketNumberFormatter] — and the line
     * between them is whether a reader would ever compare the number against an exchange.
     */
    fun format(): String = "${day.toPersianDigits()} $monthName ${year.toPersianDigits()}"

    /** «۵ شهریور» — the year dropped, for a list where every row is within a few days. */
    fun formatShort(): String = "${day.toPersianDigits()} $monthName"

    fun toGregorian(): LocalDate = LocalDate.ofEpochDay(toEpochDay())

    private fun toEpochDay(): Long {
        val calculation = calculate(year)
        return LocalDate.of(calculation.gregorianYear, 3, calculation.march).toEpochDay() +
            (month - 1) * 31L - (month / 7) * (month - 7L) + day - 1
    }

    companion object {
        private val MONTH_NAMES = listOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
        )

        /**
         * Persian weekday names, indexed by [java.time.DayOfWeek.getValue] minus one.
         *
         * Saturday is the first day of the Iranian week, but this is a *lookup*, not an ordering,
         * so it stays in `DayOfWeek`'s own Monday-first order. Rotating it here to look like a week
         * would put every name one slot from where its index says it is.
         */
        private val WEEKDAY_NAMES = listOf(
            "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه",
        )

        /**
         * The breaks in the 33-year leap cycle, from Borkowski's analysis of the Iranian calendar.
         *
         * These are not decorative. Between two breaks the cycle is regular; at a break it is not,
         * and every simplified `year % 33 in setOf(...)` rule on the internet is a table that has
         * had these filed off. The result agrees with the printed calendar for a century and then
         * quietly does not.
         */
        private val BREAKS = intArrayOf(
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
            1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
        )

        fun fromGregorian(date: LocalDate): JalaliDate {
            val epochDay = date.toEpochDay()
            var jalaliYear = date.year - 621
            val calculation = calculate(jalaliYear)
            val firstDay = LocalDate.of(calculation.gregorianYear, 3, calculation.march).toEpochDay()

            var offset = epochDay - firstDay
            if (offset >= 0) {
                if (offset <= 185) {
                    // The first six months are 31 days each, without exception, in every year.
                    return JalaliDate(jalaliYear, 1 + (offset / 31).toInt(), (offset % 31).toInt() + 1)
                }
                offset -= 186
            } else {
                // Before Nowruz: this is the tail of the previous year, whose last month is 29 days
                // or 30 in a leap year — which is the only place [Calculation.leap] is read.
                jalaliYear -= 1
                offset += 179
                if (calculation.leap == 1) offset += 1
            }
            return JalaliDate(jalaliYear, 7 + (offset / 30).toInt(), (offset % 30).toInt() + 1)
        }

        fun fromInstant(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): JalaliDate =
            fromGregorian(instant.atZone(zone).toLocalDate())

        /**
         * The same conversion, answering null instead of throwing — and the one every caller
         * holding a date **from a server** must use.
         *
         * [calculate] refuses a year outside its table, deliberately and correctly: outside the
         * break table there is no answer, and a plausible one is worse than an exception. That is
         * right for a date this app computed. It is wrong for a date this app was *sent*.
         *
         * A backend's "never expires" is routinely `9999-12-31` and its "unset" is routinely
         * `0001-01-01`. Both are outside the table, so both threw — and the throw happened inside a
         * composable, on the main thread, with nothing catching it. Switching to the platform whose
         * account carried such a date took the whole app down. A screen that cannot render a date
         * should show a dash; it should not be able to kill the process.
         *
         * So: this exists, and nothing that formats a value which crossed the network may call the
         * throwing pair.
         */
        fun fromGregorianOrNull(date: LocalDate): JalaliDate? =
            runCatching { fromGregorian(date) }.getOrNull()

        fun fromInstantOrNull(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): JalaliDate? =
            runCatching { fromInstant(instant, zone) }.getOrNull()

        /** «چهارشنبه» for the given Gregorian date. */
        fun weekdayName(date: LocalDate): String = WEEKDAY_NAMES[date.dayOfWeek.value - 1]

        private data class Calculation(val leap: Int, val gregorianYear: Int, val march: Int)

        private fun calculate(jalaliYear: Int): Calculation {
            require(jalaliYear >= BREAKS.first() && jalaliYear < BREAKS.last()) {
                // Refused rather than extrapolated. The table is the algorithm; outside it there is
                // no answer to give, and a plausible one is worse than an exception.
                "Jalali year $jalaliYear is outside the range this calendar is defined for"
            }
            val gregorianYear = jalaliYear + 621
            var leapJ = -14
            var previousBreak = BREAKS[0]
            var jump = 0

            for (index in 1 until BREAKS.size) {
                val current = BREAKS[index]
                jump = current - previousBreak
                if (jalaliYear < current) break
                leapJ += (jump / 33) * 8 + (jump % 33) / 4
                previousBreak = current
            }

            var n = jalaliYear - previousBreak
            leapJ += (n / 33) * 8 + (n % 33 + 3) / 4
            if (jump % 33 == 4 && jump - n == 4) leapJ += 1

            val leapG = gregorianYear / 4 - ((gregorianYear / 100 + 1) * 3) / 4 - 150
            val march = 20 + leapJ - leapG

            if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
            var leap = ((n + 1) % 33 - 1) % 4
            if (leap == -1) leap = 4

            return Calculation(leap = leap, gregorianYear = gregorianYear, march = march)
        }
    }
}
