package com.coinepro.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The brief's calendar.
 *
 * Every case here is one whose wrong answer takes a day to notice, which is the whole reason the
 * clock is a parameter.
 */
class MorningBriefScheduleTest {

    private fun minutes(millis: Long): Long = millis / MorningBriefSchedule.MINUTE_MILLIS

    @Test
    fun `later today is later today`() {
        assertEquals(120L, minutes(MorningBriefSchedule.delayMillis(5 * 60, 7 * 60)))
    }

    @Test
    fun `an hour already past is tomorrow`() {
        assertEquals(
            (22 * 60).toLong(),
            minutes(MorningBriefSchedule.delayMillis(9 * 60, 7 * 60)),
        )
    }

    @Test
    fun `exactly on the minute is a whole day, never zero`() {
        // Zero is how a daily brief becomes a loop: fire, reschedule for now, fire again.
        assertEquals(
            (24 * 60).toLong(),
            minutes(MorningBriefSchedule.delayMillis(7 * 60, 7 * 60)),
        )
    }

    @Test
    fun `one minute before and one minute after`() {
        assertEquals(1L, minutes(MorningBriefSchedule.delayMillis(7 * 60 - 1, 7 * 60)))
        assertEquals(24L * 60L - 1L, minutes(MorningBriefSchedule.delayMillis(7 * 60 + 1, 7 * 60)))
    }

    @Test
    fun `midnight at either end`() {
        assertEquals(1L, minutes(MorningBriefSchedule.delayMillis(24 * 60 - 1, 0)))
        assertEquals(1L, minutes(MorningBriefSchedule.delayMillis(0, 1)))
    }

    @Test
    fun `an out-of-range stored minute is clamped rather than scheduling for never`() {
        assertTrue(MorningBriefSchedule.delayMillis(0, 5_000) > 0L)
        assertTrue(MorningBriefSchedule.delayMillis(-10, 60) > 0L)
    }

    @Test
    fun `a brief never delivered is not today's`() {
        assertFalse(MorningBriefSchedule.alreadyDeliveredToday(null, null, todayLocalDay = 20_000L))
        assertFalse(MorningBriefSchedule.alreadyDeliveredToday(0L, 20_000L, todayLocalDay = 20_000L))
    }

    @Test
    fun `a second wake-up on the same day does not send a second brief`() {
        // WorkManager fires when it can, and a phone out of Doze can have two runs queued.
        assertTrue(
            MorningBriefSchedule.alreadyDeliveredToday(1L, 20_000L, todayLocalDay = 20_000L),
        )
    }

    @Test
    fun `yesterday's brief does not suppress today's`() {
        assertFalse(
            MorningBriefSchedule.alreadyDeliveredToday(1L, 19_999L, todayLocalDay = 20_000L),
        )
    }

    @Test
    fun `a brief delivered early still leaves tomorrow's alone`() {
        // The case a «twenty hours ago» rule gets wrong: 07:05 Monday, 06:50 Tuesday is 23h45m and
        // would be refused, leaving Tuesday with no brief at all.
        assertFalse(
            MorningBriefSchedule.alreadyDeliveredToday(1L, 19_999L, todayLocalDay = 20_000L),
        )
    }

    @Test
    fun `the brief is scheduled only when the reader switched it on`() {
        val off = NotificationSettings()
        assertFalse(off.briefScheduled)
        val on = off.copy(
            categories = off.categories + (NotificationCategory.MORNING_BRIEF to true),
        )
        assertTrue(on.briefScheduled)
        assertFalse(on.copy(enabled = false).briefScheduled)
    }

    @Test
    fun `the default hour is seven and it is off until asked for`() {
        assertEquals(7 * 60, NotificationSettings().briefMinuteOfDay)
        assertFalse(NotificationCategory.MORNING_BRIEF.defaultOn)
    }
}
