package com.coinepro.feature.calendar

import com.coinepro.core.marketintel.EconomicEvent
import com.coinepro.core.marketintel.MarketImpact
import com.coinepro.core.marketintel.MarketIntelState
import com.coinepro.core.model.MarketPlatform
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An empty calendar has to say which kind of empty it is.
 *
 * «تقویم داده ندارد» was reported as a broken screen, and the screen was not broken — both backends
 * send an empty `calendar` array today, for two different reasons of their own, which is a server
 * matter recorded in `docs/SERVER_ASK_ECONOMIC_CALENDAR.md`. What the app got wrong was the
 * sentence: a server that published nothing and a filter that hid everything drew the same words,
 * so a reader with the filter on «همه» was told their filter was the problem.
 */
class CalendarModeTest {

    private fun event(impact: MarketImpact) = EconomicEvent(
        id = impact.name,
        title = "release",
        country = "US",
        currency = "USD",
        scheduledAt = Instant.parse("2026-08-30T12:30:00Z"),
        impact = impact,
        actual = null,
        forecast = null,
        previous = null,
        relevance = emptySet(),
        isStale = false,
    )

    @Test
    fun `a server that published nothing does not blame the reader's filter`() {
        val mode = calendarMode(MarketIntelState(), filtered = emptyList())

        assertEquals(CalendarMode.NOTHING_PUBLISHED, mode)
    }

    @Test
    fun `a filter that matched none of the server's events says so`() {
        val state = MarketIntelState(calendar = listOf(event(MarketImpact.LOW)))

        assertEquals(CalendarMode.FILTERED_OUT, calendarMode(state, filtered = emptyList()))
    }

    @Test
    fun `events on screen are events`() {
        val events = listOf(event(MarketImpact.HIGH))

        assertEquals(CalendarMode.EVENTS, calendarMode(MarketIntelState(calendar = events), events))
    }

    @Test
    fun `a failed refresh over a calendar already on screen keeps the calendar`() {
        // Stale release times are still the release times, and the strip above them reports the
        // failure. Replacing them with an error page would lose the only thing the reader came for.
        val events = listOf(event(MarketImpact.HIGH))
        val state = MarketIntelState(calendar = events, failed = true, error = "سرور پاسخ نداد.")

        assertEquals(CalendarMode.EVENTS, calendarMode(state, events))
    }

    @Test
    fun `a failure with nothing to show is the failure`() {
        val state = MarketIntelState(failed = true, error = "سرور پاسخ نداد.")

        assertEquals(CalendarMode.ERROR, calendarMode(state, filtered = emptyList()))
    }

    @Test
    fun `a failure without a server sentence is still the failure`() {
        // The shape of «تقویم هنوز خراب بود». A timeout, a DNS miss or a fault on the main thread
        // carries no HTTP body, so `error` stays null; the mode must come from `failed`, or the
        // reader is told nothing was published about a week the app never managed to fetch.
        val state = MarketIntelState(failed = true)

        assertEquals(CalendarMode.ERROR, calendarMode(state, filtered = emptyList()))
    }

    @Test
    fun `a platform that publishes no calendar says that, and offers no refresh`() {
        // The other half of «تقویم خالی است», reported three times. TradeYar has no calendar route
        // in its API at all and is asked for `calendar: []` by contract, so a crypto reader was
        // being told the server had sent nothing — beside a refresh button that could never change
        // the answer. That is a fact about the product, not an outage.
        val state = MarketIntelState(platform = MarketPlatform.TRADEYAR)

        assertEquals(CalendarMode.NOT_ON_THIS_PLATFORM, calendarMode(state, filtered = emptyList()))
    }

    @Test
    fun `the forex platform with an empty calendar is still an empty publication`() {
        val state = MarketIntelState(platform = MarketPlatform.COINEPRO_FX)

        assertEquals(CalendarMode.NOTHING_PUBLISHED, calendarMode(state, filtered = emptyList()))
    }

    @Test
    fun `before a fetch answers, the platform is unknown and nothing is claimed about it`() {
        // Guessing here would put «this platform has no calendar» in front of a forex reader whose
        // calendar is still in flight.
        assertEquals(
            CalendarMode.LOADING,
            calendarMode(MarketIntelState(loading = true, platform = null), filtered = emptyList()),
        )
    }

    @Test
    fun `the first read is loading rather than a calendar with no events in it`() {
        assertEquals(
            CalendarMode.LOADING,
            calendarMode(MarketIntelState(loading = true), filtered = emptyList()),
        )
    }
}
