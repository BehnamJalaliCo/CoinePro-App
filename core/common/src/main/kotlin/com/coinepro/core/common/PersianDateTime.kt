package com.coinepro.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * How this app writes a date and a time to a Persian reader.
 *
 * There were eight of these before it moved here — `"MMM d · HH:mm"` copied into the calendar, the
 * news list, the activity log, the signal detail, the portfolio, the copy-trading screen and twice
 * into Home — and every one of them printed «Aug 26» into an interface whose every other word is
 * Persian. That is not a translation oversight. A reader planning around a data release converts it
 * in their head every time, and the calendar they convert it *to* is the one their bank, their
 * broker and their phone's lock screen all use.
 *
 * ## Why the date is Persian and the clock is not
 *
 * The date is prose: it names a day, and nobody compares a day against an exchange. It is written
 * in Solar Hijri with Persian digits, which is the rule this app follows for prose counts.
 *
 * The clock is **not**. `14:30` stays Latin, because that number is read against MetaTrader, LBank
 * or a broker's session table — it is a market figure in everything but name, and converting it
 * would be the same mistake as converting a price. This is the line the whole app draws, and it is
 * worth stating plainly here because a date and a time sitting either side of one middle dot is
 * exactly where somebody would be tempted to make them match.
 */
object PersianDateTime {

    /** Clock time only, Latin, in the reader's own zone — `14:30`. */
    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** «۵ شهریور» — a day, without the year, for a list where every row is within a few weeks. */
    fun day(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        JalaliDate.fromInstant(instant, zone).formatShort()

    /** «۵ شهریور ۱۴۰۵» — a day that could be any year, so it says which. */
    fun dayWithYear(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        JalaliDate.fromInstant(instant, zone).format()

    /** «۱۴۰۵/۰۶/۰۵» — the numeric form, for a field where a name would not fit. */
    fun numericDay(date: LocalDate): String {
        val jalali = JalaliDate.fromGregorian(date)
        return "${jalali.year.toPersianDigits()}/${pad(jalali.month)}/${pad(jalali.day)}"
    }

    fun numericDay(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        numericDay(instant.atZone(zone).toLocalDate())

    /** «پنجشنبه ۵ شهریور» — for a calendar, where the weekday is half of what a reader is after. */
    fun weekdayAndDay(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val zoned = instant.atZone(zone)
        return JalaliDate.weekdayName(zoned.toLocalDate()) + " " +
            JalaliDate.fromGregorian(zoned.toLocalDate()).formatShort()
    }

    /**
     * «۵ شهریور · 14:30» — a moment.
     *
     * The two halves are deliberately in different scripts. See the note above: the day is prose
     * and the clock is a figure a reader checks against their platform.
     */
    fun moment(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val zoned = instant.atZone(zone)
        return JalaliDate.fromGregorian(zoned.toLocalDate()).formatShort() + " · " +
            BidiText.isolateLtr(zoned.format(CLOCK))
    }

    /** Just the clock, isolated so a Latin `14:30` does not reorder inside a Persian line. */
    fun clock(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        BidiText.isolateLtr(instant.atZone(zone).format(CLOCK))

    /** «شهریور» — a month on its own, for an axis label or a monthly breakdown. */
    fun monthName(date: LocalDate): String = JalaliDate.fromGregorian(date).monthName

    private fun pad(value: Int): String =
        if (value < 10) "۰" + value.toPersianDigits() else value.toPersianDigits()
}
