package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The markets a reader opened from search, newest first (F3).
 *
 * The search screen's own documentation has described «the recent list when the field is empty»
 * since it was written, and there was no such list: an empty field showed the browse ranking, which
 * is the same list the markets tab shows one tap away. A reader who searches `XAU` every morning
 * had to type it every morning.
 *
 * ### Tickers, not queries
 *
 * What is remembered is the **market that was opened**, not the text that found it. Three reasons,
 * and the third is the one that decides it:
 *
 * * A query is somebody's half-typed guess. «bitc» found Bitcoin and is not worth offering back.
 * * A ticker is a row — logo, name, price — and a query is a string, so a list of tickers is the
 *   same row the results below it use rather than a second kind of thing on one screen.
 * * A stored query is stored text a reader typed. A stored ticker is a market they visited. On a
 *   phone that may be shared, and in a product whose subject is money, the second is the smaller
 *   thing to be keeping.
 *
 * Delimited strings, like every other list in this module: a serialisation library is not worth its
 * weight in a preferences file, and a ticker is letters and digits so the separator cannot appear
 * inside one.
 */
class RecentSearchStore(private val dataStore: DataStore<Preferences>) {

    /** The markets opened from search, newest first, at most [MAX_RECENT]. */
    fun recent(): Flow<List<String>> = dataStore.data
        .map { preferences -> decode(preferences[RECENT]) }
        .distinctUntilChanged()

    /** Notes that [symbol] was just opened from search: it moves to the head of the list. */
    suspend fun record(symbol: String) {
        val clean = usable(symbol) ?: return
        dataStore.edit { preferences ->
            val current = decode(preferences[RECENT])
            write(preferences, (listOf(clean) + (current - clean)).take(MAX_RECENT))
        }
    }

    /** Forgets one market — the × on its chip. */
    suspend fun forget(symbol: String) {
        val clean = usable(symbol) ?: return
        dataStore.edit { preferences -> write(preferences, decode(preferences[RECENT]) - clean) }
    }

    /** Forgets all of them. */
    suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(RECENT) }
    }

    companion object {
        internal val RECENT = stringPreferencesKey("market_search_recent")

        /**
         * Eight, which is a screenful of chips and about a week of one reader's habits.
         *
         * Longer is a second catalogue on a screen whose whole argument is that it is one column of
         * rows and nothing else.
         */
        const val MAX_RECENT = 8

        private const val GROUP = ""
        private const val MAX_TICKER_LENGTH = 24

        private fun write(preferences: MutablePreferences, symbols: List<String>) {
            if (symbols.isEmpty()) preferences.remove(RECENT) else preferences[RECENT] = symbols.joinToString(GROUP)
        }

        private fun decode(stored: String?): List<String> =
            stored.orEmpty().split(GROUP).mapNotNull(::usable).distinct()

        /** One ticker, or null if it cannot be one: letters and digits, upper-cased, and short. */
        internal fun usable(symbol: String?): String? {
            val clean = symbol?.trim()?.uppercase() ?: return null
            if (clean.isEmpty() || clean.length > MAX_TICKER_LENGTH) return null
            if (clean.any { !it.isLetterOrDigit() }) return null
            return clean
        }
    }
}
