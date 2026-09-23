package com.coinepro.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertDrawingLinksTest {

    private fun alert(
        id: String,
        symbol: String = "BTCUSDT",
        trigger: AlertTrigger? = null,
    ) = LocalPriceAlert(
        id = id,
        symbol = symbol,
        condition = LocalAlertCondition.ABOVE,
        value = 1.0,
        trigger = trigger,
    )

    @Test
    fun `a bare touch is one link`() {
        val alerts = listOf(alert("a", trigger = AlertTrigger.DrawingTouch("17")))
        assertEquals(
            listOf(DrawingAlertLink(alertId = "a", symbol = "BTCUSDT", drawingId = "17")),
            AlertDrawingLinks.linksOf(alerts),
        )
    }

    @Test
    fun `a touch inside a multi-condition is found too`() {
        val trigger = AlertTrigger.MultiCondition(
            listOf(
                AlertTrigger.Price(PriceOp.GREATER_THAN, 100.0),
                AlertTrigger.DrawingTouch("42"),
            ),
        )
        assertEquals(setOf("42"), AlertDrawingLinks.drawingIdsOf(trigger))
        assertEquals(1, AlertDrawingLinks.linksOf(listOf(alert("a", trigger = trigger))).size)
    }

    @Test
    fun `an alert with no drawing contributes nothing`() {
        val alerts = listOf(
            alert("a"),
            alert("b", trigger = AlertTrigger.Price(PriceOp.LESS_THAN, 5.0)),
        )
        assertTrue(AlertDrawingLinks.linksOf(alerts).isEmpty())
        assertTrue(AlertDrawingLinks.symbolsOf(alerts).isEmpty())
    }

    @Test
    fun `the symbol is matched whatever case it was stored in`() {
        val alerts = listOf(alert("a", symbol = "btcusdt", trigger = AlertTrigger.DrawingTouch("9")))
        assertEquals(listOf("a"), AlertDrawingLinks.on(alerts, "BTCUSDT", 9L).map { it.id })
        assertEquals(setOf("BTCUSDT"), AlertDrawingLinks.symbolsOf(alerts))
    }

    @Test
    fun `an alert on another symbol's drawing of the same id is not on this one`() {
        val alerts = listOf(alert("a", symbol = "XAUUSD", trigger = AlertTrigger.DrawingTouch("9")))
        assertTrue(AlertDrawingLinks.on(alerts, "BTCUSDT", 9L).isEmpty())
    }

    @Test
    fun `an unread symbol yields no verdict at all`() {
        // The rule that keeps the first frame of the alert centre honest: nothing has loaded, so
        // nothing is broken, rather than everything being broken.
        val alerts = listOf(alert("a", trigger = AlertTrigger.DrawingTouch("17")))
        assertTrue(AlertDrawingLinks.orphanIds(alerts, emptyMap()).isEmpty())
        assertTrue(AlertDrawingLinks.orphanIds(alerts, mapOf("XAUUSD" to setOf("17"))).isEmpty())
    }

    @Test
    fun `a symbol read and found empty does make its alerts orphans`() {
        val alerts = listOf(alert("a", trigger = AlertTrigger.DrawingTouch("17")))
        assertEquals(setOf("a"), AlertDrawingLinks.orphanIds(alerts, mapOf("BTCUSDT" to emptySet())))
    }

    @Test
    fun `a drawing that is still there is not an orphan`() {
        val alerts = listOf(alert("a", trigger = AlertTrigger.DrawingTouch("17")))
        assertTrue(AlertDrawingLinks.orphanIds(alerts, mapOf("BTCUSDT" to setOf("17", "18"))).isEmpty())
    }

    @Test
    fun `one missing term of an AND orphans the whole alert`() {
        val trigger = AlertTrigger.MultiCondition(
            listOf(AlertTrigger.DrawingTouch("17"), AlertTrigger.DrawingTouch("18")),
        )
        val alerts = listOf(alert("a", trigger = trigger))
        assertEquals(setOf("a"), AlertDrawingLinks.orphanIds(alerts, mapOf("BTCUSDT" to setOf("17"))))
    }

    @Test
    fun `an alert with no drawing is never an orphan`() {
        val alerts = listOf(alert("a", trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 1.0)))
        assertTrue(AlertDrawingLinks.orphanIds(alerts, mapOf("BTCUSDT" to emptySet())).isEmpty())
    }
}
