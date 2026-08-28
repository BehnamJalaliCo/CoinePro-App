package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.coinepro.core.notifications.AlertRepeat
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Price alerts that belong to this phone rather than to an account.
 *
 * One row per alert, fields separated by characters no field can contain, all of it in one
 * preference. It is a small, closed format for at most [LocalPriceAlert.MAX_ALERTS] rows and it has
 * one property that matters more than elegance: **decoding cannot throw**. A malformed row — from a
 * half-written file, or a format a later release changed — is dropped and the rest are kept. An
 * alert screen that crashed on its own stored value would be unreachable without clearing the app's
 * data, and the reader would lose every other alert to fix one.
 */
class LocalAlertStore(private val dataStore: DataStore<Preferences>) {

    val alerts: Flow<List<LocalPriceAlert>> = dataStore.data.map { preferences ->
        decode(preferences[ALERTS])
    }

    suspend fun current(): List<LocalPriceAlert> = alerts.first()

    /**
     * Adds one, and returns whether there was room.
     *
     * False rather than silently dropping the oldest: the reader chose every one of these, and an
     * app that quietly discards a choice to make room for another is worse than one that says it
     * is full.
     */
    suspend fun add(alert: LocalPriceAlert): Boolean {
        var added = false
        dataStore.edit { preferences ->
            val existing = decode(preferences[ALERTS])
            if (existing.size >= LocalPriceAlert.MAX_ALERTS) return@edit
            preferences[ALERTS] = encode(existing + alert)
            added = true
        }
        return added
    }

    suspend fun remove(id: String) {
        dataStore.edit { preferences ->
            preferences[ALERTS] = encode(decode(preferences[ALERTS]).filterNot { it.id == id })
        }
    }

    suspend fun setActive(id: String, active: Boolean) {
        update(id) { it.copy(active = active) }
    }

    /**
     * Writes back the alerts that fired.
     *
     * Takes the whole list rather than one id because the evaluator finds them in a batch, and
     * writing them one at a time would be one disk write per alert on a tick that moved several.
     */
    suspend fun markFired(fired: List<LocalPriceAlert>, atEpochMillis: Long) {
        if (fired.isEmpty()) return
        val ids = fired.mapTo(mutableSetOf(), LocalPriceAlert::id)
        dataStore.edit { preferences ->
            preferences[ALERTS] = encode(
                decode(preferences[ALERTS]).map { alert ->
                    if (alert.id in ids) alert.fired(atEpochMillis) else alert
                },
            )
        }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(ALERTS) }
    }

    private suspend fun update(id: String, transform: (LocalPriceAlert) -> LocalPriceAlert) {
        dataStore.edit { preferences ->
            preferences[ALERTS] = encode(
                decode(preferences[ALERTS]).map { if (it.id == id) transform(it) else it },
            )
        }
    }

    internal companion object {
        val ALERTS = stringPreferencesKey("local_price_alerts")

        /**
         * A semicolon between rows and a vertical bar between fields.
         *
         * Neither can appear in a ticker, in a number Kotlin prints, or in the ids this app
         * generates — which are hexadecimal. Both are printable, which matters when the next person
         * to debug this reads the value out of a preferences file by eye.
         */
        private const val ROW = ";"
        private const val FIELD = "|"

        fun encode(alerts: List<LocalPriceAlert>): String = alerts.joinToString(ROW) { alert ->
            listOf(
                alert.id,
                alert.symbol,
                alert.condition.id,
                alert.value.toString(),
                alert.repeat.id,
                alert.referencePrice?.toString().orEmpty(),
                if (alert.active) "1" else "0",
                alert.createdAtEpochMillis.toString(),
                alert.lastFiredAtEpochMillis?.toString().orEmpty(),
            ).joinToString(FIELD)
        }

        fun decode(raw: String?): List<LocalPriceAlert> = raw
            .orEmpty()
            .split(ROW)
            .filter(String::isNotBlank)
            .mapNotNull { row ->
                val parts = row.split(FIELD)
                if (parts.size < 9) return@mapNotNull null
                val id = parts[0].takeIf(String::isNotBlank) ?: return@mapNotNull null
                val symbol = parts[1].takeIf(String::isNotBlank) ?: return@mapNotNull null
                val condition = LocalAlertCondition.fromId(parts[2]) ?: return@mapNotNull null
                val value = parts[3].toDoubleOrNull() ?: return@mapNotNull null
                LocalPriceAlert(
                    id = id,
                    symbol = symbol,
                    condition = condition,
                    value = value,
                    repeat = AlertRepeat.fromId(parts[4]) ?: AlertRepeat.ONCE,
                    referencePrice = parts[5].toDoubleOrNull(),
                    active = parts[6] == "1",
                    createdAtEpochMillis = parts[7].toLongOrNull() ?: 0L,
                    lastFiredAtEpochMillis = parts[8].toLongOrNull(),
                )
            }
    }
}
