package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.coinepro.core.papertrade.PaperLedgerStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The paper book, in one preference.
 *
 * One string rather than a table, and that is the shape the book was written for: the format and
 * its tolerance are `PaperBookCodec`'s, so everything that could lose a reader's account is tested
 * inside `core:papertrade` and this class has nothing left to get wrong.
 */
class PaperLedgerPrefStore(private val dataStore: DataStore<Preferences>) : PaperLedgerStore {
    override val text: Flow<String?> = dataStore.data.map { it[BOOK] }

    override suspend fun save(text: String) {
        dataStore.edit { it[BOOK] = text }
    }

    private companion object {
        val BOOK = stringPreferencesKey("paper_trade_book")
    }
}
