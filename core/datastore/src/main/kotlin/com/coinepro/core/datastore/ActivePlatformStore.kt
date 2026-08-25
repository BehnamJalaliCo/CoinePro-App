package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.coinepro.core.model.MarketPlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Reads the active platform once, for code that needs the value rather than the stream.
 *
 * A narrow seam on purpose: background work needs one answer at the moment it runs, and depending
 * on the whole store would drag a DataStore — and a file on disk — into every test of that work.
 */
fun interface ActivePlatformSelector {
    suspend fun current(): MarketPlatform
}

/**
 * Which platform the app is currently showing.
 *
 * CoinePro-FX and TradeYar are separate systems with separate accounts, and the product's rule is
 * that exactly one of them is on screen at a time. That is not a display preference — it decides
 * which credentials every call carries and which feed every quote comes from — so it is stored
 * rather than recomputed, and a reader who left the app on crypto comes back to crypto.
 *
 * [available] is what the build was actually configured for. A platform with no base URL must not
 * be offerable: switching to it would produce a screen that can never load.
 */
class ActivePlatformStore(
    private val dataStore: DataStore<Preferences>,
    val available: List<MarketPlatform>,
    private val fallback: MarketPlatform = available.first(),
) {
    init {
        require(available.isNotEmpty()) { "At least one platform must be configured." }
        require(fallback in available) { "The fallback platform must be one of the available ones." }
    }

    /**
     * A stored value naming a platform this build no longer offers resolves to [fallback] rather
     * than failing: a build that drops a platform must still open for someone who was last on it.
     */
    val active: Flow<MarketPlatform> = dataStore.data.map { preferences ->
        preferences[ACTIVE_PLATFORM]
            ?.let(MarketPlatform::fromId)
            ?.takeIf { it in available }
            ?: fallback
    }

    suspend fun setActive(platform: MarketPlatform) {
        require(platform in available) { "Platform ${platform.id} is not configured in this build." }
        dataStore.edit { preferences -> preferences[ACTIVE_PLATFORM] = platform.id }
    }

    fun selector(): ActivePlatformSelector = ActivePlatformSelector { active.first() }

    private companion object {
        val ACTIVE_PLATFORM = stringPreferencesKey("active_platform")
    }
}
