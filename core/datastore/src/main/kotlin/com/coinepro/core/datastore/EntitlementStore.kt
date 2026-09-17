package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The last entitlement the server served, kept so the **next** launch can start from it (run Τ2, B2).
 *
 * ### Why it is stored rather than applied
 *
 * `FeatureFlags` is read from composition, and its own note is the reason this class exists: a flag
 * that moved under a reader would be a different feature. An entitlement that arrived mid-session
 * and took effect immediately would close a door somebody was standing in — the academy lesson they
 * were three minutes into, the layout they were about to save — and a wall that appears while the
 * screen is up reads as the app breaking, not as a subscription ending.
 *
 * So the wire writes *here*, and [applyAtStart] reads it before the first screen. The reader always
 * gets the entitlement the server served **last time they opened the app**, which is at most one
 * launch stale and is never a surprise. That is the ordinary remote-configuration contract and it
 * is the one every store on the phone already follows.
 *
 * ### Null is not false
 *
 * Absent means «the server has never answered», which is every install until the backend grows the
 * route — and until then [com.coinepro.core.common.FeatureFlags.ALL_UNLOCKED_DEFAULT] decides, which
 * is `true`. A missing answer must never read as a locked app: a reader whose phone was offline on
 * first launch, or who installed before the route existed, would open a product with every wall up
 * and no way to explain it.
 */
class EntitlementStore(private val dataStore: DataStore<Preferences>) {

    /** What the server last said, or null where it has never said anything. */
    val all: Flow<Boolean?> = dataStore.data.map { it[ALL] }

    /** Records the server's answer. Takes effect on the next launch, deliberately. */
    suspend fun serve(all: Boolean) {
        dataStore.edit { it[ALL] = all }
    }

    /** Reads the stored answer once, for the start-up apply. Null where nothing was ever served. */
    suspend fun current(): Boolean? = all.first()

    private companion object {
        val ALL = booleanPreferencesKey("entitlement_all")
    }
}
