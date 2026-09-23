package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The duel.
 *
 * The two assertions this file exists for: that a market which barely moved makes the reader
 * **neither** right nor wrong, and that the round the caller is handed never reaches past the bar
 * the reader is allowed to see.
 */
class DuelTest {

    private val symbols = listOf("BTCUSDT", "ETHUSDT", "XAUUSD")

    /** A flat series with the close at [resolveBar] moved [movePercent] from the close at [atBar]. */
    private fun series(size: Int, atBar: Int, resolveBar: Int, movePercent: Double): CandleSeries =
        CandleSeries(
            (0 until size).map { index ->
                val close = if (index >= resolveBar) 100.0 * (1 + movePercent / 100.0) else 100.0
                Candle(
                    t = 1_700_000_000L + index * 3_600L,
                    o = close,
                    h = close + 1.0,
                    l = close - 1.0,
                    c = close,
                    v = 1.0,
                )
            },
        ).also { require(atBar < resolveBar) }

    @Test
    fun `no symbols is no round`() {
        assertNull(Duel.roundFor(20_000L, emptyList(), historyBars = 1_000))
    }

    @Test
    fun `a series too short to hold both the past and the future is no round`() {
        assertNull(Duel.roundFor(20_000L, symbols, historyBars = Duel.CONTEXT_BARS))
        assertNotNull(
            Duel.roundFor(20_000L, symbols, historyBars = Duel.CONTEXT_BARS + Duel.FORWARD_BARS + 1),
        )
    }

    @Test
    fun `the same day gives the same round`() {
        // Otherwise closing the app rerolls the question, and a reader who did not like the look of
        // one chart gets another — which is not a duel, it is a slot machine.
        assertEquals(
            Duel.roundFor(20_000L, symbols, 1_000),
            Duel.roundFor(20_000L, symbols, 1_000),
        )
    }

    @Test
    fun `a different day gives a different round`() {
        val days = (20_000L until 20_040L).mapNotNull { Duel.roundFor(it, symbols, 1_000) }
        assertTrue(days.map { it.symbol to it.atBar }.toSet().size > 1)
    }

    @Test
    fun `the round leaves room in front of it and history behind it`() {
        val round = Duel.roundFor(20_000L, symbols, historyBars = 1_000)!!
        assertTrue(round.atBar >= Duel.CONTEXT_BARS)
        assertEquals(round.atBar + Duel.FORWARD_BARS, round.resolveBar)
        assertTrue(round.resolveBar < 1_000)
    }

    @Test
    fun `the resolving bar is always inside the series the caller loaded`() {
        // The bug this guards is off-by-one at the tight end, where the window only just fits.
        for (bars in (Duel.CONTEXT_BARS + Duel.FORWARD_BARS + 1)..(Duel.CONTEXT_BARS + Duel.FORWARD_BARS + 40)) {
            for (day in 20_000L until 20_020L) {
                val round = Duel.roundFor(day, symbols, bars) ?: continue
                assertTrue("bars=$bars day=$day", round.resolveBar < bars)
            }
        }
    }

    @Test
    fun `a rise called up is right`() {
        val round = DuelRound("BTCUSDT", atBar = 100, resolveBar = 120, epochDay = 1)
        val outcome = Duel.judge(series(200, 100, 120, 4.0), round, DuelCall.UP)!!
        assertEquals(DuelVerdict.RIGHT, outcome.verdict)
        assertEquals(4.0, outcome.movePercent, 0.001)
    }

    @Test
    fun `a rise called down is wrong`() {
        val round = DuelRound("BTCUSDT", atBar = 100, resolveBar = 120, epochDay = 1)
        assertEquals(
            DuelVerdict.WRONG,
            Duel.judge(series(200, 100, 120, 4.0), round, DuelCall.DOWN)!!.verdict,
        )
    }

    @Test
    fun `a fall called down is right`() {
        val round = DuelRound("BTCUSDT", atBar = 100, resolveBar = 120, epochDay = 1)
        assertEquals(
            DuelVerdict.RIGHT,
            Duel.judge(series(200, 100, 120, -3.0), round, DuelCall.DOWN)!!.verdict,
        )
    }

    @Test
    fun `a market that barely moved makes the reader neither right nor wrong`() {
        // The rule the whole feature rests on. Scored as a win, this teaches a reader that noise is
        // a read — which is the one habit this product exists to argue against.
        val round = DuelRound("BTCUSDT", atBar = 100, resolveBar = 120, epochDay = 1)
        val barely = series(200, 100, 120, Duel.FLAT_PERCENT - 0.01)
        assertEquals(DuelVerdict.TOO_CLOSE, Duel.judge(barely, round, DuelCall.UP)!!.verdict)
        assertEquals(DuelVerdict.TOO_CLOSE, Duel.judge(barely, round, DuelCall.DOWN)!!.verdict)
    }

