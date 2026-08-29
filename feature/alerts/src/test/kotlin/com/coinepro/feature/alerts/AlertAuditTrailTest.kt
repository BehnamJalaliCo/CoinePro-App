package com.coinepro.feature.alerts

import com.coinepro.core.notifications.AlertChannel
import com.coinepro.core.notifications.AlertFrequency
import com.coinepro.core.notifications.AlertRepeat
import com.coinepro.core.notifications.AlertScope
import com.coinepro.core.notifications.AlertTrigger
import com.coinepro.core.notifications.AuditEvent
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.core.notifications.PriceOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which saves are worth a line of history, and which are somebody closing a sheet.
 *
 * The half of the audit log that records the reader is the half that can be written wrongly without
 * anybody noticing: a missing line is a question the sheet cannot answer, and a spurious one is a
 * column of «ویرایش شد» that makes the two lines that matter unfindable. Both failures are silent
 * on a device, so they are pinned here instead.
 */
class AlertAuditTrailTest {

    private val alert = LocalPriceAlert(
        id = "abc123",
        symbol = "BTCUSDT",
        condition = LocalAlertCondition.ABOVE,
        value = 64_500.0,
        repeat = AlertRepeat.ONCE,
        frequency = AlertFrequency.ONCE,
        trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 64_500.0),
        scope = AlertScope.Symbol("BTCUSDT"),
        createdAtEpochMillis = 1_700_000_000_000L,
    )

    @Test
    fun `a new alert is created, and says nothing more than that`() {
        val write = AlertAuditTrail.save(previous = null, next = alert)

        assertEquals(AuditEvent.CREATED, write?.event)
        // The sheet's own subtitle is the alert's sentence, so a note repeating it would be the
        // first line of the log arguing with the heading over it.
        assertNull(write?.note)
    }

    @Test
    fun `re-saving an alert without touching it writes nothing`() {
        assertNull(AlertAuditTrail.save(previous = alert, next = alert))
    }

    @Test
    fun `an alert stored before triggers and frequencies existed is not an edit when re-saved`() {
        // What the store holds for every alert made before those two fields did: a flat condition,
        // a wall-clock repeat, and no scope. Re-opening it in the editor and saving fills all three
        // in — a real change to the row on disk and no change at all to what the alert waits for.
        val legacy = alert.copy(trigger = null, frequency = null, scope = null)

        assertEquals(emptyList<String>(), AlertAuditTrail.changes(legacy, alert))
        assertNull(AlertAuditTrail.save(previous = legacy, next = alert))
    }

    @Test
    fun `changing the level is an edit, and the note names the condition`() {
        val next = alert.copy(
            value = 70_000.0,
            trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 70_000.0),
        )

        val write = AlertAuditTrail.save(previous = alert, next = next)

        assertEquals(AuditEvent.EDITED, write?.event)
        assertEquals(listOf("شرط"), AlertAuditTrail.changes(alert, next))
        assertTrue(write?.note.orEmpty().contains("شرط"))
    }

    @Test
    fun `several changes are named together in one clause`() {
        val next = alert.copy(
            frequency = AlertFrequency.EVERY_TIME,
            repeat = AlertRepeat.ALWAYS,
            channels = setOf(AlertChannel.PUSH),
            message = "رسید",
        )

        assertEquals(listOf("تکرار", "کانال‌ها", "پیام"), AlertAuditTrail.changes(alert, next))
        // Persian joins the list with «، » and the last pair with « و », and the verb stays
        // singular; the sentence is asserted as a whole because a fragment of it reads as nothing.
        assertEquals("تکرار، کانال‌ها و پیام تغییر کرد", AlertAuditTrail.save(alert, next)?.note)
    }

    @Test
    fun `a loudness that only moved within one step is not a change`() {
        // A hand-edited preference, or a level written by a build with different steps. The control
        // has three positions and this is the same position, so reporting it would report
        // arithmetic rather than anything the reader did.
        val nudged = alert.copy(soundLevel = alert.effectiveSoundLevel + 0.01f)

        assertEquals(emptyList<String>(), AlertAuditTrail.changes(alert, nudged))
    }

    @Test
    fun `saving a paused alert unchanged arms it, and says so`() {
        // `AlertDraft.toAlert` writes `active = true` whatever the alert was, so this save is the
        // moment a switched-off alert starts watching again — with nothing else to explain it.
        val paused = alert.copy(active = false)

        val write = AlertAuditTrail.save(previous = paused, next = alert)

        assertEquals(AuditEvent.ARMED, write?.event)
        assertTrue(AlertAuditTrail.rearms(paused, alert))
    }

    @Test
    fun `saving a spent one-shot unchanged arms it`() {
        // The other way in, and the same event to a reader: the edit cleared the last firing, so an
        // alert that had already spoken is eligible again.
        val spent = alert.copy(lastFiredAtEpochMillis = 1_700_000_100_000L)

        assertEquals(AuditEvent.ARMED, AlertAuditTrail.save(previous = spent, next = alert)?.event)
    }

    @Test
    fun `an edit that also re-arms writes one line, not two`() {
        val paused = alert.copy(active = false)
        val next = alert.copy(
            value = 70_000.0,
            trigger = AlertTrigger.Price(PriceOp.GREATER_THAN, 70_000.0),
        )

        // Both are true of this save. The edit is the one that explains the other, and two lines
        // stamped at the same millisecond would leave the sheet's oldest-first order to break the
        // tie by insertion.
        assertEquals(AuditEvent.EDITED, AlertAuditTrail.save(previous = paused, next = next)?.event)
    }

    @Test
    fun `moving an alert onto a watchlist is a change of scope`() {
        val listed = alert.copy(scope = AlertScope.Watchlist("list-1"))

        assertEquals(listOf("دامنه"), AlertAuditTrail.changes(alert, listed))
    }
}
