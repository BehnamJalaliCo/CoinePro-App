package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The reader's duel record, and which day they last answered — run Τ2, C3.
 *
 * ### Three integers and a day, not a list of rounds
 *
 * `ArenaStore` keeps a row per day because the Arena's screen shows a history: the streak, the
 * league of one, what you scored last Tuesday. A duel has nothing to show per round — «you said up
 * on the 14th» is not a thing anybody looks back at — so what is kept is the record itself, which
 * is the only thing the screen prints. Three counters cannot go out of step with a list that does
 * not exist.
 *
 * ### The day is what stops a reroll
 *
 * `Duel.roundFor` is deterministic in the date, so the question does not change while the app is
 * closed. What it cannot do by itself is stop a reader **answering the same day twice** — and a
 * record that could be padded by tapping «up» five times on one chart is not a record. [answeredDay]
 * is the guard, and it is the reader's own local day, so it rolls over when their day does and not
 * at midnight UTC.
 */
class DuelStore(private val dataStore: DataStore<Preferences>) {

    val tally: Flow<DuelTally> = dataStore.data
        .map { preferences ->
            DuelTally(
                played = preferences[PLAYED] ?: 0,
                right = preferences[RIGHT] ?: 0,
                tooClose = preferences[TOO_CLOSE] ?: 0,
            )
        }
        .distinctUntilChanged()

    /** The local day the reader last answered on, or null for somebody who never has. */
    val answeredDay: Flow<Long?> = dataStore.data
        .map { preferences -> preferences[ANSWERED_DAY] }
        .distinctUntilChanged()

    /**
     * Writes one answered round, and refuses a second on the same day.
     *
     * Returns whether it was recorded. False means the reader has already answered today and the
     * screen should say so rather than pretending it counted — a silent refusal would look exactly
     * like a record that does not update.
     *
     * The read and the write are inside one `edit`, which is what makes the guard real: two taps
     * arriving together would otherwise both read «not answered» and both write.
     */
    suspend fun answer(right: Boolean, tooClose: Boolean, localDay: Long): Boolean {
        var recorded = false
        dataStore.edit { preferences ->
            if (preferences[ANSWERED_DAY] == localDay) return@edit
            preferences[PLAYED] = (preferences[PLAYED] ?: 0) + 1
            if (right) preferences[RIGHT] = (preferences[RIGHT] ?: 0) + 1
            if (tooClose) preferences[TOO_CLOSE] = (preferences[TOO_CLOSE] ?: 0) + 1
            preferences[ANSWERED_DAY] = localDay
            recorded = true
        }
        return recorded
    }

    /** Forgets the record. For the reader who asks, and for a test. */
    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(PLAYED)
            preferences.remove(RIGHT)
            preferences.remove(TOO_CLOSE)
            preferences.remove(ANSWERED_DAY)
        }
    }

    private companion object {
        val PLAYED = intPreferencesKey("duel_played")
        val RIGHT = intPreferencesKey("duel_right")
        val TOO_CLOSE = intPreferencesKey("duel_too_close")
        val ANSWERED_DAY = longPreferencesKey("duel_answered_day")
    }
}

/**
 * The three counters, as this module keeps them.
 *
 * Its own type rather than `core:chart`'s `DuelRecord`, for the reason `ArenaResult` is its own
 * type beside `ArenaScore`: this module does not depend on the chart engine and must not start —
 * a preferences store that dragged in a rendering module would be on every gateway's classpath.
 * The caller maps three integers at its own edge, and the *meaning* of them — in particular the
 * floor below which there is no hit rate — stays in `Duel`, where it is tested.
 */
data class DuelTally(
    val played: Int = 0,
    val right: Int = 0,
    val tooClose: Int = 0,
)
