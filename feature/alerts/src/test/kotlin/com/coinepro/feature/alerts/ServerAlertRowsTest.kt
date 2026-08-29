package com.coinepro.feature.alerts

import com.coinepro.core.notifications.AlertChannel
import com.coinepro.core.notifications.AlertRepeat
import com.coinepro.core.notifications.AlertScope
import com.coinepro.core.notifications.AlertTrigger
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.PriceAlert
import com.coinepro.core.notifications.PriceAlertCondition
import com.coinepro.core.notifications.PriceAlertTrigger
import com.coinepro.core.notifications.PriceOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A server alert becoming a row of the one list, and back.
 *
 * The app had two alert systems: this list, with thirteen triggers and an audit log, and a
 * primitive server one on the activity screen. Unifying them means the server's shape passes
 * through this converter and is then grouped, sorted and read as a sentence by exactly the code
 * that handles a device alert — so what has to be right is the conversion, and nothing else.
 */
class ServerAlertRowsTest {

    private val delta = 1e-9

    private fun server(
        id: String = "srv-1",
        condition: PriceAlertCondition = PriceAlertCondition.ABOVE,
        trigger: PriceAlertTrigger = PriceAlertTrigger.ONCE,
        active: Boolean = true,
        lastTriggered: Long? = null,
    ) = PriceAlert(
        id = id,
        market = "crypto",
        symbol = "BTCUSDT",
        condition = condition,
        value = 68_500.0,
        trigger = trigger,
        expiresAtEpochMillis = null,
        active = active,
        createdAtEpochMillis = 1_000L,
        lastTriggeredAtEpochMillis = lastTriggered,
    )

    @Test
    fun `a server row's id is prefixed so the two id spaces cannot collide`() {
        val row = ServerAlertRows.asLocal(server())

        assertNotEquals("srv-1", row.id)
        assertEquals("srv-1", ServerAlertRows.serverIdOf(row.id))
    }

    @Test
    fun `a device alert's id is not mistaken for a server one`() {
        // Every action on a row is routed by this answer. A device id read as a server id would
        // send a pause to an HTTP route and leave the alert running on the phone.
        assertNull(ServerAlertRows.serverIdOf("abc123"))
        assertNull(ServerAlertRows.serverIdOf(""))
    }

    @Test
    fun `above and below stay the flat inclusive comparison they have always been`() {
        val above = ServerAlertRows.asLocal(server(condition = PriceAlertCondition.ABOVE))
        val below = ServerAlertRows.asLocal(server(condition = PriceAlertCondition.BELOW))

        assertEquals(LocalAlertCondition.ABOVE, above.condition)
        assertNull("no trigger, so the sentence reads «بالای»", above.trigger)
        assertEquals(LocalAlertCondition.BELOW, below.condition)
        assertEquals(68_500.0, above.value, delta)
    }

    @Test
    fun `a crossing becomes a crossing, which the flat pair cannot say`() {
        val up = ServerAlertRows.asLocal(server(condition = PriceAlertCondition.CROSS_UP))
        val down = ServerAlertRows.asLocal(server(condition = PriceAlertCondition.CROSS_DOWN))
        val either = ServerAlertRows.asLocal(server(condition = PriceAlertCondition.CROSS))

        assertEquals(AlertTrigger.Price(PriceOp.CROSSING_UP, 68_500.0), up.trigger)
        assertEquals(AlertTrigger.Price(PriceOp.CROSSING_DOWN, 68_500.0), down.trigger)
        assertEquals(AlertTrigger.Price(PriceOp.CROSSING, 68_500.0), either.trigger)
    }

    @Test
    fun `a recurring server alert is not a one-shot, so it is not filed as spent`() {
        val once = ServerAlertRows.asLocal(server(trigger = PriceAlertTrigger.ONCE, lastTriggered = 10L))
        val always = ServerAlertRows.asLocal(server(trigger = PriceAlertTrigger.RECURRING, lastTriggered = 10L))

        assertEquals(AlertRepeat.ONCE, once.repeat)
        assertEquals(AlertRepeat.ALWAYS, always.repeat)
        // A day later the one-shot is expired and the recurring one is merely waiting again.
        val later = 10L + AlertGrouping.RECENT_WINDOW_MILLIS + 1L
        assertEquals(AlertSectionKind.EXPIRED, AlertGrouping.kindOf(once, later))
        assertEquals(AlertSectionKind.ARMED, AlertGrouping.kindOf(always, later))
    }

    @Test
    fun `a server alert reaches the reader by notification and nothing else`() {
        // It is decided somewhere else, so the in-app banner and the per-alert sound have nothing
        // to attach to. Offering them in the editor would be offering settings that do nothing.
        val row = ServerAlertRows.asLocal(server())

        assertEquals(setOf(AlertChannel.PUSH), row.channels)
        assertEquals(AlertScope.Symbol("BTCUSDT"), row.scope)
        assertTrue(row.active)
    }

    @Test
    fun `the five comparisons the server has all map back to it`() {
        assertEquals(PriceAlertCondition.ABOVE, ServerAlertRows.conditionOf(PriceOp.GREATER_THAN))
        assertEquals(PriceAlertCondition.BELOW, ServerAlertRows.conditionOf(PriceOp.LESS_THAN))
        assertEquals(PriceAlertCondition.CROSS_UP, ServerAlertRows.conditionOf(PriceOp.CROSSING_UP))
        assertEquals(PriceAlertCondition.CROSS_DOWN, ServerAlertRows.conditionOf(PriceOp.CROSSING_DOWN))
        assertEquals(PriceAlertCondition.CROSS, ServerAlertRows.conditionOf(PriceOp.CROSSING))
    }

    @Test
    fun `a bar-aware frequency becomes recurring, because only the device knows a bar`() {
        val perBar = AlertDraft(
            symbol = "BTCUSDT",
            conditions = listOf(AlertConditionDraft(priceOp = PriceOp.GREATER_THAN, first = "68500")),
            frequency = com.coinepro.core.notifications.AlertFrequency.ONCE_PER_BAR_CLOSE,
        )

        assertEquals(PriceAlertTrigger.RECURRING, ServerAlertRows.requestOf(perBar)?.trigger)
    }

    @Test
    fun `a level that is not a price is refused before it reaches the network`() {
        val zero = AlertDraft(
            symbol = "BTCUSDT",
            conditions = listOf(AlertConditionDraft(priceOp = PriceOp.GREATER_THAN, first = "0")),
        )

        assertNull(ServerAlertRows.requestOf(zero))
    }
}
