package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.coinepro.core.notifications.AlertRepeat
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two writes an alert screen makes that are not simply «add» or «remove».
 *
 * Both exist to stop a specific, silent loss. An edit written as a removal and an insertion
 * duplicates the alert the moment the id is not matched, and stamping a bar-policy alert through
 * [LocalPriceAlert.fired] switches it off after its first bar — in both cases the reader is left
 * looking at a list that says something untrue about their own alerts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalAlertStoreWriteTest {

    private val alert = LocalPriceAlert(
        id = "abc123",
        symbol = "BTCUSDT",
        condition = LocalAlertCondition.ABOVE,
        value = 64_500.0,
        repeat = AlertRepeat.ONCE,
        createdAtEpochMillis = 1_700_000_000_000L,
    )

    @Test
    fun `upsert replaces the alert with the same id rather than storing a second one`() = runTest {
        val store = LocalAlertStore(FakeAlertPreferences())
        store.add(alert)

        store.upsert(alert.copy(value = 70_000.0))

        val stored = store.current()
        assertEquals(1, stored.size)
        assertEquals(70_000.0, stored.single().value, 0.0001)
    }

    @Test
    fun `upsert leaves an edited alert where it was in the list`() = runTest {
        val store = LocalAlertStore(FakeAlertPreferences())
        store.add(alert)
        store.add(alert.copy(id = "def456", symbol = "ETHUSDT"))
        store.add(alert.copy(id = "ghi789", symbol = "XAUUSD"))

        store.upsert(alert.copy(value = 70_000.0))

        // The order is the order the reader made them in, and editing one is not a request to
        // move it to the end of their own list.
        assertEquals(listOf("abc123", "def456", "ghi789"), store.current().map(LocalPriceAlert::id))
    }

    @Test
    fun `upsert stores an alert the list has never seen`() = runTest {
        val store = LocalAlertStore(FakeAlertPreferences())

        assertTrue(store.upsert(alert))
        assertEquals(listOf(alert), store.current())
    }

    @Test
    fun `upsert refuses a new alert on a full list and still accepts an edit to one in it`() = runTest {
        val store = LocalAlertStore(FakeAlertPreferences())
        repeat(LocalPriceAlert.MAX_ALERTS) { at -> store.add(alert.copy(id = "id$at")) }

        assertFalse(store.upsert(alert.copy(id = "one too many")))
        assertTrue(store.upsert(alert.copy(id = "id0", value = 70_000.0)))
        assertEquals(LocalPriceAlert.MAX_ALERTS, store.current().size)
        assertEquals(70_000.0, store.current().first { it.id == "id0" }.value, 0.0001)
    }

    @Test
    fun `markFiredKeepingActive stamps a one-shot alert without switching it off`() = runTest {
        val store = LocalAlertStore(FakeAlertPreferences())
        store.add(alert)

        store.markFiredKeepingActive(listOf(alert), atEpochMillis = 1_700_000_500_000L)

        // The whole point: `fired()` would deactivate this, which is right for a one-shot alert
        // and wrong for the bar policy that borrows the same stamp.
        val stored = store.current().single()
        assertEquals(1_700_000_500_000L, stored.lastFiredAtEpochMillis)
        assertTrue(stored.active)
    }

    @Test
    fun `markFired still switches a one-shot alert off`() = runTest {
        val store = LocalAlertStore(FakeAlertPreferences())
        store.add(alert)

        store.markFired(listOf(alert), atEpochMillis = 1_700_000_500_000L)

        assertFalse(store.current().single().active)
    }

    @Test
    fun `markFiredKeepingActive touches only the alerts it was given`() = runTest {
        val store = LocalAlertStore(FakeAlertPreferences())
        store.add(alert)
        store.add(alert.copy(id = "def456", symbol = "ETHUSDT"))

        store.markFiredKeepingActive(listOf(alert), atEpochMillis = 1_700_000_500_000L)

        assertNull(store.current().first { it.id == "def456" }.lastFiredAtEpochMillis)
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
