package com.coinepro.feature.calendar

import com.coinepro.core.marketintel.EconomicEvent
import java.time.Instant

/**
 * What order a week of releases is read in.
 *
 * ### The report
 *
 * «تقویم اقتصادی باید به‌روزترین انتشار در بالاترین قسمت قرار بگیره نه اینکه پایین باشه… اگر تقویمی
 * برای امروز بود این اول باشه تا اینکه ۸ شهریوری که گذشته تایم بالا باشه.» The screen opened on the
 * eighth of the month with today six days later, and it was not a bug in the feed: the sources sort
 * ascending, and a plain ascending week puts Monday's released figures above Thursday's pending
 * ones for anybody reading it on Thursday.
 *
 * ### Why the scroll that was there did not fix it
 *
 * The screen used to jump to the first row that was not stale. That works only while the week
 * contains one — and the calendar most in need of this is the one nobody has refreshed, where
 * **every** row is behind. On that calendar the jump found nothing, the list stayed at the top, and
 * the top was the oldest thing in the file. An order is a property of the list; a scroll is a
 * position in it, and the position is lost the moment anything moves.
 *
 * ### The order
 *
 * What has not happened yet, soonest first — that is what a calendar is for. Then what has already
 * happened, most recent first, because the reading that just landed is the one that explains the
 * price on the screen behind this one and the one from Monday is history. Nothing is hidden: a
 * released figure is half of what the next one means, and it is one flick down rather than a filter
 * away.
 *
 * The boundary is [EconomicEvent.isStale] where the source stated it and two hours past the
 * scheduled moment otherwise — the same window `PublicCalendarFeed` uses, because a release twenty
 * minutes old is still the thing the market is trading.
 */
internal object CalendarOrder {

    /** Two hours, matching the feed's own staleness rule. */
    const val SETTLED_AFTER_SECONDS = 2 * 60 * 60L

    /** Whether [event] is behind the reader rather than ahead of them. */
    fun isPast(event: EconomicEvent, now: Instant): Boolean =
        event.isStale || event.scheduledAt.isBefore(now.minusSeconds(SETTLED_AFTER_SECONDS))

    /**
     * [events] with the upcoming ones first, ascending, and the past ones after, descending.
     *
     * Stable in both halves: two releases at the same instant keep the order the source sent them
     * in, which is the only order either of them has.
     */
    fun arrange(events: List<EconomicEvent>, now: Instant): List<EconomicEvent> {
        if (events.size < 2) return events
        val (past, upcoming) = events.partition { isPast(it, now) }
        return upcoming.sortedBy { it.scheduledAt } + past.sortedByDescending { it.scheduledAt }
    }
}
