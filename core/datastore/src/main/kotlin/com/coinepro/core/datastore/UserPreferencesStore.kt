package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferencesStore(
    private val dataStore: DataStore<Preferences>,
) {
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[NOTIFICATIONS_ENABLED] ?: true
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED] = enabled
        }
    }

    /**
     * Which palette to draw with. See [ThemeMode] for why this is stored at all.
     *
     * Absent means [ThemeMode.SYSTEM], so every install that predates this setting keeps the
     * behaviour it already had rather than being flipped to a fixed theme on upgrade.
     */
    val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        ThemeMode.fromId(preferences[THEME_MODE])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.id
        }
    }

    /**
     * Which colour a rise is drawn in. See [MarketColorScheme] for why this is not about taste.
     *
     * Device-wide like the theme, and for the same reason: it is how this reader reads a chart,
     * not a property of the account they happen to be signed into.
     */
    val marketColors: Flow<MarketColorScheme> = dataStore.data.map { preferences ->
        MarketColorScheme.fromId(preferences[MARKET_COLORS])
    }

    suspend fun setMarketColors(scheme: MarketColorScheme) {
        dataStore.edit { preferences ->
            preferences[MARKET_COLORS] = scheme.id
        }
    }

    /**
     * Whether the app asks for a fingerprint, a face or the phone's passcode when it opens.
     *
     * Off by default, and that is deliberate rather than lazy: a lock the reader did not ask for
     * is a lock they meet as an obstacle, and this app opens to guests with nothing behind it
     * worth locking until they sign in.
     *
     * Device-wide like the theme. It describes this phone — the one with the fingerprint on it —
     * not the account, and it must survive a sign-out: the next person to pick up the phone is
     * the reason it is on.
     */
    val appLockEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[APP_LOCK] ?: false
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[APP_LOCK] = enabled
        }
    }

    private companion object {
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MARKET_COLORS = stringPreferencesKey("market_colors")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
    }
}
