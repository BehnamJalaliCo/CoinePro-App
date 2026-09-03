package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where the app opens: the tab the reader was last on.
 *
 * The value is deliberately a **route string** and not an enum ordinal, and that is the thing worth
 * a test. This app's bottom bar has been five destinations, then six, and is five again; an ordinal
 * survives none of those and would silently move a reader to whichever tab now occupies position
 * three. A route either still exists or it does not, and "does not" is a question the shell answers
 * by falling back — which is the same answer a first launch gets.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LastRootRouteTest {

    private class FakeDataStore : DataStore<Preferences> {
        val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> get() = state
        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    @Test
    fun `a first launch has no preference`() = runTest {
        val store = UserPreferencesStore(FakeDataStore())
        assertNull(store.lastRootRoute.first())
    }

    @Test
    fun `the route the reader chose is the route that comes back`() = runTest {
        val store = UserPreferencesStore(FakeDataStore())
        store.setLastRootRoute("chart-tab")
        assertEquals("chart-tab", store.lastRootRoute.first())

        store.setLastRootRoute("ideas")
        assertEquals("ideas", store.lastRootRoute.first())
    }

    @Test
    fun `blank is not a choice and does not overwrite one`() = runTest {
        // Nothing in the app writes a blank, and if something ever does it must not turn a real
        // preference into "no preference" — which on the next launch is a reader sent somewhere
        // they did not ask for, with nothing to explain it.
        val store = UserPreferencesStore(FakeDataStore())
        store.setLastRootRoute("watchlist")
        store.setLastRootRoute("   ")
        assertEquals("watchlist", store.lastRootRoute.first())
    }

    @Test
    fun `a stored value that is only whitespace reads as no preference`() = runTest {
        // Written by an older build, or by a store that was edited by hand. Reading it back as a
        // route would make the start destination a blank string.
        val backing = FakeDataStore()
        backing.state.value = mutablePreferencesOf(
            androidx.datastore.preferences.core.stringPreferencesKey("last_root_route") to "  ",
        )
        assertNull(UserPreferencesStore(backing).lastRootRoute.first())
    }
}
