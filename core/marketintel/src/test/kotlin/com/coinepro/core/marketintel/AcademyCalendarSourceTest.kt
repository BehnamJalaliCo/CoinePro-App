package com.coinepro.core.marketintel

import com.google.gson.JsonParser
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The calendar route this app had never called, read without having seen its body.
 *
 * Every case here is a shape `academy/bn/calendar` might plausibly answer in, because nobody on
 * this side has seen one. That is the honest position and these tests are what makes it safe: the
 * reader either understands a response or reports precisely what it could not use, and it can never
 * invent an event that was not sent.
 */
class AcademyCalendarSourceTest {

    private fun read(body: String) = readCalendar(JsonParser.parseString(body))

    @Test
    fun `the contract's own shape reads`() {
        val outcome = read(
            """
            {"calendar":[{"id":"us-cpi","title":"شاخص قیمت مصرف‌کننده آمریکا","country":"US",
              "currency":"USD","scheduled_at":"2026-08-26T12:30:00Z","impact":"high",
              "forecast":"0.3%","previous":"0.2%","relevance":["gold","silver"],"stale":false}]}
            """.trimIndent(),
        )
        val event = outcome.events.single()
        assertEquals("us-cpi", event.id)
        assertEquals(MarketImpact.HIGH, event.impact)
        assertEquals(Instant.parse("2026-08-26T12:30:00Z"), event.scheduledAt)
        assertEquals(setOf(MarketRelevance.GOLD, MarketRelevance.SILVER), event.relevance)
        assertEquals(0, outcome.dropped)
    }

    @Test
    fun `a bare array is an envelope too`() {
        // The likeliest shape for a route written years before this contract existed, and the one
        // a typed body would have failed on while looking exactly like an empty calendar.
        val outcome = read("""[{"event":"Rate decision","date":"2026-08-26T12:30:00Z"}]""")
        assertEquals("Rate decision", outcome.events.single().title)
    }

    @Test
    fun `an epoch is read, in seconds or in milliseconds`() {
        val seconds = read("""[{"title":"A","time":1787000000}]""").events.single()
        val millis = read("""[{"title":"B","time":1787000000000}]""").events.single()
        assertEquals(seconds.scheduledAt, millis.scheduledAt)
    }

    @Test
    fun `a numeric or Persian impact is graded rather than shrugged at`() {
        assertEquals(MarketImpact.HIGH, parseImpact("3"))
        assertEquals(MarketImpact.HIGH, parseImpact("بالا"))
        assertEquals(MarketImpact.MEDIUM, parseImpact("2"))
        assertEquals(MarketImpact.LOW, parseImpact("کم"))
        // And the line the looseness stops at: an unrecognised grade is never guessed, because the
        // chart's high-impact warning is built on this.
        assertEquals(MarketImpact.UNKNOWN, parseImpact("severe"))
    }

    @Test
    fun `a row with no time is dropped, and the drop is counted`() {
        val outcome = read(
            """[{"title":"A","date":"2026-08-26T12:30:00Z"},{"title":"B"},{"date":"2026-08-26T12:30:00Z"}]""",
        )
        assertEquals(listOf("A"), outcome.events.map { it.title })
        assertEquals(3, outcome.received)
        assertEquals(2, outcome.dropped)
        // The keys of the first row, which is the one thing a reader of the export needs to fix a
        // shape this file guessed wrong.
        assertEquals("title,date", outcome.sampleKeys)
    }

    @Test
    fun `an id is derived where the feed carries none, rather than losing the event`() {
        val event = read("""[{"title":"CPI","date":"2026-08-26T12:30:00Z"}]""").events.single()
        assertTrue(event.id.startsWith("CPI@"))
    }

    @Test
    fun `a shape with no array at all is reported, not silently empty`() {
        val outcome = read("""{"ok":true}""")
        assertTrue(outcome.events.isEmpty())
        assertNotNull(outcome.failure)
        assertNull(outcome.sampleKeys)
    }

    @Test
    fun `the body the route actually answers today is an empty publication, not a failure`() {
        // Measured on 2026-08-30, with no credential of any kind:
        //   GET https://coineprofx.com/api/academy/bn/calendar → 200 {"items":[]}
        // Their OpenAPI marks the route public and says it reads Redis key `bn:calendar`, written
        // by a `news-worker`. `academy/bn/news` answers `{"items":[]}` and `academy/bn/ads` answers
        // `{"slots":{}}`, so the whole `bn:*` namespace is unwritten and the worker is not running.
        // The distinction this asserts is the one that matters: `items` is recognised as an
        // envelope, so this is nought rows published — not a body the app could not read.
        val outcome = read("""{"items":[]}""")
        assertTrue(outcome.events.isEmpty())
        assertEquals(0, outcome.received)
        assertNull(outcome.failure)
    }

    @Test
    fun `which route answered is recorded, because there are two of them now`() {
        val outcome = readCalendar(
            JsonParser.parseString("""[{"title":"CPI","date":"2026-08-26T12:30:00Z"}]"""),
            route = "user/economic-calendar",
        )
        assertEquals("user/economic-calendar", outcome.route)
        assertEquals(1, outcome.events.size)
    }

    @Test
    fun `events come back oldest first`() {
        val outcome = read(
            """[{"title":"B","date":"2026-08-27T00:00:00Z"},{"title":"A","date":"2026-08-26T00:00:00Z"}]""",
        )
        assertEquals(listOf("A", "B"), outcome.events.map { it.title })
    }
}