    @Test
    fun `either side of the floor, and the floor itself is not a case a market can reach`() {
        // The rule is `< FLAT_PERCENT`, so a move *at* the floor resolves. That boundary cannot be
        // asserted from prices: a close of 100.5 against 100.0 comes back as 0.4999999999999858 in
        // binary floating point, and a test that claimed to hit the floor exactly would be
        // asserting about the arithmetic rather than about the rule. So both sides are asserted and
        // the unreachable middle is written down instead.
        val round = DuelRound("BTCUSDT", atBar = 100, resolveBar = 120, epochDay = 1)
        assertEquals(
            DuelVerdict.RIGHT,
            Duel.judge(series(200, 100, 120, Duel.FLAT_PERCENT + 0.01), round, DuelCall.UP)!!.verdict,
        )
        assertEquals(
            DuelVerdict.TOO_CLOSE,
            Duel.judge(series(200, 100, 120, Duel.FLAT_PERCENT - 0.01), round, DuelCall.UP)!!.verdict,
        )
    }

    @Test
    fun `a round the series cannot answer is not judged at all`() {
        // Not a guess and not a loss: a record that counted this would be a record of something
        // that did not happen.
        val round = DuelRound("BTCUSDT", atBar = 100, resolveBar = 220, epochDay = 1)
        assertNull(Duel.judge(series(200, 100, 120, 4.0), round, DuelCall.UP))
    }

    @Test
    fun `a zero close has no percentage and so no verdict`() {
        val flat = CandleSeries(
            (0 until 130).map { index ->
                Candle(t = 1_700_000_000L + index * 3_600L, o = 0.0, h = 0.0, l = 0.0, c = 0.0, v = 1.0)
            },
        )
        assertNull(Duel.judge(flat, DuelRound("X", 100, 120, 1), DuelCall.UP))
    }

    @Test
    fun `a set-aside round counts as played and as neither`() {
        val record = Duel.record(
            DuelRecord(),
            DuelOutcome(DuelCall.UP, DuelVerdict.TOO_CLOSE, movePercent = 0.1),
        )
        assertEquals(1, record.played)
        assertEquals(0, record.right)
        assertEquals(1, record.tooClose)
        assertEquals(0, record.judged)
    }

    @Test
    fun `the hit rate is over judged rounds, not over played ones`() {
        var record = DuelRecord()
        repeat(3) {
            record = Duel.record(record, DuelOutcome(DuelCall.UP, DuelVerdict.RIGHT, 5.0))
        }
        repeat(2) {
            record = Duel.record(record, DuelOutcome(DuelCall.UP, DuelVerdict.WRONG, -5.0))
        }
        repeat(4) {
            record = Duel.record(record, DuelOutcome(DuelCall.UP, DuelVerdict.TOO_CLOSE, 0.1))
        }
        assertEquals(9, record.played)
        assertEquals(5, record.judged)
        // Three of five, not three of nine: the four the market did not answer are not losses.
        assertEquals(60.0, record.rightPercent!!, 0.001)
    }

    @Test
    fun `a rate under the floor is withheld`() {
        var record = DuelRecord()
        repeat(4) {
            record = Duel.record(record, DuelOutcome(DuelCall.UP, DuelVerdict.RIGHT, 5.0))
        }
        assertEquals(4, record.judged)
        assertNull(record.rightPercent)
    }

    @Test
    fun `a record of nothing but set-aside rounds has no rate`() {
        var record = DuelRecord()
        repeat(8) {
            record = Duel.record(record, DuelOutcome(DuelCall.UP, DuelVerdict.TOO_CLOSE, 0.0))
        }
        assertNull(record.rightPercent)
    }

    @Test
    fun `the duel and the Arena do not pick the same instrument every day`() {
        // Two features asking about the same chart on the same day reads as the app having one idea
        // rather than two. Different constants, so the sequences diverge.
        val same = (20_000L until 20_060L).count { day ->
            val duel = Duel.roundFor(day, symbols, 1_000)?.symbol
            val arena = Arena.challengeFor(day, symbols, 1_000)?.symbol
            duel != null && duel == arena
        }
        assertTrue("collided on $same of 60 days", same < 40)
    }
}
