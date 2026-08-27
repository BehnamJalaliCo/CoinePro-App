package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The property that makes a watchlist a watchlist: the order is the reader's, and nothing the
 * market does may rearrange it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistStoreTest {

    @Test
    fun `symbols keep the order they were added in`() = runTest {
        val store = WatchlistStore(FakeDataStore())

        store.toggle("SOLUSDT")
        store.toggle("BTCUSDT")
        store.toggle("ETHUSDT")

        // Not alphabetical, not by price, not by anything the feed decides.
        assertEquals(listOf("SOLUSDT", "BTCUSDT", "ETHUSDT"), store.symbols.first())
    }

    @Test
    fun `toggling something already there removes it`() = runTest {
        val store = WatchlistStore(FakeDataStore())
        store.toggle("BTCUSDT")
        store.toggle("ETHUSDT")

        store.toggle("BTCUSDT")

        assertEquals(listOf("ETHUSDT"), store.symbols.first())
    }

    @Test
    fun `a ticker is normalised, so one symbol cannot appear twice in two cases`() = runTest {
        val store = WatchlistStore(FakeDataStore())

        store.toggle("btcusdt")
        store.toggle(" BTCUSDT ")

        // The second call is a removal, not a duplicate. A star that showed "on" and added a second
        // row would look like a bug in the feed rather than in the store.
        assertEquals(emptyList<String>(), store.symbols.first())
    }

    @Test
    fun `an empty store is an empty list, not a list holding one blank`() = runTest {
        assertEquals(emptyList<String>(), WatchlistStore(FakeDataStore()).symbols.first())
    }
}

/** Enough of DataStore to exercise the store without a file on disk. */
private class FakeDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())
    override val data = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
