package com.coinepro.feature.alerts

import com.coinepro.core.notifications.AlertFrequency
import com.coinepro.core.notifications.AlertScope
import com.coinepro.core.notifications.AlertTrigger
import com.coinepro.core.notifications.ChannelOp
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.core.notifications.MoveOp
import com.coinepro.core.notifications.PriceOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor's draft: what it will build, and what it refuses to.
 *
 * The cap is the case worth the most here. `AlertTrigger.MultiCondition` throws above five, and the
 * sheet asks it for a trigger on every keystroke in order to decide whether the save button is
 * live — so a draft that could hold six conditions would not produce a bad alert, it would take the
 * sheet down while somebody was typing into it.
 */
class AlertDraftTest {

    private val delta = 1e-9

    @Test
    fun `a fresh draft holds one condition and can take another`() {
        val draft = AlertDraft()

        assertEquals(1, draft.conditions.size)
        assertTrue(draft.canAddCondition)
    }

    @Test
    fun `the cap is five and the draft stops offering another there`() {
        assertEquals(5, AlertTrigger.MultiCondition.MAX_CONDITIONS)

        val full = AlertDraft(conditions = List(5) { level("${it + 1}") })

        assertFalse("a sixth condition would be refused by the trigger itself", full.canAddCondition)
        assertTrue(full.copy(symbol = "BTCUSDT").valid)
    }

    @Test
    fun `a draft at the cap still builds a trigger with every condition in it`() {
        val full = AlertDraft(symbol = "BTCUSDT", conditions = List(5) { level("${it + 1}") })

        val trigger = full.trigger() as AlertTrigger.MultiCondition

        assertEquals(5, trigger.conditions.size)
        assertEquals(1.0, (trigger.conditions.first() as AlertTrigger.Price).value, delta)
        assertEquals(5.0, (trigger.conditions.last() as AlertTrigger.Price).value, delta)
    }

    @Test
    fun `one condition stays a bare trigger rather than becoming a one-element and`() {
        val single = AlertDraft(symbol = "BTCUSDT", conditions = listOf(level("100")))

        assertTrue(single.trigger() is AlertTrigger.Price)
    }

    @Test
    fun `a draft with any incomplete condition builds nothing and cannot be saved`() {
        val half = AlertDraft(
            symbol = "BTCUSDT",
            conditions = listOf(level("100"), level("")),
        )

        assertNull(half.trigger())
        assertFalse(half.valid)
    }

    @Test
    fun `a draft with no symbol cannot be saved however complete its condition is`() {
        val anonymous = AlertDraft(conditions = listOf(level("100")))

        assertNotNull(anonymous.trigger())
        assertFalse(anonymous.valid)
    }

    @Test
    fun `Persian digits typed on a Persian keyboard are folded before the number is read`() {
        val typed = AlertConditionDraft(kind = AlertTriggerKind.PRICE, first = "۶۸۵۰۰")

        assertEquals(68_500.0, typed.firstValue!!, delta)
    }

    @Test
    fun `a channel whose bounds are the wrong way round is reported rather than silently sorted`() {
        val inverted = AlertConditionDraft(
            kind = AlertTriggerKind.CHANNEL,
            channelOp = ChannelOp.ENTERING,
            first = "66000",
            second = "64000",
        )

        assertTrue(inverted.boundsInverted)
        assertNull("an inverted channel must not be built", inverted.build())
    }

    @Test
    fun `changing an existing alert starts from the condition it already has`() {
        val existing = LocalPriceAlert(
            id = "a",
            symbol = "BTCUSDT",
            condition = LocalAlertCondition.ABOVE,
            value = 0.0,
            trigger = AlertTrigger.Indicator("rsi", 14, PriceOp.GREATER_THAN, 70.0),
            frequency = AlertFrequency.ONCE_PER_BAR_CLOSE,
        )

        val draft = AlertDraft.of(existing)!!
        val row = draft.conditions.single()

        assertEquals("BTCUSDT", draft.symbol)
        assertEquals(AlertFrequency.ONCE_PER_BAR_CLOSE, draft.frequency)
        assertEquals(AlertTriggerKind.INDICATOR, row.kind)
        assertEquals("rsi", row.indicatorId)
        assertEquals(14, row.period)
        assertEquals(70.0, row.firstValue!!, delta)
    }

    @Test
    fun `an alert this sheet cannot express is not opened in it`() {
        val drawn = LocalPriceAlert(
            id = "a",
            symbol = "BTCUSDT",
            condition = LocalAlertCondition.ABOVE,
            value = 1.0,
            trigger = AlertTrigger.DrawingTouch("line-1"),
        )
        val daily = LocalPriceAlert(
            id = "b",
            symbol = "BTCUSDT",
            condition = LocalAlertCondition.CHANGE_24H_OVER,
            value = 5.0,
        )

        assertNull("a drawing alert is made on the chart, not here", AlertDraft.of(drawn))
        assertNull("no trigger measures the feed's own daily change", AlertDraft.of(daily))
    }

    @Test
    fun `saving writes the scope, the trigger and a flat condition an older evaluator can read`() {
        val draft = AlertDraft(
            symbol = "btcusdt",
            conditions = listOf(
                AlertConditionDraft(kind = AlertTriggerKind.MOVE, moveOp = MoveOp.DOWN_PERCENT, first = "3"),
            ),
            frequency = AlertFrequency.EVERY_TIME,
        )

        val alert = draft.toAlert(existing = null, id = "new", nowEpochMillis = 42L)!!

        assertEquals("BTCUSDT", alert.symbol)
        assertEquals(AlertScope.Symbol("BTCUSDT"), alert.scope)
        assertEquals(AlertTrigger.Move(MoveOp.DOWN_PERCENT, 3.0), alert.trigger)
        assertEquals(LocalAlertCondition.PERCENT_DOWN, alert.condition)
        assertEquals(3.0, alert.value, delta)
        assertEquals(42L, alert.createdAtEpochMillis)
    }

    @Test
    fun `an edited alert keeps its birthday and is armed again`() {
        val existing = LocalPriceAlert(
            id = "a",
            symbol = "BTCUSDT",
            condition = LocalAlertCondition.ABOVE,
            value = 100.0,
            active = false,
            createdAtEpochMillis = 7L,
            lastFiredAtEpochMillis = 9L,
        )
        val draft = AlertDraft.of(existing)!!.copy(conditions = listOf(level("200")))

        val saved = draft.toAlert(existing = existing, id = "ignored", nowEpochMillis = 99L)!!

        assertEquals("a", saved.id)
        assertEquals(7L, saved.createdAtEpochMillis)
        assertTrue("an alert somebody has just corrected must be able to fire", saved.active)
        assertNull("otherwise a spent one-shot would be edited and then never fire", saved.lastFiredAtEpochMillis)
    }

    private fun level(value: String) =
        AlertConditionDraft(kind = AlertTriggerKind.PRICE, priceOp = PriceOp.GREATER_THAN, first = value)
}
