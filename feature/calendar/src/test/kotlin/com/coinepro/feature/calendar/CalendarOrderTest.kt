package com.coinepro.feature.calendar

import com.coinepro.core.marketintel.EconomicEvent
import com.coinepro.core.marketintel.MarketImpact
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The calendar opens on what is coming, on every week — including the week nobody refreshed.
 *
 * «اگر تقویمی برای امروز بود این اول باشه تا اینکه ۸ شهریوریِ گذشته بالا باشه.» The screen was
 * showing a release six days old at the top of a list on the fourteenth, because the sources sort
 * ascending and the screen's answer to that was a scroll rather than an order. The last test here
 * is the one that fault actually was: a calendar where every row is behind, on which the scroll had
 * nothing to find and did nothing at all.
 */
class CalendarOrderTest {

    private val now: Instant = Instant.parse("2026-09-04T21:00:00Z")

    private fun event(id: String, at: String, stale: Boolean = false) = EconomicEvent(
        id = id,
        title = id,
        country = "US",
        currency = "USD",
        scheduledAt = Instant.parse(at),
        impact = MarketImpact.HIGH,
        actual = null,
        forecast = null,
        previous = null,
        relevance = emptySet(),
        isStale = stale,
    )

    private fun ids(events: List<EconomicEvent>) = events.map(EconomicEvent::id)

    @Test
    fun `what has not happened yet comes first, soonest at the top`() {
        val ordered = CalendarOrder.arrange(
            listOf(
                event("friday", "2026-09-05T12:30:00Z"),
                event("tonight", "2026-09-04T23:30:00Z"),
                event("monday", "2026-09-07T08:00:00Z"),
            ),
            now,
        )

        assertEquals(listOf("tonight", "friday", "monday"), ids(ordered))
    }

    @Test
    fun `what is already out sits under it, most recent first`() {
        val ordered = CalendarOrder.arrange(
            listOf(
                event("monday", "2026-08-31T12:30:00Z"),
                event("tomorrow", "2026-09-05T12:30:00Z"),
                event("wednesday", "2026-09-02T12:30:00Z"),
            ),
            now,
        )

        assertEquals(listOf("tomorrow", "wednesday", "monday"), ids(ordered))
    }

    @Test
    fun `a release an hour old is still what the market is trading`() {
        // Two hours, the same window the feed marks staleness with. A figure out at eight in the
        // evening is the thing being priced at nine, not history to be filed under the week.
        val ordered = CalendarOrder.arrange(
            listOf(
                event("tomorrow", "2026-09-05T12:30:00Z"),
                event("an-hour-ago", "2026-09-04T20:00:00Z"),
            ),
            now,
        )

        assertEquals(listOf("an-hour-ago", "tomorrow"), ids(ordered))
    }

    @Test
    fun `the source's own staleness flag is believed over the clock`() {
        // The server knows whether it has published a figure; the app only knows the time. A row
        // the server called stale is past even when its scheduled moment has not arrived.
        val ordered = CalendarOrder.arrange(
            listOf(
                event("server-says-done", "2026-09-05T09:00:00Z", stale = true),
                event("pending", "2026-09-06T09:00:00Z"),
            ),
            now,
        )

        assertEquals(listOf("pending", "server-says-done"), ids(ordered))
    }

    @Test
    fun `a week where everything is behind opens on the most recent, not the oldest`() {
        // The reported case. Nothing here is upcoming, so there is no first-upcoming row to scroll
        // to — which is precisely why the old fix could not work.
        val ordered = CalendarOrder.arrange(
            listOf(
                event("eighth", "2026-08-30T15:15:00Z"),
                event("ninth", "2026-08-31T23:50:00Z"),
                event("eleventh", "2026-09-02T06:30:00Z"),
            ),
            now,
        )

        assertEquals(listOf("eleventh", "ninth", "eighth"), ids(ordered))
    }

    @Test
    fun `an empty calendar and a single row are handed back untouched`() {
        assertEquals(emptyList<EconomicEvent>(), CalendarOrder.arrange(emptyList(), now))

        val one = listOf(event("only", "2026-08-01T00:00:00Z"))
        assertEquals(one, CalendarOrder.arrange(one, now))
    }
}
