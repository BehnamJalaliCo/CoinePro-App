package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The symbols the reader chose to keep an eye on.
 *
 * **Local, and deliberately so.** TradeYar does serve a watchlist, but behind its own device-link
 * flow — a second identity to establish before the first star can be placed. That is the wrong
 * price for this feature. A watchlist has to answer instantly, work with no signal, and survive a
 * server being down; a round trip per star turns the most-tapped control in a trading app into the
 * slowest one. Sync is asked for in `docs/REQUEST4_ACCOUNT_DELETION.md` and belongs on top of
 * this, not instead of it.
 *
 * Order is **insertion order, oldest first**, not alphabetical and not by price. The reader put
 * them in a sequence and the sequence is information; re-sorting a watchlist by anything the market
 * does means it rearranges itself while being read, which is the one thing a personal list must
 * never do.
 *
 * Stored as one delimited string rather than a set, because a `Set` preference has no order and
 * losing the order is losing the feature. The delimiter is a character no ticker contains.
 */
class WatchlistStore(private val dataStore: DataStore<Preferences>) {

    val symbols: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[SYMBOLS].orEmpty()
            .split(SEPARATOR)
            .filter(String::isNotBlank)
    }

    /**
     * Adds or removes, and returns nothing.
     *
     * One entry point rather than `add` and `remove`, because the caller is a star that is already
     * showing the current state: two methods would let a screen decide to add something already
     * present, and the duplicate would sit in the list looking like a bug in the feed.
     */
    suspend fun toggle(symbol: String) {
        val ticker = symbol.trim().uppercase()
        if (ticker.isEmpty()) return
        dataStore.edit { preferences ->
            val current = preferences[SYMBOLS].orEmpty()
                .split(SEPARATOR)
                .filter(String::isNotBlank)
            val next = if (ticker in current) current - ticker else current + ticker
            preferences[SYMBOLS] = next.joinToString(SEPARATOR)
        }
    }

    suspend fun clear() {
        dataStore.edit { it[SYMBOLS] = "" }
    }

    private companion object {
        val SYMBOLS = stringPreferencesKey("watchlist_symbols")

        /** A vertical bar: no exchange puts one in a ticker, and it survives a round trip as text. */
        const val SEPARATOR = "|"
    }
}
