package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The reader's own Arena history: one row per day they played (run Ω4).
 *
 * ### Why this is on the phone and not on a server
 *
 * It has to be somewhere, and there is no endpoint — `docs/runs/RUN_OMEGA/BLOCKED.md` entry 3 names
 * the absence. But the local version is not a placeholder for a leaderboard: it is the *league of
 * one*, «you, over time», which is the only comparison that is honest with nobody to compare
 * against. A leaderboard of one reader is worse than no leaderboard — it says «you are first» to
 * somebody who has beaten nobody.
 *
 * When there is a server, this stays: a reader's own history is the thing they look at most and it
 * must survive being offline.
 *
 * ### Why the streak is computed rather than stored
 *
 * A stored counter is a second source of truth for something the rows already say, and it is the
 * one that goes wrong — a day skipped while the app was closed, a clock moved, a row written twice.
 * [streak] counts back from today over the rows, so it cannot disagree with them.
 *
 * A delimited string rather than a serialisation library, like every other store in this module: a
 * row is four numbers and a ticker, none of which can contain the separator.
 */
class ArenaStore(private val dataStore: DataStore<Preferences>) {

    /** Every day the reader has played, oldest first. */
    val results: Flow<List<ArenaResult>> = dataStore.data
        .map { preferences -> decode(preferences[RESULTS]) }
        .distinctUntilChanged()

    /**
     * Records a finished session.
     *
     * One row per day, and a second play of the same day **replaces** the first rather than adding
     * to it. That is the honest reading of a *daily* challenge: it is one challenge, and a reader
     * who plays it again is improving their answer to it, not answering a second question. Keeping
     * both would let somebody's history show three attempts at Tuesday.
     */
    suspend fun record(result: ArenaResult) {
        dataStore.edit { preferences ->
            val rows = decode(preferences[RESULTS]).filterNot { it.epochDay == result.epochDay }
            val next = (rows + result).sortedBy { it.epochDay }.takeLast(MAX_ROWS)
            preferences[RESULTS] = next.joinToString(ROW) { row ->
                listOf(
                    row.epochDay.toString(),
                    row.symbol,
                    row.total.toString(),
                    row.discipline.toString(),
                    row.profit.toString(),
                ).joinToString(FIELD)
            }
        }
    }

    /** Forgets everything. For the reader who asks, and for a test. */
    suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(RESULTS) }
    }

    private fun decode(raw: String?): List<ArenaResult> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(ROW).mapNotNull { row ->
            val parts = row.split(FIELD)
            if (parts.size != 5) return@mapNotNull null
            val day = parts[0].toLongOrNull() ?: return@mapNotNull null
            val total = parts[2].toIntOrNull() ?: return@mapNotNull null
            val discipline = parts[3].toIntOrNull() ?: return@mapNotNull null
            val profit = parts[4].toIntOrNull() ?: return@mapNotNull null
            ArenaResult(
                epochDay = day,
                symbol = parts[1],
                total = total,
                discipline = discipline,
                profit = profit,
            )
        }.sortedBy { it.epochDay }
    }

    private companion object {
        val RESULTS = stringPreferencesKey("arena_results")

        /** Two years of daily play. Past that the oldest row is not a thing anybody looks at. */
        const val MAX_ROWS = 730
        /**
         * The two separators, from the same ASCII block every other store here uses.
         *
         * A row is five fields of digits and a ticker — letters, digits and at most a slash —
         * so neither separator can appear inside one and no escaping is needed.
         */
        const val ROW = "\u001E"
        const val FIELD = "\u001D"
    }
}

/** One day's Arena result, as the reader's own history keeps it. */
data class ArenaResult(
    val epochDay: Long,
    val symbol: String,
    val total: Int,
    val discipline: Int,
    val profit: Int,
)

/**
 * How many days in a row, counting back from [today].
 *
 * ### Why today not counting does not break the streak
 *
 * A reader who has played every day for a fortnight and opens the app at nine in the morning has a
 * streak of fourteen, not zero. So the count starts at today *or* yesterday — whichever the newest
 * row is — and a gap of two days is what ends it. Anything stricter turns the streak into a clock
 * that runs out at midnight, which is a mechanic for making people anxious rather than a record of
 * what they did.
 */
fun List<ArenaResult>.streak(today: Long): Int {
    if (isEmpty()) return 0
    val days = mapTo(HashSet()) { it.epochDay }
    var day = when {
        today in days -> today
        today - 1 in days -> today - 1
        else -> return 0
    }
    var count = 0
    while (day in days) {
        count++
        day--
    }
    return count
}

/** The reader's best day, or null before they have had one. */
fun List<ArenaResult>.best(): ArenaResult? = maxByOrNull { it.total }
