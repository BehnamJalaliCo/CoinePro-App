package com.coinepro.feature.chart

import com.coinepro.core.chart.DuelCall
import com.coinepro.core.chart.DuelOutcome
import com.coinepro.core.chart.DuelRound
import com.coinepro.core.chart.DuelVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The duel's session.
 *
 * Two assertions this file exists for, and they are the two ways the surface could lie: that a
 * second call is ignored once the answer is on screen, and that a round the record **refused** says
 * so rather than looking like a round that counted.
 */
class DuelSessionTest {

    private val round = DuelRound(symbol = "BTCUSDT", atBar = 300, resolveBar = 320, epochDay = 20_000L)

    private fun session() = DuelSession(round = round, localDay = 20_000L)

    private fun outcome(verdict: DuelVerdict, move: Double = 4.0) =
        DuelOutcome(call = DuelCall.UP, verdict = verdict, movePercent = move)

    @Test
    fun `a fresh session has no outcome and is not answered`() {
        val session = session()
        assertNull(session.outcome)
        assertFalse(session.answered)
        assertFalse(session.alreadyAnswered)
    }

    @Test
    fun `answering sets the outcome`() {
        val session = session()
        session.answer(outcome(DuelVerdict.RIGHT), recorded = true)
        assertTrue(session.answered)
        assertEquals(DuelVerdict.RIGHT, session.outcome?.verdict)
        assertFalse(session.alreadyAnswered)
    }

    @Test
    fun `a second call is ignored`() {
        // The rule the mode rests on. Once the call is made the next twenty bars are on screen, and
        // a reader who could answer again would be recording a prediction they did not make — so
        // this is refused in the session rather than only hidden by the band drawing no buttons.
        val session = session()
        session.answer(outcome(DuelVerdict.WRONG), recorded = true)
        session.answer(outcome(DuelVerdict.RIGHT), recorded = true)
        assertEquals(DuelVerdict.WRONG, session.outcome?.verdict)
    }

    @Test
    fun `a refused round says it was refused`() {
        // `DuelStore.answer` returns false when the day was already answered. Swallowing that would
        // make a refusal indistinguishable from a counter that stopped working.
        val session = session()
        session.answer(outcome(DuelVerdict.RIGHT), recorded = false)
        assertTrue(session.answered)
        assertTrue(session.alreadyAnswered)
    }

    @Test
    fun `a refused round still shows the verdict`() {
        // The record refused it; the market did not. The reader asked which way it went and the
        // answer is the same answer whether or not it counted towards a rate.
        val session = session()
        session.answer(outcome(DuelVerdict.TOO_CLOSE, move = 0.1), recorded = false)
        assertEquals(DuelVerdict.TOO_CLOSE, session.outcome?.verdict)
        assertEquals(0.1, session.outcome!!.movePercent, 0.0001)
    }

    @Test
    fun `the session carries the round it was built for`() {
        val session = session()
        assertEquals(round, session.round)
        assertEquals(20_000L, session.localDay)
    }
}
