package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The indicators a reader has starred, and the ones they switched on most recently.
 *
 * ### Why two lists
 *
 * The reference's indicator sheet opens on «Favourites» and «Recent» before any family, because
 * an eighty-row catalogue is scrolled once and then never again: after a week a reader switches
 * the same five on and off. The star is a choice they make; the recent list is one the app makes
 * for them, newest first and capped at [MAX_RECENT], so the second chip is never empty for
 * somebody who has never starred anything.
 *
 * ### Ids are plain strings
 *
 * The catalogue ids — `ema`, `rsi`, `volumeprofile_ind` — with the same rule every other store in
 * this module follows: `core:datastore` does not depend on `core:chart`, so it stores the key and
 * the picker resolves it. An id this build no longer knows is skipped on the way out.
 *
 * Delimited strings, like [IntervalFavouritesStore], for the same reason: a serialisation library
 * is not worth its weight in a preferences module. An id is letters, digits and underscores, so
 * the group separator can never appear in one.
 */
class IndicatorFavouritesStore(private val dataStore: DataStore<Preferences>) {

    /** The starred indicators, in the order they were starred. */
    fun favourites(): Flow<List<String>> = dataStore.data
        .map { preferences -> decode(preferences[FAVOURITES]) }
        .distinctUntilChanged()

    /** The most recently switched-on indicators, newest first. */
    fun recent(): Flow<List<String>> = dataStore.data
        .map { preferences -> decode(preferences[RECENT]) }
        .distinctUntilChanged()

    /** Stars or unstars one indicator. */
    suspend fun toggleFavourite(id: String) {
        val clean = usable(id) ?: return
        dataStore.edit { preferences ->
            val current = decode(preferences[FAVOURITES])
            val next = if (clean in current) current - clean else (current + clean).takeLast(MAX_FAVOURITES)
            write(preferences, FAVOURITES, next)
        }
    }

    /** Notes that [id] was just switched on: it moves to the head of the recent list. */
    suspend fun recordRecent(id: String) {
        val clean = usable(id) ?: return
        dataStore.edit { preferences ->
            val current = decode(preferences[RECENT])
            write(preferences, RECENT, (listOf(clean) + (current - clean)).take(MAX_RECENT))
        }
    }

    companion object {
        internal val FAVOURITES = stringPreferencesKey("chart_indicator_favourites")
        internal val RECENT = stringPreferencesKey("chart_indicator_recent")

        /** Eight is what the reference's «Recent» chip shows; more is a second catalogue. */
        const val MAX_RECENT = 8

        /** A bound on a runaway writer, not a rule anyone meets by hand. */
        const val MAX_FAVOURITES = 64

        private const val GROUP = "\u001D"
        private const val MAX_ID_LENGTH = 32

        private fun write(preferences: androidx.datastore.preferences.core.MutablePreferences, key: Preferences.Key<String>, ids: List<String>) {
            if (ids.isEmpty()) preferences.remove(key) else preferences[key] = ids.joinToString(GROUP)
        }

        private fun decode(stored: String?): List<String> =
            stored.orEmpty().split(GROUP).mapNotNull(::usable).distinct()

        /** One id, or null if it cannot be one: letters, digits and underscores, and short. */
        internal fun usable(id: String?): String? {
            val clean = id?.trim() ?: return null
            if (clean.isEmpty() || clean.length > MAX_ID_LENGTH) return null
            if (clean.any { !it.isLetterOrDigit() && it != '_' }) return null
            return clean
        }
    }
}
