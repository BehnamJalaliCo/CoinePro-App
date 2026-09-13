package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * When the reader was last here, and what they had already been told about (run Σ, S5).
 *
 * ### Two timestamps, not one
 *
 * [lastVisit] is when Home was last opened, and it is what «since your last visit» measures from.
 * [lastSeen] is when the reader last *read* that card — which is a different moment, because a
 * reader who opens Home, reads the card and comes back an hour later must not be shown the same
 * three movers as news.
 *
 * Collapsing them into one was the first version, and it had exactly that fault: the card
 * reappeared every time Home did, which is the behaviour that teaches a reader to stop looking at
 * the top of the screen. One is the clock; the other is the acknowledgement.
 *
 * Zero means never, and `ReturnLoop.sinceLastVisit` treats it as a first launch — no card at all,
 * which is right: somebody who has just installed the app has been away from nothing.
 */
class LastVisitStore(private val dataStore: DataStore<Preferences>) {

    val lastVisit: Flow<Long> = dataStore.data.map { it[LAST_VISIT] ?: 0L }

    val lastSeen: Flow<Long> = dataStore.data.map { it[LAST_SEEN] ?: 0L }

    /**
     * Records that Home was opened at [epochMillis].
     *
     * Written on the way *out* rather than on the way in — see `CoineProApp` — because a visit
     * stamped on arrival would make the card measure from the moment it was drawn, which is a
     * window of zero.
     */
    suspend fun visited(epochMillis: Long) {
        dataStore.edit { it[LAST_VISIT] = epochMillis }
    }

    /** Records that the reader has seen what happened up to [epochMillis]. */
    suspend fun seen(epochMillis: Long) {
        dataStore.edit { it[LAST_SEEN] = epochMillis }
    }

    private companion object {
        val LAST_VISIT = longPreferencesKey("last_visit_epoch_millis")
        val LAST_SEEN = longPreferencesKey("last_seen_epoch_millis")
    }
}
