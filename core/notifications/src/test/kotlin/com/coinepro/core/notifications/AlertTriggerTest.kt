package com.coinepro.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where each trigger's answer changes, which is the only part of a trigger anybody argues about.
 *
 * Almost every one of these tests is about a boundary — the price exactly on the level, the sample
 * exactly on the edge of the channel, the series exactly one bar too short. Those are the cases a
 * reader notices, because they are the cases where the app and the chart in front of them can be
 * seen to disagree.
 */
class AlertTriggerTest {

    /* ------------------------------------------------------------------ PriceOp */

    @Test
    fun `greater than and less than exclude the level itself`() {
        assertFalse(PriceOp.GREATER_THAN.matches(previous = null, current = 100.0, level = 100.0))
        assertTrue(PriceOp.GREATER_THAN.matches(previous = null, current = 100.01, level = 100.0))

        assertFalse(PriceOp.LESS_THAN.matches(previous = null, current = 100.0, level = 100.0))
        assertTrue(PriceOp.LESS_THAN.matches(previous = null, current = 99.99, level = 100.0))
    }

    @Test
    fun `crossing up includes landing exactly on the level`() {
        assertTrue(PriceOp.CROSSING_UP.matches(previous = 99.0, current = 100.0, level = 100.0))
        assertTrue(PriceOp.CROSSING_UP.matches(previous = 99.0, current = 101.0, level = 100.0))
        assertFalse(PriceOp.CROSSING_UP.matches(previous = 100.0, current = 101.0, level = 100.0))
        assertFalse(PriceOp.CROSSING_UP.matches(previous = 101.0, current = 102.0, level = 100.0))
    }

    @Test
    fun `crossing down includes landing exactly on the level`() {
        assertTrue(PriceOp.CROSSING_DOWN.matches(previous = 101.0, current = 100.0, level = 100.0))
        assertTrue(PriceOp.CROSSING_DOWN.matches(previous = 101.0, current = 99.0, level = 100.0))
        assertFalse(PriceOp.CROSSING_DOWN.matches(previous = 100.0, current = 99.0, level = 100.0))
        assertFalse(PriceOp.CROSSING_DOWN.matches(previous = 99.0, current = 98.0, level = 100.0))
    }

    @Test
    fun `a plain crossing answers for either direction`() {
        assertTrue(PriceOp.CROSSING.matches(previous = 99.0, current = 101.0, level = 100.0))
        assertTrue(PriceOp.CROSSING.matches(previous = 101.0, current = 99.0, level = 100.0))
        assertFalse(PriceOp.CROSSING.matches(previous = 101.0, current = 102.0, level = 100.0))
    }

    /**
     * A crossing with nothing to have crossed from does not fire.
     *
     * This is the first evaluation after a restart, and it is the difference between an app that is
     * quiet on launch and one that sends every crossing alert the reader owns at once.
     */
    @Test
    fun `a crossing with no previous sample never fires`() {
        assertFalse(PriceOp.CROSSING.matches(previous = null, current = 101.0, level = 100.0))
        assertFalse(PriceOp.CROSSING_UP.matches(previous = null, current = 101.0, level = 100.0))
        assertFalse(PriceOp.CROSSING_DOWN.matches(previous = null, current = 99.0, level = 100.0))
        assertFalse(PriceOp.GREATER_THAN.needsPrevious)
        assertTrue(PriceOp.CROSSING.needsPrevious)
    }

    @Test
    fun `a price trigger asks its own operator`() {
        val trigger = AlertTrigger.Price(PriceOp.CROSSING_UP, 65_000.0)
        assertTrue(trigger.evaluate(previous = 64_999.0, current = 65_000.0, series = null))
        assertFalse(trigger.evaluate(previous = 65_000.0, current = 65_100.0, series = null))
    }

    /* ----------------------------------------------------------------- ChannelOp */

    private val channel = AlertTrigger.Channel(ChannelOp.INSIDE, low = 100.0, high = 110.0)

    @Test
    fun `both bounds of a channel are inside it`() {
        assertTrue(channel.evaluate(previous = null, current = 100.0, series = null))
        assertTrue(channel.evaluate(previous = null, current = 110.0, series = null))
        assertTrue(channel.evaluate(previous = null, current = 105.0, series = null))
        assertFalse(channel.evaluate(previous = null, current = 99.99, series = null))
        assertFalse(channel.evaluate(previous = null, current = 110.01, series = null))
    }

