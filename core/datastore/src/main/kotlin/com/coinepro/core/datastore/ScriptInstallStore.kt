package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Which shared scripts **this device** has put on its chart (run Σ-FIX 6).
 *
 * ### What this is, and what it is deliberately not
 *
 * It is a set of public script ids that this reader added. It is *not* an install count. The
 * owner's review asked for «installs/★ counters» on a shared post, and half of that is a number
 * this app is in no position to know: how many people installed a script is a fact about everybody,
 * and there is no endpoint that holds it. A card that printed this device's own count under the
 * word «نصب» would be a public-looking number with a private value behind it — the kind of figure
 * D2 exists to forbid.
 *
 * So what the screen says is what this store can honestly answer: «شما این را اضافه کرده‌اید». The
 * public counter is named in `BLOCKED.md` with the endpoint it waits on, and the board's own like
 * — which *is* server-side and *is* everybody's — stays the social number on the post.
 *
 * Ids rather than sources: the id is what `ScriptDocument.idFor` derives from the code, so a script
 * edited after it was added is a different id and the mark does not follow it. That is the right
 * behaviour — what the reader has on their chart is then genuinely not the thing in the post.
 */
class ScriptInstallStore(private val dataStore: DataStore<Preferences>) {

    /** Public ids this device has added to a chart. */
    val installed: Flow<Set<String>> = dataStore.data.map { it[INSTALLED] ?: emptySet() }

    /** Records that [publicId] was added to a chart on this device. */
    suspend fun markInstalled(publicId: String) {
        if (publicId.isBlank()) return
        dataStore.edit { it[INSTALLED] = (it[INSTALLED] ?: emptySet()) + publicId }
    }

    /** Forgets [publicId] — the reader removed it from the chart. */
    suspend fun forget(publicId: String) {
        dataStore.edit { it[INSTALLED] = (it[INSTALLED] ?: emptySet()) - publicId }
    }

    private companion object {
        val INSTALLED = stringSetPreferencesKey("installed_shared_scripts")
    }
}
