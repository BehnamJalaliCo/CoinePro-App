package com.coinepro.core.common

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The Solar Hijri conversion, pinned against dates that can be checked against a printed calendar.
 *
 * Every case here is a date whose Jalali equivalent is publicly verifiable, and several are chosen
 * because a simplified `year % 33` leap rule gets them wrong. That is the point: the failure mode
 * of this code is not a crash, it is being one day out on a date somebody is planning around, and
 * the only defence against that is fixtures somebody has checked by hand.
 */
class JalaliDateTest {

    @Test
    fun `Nowruz maps to the first of Farvardin`() {
        assertEquals(JalaliDate(1404, 1, 1), JalaliDate.fromGregorian(LocalDate.of(2025, 3, 21)))
        assertEquals(JalaliDate(1403, 1, 1), JalaliDate.fromGregorian(LocalDate.of(2024, 3, 20)))
        assertEquals(JalaliDate(1399, 1, 1), JalaliDate.fromGregorian(LocalDate.of(2020, 3, 20)))
    }

    @Test
    fun `the day before Nowruz is the last of Esfand`() {
        // 1403 is a leap year, so Esfand has 30 days. A simplified modulus rule that got the leap
        // wrong would put this on the 29th and every date after it one day out.
        assertEquals(JalaliDate(1403, 12, 30), JalaliDate.fromGregorian(LocalDate.of(2025, 3, 20)))
        // 1402 is not, so Esfand has 29.
        assertEquals(JalaliDate(1402, 12, 29), JalaliDate.fromGregorian(LocalDate.of(2024, 3, 19)))
    }

    @Test
    fun `an ordinary date in the second half of the year`() {
        assertEquals(JalaliDate(1405, 6, 5), JalaliDate.fromGregorian(LocalDate.of(2026, 8, 27)))
        assertEquals(JalaliDate(1404, 10, 11), JalaliDate.fromGregorian(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `the conversion round-trips over four thousand consecutive days`() {
        // Roughly eleven years, crossing three leap years in both calendars. A conversion that is
        // right on the fixtures and wrong in between is the failure this catches.
        var date = LocalDate.of(2020, 1, 1)
        repeat(4_000) {
            assertEquals(date, JalaliDate.fromGregorian(date).toGregorian())
            date = date.plusDays(1)
        }
    }

    @Test
    fun `every day of a leap year is accounted for, and the year is 366 days`() {
        val start = JalaliDate(1403, 1, 1).toGregorian()
        val next = JalaliDate(1404, 1, 1).toGregorian()
        assertEquals(366L, next.toEpochDay() - start.toEpochDay())

        val ordinary = JalaliDate(1402, 1, 1).toGregorian()
        assertEquals(365L, start.toEpochDay() - ordinary.toEpochDay())
    }

    @Test
    fun `the month name and the digits are Persian`() {
        assertEquals("شهریور", JalaliDate(1405, 6, 5).monthName)
        assertEquals("۵ شهریور ۱۴۰۵", JalaliDate(1405, 6, 5).format())
        assertEquals("۵ شهریور", JalaliDate(1405, 6, 5).formatShort())
    }

    @Test
    fun `weekday names line up with the day the date actually fell on`() {
        // 2026-08-27 is a Thursday.
        assertEquals("پنجشنبه", JalaliDate.weekdayName(LocalDate.of(2026, 8, 27)))
        // 2026-08-29 is a Saturday — the first day of the Iranian week.
        assertEquals("شنبه", JalaliDate.weekdayName(LocalDate.of(2026, 8, 29)))
    }

    @Test
    fun `a date outside the break table is refused rather than guessed`() {
        // The break table covers Jalali -61 to 3177, which is 560 to 3798 CE. Beyond it there is
        // no answer to give, and a plausible-looking one is worse than an exception nobody can
        // ignore. Neither end is a date this app will ever be handed; the guard is there because
        // the alternative to throwing is extrapolating, and extrapolating produces a real-looking
        // wrong date.
        assertThrows(IllegalArgumentException::class.java) {
            JalaliDate.fromGregorian(LocalDate.of(400, 1, 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            JalaliDate.fromGregorian(LocalDate.of(3900, 1, 1))
        }
    }
}
