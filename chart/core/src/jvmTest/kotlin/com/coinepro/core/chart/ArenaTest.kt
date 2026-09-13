package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two properties the Arena rests on.
 *
 * **Everybody gets the same challenge**, or a shared score compares two different questions — and
 * with no endpoint to serve one, the only thing that makes that true is arithmetic written out in
 * one file and tested here.
 *
 * **Discipline outweighs profit**, or the Arena teaches the thing this product argues against: that
 * a good trade is a profitable one. The scoring tests below are all versions of that one claim.
 */
class ArenaTest {

    private val universe = listOf("BTCUSDT", "ETHUSDT", "XAUUSD", "EURUSD", "SOLUSDT")

    @Test
    fun `the same day gives the same challenge, every time`() {
        val first = Arena.challengeFor(20_000L, universe, historyBars = 500)
        val second = Arena.challengeFor(20_000L, universe, historyBars = 500)
        assertEquals(first, second)
    }

    @Test
    fun `different days give different challenges`() {
        // Not a guarantee for any *pair* — five symbols means collisions — but over a fortnight the
        // challenge must actually move, or «today's» is a word for «the same one again».
        val fortnight = (20_000L until 20_014L).mapNotNull { Arena.challengeFor(it, universe, 500) }
        assertTrue("the challenge never changed", fortnight.map { it.symbol }.toSet().size > 1)
        assertTrue("the window never moved", fortnight.map { it.startBar }.toSet().size > 1)
    }

    @Test
    fun `the window always has a past and a future`() {
        for (day in 20_000L until 20_120L) {
            val challenge = Arena.challengeFor(day, universe, historyBars = 400) ?: continue
            assertTrue(
                "day $day starts at ${challenge.startBar}, with no chart behind it",
                challenge.startBar >= Arena.MINIMUM_CONTEXT_BARS,
            )
            assertTrue(
                "day $day leaves ${400 - challenge.startBar} bars to play",
                400 - challenge.startBar >= Arena.MINIMUM_FORWARD_BARS,
            )
        }
    }

    @Test
    fun `too little history is no challenge rather than a bad one`() {
        assertNull(Arena.challengeFor(20_000L, universe, historyBars = 100))
        assertNull(Arena.challengeFor(20_000L, emptyList(), historyBars = 500))
        // Exactly enough is enough, and is the one window there is.
        val tight = Arena.challengeFor(20_000L, universe, Arena.MINIMUM_CONTEXT_BARS + Arena.MINIMUM_FORWARD_BARS)
        assertNotNull(tight)
        assertEquals(Arena.MINIMUM_CONTEXT_BARS, tight!!.startBar)
    }

    @Test
    fun `a session with no trades is unplayed rather than zero`() {
        val score = Arena.score(emptyList())
        assertEquals(0, score.total)
        assertTrue("an empty session must not read as a played one", !score.played)
    }

    @Test
    fun `a disciplined loss beats an undisciplined win`() {
        // The whole argument of the Arena, as one assertion. Two trades, each taken once: one with a
        // stop that lost a unit of risk, one with no stop that made three. If the second scores
        // higher, the Arena is teaching people to trade without stops.
        val careful = Arena.score(listOf(ArenaTrade(hadStop = true, rMultiple = -1.0)))
        val lucky = Arena.score(listOf(ArenaTrade(hadStop = false, rMultiple = 3.0)))
        assertTrue(
            "an undisciplined winner out-scored a disciplined loser: ${lucky.total} vs ${careful.total}",
            careful.total >= lucky.total,
        )
    }

    @Test
    fun `every trade with a stop is full marks for discipline`() {
        val score = Arena.score(
            listOf(
                ArenaTrade(hadStop = true, rMultiple = 0.5),
                ArenaTrade(hadStop = true, rMultiple = -1.0),
            ),
        )
        assertEquals(Arena.DISCIPLINE_POINTS, score.discipline)
        assertEquals(2, score.withStop)
    }

    @Test
    fun `a revenge trade costs the same whether it is one in three or one in ten`() {
        val ofThree = Arena.score(
            List(2) { ArenaTrade(hadStop = true, rMultiple = 1.0) } +
                ArenaTrade(hadStop = true, rMultiple = -1.0, revenge = true),
        )
        val ofTen = Arena.score(
            List(9) { ArenaTrade(hadStop = true, rMultiple = 1.0) } +
                ArenaTrade(hadStop = true, rMultiple = -1.0, revenge = true),
        )
        assertEquals(
            "the penalty is about a thing that happened, not a rate",
            Arena.DISCIPLINE_POINTS - Arena.REVENGE_PENALTY,
            ofThree.discipline,
        )
        assertEquals(ofThree.discipline, ofTen.discipline)
    }

    @Test
    fun `the profit half is capped so a lucky session cannot buy the score`() {
        val good = Arena.score(listOf(ArenaTrade(hadStop = true, rMultiple = Arena.TARGET_R)))
        val enormous = Arena.score(listOf(ArenaTrade(hadStop = true, rMultiple = 40.0)))
        assertEquals(Arena.PROFIT_POINTS, good.profit)
        assertEquals("forty is forty, however big the move was", good.profit, enormous.profit)
        assertEquals(100, enormous.total)
    }

    @Test
    fun `a losing session never scores below zero on discipline`() {
        val score = Arena.score(
            List(4) { ArenaTrade(hadStop = false, rMultiple = -1.0, revenge = true) },
        )
        assertEquals(0, score.discipline)
        assertEquals(0, score.profit)
    }

    @Test
    fun `a trade opened moments after a loss is marked, and one opened later is not`() {
        val trades = List(3) { ArenaTrade(hadStop = true) }
        val marked = markRevengeTrades(
            trades = trades,
            opened = listOf(0L, 120L, 600L),
            closed = listOf(100L, 300L, 900L),
            // The first lost; the second opened twenty seconds later; the third five minutes after
            // the second, which also lost.
            results = listOf(-1.0, -1.0, 1.0),
        )
        assertTrue("the trade twenty seconds after a loss was not marked", marked[1].revenge)
        assertTrue("the trade five minutes later was marked", !marked[2].revenge)
        assertTrue("the first trade cannot be revenge for anything", !marked[0].revenge)
    }

    @Test
    fun `a mismatched record is left alone rather than half marked`() {
        val trades = List(3) { ArenaTrade(hadStop = true) }
        assertEquals(trades, markRevengeTrades(trades, listOf(0L), listOf(1L), listOf(1.0)))
    }
}
