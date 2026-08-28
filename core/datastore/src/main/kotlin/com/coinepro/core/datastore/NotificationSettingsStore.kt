package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.coinepro.core.notifications.NotificationCategory
import com.coinepro.core.notifications.NotificationSettings
import com.coinepro.core.notifications.QuietHours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The reader's notification choices, on this device.
 *
 * One key per category rather than one packed string, deliberately. A packed value has to be parsed
 * and re-written whole, so adding a category in a later release either loses everybody's settings
 * or needs a migration; a key per category means a new one simply reads as absent and falls back to
 * its own default. The cost is sixteen keys in a preferences file, which is nothing.
 *
 * A category the reader has never touched is **not stored**. That matters more than it looks:
 * `defaultOn` is the app's judgement about what is worth interrupting somebody for, and it can be
 * improved in a later release — but only for the people who never expressed a preference. Writing
 * every default on first read would freeze today's judgement onto every install for ever.
 */
class NotificationSettingsStore(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<NotificationSettings> = dataStore.data.map { preferences ->
        NotificationSettings(
            enabled = preferences[ENABLED] ?: true,
            mutedUntilEpochMillis = preferences[MUTED_UNTIL]?.takeIf { it > 0L },
            categories = NotificationCategory.entries.associateWith { category ->
                preferences[key(category)] ?: category.defaultOn
            },
            quietHours = QuietHours(
                enabled = preferences[QUIET_ENABLED] ?: false,
                fromMinuteOfDay = preferences[QUIET_FROM] ?: (23 * 60),
                toMinuteOfDay = preferences[QUIET_TO] ?: (7 * 60),
            ),
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED] = enabled }
    }

    suspend fun setCategory(category: NotificationCategory, on: Boolean) {
        // Security has no switch. Refusing here as well as in the UI means a future screen cannot
        // quietly acquire one by calling this.
        if (!category.silenceable) return
        dataStore.edit { it[key(category)] = on }
    }

    /** Silences everything until [untilEpochMillis], or clears the pause when null. */
    suspend fun muteUntil(untilEpochMillis: Long?) {
        dataStore.edit { preferences ->
            if (untilEpochMillis == null) preferences.remove(MUTED_UNTIL) else preferences[MUTED_UNTIL] = untilEpochMillis
        }
    }

    suspend fun setQuietHours(enabled: Boolean, fromMinuteOfDay: Int, toMinuteOfDay: Int) {
        dataStore.edit { preferences ->
            preferences[QUIET_ENABLED] = enabled
            preferences[QUIET_FROM] = fromMinuteOfDay.coerceIn(0, 24 * 60 - 1)
            preferences[QUIET_TO] = toMinuteOfDay.coerceIn(0, 24 * 60 - 1)
        }
    }

    /** Back to the app's own defaults — every stored choice forgotten, not overwritten. */
    suspend fun reset() {
        dataStore.edit { preferences ->
            preferences.remove(ENABLED)
            preferences.remove(MUTED_UNTIL)
            preferences.remove(QUIET_ENABLED)
            preferences.remove(QUIET_FROM)
            preferences.remove(QUIET_TO)
            NotificationCategory.entries.forEach { preferences.remove(key(it)) }
        }
    }

    private fun key(category: NotificationCategory) = booleanPreferencesKey("notify_" + category.id)

    private companion object {
        val ENABLED = booleanPreferencesKey("notify_enabled")
        val MUTED_UNTIL = longPreferencesKey("notify_muted_until")
        val QUIET_ENABLED = booleanPreferencesKey("notify_quiet_enabled")
        val QUIET_FROM = intPreferencesKey("notify_quiet_from")
        val QUIET_TO = intPreferencesKey("notify_quiet_to")
    }
}
