package com.coinepro.core.watchlistsync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Enough of DataStore to exercise `WatchlistStore` without a file on disk.
 *
 * A real store is used rather than a fake of it, throughout these tests, because half of what this
 * module does happens *inside* `applyMerged` — a transform run within one preferences edit — and a
 * fake store would be a fake of exactly the transaction the merge depends on.
 */
internal class FakeDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
