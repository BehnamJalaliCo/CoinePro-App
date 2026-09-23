package com.coinepro.core.notifications

/**
 * When the next morning brief is due, and how long from now that is.
 *
 * ### Why this is a pure function and not three lines inside a worker
 *
 * Because every bug it can have is a bug nobody sees for a day. «The brief arrived at four in the
 * afternoon», «the brief arrived twice», «the brief stopped after the clocks changed» are all one
 * arithmetic slip apart from each other, and none of them can be found by running the app — you
 * would have to wait until tomorrow to see the wrong answer. With the clock as a parameter, each
 * of them is a case in a list.
 *
 * ### What it does not decide
 *
 * Whether there should be a brief at all — [NotificationSettings.briefScheduled] answers that — and
 * what the brief says, which is `RasadBrief`'s. This is the calendar and nothing else.
 */
object MorningBriefSchedule {

    /** One day, in milliseconds. The period between briefs where nothing intervenes. */
    const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

    /** One minute, in milliseconds. */
    const val MINUTE_MILLIS: Long = 60L * 1000L

    /**
     * How long from now until the next delivery, in milliseconds.
     *
     * [nowMinuteOfDay] is the reader's **local** minute since midnight, which is what makes this
     * follow them across a time zone: the caller reads it from the device's own calendar each time
     * the work is scheduled, so a phone that moved five hours east recomputes rather than arriving
     * five hours late for ever.
     *
     * The boundary is the only interesting case and it goes **forward**: at exactly the chosen
     * minute the answer is a whole day, not zero. Zero would mean a worker that fires, reschedules
     * itself for zero, and fires again — which is the way a daily brief becomes a loop — and the
     * cost of the other reading is at most one minute of lateness on the very first day.
     */
    fun delayMillis(nowMinuteOfDay: Int, targetMinuteOfDay: Int): Long {
        val now = nowMinuteOfDay.coerceIn(0, MINUTES_IN_DAY - 1)
        val target = targetMinuteOfDay.coerceIn(0, MINUTES_IN_DAY - 1)
        val minutes = if (target > now) target - now else target - now + MINUTES_IN_DAY
        return minutes * MINUTE_MILLIS
    }

    /**
     * Whether a brief delivered at [lastDeliveredEpochMillis] means today's has already gone.
     *
     * WorkManager fires when it can rather than when it was asked to, and a phone that was in Doze
     * over the chosen hour wakes some time afterwards — sometimes with two runs queued. Without
     * this, a reader would get two identical briefs a few minutes apart, which is the fastest way
     * to teach somebody to switch a feature off.
     *
     * Keyed on **the local day** rather than on «at least twenty hours ago», because the second
     * reading silently skips a day whenever a run lands early: a brief at 07:05 on Monday and a
     * wake-up at 06:58 on Tuesday is 23 hours 53 minutes, which passes, but a wake-up at 06:50 is
     * not and would leave Tuesday with no brief at all.
     */
    fun alreadyDeliveredToday(
        lastDeliveredEpochMillis: Long?,
        lastDeliveredLocalDay: Long?,
        todayLocalDay: Long,
    ): Boolean {
        if (lastDeliveredEpochMillis == null || lastDeliveredEpochMillis <= 0L) return false
        // A stored day from the future is a clock that went backwards — a manual change, or a
        // network sync after a flat battery. Treated as «not today», so the reader gets their
        // brief rather than silently losing it until the calendar catches up.
        val day = lastDeliveredLocalDay ?: return false
        return day == todayLocalDay
    }

    private const val MINUTES_IN_DAY = 24 * 60
}
