package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * How far a drawing travels: this chart, this layout, or everywhere.
 *
 * ### Why there are three and not a switch
 *
 * A trend line means one thing on one instrument, which is why [ChartDrawingStore] keys drawings by
 * symbol. But a reader looking at the same symbol in a four-pane layout — the daily, the four-hour,
 * the hourly and the fifteen — draws a level once and then watches it fail to appear in the other
 * three panes, which is not a subtle bug to the person it happens to; it looks like the app lost
 * the line. That is [LAYOUT].
 *
 * [GLOBAL] is a different request and a much older one. Sync of drawings **across layouts** —
 * including layouts made after the line was drawn — sits in the top twenty-five most-upvoted
 * requests of all time on TradingView's own subreddit and they have never shipped it. Somebody who
 * keeps one layout for scalping and another for swing has to redraw the same weekly support in both
 * and then keep them in step by hand, forever. There is no technical reason for that; it is simply
 * a feature nobody built. It is written down here so that the next person to read this file knows
 * why the third value exists and does not quietly remove it as redundant.
 *
 * A boolean would collapse [LAYOUT] and [GLOBAL] into one another, and they are genuinely different
 * intentions: "keep my panes in step" is about one reading session, "keep my levels everywhere" is
 * about a body of work.
 */
enum class DrawingSyncMode(
    /** Stable key for storage. Never localise, never reuse for a different meaning. */
    val id: String,
) {
    /**
     * A drawing belongs to the chart it was drawn on. The default, and what the app does today.
     *
     * It stays the default for the reason a migration always has to: an update that suddenly
     * copies a year of drawings into every layout a reader owns is indistinguishable from a bug,
     * and there is no undo for it.
     */
    NONE("none"),

    /** Every pane in the current layout showing the same symbol gets the drawing. */
    LAYOUT("layout"),

    /** Every layout on that symbol gets it, including layouts created afterwards. */
    GLOBAL("global");

    companion object {
        /**
         * Reads a stored id back, falling to [NONE] for anything unrecognised.
         *
         * [NONE] rather than a guess, because an id this build does not know is either a downgrade
         * from a build with a fourth mode or a corrupt row, and the safe answer to both is the mode
         * that copies nothing anywhere. Widening a reader's drawings on the strength of a string we
         * cannot read is the one failure here that cannot be taken back.
         */
        fun fromId(id: String?): DrawingSyncMode = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/**
 * Where the drawing-sync setting lives between sessions.
 *
 * ### One row, so no cap
 *
 * Every other store in this package documents how many rows it will keep, because every other one
 * holds a list that a runaway caller could grow without bound. This one holds a single value from a
 * closed set of three, so there is nothing to cap and no eviction to reason about — said out loud
 * rather than left as an omission, so the next reader does not go looking for the missing constant.
 *
 * ### Device-wide, not per symbol
 *
 * Unlike [ChartDrawingStore] and [SymbolChartStateStore], this is not keyed by anything. It is not
 * a fact about an instrument; it is how this reader works — whether their marks are notes on one
 * chart or levels they expect to meet again everywhere. A reader who wants it per symbol wants
 * something else, and would find a setting that silently applied to only the symbol they happened
 * to be on when they set it much stranger than one that applies to all of them.
 *
 * ### Reading never throws
 *
 * The stored value is [DrawingSyncMode.id]. An absent entry, a blank one and one holding an id this
 * build has never heard of all read as [DrawingSyncMode.NONE] — see [DrawingSyncMode.fromId] for
 * why that particular fallback and not another.
 */
class DrawingSyncStore(private val dataStore: DataStore<Preferences>) {

    /**
     * How far a drawing should travel.
     *
     * Distinct-until-changed because the chart collects this for the life of the screen while
     * unrelated preferences are written next to it, and re-running a sync pass because somebody
     * starred a symbol would be a visible stutter.
     */
    fun mode(): Flow<DrawingSyncMode> = dataStore.data
        .map { preferences -> DrawingSyncMode.fromId(preferences[MODE]) }
        .distinctUntilChanged()

    /**
     * Records the reader's choice.
     *
     * [DrawingSyncMode.NONE] is written rather than removed. The entry's absence and an explicit
     * `none` mean the same thing to [mode] today, but writing the choice means a later change to
     * what the app defaults to cannot silently reach a reader who has already said they want their
     * drawings kept to one chart.
     */
    suspend fun setMode(mode: DrawingSyncMode) {
        dataStore.edit { preferences -> preferences[MODE] = mode.id }
    }

    private companion object {
        val MODE = stringPreferencesKey("chart_drawing_sync_mode")
    }
}
