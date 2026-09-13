package com.coinepro.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Doctrine D7 — **a variable reward on every return** — as the gate the doctrine names: a test in
 * three data states, «nothing new, something new, and offline».
 *
 * The failure this file exists for is the one that makes a card worse than no card: a «since your
 * last visit» that appears every single morning whether or not anything happened. A reader learns
 * that in about four days, and after that the top of Home is a thing they scroll past — which costs
 * more than the card was ever going to earn.
 *
 * So most of what is asserted here is when the card must **not** appear.
 */
class ReturnLoopTest {

    private val hour = 3_600_000L
    private val now = 1_700_000_000_000L

    private fun movers(vararg changes: Pair<String, Double>) = changes.map { SymbolMove(it.first, it.second) }

    // ── nothing new ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a quiet morning shows nothing at all`() {
        assertNull(
            ReturnLoop.sinceLastVisit(
                lastVisitEpochMillis = now - 20 * hour,
                nowEpochMillis = now,
                movers = movers("BTCUSDT" to 0.004, "XAUUSD" to -0.009),
            ),
        )
    }

    @Test
    fun `a move smaller than a candle is not news`() {
        // One per cent is about the width of the bar it happened in. A card announcing it teaches
        // the reader that the card means nothing.
        assertNull(
            ReturnLoop.sinceLastVisit(now - 20 * hour, now, movers("BTCUSDT" to 0.019)),
        )
        assertNotNull(ReturnLoop.sinceLastVisit(now - 20 * hour, now, movers("BTCUSDT" to 0.021)))
    }

    @Test
    fun `a reader who was here an hour ago is not welcomed back`() {
        assertNull(ReturnLoop.sinceLastVisit(now - hour, now, movers("BTCUSDT" to 0.09)))
        assertNull(ReturnLoop.sinceLastVisit(now - 3 * hour, now, movers("BTCUSDT" to 0.09)))
        assertNotNull(ReturnLoop.sinceLastVisit(now - 5 * hour, now, movers("BTCUSDT" to 0.09)))
    }

    @Test
    fun `a first launch has no last visit and says so by saying nothing`() {
        assertNull(ReturnLoop.sinceLastVisit(0L, now, movers("BTCUSDT" to 0.5)))
        assertNull(ReturnLoop.sinceLastVisit(-1L, now, movers("BTCUSDT" to 0.5)))
    }

    @Test
    fun `an empty watchlist with nothing fired is nothing`() {
        assertNull(ReturnLoop.sinceLastVisit(now - 48 * hour, now, emptyList()))
    }

    // ── something new ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the biggest movers come first, up or down`() {
        val since = ReturnLoop.sinceLastVisit(
            now - 20 * hour,
            now,
            movers("A" to 0.03, "B" to -0.11, "C" to 0.07, "D" to -0.04, "E" to 0.001),
        )
        assertEquals(listOf("B", "C", "D"), since?.movers?.map { it.symbol })
        assertEquals(ReturnLoop.MOVERS_SHOWN, since?.movers?.size)
    }

    @Test
    fun `a signal or an alert is news even when nothing moved much`() {
        // The two things that are *about* the reader: a study they follow spoke, or an alert they
        // set themselves went off. Neither needs a big candle to be worth a line.
        assertNotNull(ReturnLoop.sinceLastVisit(now - 20 * hour, now, emptyList(), signals = 2))
        assertNotNull(ReturnLoop.sinceLastVisit(now - 20 * hour, now, emptyList(), alerts = 1))
    }

    @Test
    fun `how long they were away is a number the screen can write a sentence from`() {
        val since = ReturnLoop.sinceLastVisit(now - 50 * hour, now, movers("A" to 0.2))
        assertEquals(50, since?.awayHours)
        assertEquals(2, since?.awayDays)
    }

    @Test
    fun `direction survives`() {
        val since = ReturnLoop.sinceLastVisit(now - 20 * hour, now, movers("A" to -0.08))
        assertEquals(false, since?.movers?.single()?.isUp)
    }

    // ── offline ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `offline is the quiet case, not a broken one`() {
        // Offline means no quotes, so no movers and no counts. The card must be absent rather than
        // showing «0 signals», which reads as a claim about the market rather than about the radio.
        assertNull(
            ReturnLoop.sinceLastVisit(
                lastVisitEpochMillis = now - 72 * hour,
                nowEpochMillis = now,
                movers = emptyList(),
                signals = 0,
                alerts = 0,
            ),
        )
    }

    @Test
    fun `an alert that fired while offline is still news when the app reopens`() {
        // Alerts are decided on the device, so they survive a night with no signal — and they are
        // exactly what a reader coming back wants to know about.
        val since = ReturnLoop.sinceLastVisit(now - 72 * hour, now, emptyList(), alerts = 3)
        assertEquals(3, since?.alerts)
        assertTrue(since?.movers?.isEmpty() == true)
    }

    // ── the challenge ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the same day gives the same challenge, however many times it is asked`() {
        val day = 19_700L
        val once = ReturnLoop.challengeFor(day)
        repeat(5) { assertEquals(once, ReturnLoop.challengeFor(day)) }
    }

    @Test
    fun `consecutive days are different challenges`() {
        // The whole point. A reader who opens the app three mornings running must not be told to do
        // the same thing three times.
        val days = (19_700L..19_711L).map { ReturnLoop.challengeFor(it).id }
        assertEquals("a day repeated inside a fortnight", days.size, days.toSet().size)
    }

    @Test
    fun `the walk visits every challenge before it repeats`() {
        // The stride is coprime with the table, which is the property that makes the cycle the full
        // length rather than a short loop over three of them.
        val ids = (0L until 12L).map { ReturnLoop.challengeFor(it).id }.toSet()
        val all = (0L until 400L).map { ReturnLoop.challengeFor(it).id }.toSet()
        assertEquals(all, ids)
        assertEquals(12, all.size)
    }

    @Test
    fun `a day before the epoch is still a challenge rather than a crash`() {
        // Negative epoch days reach this in one place: a device whose clock is wrong. The modulo
        // has to be the mathematical one, not Kotlin's remainder, or this throws.
        for (day in -400L..0L) {
            assertTrue(ReturnLoop.challengeFor(day).id.isNotEmpty())
        }
    }

    @Test
    fun `every challenge is named in both languages and neither leaks`() {
        for (day in 0L until 12L) {
            val persian = ReturnLoop.challengeFor(day, english = false)
            val english = ReturnLoop.challengeFor(day, english = true)
            assertEquals(persian.id, english.id)
            assertTrue("${persian.id} has no Persian title", persian.title.any { it in '؀'..'ۿ' })
            assertTrue("${english.id}'s English title is Persian", english.title.none { it in '؀'..'ۿ' })
        }
    }

    @Test
    fun `every challenge names somewhere it can be done`() {
        val surfaces = (0L until 12L).map { ReturnLoop.challengeFor(it).surface }.toSet()
        assertTrue("the challenges all point at one screen", surfaces.size >= 4)
    }

    // ── the streak ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a streak survives a day's grace and not two`() {
        // Somebody asleep at midnight in the wrong time zone must not lose a fortnight's work; two
        // days missed and it is genuinely over, which is what keeps the number worth anything.
        assertEquals(6, ReturnLoop.streakAfter(current = 6, lastKeptEpochDay = 19_700, today = 19_700))
        assertEquals(6, ReturnLoop.streakAfter(current = 6, lastKeptEpochDay = 19_700, today = 19_701))
        assertEquals(0, ReturnLoop.streakAfter(current = 6, lastKeptEpochDay = 19_700, today = 19_702))
    }

    @Test
    fun `a streak that never started is zero rather than one`() {
        assertEquals(0, ReturnLoop.streakAfter(current = 3, lastKeptEpochDay = 0, today = 19_700))
    }

    @Test
    fun `a clock that went backwards does not inflate a streak`() {
        // A device whose date was corrected backwards. The streak holds rather than growing.
        assertEquals(0, ReturnLoop.streakAfter(current = 6, lastKeptEpochDay = 19_700, today = 19_690))
    }
}
