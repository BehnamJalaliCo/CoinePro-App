package com.coinepro.core.common

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reader's own week.
 *
 * Two of these assertions are the reason the file exists: that the week opens on Saturday for a
 * reader in Tehran, and that a win rate over four trades is **not printed**. Both are the kind of
 * thing that looks right in a screenshot and is wrong about somebody's money.
 */
class MyWeekTest {

    private val tehran = ZoneId.of("Asia/Tehran")
    private val london = ZoneId.of("Europe/London")

    /** Wednesday 2026-09-23, mid-morning in Tehran. */
    private val now = Instant.parse("2026-09-23T06:00:00Z").toEpochMilli()

    private fun dayOf(millis: Long, zone: ZoneId): DayOfWeek =
        Instant.ofEpochMilli(millis).atZone(zone).dayOfWeek

    @Test
    fun `an Iranian week opens on Saturday`() {
        assertEquals(DayOfWeek.SATURDAY, dayOf(MyWeek.weekStart(now, tehran), tehran))
    }

    @Test
    fun `and an ISO week opens on Monday`() {
        assertEquals(DayOfWeek.MONDAY, dayOf(MyWeek.weekStart(now, london), london))
    }

    @Test
    fun `the week opens at midnight in the reader's own zone`() {
        val start = Instant.ofEpochMilli(MyWeek.weekStart(now, tehran)).atZone(tehran)
        assertEquals(0, start.hour)
        assertEquals(0, start.minute)
    }

    @Test
    fun `a day that is itself the start is not pushed back a week`() {
        // Saturday morning. `previousOrSame`, not `previous`: a reader opening this on the first
        // morning of their week should see that week and not the one before it.
        val saturday = Instant.parse("2026-09-26T06:00:00Z").toEpochMilli()
        val start = MyWeek.weekStart(saturday, tehran)
        assertEquals(DayOfWeek.SATURDAY, dayOf(start, tehran))
        assertTrue(start <= saturday)
        assertTrue(saturday - start < 24L * 60 * 60 * 1000)
    }

    @Test
    fun `a win rate under the floor is withheld and the count is kept`() {
        // Two of four is fifty percent and means nothing. The screen has «۲ از ۴» to show.
        val week = MyWeek.of(now, tehran, trades = trades(won = 2, lost = 2))
        assertEquals(4, week.trades)
        assertEquals(2, week.won)
        assertNull(week.winPercent)
    }

    @Test
    fun `at the floor the figure is printed plainly`() {
        val week = MyWeek.of(now, tehran, trades = trades(won = 3, lost = 2))
        assertEquals(5, week.trades)
        assertEquals(60.0, week.winPercent!!, 0.0001)
    }

    @Test
    fun `only the trades inside the week are counted`() {
        val from = MyWeek.weekStart(now, tehran)
        val week = MyWeek.of(
            now,
            tehran,
            trades = listOf(
                WeekTrade(from - 1, won = true),
                WeekTrade(from, won = true),
                WeekTrade(from + 1_000, won = false),
                WeekTrade(from + 7L * 24 * 60 * 60 * 1000, won = true),
            ),
        )
        // Half-open at both ends: the instant the week opens is inside it, the instant the next
        // week opens is not.
        assertEquals(2, week.trades)
        assertEquals(1, week.won)
    }

    @Test
    fun `alerts armed is a state and is taken as it stands`() {
        val from = MyWeek.weekStart(now, tehran)
        val week = MyWeek.of(
            now,
            tehran,
            alertFiredAt = listOf(from - 5, from + 5, from + 6),
            alertsArmed = 9,
        )
        assertEquals(2, week.alertsFired)
        assertEquals(9, week.alertsArmed)
    }

    @Test
    fun `practice sessions are counted the same way`() {
        val from = MyWeek.weekStart(now, tehran)
        val week = MyWeek.of(now, tehran, practiceFinishedAt = listOf(from - 1, from + 1))
        assertEquals(1, week.practiceSessions)
    }

    @Test
    fun `the mover is the largest move either way, above the floor`() {
        val week = MyWeek.of(
            now,
            tehran,
            marketChanges = listOf("BTCUSDT" to 2.0, "XAUUSD" to -6.0, "ETHUSDT" to 3.0),
        )
        assertEquals("XAUUSD", week.moverSymbol)
        assertEquals(-6.0, week.moverPercent!!, 0.0001)
    }

    @Test
    fun `a quiet week has no mover rather than a manufactured one`() {
        val week = MyWeek.of(now, tehran, marketChanges = listOf("BTCUSDT" to 0.04))
        assertNull(week.moverSymbol)
    }

    @Test
    fun `a market nobody could measure is not the mover`() {
        val week = MyWeek.of(now, tehran, marketChanges = listOf("BTCUSDT" to null, "XAUUSD" to 1.0))
        assertEquals("XAUUSD", week.moverSymbol)
    }

    @Test
    fun `an untouched week says so rather than showing four zeros`() {
        assertTrue(MyWeek.of(now, tehran).empty)
        assertFalse(MyWeek.of(now, tehran, practiceFinishedAt = listOf(now)).empty)
        assertFalse(MyWeek.of(now, tehran, alertFiredAt = listOf(now)).empty)
        assertFalse(MyWeek.of(now, tehran, marketChanges = listOf("BTCUSDT" to 5.0)).empty)
    }

    @Test
    fun `a week with armed alerts and nothing else is still an untouched week`() {
        // Armed alerts are a standing state, not something the reader did this week. Counting them
        // as activity would make every week look busy for somebody who set five alerts in March.
        assertTrue(MyWeek.of(now, tehran, alertsArmed = 12).empty)
    }

    private fun trades(won: Int, lost: Int): List<WeekTrade> {
        val from = MyWeek.weekStart(now, tehran)
        return (0 until won).map { WeekTrade(from + it, won = true) } +
            (0 until lost).map { WeekTrade(from + 100 + it, won = false) }
    }
}
