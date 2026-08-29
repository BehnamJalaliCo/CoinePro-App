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
}
