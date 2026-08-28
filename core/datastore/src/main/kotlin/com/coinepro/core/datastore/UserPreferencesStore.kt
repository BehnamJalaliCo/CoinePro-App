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

    private companion object {
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
