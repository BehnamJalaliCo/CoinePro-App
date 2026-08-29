package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrawingSyncStoreTest {

    @Test
    fun `a reader who has never chosen keeps drawings on the chart they were drawn on`() = runTest {
        val store = DrawingSyncStore(FakeDrawingSyncPreferences())

        // An update that suddenly copied a year of drawings into every layout would be
        // indistinguishable from a bug, and there is no undo for it.
        assertEquals(DrawingSyncMode.NONE, store.mode().first())
    }

    @Test
    fun `every mode round-trips`() = runTest {
        DrawingSyncMode.entries.forEach { mode ->
            val store = DrawingSyncStore(FakeDrawingSyncPreferences())

            store.setMode(mode)

            assertEquals(mode, store.mode().first())
        }
    }

    @Test
    fun `choosing none is written rather than removed, so a later default cannot override it`() = runTest {
        val backing = FakeDrawingSyncPreferences()

        DrawingSyncStore(backing).setMode(DrawingSyncMode.NONE)

        assertEquals(
            DrawingSyncMode.NONE.id,
            backing.data.first()[stringPreferencesKey("chart_drawing_sync_mode")],
        )
    }

    @Test
    fun `a mode id from a newer build reads as none rather than throwing or guessing wide`() = runTest {
        val backing = FakeDrawingSyncPreferences(
            mutablePreferencesOf(stringPreferencesKey("chart_drawing_sync_mode") to "every_symbol"),
        )

        // Widening a reader's drawings on the strength of a string we cannot read is the one
        // failure here that cannot be taken back.
        assertEquals(DrawingSyncMode.NONE, DrawingSyncStore(backing).mode().first())
    }

    @Test
    fun `a blank row reads as none`() = runTest {
        val backing = FakeDrawingSyncPreferences(
            mutablePreferencesOf(stringPreferencesKey("chart_drawing_sync_mode") to ""),
        )

        assertEquals(DrawingSyncMode.NONE, DrawingSyncStore(backing).mode().first())
    }

    @Test
    fun `the stored ids are stable, because a row written today has to read back tomorrow`() {
        assertEquals(DrawingSyncMode.NONE, DrawingSyncMode.fromId("none"))
        assertEquals(DrawingSyncMode.LAYOUT, DrawingSyncMode.fromId("layout"))
        assertEquals(DrawingSyncMode.GLOBAL, DrawingSyncMode.fromId("global"))
        assertEquals(DrawingSyncMode.NONE, DrawingSyncMode.fromId(null))
    }
}

private class FakeDrawingSyncPreferences(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