    @Test
    fun `outside is exactly the complement of inside`() {
        val outside = channel.copy(op = ChannelOp.OUTSIDE)
        listOf(99.99, 100.0, 105.0, 110.0, 110.01).forEach { price ->
            assertEquals(
                "at $price",
                channel.evaluate(null, price, null),
                !outside.evaluate(null, price, null),
            )
        }
    }

    @Test
    fun `entering fires on the sample that arrives at the edge, and not again`() {
        val entering = channel.copy(op = ChannelOp.ENTERING)
        assertTrue(entering.evaluate(previous = 99.0, current = 100.0, series = null))
        assertFalse(entering.evaluate(previous = 100.0, current = 105.0, series = null))
        assertFalse(entering.evaluate(previous = 105.0, current = 106.0, series = null))
        assertFalse(entering.evaluate(previous = null, current = 105.0, series = null))
    }

    @Test
    fun `exiting fires on the sample that leaves, and the bound itself has not left`() {
        val exiting = channel.copy(op = ChannelOp.EXITING)
        assertFalse(exiting.evaluate(previous = 105.0, current = 110.0, series = null))
        assertTrue(exiting.evaluate(previous = 105.0, current = 110.01, series = null))
        assertTrue(exiting.evaluate(previous = 100.0, current = 99.0, series = null))
        assertFalse(exiting.evaluate(previous = 99.0, current = 98.0, series = null))
    }

