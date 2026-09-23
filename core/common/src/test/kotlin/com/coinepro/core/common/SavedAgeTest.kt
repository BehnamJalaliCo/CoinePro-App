package com.coinepro.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How old a saved picture is.
 *
 * The two assertions this file exists for: that an unknown or impossible timestamp produces **no
 * age at all** rather than «just now», and that every unit floors rather than rounds — a picture
 * must never be made to sound fresher than it is.
 */
class SavedAgeTest {

    private val now = 1_700_000_000_000L

    private fun ago(seconds: Long) = SavedAge.of(now - seconds * 1_000L, now)

    @Test
    fun `no timestamp is no age`() {
        // Not «همین حالا». A cache with no stored time is a cache whose age is unknown, and
        // answering «just now» would be the app answering a question it cannot answer.
        assertNull(SavedAge.of(null, now))
        assertNull(SavedAge.of(0L, now))
        assertNull(SavedAge.of(-1L, now))
    }

    @Test
    fun `a timestamp in the future is no age either`() {
        // The phone's clock moved; the data is not from the future. Clamping to zero would print
        // «همین حالا» over a picture that might be days old, which is the worse wrong answer.
        assertNull(SavedAge.of(now + 60_000L, now))
    }

    @Test
    fun `the same instant is moments, with no number`() {
        assertEquals(SavedAge(SavedAgeUnit.MOMENTS, 0), SavedAge.of(now, now))
    }

    @Test
    fun `under a minute is moments`() {
        assertEquals(SavedAgeUnit.MOMENTS, ago(59)!!.unit)
    }

    @Test
    fun `a minute is a minute`() {
        assertEquals(SavedAge(SavedAgeUnit.MINUTES, 1), ago(60))
    }

    @Test
    fun `fifty-nine minutes is not an hour`() {
        // Floored, not rounded. Rounding up makes a picture sound older than it is; rounding to
        // nearest makes it sound younger half the time, and a reader deciding whether to trust a
        // number is better served by the figure that cannot overstate freshness.
        assertEquals(SavedAge(SavedAgeUnit.MINUTES, 59), ago(59 * 60 + 59))
    }

    @Test
    fun `an hour is an hour`() {
        assertEquals(SavedAge(SavedAgeUnit.HOURS, 1), ago(3_600))
    }

    @Test
    fun `twenty-three hours is not a day`() {
        assertEquals(SavedAge(SavedAgeUnit.HOURS, 23), ago(23 * 3_600 + 3_599))
    }

    @Test
    fun `a day is a day`() {
        assertEquals(SavedAge(SavedAgeUnit.DAYS, 1), ago(86_400))
    }

    @Test
    fun `a week is seven days rather than its own unit`() {
        // No «weeks». A reader looking at a week-old picture needs to know it is seven days old,
        // and «۱ هفته» reads as a rounder, smaller fact than it is.
        assertEquals(SavedAge(SavedAgeUnit.DAYS, 7), ago(7 * 86_400))
    }

    @Test
    fun `a very old picture keeps counting in days`() {
        assertEquals(SavedAge(SavedAgeUnit.DAYS, 400), ago(400 * 86_400))
    }
}
