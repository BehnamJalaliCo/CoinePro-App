package com.coinepro.app.alerts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.coinepro.core.notifications.LocalPriceAlert
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Per-symbol firing state, in the app's ordinary preferences file.
 *
 * ### Why this is not in `core:datastore` beside the alerts themselves
 *
 * Because it is not part of the alert. `LocalPriceAlert` is what the reader made and what the
 * alerts screen shows; this is bookkeeping the evaluator needs in order to keep the promise that
 * row makes — that each watchlist member fires on its own, and that a bar-policy alert is not
 * quietly switched off by a repeat policy it never used. Putting it in the alert row would mean a
 * stored format that grows with the size of a watchlist, and a screen that has to ignore most of
 * what it reads. [AlertFireStates] explains what goes wrong without it.
 *
 * ### The encoding, and the cap
 *
 * The same delimited-string scheme every other preference in this app uses, and for the same
 * reason: the alternative is a serialisation library in a preferences file. Control characters as
 * separators, so no ticker and no alert id — which are hexadecimal — can contain one. Decoding
 * cannot throw; a half-written row is dropped and the rest are kept, because the cost of a lost
 * stamp is one duplicate notification and the cost of an exception here is an alert schedule that
 * never runs again.
 *
 * Bounded at [LocalPriceAlert.MAX_ALERTS] rows and [MAX_SYMBOLS_PER_ALERT] stamps inside a row,
 * evicting the least recently stamped. Unbounded it would keep a row for every alert the reader has
 * ever deleted and every symbol that has ever been in a watchlist, in a file that is read whole on
 * every launch.
 */
@Singleton
class AlertFireStateStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AlertFireStates {

    override suspend fun current(): Map<String, AlertFireState> =
        decode(dataStore.data.first()[STATES]).associateBy(AlertFireState::alertId)

    override suspend fun write(states: List<AlertFireState>) {
        if (states.isEmpty()) return
        val replacing = states.associateBy(AlertFireState::alertId)
        dataStore.edit { preferences ->
            val kept = decode(preferences[STATES]).filterNot { it.alertId in replacing }
            preferences[STATES] = encode(kept + states)
        }
    }

    /**
     * Forgets one alert's state.
     *
     * For an alert the reader deleted, or re-armed. Kept separate from the audit log's own
     * `removeFor`, which is the reader's history and outlives the alert on purpose; this is only
     * the evaluator's bookkeeping and there is nothing to be learned from it once the alert is gone.
     */
    suspend fun forget(alertId: String) {
        dataStore.edit { preferences ->
            preferences[STATES] = encode(decode(preferences[STATES]).filterNot { it.alertId == alertId })
        }
    }

    internal companion object {
        val STATES = stringPreferencesKey("local_alert_fire_state")

        /** Between two alerts' rows. ASCII group separator. */
        private const val GROUP = "\u001D"

        /** Between one row's fields. ASCII record separator. */
        private const val RECORD = "\u001E"

        /** Between a symbol and its stamp. ASCII unit separator. */
        private const val UNIT = "\u001F"

        /** Between two symbol stamps. No ticker contains one. */
        private const val PAIR = ","

        /**
         * How many symbols one alert keeps a stamp for.
         *
         * A watchlist alert stamps one symbol per member, and a reader's list is nowhere near this
         * long. The cap is here so that a list that somehow is cannot turn a preference read on
         * every launch into something measurable.
         */
        const val MAX_SYMBOLS_PER_ALERT = 200

        /** Every field this format writes. A shorter row is half-written and is dropped. */
        private const val FIELDS = 3

        /** The most recent stamp in a row, for deciding which row is evicted first. */
        private fun freshness(state: AlertFireState): Long =
            maxOf(state.expiredRecordedAt ?: 0L, state.lastFiredBySymbol.values.maxOrNull() ?: 0L)

        fun encode(states: List<AlertFireState>): String = states
            .sortedByDescending(::freshness)
            .take(LocalPriceAlert.MAX_ALERTS)
            .joinToString(GROUP) { state ->
                val stamps = state.lastFiredBySymbol.entries
                    .sortedByDescending { it.value }
                    .take(MAX_SYMBOLS_PER_ALERT)
                    .joinToString(PAIR) { (symbol, at) -> symbol + UNIT + at }
                listOf(state.alertId, state.expiredRecordedAt?.toString().orEmpty(), stamps)
                    .joinToString(RECORD)
            }

        fun decode(raw: String?): List<AlertFireState> = raw
            .orEmpty()
            .split(GROUP)
            .filter(String::isNotBlank)
            .mapNotNull { row ->
                val parts = row.split(RECORD)
                if (parts.size < FIELDS) return@mapNotNull null
                val alertId = parts[0].takeIf(String::isNotBlank) ?: return@mapNotNull null
                AlertFireState(
                    alertId = alertId,
                    lastFiredBySymbol = parts[2]
                        .split(PAIR)
                        .filter(String::isNotBlank)
                        .mapNotNull { pair ->
                            val symbol = pair.substringBefore(UNIT).takeIf(String::isNotBlank)
                                ?: return@mapNotNull null
                            val at = pair.substringAfter(UNIT, "").toLongOrNull() ?: return@mapNotNull null
                            symbol to at
                        }
                        .toMap(),
                    expiredRecordedAt = parts[1].toLongOrNull(),
                )
            }
    }
}
