package com.coinepro.app.alerts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * When a pass last read prices — run Τ2, B6.
 *
 * ### One number, and why it is not on the alert
 *
 * A pass reads every alert's symbols together, so «prices arrived» is a fact about the *pass*. A
 * copy of it on each alert row would be the same number written many times and able to disagree
 * with itself, and it would grow the stored format for a fact that does not vary between rows.
 *
 * What it is for is `AlertReach.uncheckedIds`: an alert created since this instant has never once
 * been compared against a market, however confidently the screen says «فعال». That is the state a
 * reader who armed an alert on a plane is in, and nothing used to say so.
 *
 * ### Written only when prices actually arrived
 *
 * Not at the start of a pass, and not when the price route failed. `AlertPassResult.Unavailable`
 * means nothing was decided and nothing was written — stamping it would mark every alert as
 * checked on precisely the passes that checked nothing, which would make the pill vanish exactly
 * when it is true.
 */
@Singleton
class AlertCheckStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AlertChecks {

    /** The last pass that read prices, or null for a reader whose passes have never succeeded. */
    val lastCheckedAt: Flow<Long?> = dataStore.data
        .map { preferences -> preferences[CHECKED_AT] }
        .distinctUntilChanged()

    override suspend fun markChecked(atEpochMillis: Long) {
        if (atEpochMillis <= 0L) return
        dataStore.edit { preferences ->
            // Never backwards. A phone whose clock was corrected would otherwise re-open the pill
            // on every alert made since, and the reader has no way to tell that from a real one.
            val current = preferences[CHECKED_AT] ?: 0L
            if (atEpochMillis > current) preferences[CHECKED_AT] = atEpochMillis
        }
    }

    private companion object {
        val CHECKED_AT = longPreferencesKey("local_alert_last_checked_at")
    }
}