    @Test
    fun `a channel with its bounds the wrong way round is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlertTrigger.Channel(ChannelOp.INSIDE, low = 110.0, high = 100.0)
        }
    }

    /* --------------------------------------------------------------------- Move */

    @Test
    fun `a one-bar move falls back to the previous sample when there is no series`() {
        val up = AlertTrigger.Move(MoveOp.UP, amount = 5.0)
        assertTrue(up.evaluate(previous = 100.0, current = 105.0, series = null))
        assertFalse(up.evaluate(previous = 100.0, current = 104.99, series = null))
        assertFalse(up.evaluate(previous = null, current = 1_000.0, series = null))
    }

    @Test
    fun `a multi-bar move measures from that many bars back`() {
        val closes = doubleArrayOf(100.0, 101.0, 102.0, 103.0)
        val threeBars = AlertTrigger.Move(MoveOp.UP, amount = 3.0, bars = 3)
        // Three bars back is 101.0, so the move is measured from there and not from 103.0.
        assertTrue(threeBars.evaluate(previous = 103.0, current = 104.0, series = closes))
        assertFalse(threeBars.evaluate(previous = 103.0, current = 103.9, series = closes))
        // One bar back is 103.0, and against that the same sample is a move of one.
        val oneBar = AlertTrigger.Move(MoveOp.UP, amount = 3.0, bars = 1)
        assertFalse(oneBar.evaluate(previous = 103.0, current = 104.0, series = closes))
    }

    @Test
    fun `a move over a window longer than the series does not fire`() {
        val tenBars = AlertTrigger.Move(MoveOp.DOWN, amount = 1.0, bars = 10)
        assertFalse(tenBars.evaluate(previous = 100.0, current = 1.0, series = doubleArrayOf(100.0, 99.0)))
    }

    @Test
    fun `a percentage move needs a reference above zero`() {
        val down = AlertTrigger.Move(MoveOp.DOWN_PERCENT, amount = 5.0)
        assertTrue(down.evaluate(previous = 100.0, current = 95.0, series = null))
        assertFalse(down.evaluate(previous = 100.0, current = 95.1, series = null))
        assertFalse(down.evaluate(previous = 0.0, current = 0.0, series = null))
    }

    @Test
    fun `a move over less than one bar is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlertTrigger.Move(MoveOp.UP, amount = 1.0, bars = 0)
        }
    }

    /* ---------------------------------------------------------------- Indicator */

    /** The values handed in are the indicator's, not the price's, and the boundary rule is shared. */
    @Test
    fun `an indicator trigger compares the indicator's own output`() {
        val rsi = AlertTrigger.Indicator("rsi", period = 14, op = PriceOp.CROSSING_UP, value = 70.0)
        assertTrue(rsi.evaluate(previous = 69.5, current = 70.0, series = null))
        assertFalse(rsi.evaluate(previous = 70.5, current = 71.0, series = null))
    }

    @Test
    fun `an indicator trigger refuses a blank id or an impossible period`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlertTrigger.Indicator(" ", period = 14, op = PriceOp.GREATER_THAN, value = 70.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlertTrigger.Indicator("rsi", period = 0, op = PriceOp.GREATER_THAN, value = 70.0)
        }
    }

    /* -------------------------------------------------------------- DrawingTouch */

    @Test
    fun `a drawing is touched when price crosses the level it had at each sample`() {
        val touch = AlertTrigger.DrawingTouch("41")
        // The line rose from 100 to 101 while price rose from 99.5 to 101.5: it was crossed.
        assertTrue(touch.evaluate(previous = 99.5, current = 101.5, series = doubleArrayOf(100.0, 101.0)))
        // Price stayed under a line that rose faster: not touched.
        assertFalse(touch.evaluate(previous = 99.5, current = 100.5, series = doubleArrayOf(100.0, 101.0)))
    }

    @Test
    fun `landing exactly on a drawn line counts as touching it`() {
        val touch = AlertTrigger.DrawingTouch("41")
        assertTrue(touch.evaluate(previous = 99.0, current = 100.0, series = doubleArrayOf(100.0)))
        assertFalse(touch.evaluate(previous = 99.0, current = 99.5, series = doubleArrayOf(100.0)))
        assertFalse(touch.evaluate(previous = 99.0, current = 99.5, series = null))
    }

    /* ------------------------------------------------------------ MultiCondition */

    private val above = AlertTrigger.Price(PriceOp.GREATER_THAN, 100.0)

    @Test
    fun `a multi-condition holds only when every condition holds`() {
        val both = AlertTrigger.MultiCondition(
            listOf(above, AlertTrigger.Channel(ChannelOp.INSIDE, 90.0, 110.0)),
        )
        assertTrue(both.evaluate(previous = 95.0, current = 105.0, series = null))
        assertFalse(both.evaluate(previous = 95.0, current = 120.0, series = null))
        assertFalse(both.evaluate(previous = 95.0, current = 95.0, series = null))
    }

    @Test
    fun `a multi-condition refuses six conditions`() {
        val six = List(6) { index -> AlertTrigger.Price(PriceOp.GREATER_THAN, index.toDouble()) }
        assertThrows(IllegalArgumentException::class.java) { AlertTrigger.MultiCondition(six) }
        assertEquals(5, AlertTrigger.MultiCondition.MAX_CONDITIONS)
        assertEquals(5, AlertTrigger.MultiCondition(six.take(5)).conditions.size)
    }

    /** An empty AND is true, so an empty multi-condition would fire on every tick forever. */
    @Test
    fun `a multi-condition refuses no conditions at all`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlertTrigger.MultiCondition(emptyList())
        }
    }

    @Test
    fun `a multi-condition refuses to nest`() {
        val inner = AlertTrigger.MultiCondition(listOf(above))
        assertThrows(IllegalArgumentException::class.java) {
            AlertTrigger.MultiCondition(listOf(above, inner))
        }
    }

    /** Price and indicator conditions in one alert need different numbers, and this is how. */
    @Test
    fun `evaluateAll gives each condition its own sample`() {
        val rsi = AlertTrigger.Indicator("rsi", 14, PriceOp.GREATER_THAN, 70.0)
        val multi = AlertTrigger.MultiCondition(listOf(above, rsi))
        // The plain evaluate hands the price to both, so it reads 105 as an RSI of 105 and says
        // yes. That is exactly the mistake evaluateAll exists to prevent.
        assertTrue(multi.evaluate(previous = null, current = 105.0, series = null))
        assertFalse(
            multi.evaluateAll(current = { condition -> if (condition == rsi) 69.0 else 105.0 }),
        )
        assertTrue(
            multi.evaluateAll(current = { condition -> if (condition == rsi) 71.0 else 105.0 }),
        )
    }
}
