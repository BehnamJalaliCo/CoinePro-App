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

    /**
     * The bottom-bar destination the reader was last on, or null on a first launch.
     *
     * ### Why the app remembers this at all
     *
     * Because the alternative is a fixed opening screen, and there is no fixed screen that is
     * right for everybody. Somebody who lives in their watchlist and somebody who opens the app to
     * read the board are both being shown the same page every morning, and one of them is always
     * paying two taps for it. A terminal opens where you left it.
     *
     * ### A raw route, deliberately
     *
     * The value stored is the route string and not an enum ordinal. An ordinal is a position in a
     * list that this app reorders — the bar has been five, then six, and is five again — so an
     * upgrade would silently move a reader to a different tab. A route is identity: one that no
     * longer exists reads back as "no preference", which is exactly the right answer.
     *
     * The shell is what decides whether a stored route is still a root; this store does not know
     * what the bar holds and must not learn.
     */
    val lastRootRoute: Flow<String?> = dataStore.data.map { preferences ->
        preferences[LAST_ROOT]?.takeIf { it.isNotBlank() }
    }

    suspend fun setLastRootRoute(route: String) {
        val clean = route.trim()
        if (clean.isEmpty()) return
        dataStore.edit { preferences ->
            preferences[LAST_ROOT] = clean
        }
    }

    private companion object {
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val LAST_ROOT = stringPreferencesKey("last_root_route")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MARKET_COLORS = stringPreferencesKey("market_colors")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
    }
}
