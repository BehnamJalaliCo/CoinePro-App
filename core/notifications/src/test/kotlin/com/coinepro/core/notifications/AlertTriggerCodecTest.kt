package com.coinepro.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A trigger's stored form, and the one property that matters about it.
 *
 * **Decoding cannot throw.** These strings are read on every launch of the alerts screen, from a
 * preferences file that a later release may have written in a shape this one does not know. Every
 * case below that is not a clean round trip is a case that must come back as null and let the alert
 * fall back to its flat condition, rather than taking a screen down with it.
 */
class AlertTriggerCodecTest {

    private val triggers = listOf(
        AlertTrigger.Price(PriceOp.CROSSING_UP, 65_000.0),
        AlertTrigger.Price(PriceOp.LESS_THAN, -0.5),
        AlertTrigger.Channel(ChannelOp.EXITING, low = 100.0, high = 110.0),
        AlertTrigger.Move(MoveOp.DOWN_PERCENT, amount = 3.5, bars = 4),
        AlertTrigger.Indicator("rsi", period = 14, op = PriceOp.CROSSING_DOWN, value = 30.0),
        AlertTrigger.Indicator("obv", period = null, op = PriceOp.GREATER_THAN, value = 0.0),
        AlertTrigger.DrawingTouch("41"),
    )

    @Test
    fun `every trigger survives a round trip whole`() {
        triggers.forEach { trigger ->
            assertEquals(trigger, AlertTriggerCodec.decode(AlertTriggerCodec.encode(trigger)))
        }
    }

    @Test
    fun `a multi-condition survives with its conditions in order`() {
        val multi = AlertTrigger.MultiCondition(triggers.take(3))
        assertEquals(multi, AlertTriggerCodec.decode(AlertTriggerCodec.encode(multi)))
    }

    /**
     * Nothing a trigger writes may contain the store's own separators.
     *
     * If it did, one alert would split into two rows and the second would decode as rubbish — so
     * the reader's alert would silently disappear for having a channel in it.
     */
    @Test
    fun `no encoded trigger contains a row or field separator`() {
        (triggers + AlertTrigger.MultiCondition(triggers.take(2))).forEach { trigger ->
            val encoded = AlertTriggerCodec.encode(trigger)
            assertFalse(encoded, encoded.contains(';'))
            assertFalse(encoded, encoded.contains('|'))
        }
    }

    @Test
    fun `no trigger at all encodes to nothing and decodes back to nothing`() {
        assertEquals("", AlertTriggerCodec.encode(null))
        assertNull(AlertTriggerCodec.decode(null))
        assertNull(AlertTriggerCodec.decode(""))
        assertNull(AlertTriggerCodec.decode("   "))
    }

    /** A case a later release added, a number that is not one, a truncated row. None of them throw. */
    @Test
    fun `an unreadable trigger decodes to null rather than throwing`() {
        val unreadable = listOf(
            "candlestick_pattern\u001Fhammer",
            "price",
            "price\u001Fcrossing_up",
            "price\u001Fnot_an_op\u001F1",
            "price\u001Fcrossing_up\u001Fnot_a_number",
            "channel\u001Finside\u001F110\u001F100",
            "channel\u001Finside\u001F110",
            "move\u001Fup\u001F1\u001F0",
            "indicator\u001F\u001F14\u001Fgreater_than\u001F70",
            "indicator\u001Frsi\u001F0\u001Fgreater_than\u001F70",
            "drawing_touch",
            "drawing_touch\u001F",
            "multi",
            "multi\u001F",
        )
        unreadable.forEach { raw ->
            assertNull(raw.replace('\u001F', '/'), AlertTriggerCodec.decode(raw))
        }
    }

    /** A stored multi-condition longer than the cap is refused rather than trimmed to fit. */
    @Test
    fun `a stored multi-condition of six conditions decodes to null`() {
        val six = (0 until 6).joinToString("\u001E") { index -> "price\u001Fgreater_than\u001F$index" }
        assertNull(AlertTriggerCodec.decode("multi\u001F$six"))
        val five = (0 until 5).joinToString("\u001E") { index -> "price\u001Fgreater_than\u001F$index" }
        assertTrue(AlertTriggerCodec.decode("multi\u001F$five") is AlertTrigger.MultiCondition)
    }

    /** One unreadable condition invalidates the whole AND; a partial AND is a different question. */
    @Test
    fun `a multi-condition with one unreadable condition decodes to null`() {
        assertNull(AlertTriggerCodec.decode("multi\u001Fprice\u001Fgreater_than\u001F1\u001Etarot\u001Fcups"))
    }

    /**
     * A script condition round-trips, **including a script with the codec's separators in it**.
     *
     * The case that would have lost the alert: `encode` refuses any payload containing `;` or `|`,
     * and a reader's script contains whatever they typed. Base64 on the two free-text fields is
     * what makes the refusal unreachable for them.
     */
    @Test
    fun `a script condition survives the field it is stored in`() {
        val trigger = AlertTrigger.ScriptCondition(
            source = "r = ta.rsi(close, 14)\nalertcondition(ta.crossunder(r, 30), \"Oversold; cross|back\")",
            condition = "Oversold; cross|back",
            name = "RSI Zones",
        )
        val encoded = AlertTriggerCodec.encode(trigger)
        assertTrue("a script's own punctuation must not empty the field", encoded.isNotEmpty())
        assertEquals(trigger, AlertTriggerCodec.decode(encoded))
    }

    @Test
    fun `a script condition fires only where the routing says the condition held`() {
        val trigger = AlertTrigger.ScriptCondition(source = "plot(close)", condition = "cross")
        assertTrue(trigger.evaluate(previous = 0.0, current = 1.0, series = null))
        assertTrue(!trigger.evaluate(previous = 1.0, current = 0.0, series = null))
        // An unanswerable condition arrives as NaN — a script that did not compile, or too few
        // bars — and every comparison against NaN is false, so nothing fires.
        assertTrue(!trigger.evaluate(previous = null, current = Double.NaN, series = null))
    }
}
