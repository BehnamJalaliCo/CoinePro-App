package com.coinepro.feature.alerts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.coinepro.core.datastore.AlertAuditStore
import com.coinepro.core.datastore.LocalAlertStore
import com.coinepro.core.notifications.AlertAuditEntry
import com.coinepro.core.notifications.AlertRepeat
import com.coinepro.core.notifications.AuditEvent
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the alert centre writes, as against what it draws.
 *
 * The drawing is covered by [AlertSentenceTest] and [AlertGroupingTest], which are pure. What is
 * left here is the handful of moments where a tap has to reach two places at once — the alerts
 * themselves and the evaluator's own bookkeeping — and where getting it wrong is silent: a deleted
 * alert whose firing stamps outlive it hands them to whatever alert is next given that id, and that
 * alert then sits armed and never speaks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertsControllerTest {

    private val alert = LocalPriceAlert(
        id = "abc123",
        symbol = "BTCUSDT",
        condition = LocalAlertCondition.ABOVE,
        value = 64_500.0,
        repeat = AlertRepeat.ONCE,
        createdAtEpochMillis = NOW,
    )

    @Test
    fun `deleting an alert forgets the fire state under its id`() = runTest {
        val forgotten = mutableListOf<String>()
        val store = store()
        store.add(alert)
        val controller = controller(store, forgotten)

        controller.requestDelete(onlyRow(controller))
        controller.confirmDelete()

        assertEquals(listOf("abc123"), forgotten)
        assertTrue(store.current().isEmpty())
    }

    @Test
    fun `switching an alert back on forgets the fire state, and pausing one does not`() = runTest {
        val forgotten = mutableListOf<String>()
        val store = store()
        store.add(alert)
        val controller = controller(store, forgotten)

        // Pausing is temporary and the stamps are still true when it resumes; re-arming is the
        // reader saying this alert has not spoken yet.
        controller.setPaused(onlyRow(controller), paused = true)
        assertEquals(emptyList<String>(), forgotten)

        controller.setPaused(onlyRow(controller), paused = false)
        assertEquals(listOf("abc123"), forgotten)
    }

    @Test
    fun `editing an alert rewrites the one that is there rather than adding a second`() = runTest {
        val forgotten = mutableListOf<String>()
        val store = store()
        store.add(alert)
        val controller = controller(store, forgotten)

        controller.editAlert(onlyRow(controller))
        controller.setFirst(index = 0, text = "70000")
        controller.save()

        val stored = store.current()
        assertEquals(1, stored.size)
        assertEquals("abc123", stored.single().id)
        assertEquals(70_000.0, stored.single().value, 0.0001)
        // An edit re-arms the alert, so its old firing stamps go with the condition they were about.
        assertEquals(listOf("abc123"), forgotten)
    }

    @Test
    fun `an edit is accepted with the list already at its cap`() = runTest {
        val store = store()
        repeat(LocalPriceAlert.MAX_ALERTS) { at -> store.add(alert.copy(id = "id$at")) }
        val controller = controller(store)

        val row = controller.state.value.sections.flatMap { it.rows }.first { it.alert.id == "id0" }
        controller.editAlert(row)
        controller.setFirst(index = 0, text = "70000")
        controller.save()

        // The old removal-then-insertion made room by hand. `upsert` needs none, and the refusal is
        // reserved for a genuinely new alert.
        assertNull(controller.state.value.refusal)
        assertEquals(70_000.0, store.current().first { it.id == "id0" }.value, 0.0001)
    }

    @Test
    fun `an alert's history starts when the reader makes it, not when it first fires`() = runTest {
        val audit = AlertAuditStore(FakeAlertPreferences())
        val store = store()
        val controller = controller(store, audit = audit)

        controller.openEditor("BTCUSDT")
        controller.setFirst(index = 0, text = "70000")
        controller.save()

        // «کِی ساخته شد» was a question the sheet could not answer while the evaluator was the only
        // thing writing to this log.
        val made = store.current().single()
        assertEquals(listOf(AuditEvent.CREATED), audit.eventsFor(made.id))
    }

    @Test
    fun `pausing and un-pausing are both recorded, newest first`() = runTest {
        val audit = AlertAuditStore(FakeAlertPreferences())
        val store = store()
        store.add(alert)
        val controller = controller(store, audit = audit)

        controller.setPaused(onlyRow(controller), paused = true)
        controller.setPaused(onlyRow(controller), paused = false)

        assertEquals(listOf(AuditEvent.ARMED, AuditEvent.SNOOZED), audit.eventsFor("abc123"))
    }

    @Test
    fun `undoing a pause corrects the record rather than leaving it standing`() = runTest {
        val audit = AlertAuditStore(FakeAlertPreferences())
        val store = store()
        store.add(alert)
        val controller = controller(store, audit = audit)

        controller.setPaused(onlyRow(controller), paused = true)
        controller.undo()

        // The alert is armed again, so a log whose last word on it is «به تعویق افتاد» would be the
        // one place this record is knowingly wrong.
        assertTrue(store.current().single().active)
        assertEquals(listOf(AuditEvent.ARMED, AuditEvent.SNOOZED), audit.eventsFor("abc123"))
    }

    @Test
    fun `an edit is recorded once and a save that changed nothing is not recorded at all`() = runTest {
        val audit = AlertAuditStore(FakeAlertPreferences())
        val store = store()
        store.add(alert)
        val controller = controller(store, audit = audit)

        controller.editAlert(onlyRow(controller))
        controller.setFirst(index = 0, text = "70000")
        controller.save()

        // Re-opened and closed with «ذخیره», which is how a reader closes a sheet they opened to
        // read. A line here would be the start of a column of them.
        controller.editAlert(onlyRow(controller))
        controller.save()

        assertEquals(listOf(AuditEvent.EDITED), audit.eventsFor("abc123"))
    }

    @Test
    fun `deleting an alert records the deletion and keeps everything before it`() = runTest {
        val audit = AlertAuditStore(FakeAlertPreferences())
        val store = store()
        store.add(alert)
        val controller = controller(store, audit = audit)

        controller.setPaused(onlyRow(controller), paused = true)
        controller.requestDelete(onlyRow(controller))
        controller.confirmDelete()

        // `AlertAuditStore.removeFor` is deliberately not called: deleting an alert is the reader
        // saying they no longer want it evaluated, and that is a different thing from saying they
        // no longer want it recorded. Erasing the log with the alert would also erase the «حذف شد»
        // written a moment earlier.
        assertTrue(store.current().isEmpty())
        assertEquals(listOf(AuditEvent.DELETED, AuditEvent.SNOOZED), audit.eventsFor("abc123"))
    }

    @Test
    fun `a duplicated alert gets a history of its own`() = runTest {
        val audit = AlertAuditStore(FakeAlertPreferences())
        val store = store()
        store.add(alert)
        val controller = controller(store, audit = audit)

        controller.duplicate(onlyRow(controller))

        // The one alert a reader can make without opening the editor. Left out, it would be the one
        // whose log still began at its first firing.
        assertEquals(listOf(AuditEvent.CREATED), audit.eventsFor("new0000000000000"))
        assertEquals(emptyList<AuditEvent>(), audit.eventsFor("abc123"))
    }

    private fun TestScope.controller(
        store: LocalAlertStore,
        forgotten: MutableList<String> = mutableListOf(),
        audit: AlertAuditStore = AlertAuditStore(FakeAlertPreferences()),
    ) = AlertsController(
        store = store,
        audit = audit,
        catalogOf = { listOf("BTCUSDT", "ETHUSDT") },
        scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
        timeframeOf = { null },
        forgetFireState = { id -> forgotten += id },
        now = { NOW },
        newId = { "new0000000000000" },
    )

    private fun store() = LocalAlertStore(FakeAlertPreferences())

    /**
     * One alert's history, newest first — the order the store keeps and the order this asserts in.
     *
     * The sheet turns it round for display, because a firing and its delivery only read as a pair
     * downwards. Asserting the stored order here rather than the displayed one keeps this test
     * about what was written.
     */
    private suspend fun AlertAuditStore.eventsFor(id: String): List<AuditEvent> =
        entriesFor(id).first().map(AlertAuditEntry::event)

    private fun onlyRow(controller: AlertsController): AlertRow =
        controller.state.value.sections.flatMap(AlertRowSection::rows).single()

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}

private class FakeAlertPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow<Preferences>(emptyPreferences())

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(data.value)
        data.value = next
        return next
    }
}
