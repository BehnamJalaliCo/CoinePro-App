package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bar replay and the trade arithmetic.
 *
 * Two ports in one file because they are two halves of the same feature: replay is how somebody
 * practises a setup, and the trade maths is what the setup is worth. The whole point of replay is
 * that the reader cannot see what happened next, so the assertion that matters most here is the
 * dullest-looking one — that the visible slice never reaches past the cursor.
 */
class ReplayAndTradeTest {

    private fun bars(count: Int): List<Candle> = (0 until count).map { index ->
        val base = 100.0 + index * 0.5
        Candle(1_700_000_000L + index * 3_600, base, base + 1, base - 1, base + 0.4)
    }

    // ── replay ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the visible slice never reaches past the cursor`() {
        // The entire feature. If this ever fails, replay is showing the reader the future and the
        // practice it exists for is worthless.
        var state = Replay.enter(bars(100))!!
        repeat(20) {
            assertEquals(state.cursor + 1, state.visible.size)
            assertEquals(state.bars[state.cursor].t, state.visible.time.last())
            state = Replay.step(state)
        }
    }

    @Test
    fun `too few bars cannot enter replay`() {
        // Below the minimum the chart starts at its own right edge: the exercise is pointless
        // rather than merely short.
        assertNull(Replay.enter(bars(Replay.MINIMUM_BARS - 1)))
        assertNotNull(Replay.enter(bars(Replay.MINIMUM_BARS)))
    }

    @Test
    fun `entering leaves more history behind the cursor than future ahead of it`() {
        val state = Replay.enter(bars(100))!!
        assertTrue("the reader needs enough past to form a view", state.cursor > 100 - state.cursor)
    }

    @Test
    fun `stepping at the end pauses instead of wrapping or stalling`() {
        var state = Replay.play(Replay.enter(bars(40), startIndex = 38)!!)
        assertTrue(state.playing)
        state = Replay.step(state)
        assertTrue(state.atEnd)
        state = Replay.step(state)
        assertTrue("it must stop rather than sit playing at the end", !state.playing)
        assertEquals(39, state.cursor)
    }

    @Test
    fun `stepping back at the start does nothing`() {
        val state = Replay.enter(bars(40), startIndex = 0)!!
        assertEquals(0, Replay.stepBack(state).cursor)
    }

    @Test
    fun `play is refused at the end, where there is nothing left to reveal`() {
        val state = Replay.enter(bars(40), startIndex = 39)!!
        assertFalse(Replay.play(state).playing)
    }

    @Test
    fun `seek finds the nearest bar to a moment, not the next one`() {
        // A reader picking a date off a calendar means "around here". Landing a bar early is as
        // good an answer as landing one late, and always rounding up drifts.
        val all = bars(100)
        val justAfter = all[50].t + 100
        assertEquals(50, Replay.indexOfTime(all, justAfter))
        val justBefore = all[50].t - 100
        assertEquals(50, Replay.indexOfTime(all, justBefore))
    }

    @Test
    fun `seek clamps rather than throwing at either end`() {
        val all = bars(40)
        assertEquals(0, Replay.indexOfTime(all, 0))
        assertEquals(39, Replay.indexOfTime(all, Long.MAX_VALUE))
        val state = Replay.enter(all)!!
        assertEquals(39, Replay.seek(state, index = 9_999).cursor)
        assertEquals(0, Replay.seek(state, index = -5).cursor)
    }

    @Test
    fun `an unknown speed is ignored rather than accepted`() {
        // A speed off the ladder would produce a delay nothing else in the app expects, and the
        // failure would look like the replay being erratic rather than misconfigured.
        val state = Replay.enter(bars(40))!!
        assertEquals(1.0, Replay.setSpeed(state, 7.5).speed, 1e-9)
        assertEquals(5.0, Replay.setSpeed(state, 5.0).speed, 1e-9)
    }

    @Test
    fun `a faster speed is a shorter step`() {
        assertTrue(Replay.delayMillis(10.0) < Replay.delayMillis(1.0))
        assertTrue(Replay.delayMillis(1.0) < Replay.delayMillis(0.1))
        // Clamped at both ends: a 30× step must still be long enough to see.
        assertTrue(Replay.delayMillis(1_000.0) >= 16)
        assertTrue(Replay.delayMillis(0.001) <= 4_000)
    }

    @Test
    fun `exiting forgets the snapshot`() {
        val state = Replay.enter(bars(40))!!
        assertTrue(state.isOn)
        assertFalse(Replay.exit().isOn)
    }

    // ── the trade arithmetic ──────────────────────────────────────────────────────────

    private val long = ChartOrder(TradeSide.BUY, entry = 100.0, stopLoss = 98.0, takeProfit = 106.0)

    @Test
    fun `risk reward is reward over risk`() {
        assertEquals(3.0, TradeFromChart.riskReward(long)!!, 1e-9)
    }

    @Test
    fun `a stop on the entry has no ratio rather than an infinite one`() {
        // Any number printed for it would be read as a real ratio. There isn't one.
        assertNull(TradeFromChart.riskReward(long.copy(stopLoss = 100.0)))
    }

    @Test
    fun `a setup with its lines crossed is invalid`() {
        // Usually a stop and a target dragged past each other. It is a mistake, not a pessimistic
        // trade, and it must not reach an order ticket.
        assertTrue(TradeFromChart.isValid(long))
        assertFalse(TradeFromChart.isValid(long.copy(takeProfit = 95.0)))
        assertFalse(TradeFromChart.isValid(long.copy(stopLoss = 105.0)))

        val short = ChartOrder(TradeSide.SELL, entry = 100.0, stopLoss = 102.0, takeProfit = 94.0)
        assertTrue(TradeFromChart.isValid(short))
        assertFalse(TradeFromChart.isValid(short.copy(stopLoss = 98.0)))
    }

    @Test
    fun `pip size follows the instrument, not the number`() {
        assertEquals(0.01, TradeFromChart.pipSize("USDJPY"), 1e-12)
        assertEquals(0.1, TradeFromChart.pipSize("XAUUSD"), 1e-12)
        assertEquals(0.01, TradeFromChart.pipSize("XAGUSD"), 1e-12)
        assertEquals(0.0001, TradeFromChart.pipSize("EURUSD"), 1e-12)
        // A large price with no recognised ticker falls back to whole units — a pip of 0.0001 on
        // an index would be a stop distance of forty million pips, which nobody can read.
        assertEquals(1.0, TradeFromChart.pipSize("US500", price = 5_400.0), 1e-12)
    }

    @Test
    fun `stop distance in pips uses the instrument's pip`() {
        val gold = ChartOrder(TradeSide.BUY, entry = 2_600.0, stopLoss = 2_598.0, takeProfit = 2_610.0)
        assertEquals(20.0, TradeFromChart.stopPips(gold, "XAUUSD"), 1e-9)
    }

    @Test
    fun `size comes from risk, and doubling the stop halves the size`() {
        // Risk decides size; size does not decide risk. That is the habit this direction enforces.
        val near = TradeFromChart.positionSize(long, riskAmount = 100.0, contractSize = 1.0)
        val far = TradeFromChart.positionSize(
            long.copy(stopLoss = 96.0),
            riskAmount = 100.0,
            contractSize = 1.0,
        )
        assertEquals(50.0, near.units, 1e-9)
        assertEquals(25.0, far.units, 1e-9)
    }

    @Test
    fun `no risk means no position rather than an infinite one`() {
        assertEquals(0.0, TradeFromChart.positionSize(long, riskAmount = 0.0).units, 1e-9)
        val flat = long.copy(stopLoss = 100.0)
        assertEquals(0.0, TradeFromChart.positionSize(flat, riskAmount = 100.0).units, 1e-9)
    }

    @Test
    fun `unrealised profit reverses with the side`() {
        val up = TradeFromChart.unrealised(long, livePrice = 102.0, symbol = "EURUSD", units = 10.0)
        assertTrue(up.amount > 0)
        val short = long.copy(side = TradeSide.SELL, stopLoss = 102.0, takeProfit = 94.0)
        val down = TradeFromChart.unrealised(short, livePrice = 102.0, symbol = "EURUSD", units = 10.0)
        assertTrue(down.amount < 0)
    }

    @Test
    fun `a default setup is valid and sits at the stated ratio`() {
        for (side in TradeSide.entries) {
            val order = TradeFromChart.defaultOrder(side, 2_600.0)!!
            assertTrue("$side default is not a valid setup", TradeFromChart.isValid(order))
            assertEquals(2.0, TradeFromChart.riskReward(order)!!, 1e-9)
        }
    }

    @Test
    fun `breakeven moves the stop and nothing else`() {
        val moved = TradeFromChart.moveToBreakeven(long)
        assertEquals(long.entry, moved.stopLoss, 1e-9)
        assertEquals(long.entry, moved.entry, 1e-9)
        assertEquals(long.takeProfit, moved.takeProfit, 1e-9)
    }

    @Test
    fun `an entry on the live price is a market order`() {
        // Without the tolerance, dragging the entry onto the current price makes a limit order that
        // fills only if price comes back — which is not what that gesture means.
        assertEquals(
            TradeFromChart.OrderType.MARKET,
            TradeFromChart.classify(long, livePrice = 100.0),
        )
        assertEquals(
            TradeFromChart.OrderType.MARKET,
            TradeFromChart.classify(long, livePrice = 100.01),
        )
    }

    @Test
    fun `entry above or below the market decides limit against stop`() {
        assertEquals(
            TradeFromChart.OrderType.BUY_LIMIT,
            TradeFromChart.classify(long, livePrice = 110.0),
        )
        assertEquals(
            TradeFromChart.OrderType.BUY_STOP,
            TradeFromChart.classify(long, livePrice = 90.0),
        )
        val short = long.copy(side = TradeSide.SELL, stopLoss = 102.0, takeProfit = 94.0)
        assertEquals(
            TradeFromChart.OrderType.SELL_LIMIT,
            TradeFromChart.classify(short, livePrice = 90.0),
        )
        assertEquals(
            TradeFromChart.OrderType.SELL_STOP,
            TradeFromChart.classify(short, livePrice = 110.0),
        )
    }
}
