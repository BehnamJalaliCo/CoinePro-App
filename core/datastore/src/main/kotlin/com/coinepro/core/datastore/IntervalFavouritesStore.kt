package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Which intervals the reader wants on the chart's interval bar, and which ones they want gone.
 *
 * ### Why this exists
 *
 * The bar above the chart is a hard-coded row of six — one minute, five, fifteen, one hour, four
 * hours, one day — chosen once, for everybody. It is a reasonable six and it is the wrong six for
 * anybody who does not trade the way it assumes: a swing reader who lives on the daily and the
 * weekly reaches past the whole bar every single time, and a scalper who wants the three-minute
 * cannot put it there at all. The set is not a hard question, it was simply never asked; every
 * desktop terminal lets the reader pin their own. This store is where their answer goes.
 *
 * ### Every interval here is a plain String
 *
 * The same rule [SymbolChartState] gives and for the same reason: `core:datastore` must not depend
 * on `core:marketdata` or `core:chart`, and it does not. What is stored is `ChartInterval.wire` —
 * `M15`, `H4`, `MN1` for one of the fifteen presets, and a bare minute count like `205` for an
 * interval the reader typed. Resolving one back to an interval is one function on the far side of
 * the boundary, where both types are already on the classpath. A wire this build no longer knows
 * stays on disk and is simply never resolved, which is exactly right for a downgrade or a rename.
 *
 * That a wire is **letters and digits only** is a property of both spellings, and this file leans
 * on it: it is what makes [EMPTY_SELECTION] a token no interval can collide with, and what
 * [usable] enforces on the way in.
 *
 * ### Stored-empty and never-stored both mean "the default". Explicitly-empty does not.
 *
 * This is the whole risk in this file and it is worth being blunt about. Three states have to be
 * told apart:
 *
 * * **Nothing written** — a fresh install, or one that predates this setting. The reader never
 *   expressed an opinion, so the bar shows [DEFAULT_FAVOURITES].
 * * **A blank or unreadable string written** — a truncated write, or a row from a build whose
 *   format this one cannot read. Also no expressed opinion, so also the default. Falling back to
 *   *nothing* here would empty the interval bar over a storage accident, and a chart with no way
 *   to change timeframe is a broken chart.
 * * **[EMPTY_SELECTION] written** — the reader unstarred every interval, deliberately, one at a
 *   time. That is an opinion and it is honoured: the bar comes back empty, and the picker sheet is
 *   how they get an interval back. Storing it as an empty string instead would make it
 *   indistinguishable from the accident above, and the reader would find their six back tomorrow
 *   with no explanation for either.
 *
 * [reset] is the way back to the default from any of them, because it removes the entry rather
 * than writing the default into it — writing the default would freeze it, and changing the app's
 * six later would then reach nobody who had ever opened this screen.
 *
 * ### Starring and hiding are two settings, and they must not contradict each other
 *
 * [favourites] is what sits on the bar. [hidden] is what the reader has struck out of the full
 * picker sheet behind it — fifteen presets is more than most people ever want to scroll past. An
 * interval that is both starred and hidden is a contradiction the reader can neither see nor fix,
 * so neither setter lets one arise: [hide] unstars, [star] unhides. [unhide] deliberately does
 * **not** re-star, because nothing on disk remembers whether it was starred before it was hidden,
 * and guessing would put a chip back on the bar that nobody asked for.
 *
 * ### The encoding
 *
 * The delimited-string scheme [ChartDrawingStore] and [SymbolChartStateStore] use, for the same
 * reason: the alternative is a serialisation library in a preferences module. One separator is
 * enough here — ASCII's group separator between wires — because a wire has no fields. Decoding
 * never throws: a wire that is not letters and digits, or is too long to be one, is dropped on its
 * own and every wire around it is kept.
 */
class IntervalFavouritesStore(private val dataStore: DataStore<Preferences>) {

    /**
     * The intervals on the bar, in the order they should appear.
     *
     * Insertion order, oldest first, and [DEFAULT_FAVOURITES]' own order when nothing has been
     * stored — a bar that rearranges itself between launches is a bar the reader has to re-read
     * every time. Distinct-until-changed because an open chart collects this while unrelated
     * preferences are being written next to it.
     */
    fun favourites(): Flow<List<String>> = dataStore.data
        .map { preferences -> readFavourites(preferences[FAVOURITES]) }
        .distinctUntilChanged()

    /**
     * Pins one interval to the end of the bar, and un-hides it if it was struck out.
     *
     * Starring for the first time materialises [DEFAULT_FAVOURITES] and appends to them rather
     * than writing a list of one. A reader who adds the weekly is adding it to the six they can
     * see; dropping the other five would look like the app had eaten them.
     */
    suspend fun star(wire: String) {
        val clean = usable(wire) ?: return
        dataStore.edit { preferences ->
            val current = readFavourites(preferences[FAVOURITES])
            if (clean !in current) {
                preferences[FAVOURITES] = writeFavourites(current + clean)
            }
            writeHidden(preferences, decodeSet(preferences[HIDDEN]) - clean)
        }
    }

    /**
     * Takes one interval off the bar and leaves every other one in place.
     *
     * Unstarring one of the six for the first time writes the other five out explicitly, which is
     * the point: from then on the list is the reader's and no longer tracks the app's default.
     * Unstarring the last one writes [EMPTY_SELECTION] rather than an empty string — see the class
     * note for why that difference is load-bearing.
     */
    suspend fun unstar(wire: String) {
        val clean = usable(wire) ?: return
        dataStore.edit { preferences ->
            val current = readFavourites(preferences[FAVOURITES])
            if (clean !in current) return@edit
            preferences[FAVOURITES] = writeFavourites(current - clean)
        }
    }

    /**
     * The intervals struck out of the full picker sheet.
     *
     * A set rather than a list because order means nothing here — it is a filter, not a display —
     * and empty when nothing was ever hidden, which is also the only sensible reading of a blank
     * or unreadable row. There is no [EMPTY_SELECTION] equivalent for this entry: "hide nothing"
     * and "the default" are the same state, so there is nothing to tell apart.
     */
    fun hidden(): Flow<Set<String>> = dataStore.data
        .map { preferences -> decodeSet(preferences[HIDDEN]) }
        .distinctUntilChanged()

    /** Strikes one interval out of the picker, and takes it off the bar if it was pinned there. */
    suspend fun hide(wire: String) {
        val clean = usable(wire) ?: return
        dataStore.edit { preferences ->
            writeHidden(preferences, decodeSet(preferences[HIDDEN]) + clean)
            val current = readFavourites(preferences[FAVOURITES])
            if (clean in current) {
                preferences[FAVOURITES] = writeFavourites(current - clean)
            }
        }
    }

    /** Puts one interval back in the picker. It does not go back on the bar; see the class note. */
    suspend fun unhide(wire: String) {
        val clean = usable(wire) ?: return
        dataStore.edit { preferences ->
            writeHidden(preferences, decodeSet(preferences[HIDDEN]) - clean)
        }
    }

    /**
     * Forgets both settings: the bar goes back to [DEFAULT_FAVOURITES], the picker back to all of
     * them.
     *
     * The entries are removed rather than rewritten with the defaults, which is what makes this a
     * return to the app's choice rather than a snapshot of today's copy of it.
     */
    suspend fun reset() {
        dataStore.edit { preferences ->
            preferences.remove(FAVOURITES)
            preferences.remove(HIDDEN)
        }
    }

    companion object {
        internal val FAVOURITES = stringPreferencesKey("chart_interval_favourites")

        internal val HIDDEN = stringPreferencesKey("chart_interval_hidden")

        /**
         * The bar every reader starts with.
         *
         * The six the interval bar hard-codes today, in its order, duplicated here rather than
         * depended on — the same trade [ChartDrawingStore.DEFAULT_COLOUR] makes, because the
         * alternative is a preferences module that depends on a feature module. They are not a
         * guess either: they are also the six the keyboard binds to the number keys and the six
         * chart vision accepts, and three controls agreeing on which intervals matter is worth
         * more than each of them picking its own.
         */
        val DEFAULT_FAVOURITES: List<String> = listOf("M1", "M5", "M15", "H1", "H4", "D1")

        /**
         * What "the reader chose none" is written as.
         *
         * A hyphen, and it is safe precisely because every interval wire is letters and digits —
         * `M15`, `MN1`, `205` — so no interval can be spelled this way and no round trip can turn
         * a real interval into this token. [usable] is what guarantees that, and anything added to
         * this file later has to keep guaranteeing it.
         */
        internal const val EMPTY_SELECTION = "-"

        /** Between wires. ASCII group separator. */
        private const val GROUP = "\u001D"

        /**
         * How many intervals may sit on the bar.
         *
         * The bar is one scrolling row of chips, and a reader who pins two dozen has already made
         * it useless — so this is not a limit anybody reaches by hand. It exists so a caller stuck
         * in a loop cannot grow one preferences string without bound, the same job
         * [SymbolChartStateStore.MAX_SYMBOLS] does and for the same reason.
         */
        const val MAX_FAVOURITES = 24

        /**
         * How many intervals may be struck out of the picker.
         *
         * Comfortably past the fifteen presets plus every custom interval a reader is likely to
         * have typed and then thought better of. Same job as [MAX_FAVOURITES]: a bound on a
         * runaway writer, not a rule anyone meets.
         */
        const val MAX_HIDDEN = 64

        /**
         * Reads the stored bar, resolving all three of the states the class note describes.
         *
         * Internal so a test can pin that distinction directly rather than through a fake
         * DataStore, because it is the one thing in this file that is easy to get quietly wrong.
         */
        internal fun readFavourites(stored: String?): List<String> {
            if (stored.isNullOrBlank()) return DEFAULT_FAVOURITES
            if (stored == EMPTY_SELECTION) return emptyList()
            // Non-blank but nothing in it survived: a truncated write, or a row this build cannot
            // read. An accident rather than an opinion, so it reads as the default — an interval
            // bar with nothing on it is a chart whose timeframe cannot be changed.
            return decode(stored).ifEmpty { DEFAULT_FAVOURITES }
        }

        /** An empty list is the reader's own choice, and is written as such. See the class note. */
        internal fun writeFavourites(wires: List<String>): String =
            if (wires.isEmpty()) EMPTY_SELECTION else wires.take(MAX_FAVOURITES).joinToString(GROUP)

        private fun writeHidden(preferences: MutablePreferences, wires: Set<String>) {
            if (wires.isEmpty()) {
                // Removed rather than stored as an empty string, so a reader who un-hides their
                // last interval leaves nothing behind for the next version to have to parse.
                preferences.remove(HIDDEN)
            } else {
                preferences[HIDDEN] = wires.take(MAX_HIDDEN).joinToString(GROUP)
            }
        }

        private fun decodeSet(stored: String?): Set<String> = decode(stored.orEmpty()).toSet()

        /**
         * Splits a stored row, dropping anything that is not a wire and keeping everything that is.
         *
         * [EMPTY_SELECTION] falls out here too, so a row carrying both the token and real wires —
         * two writers racing, or an older build's leftovers — reads as the wires rather than as a
         * bar with a stray chip on it.
         */
        private fun decode(stored: String): List<String> = stored
            .split(GROUP)
            .mapNotNull(::usable)
            .distinct()

        /**
         * One wire, normalised, or null if it cannot be one.
         *
         * Uppercased so `h4` and `H4` are one entry rather than two chips that look identical; a
         * custom interval is digits and is unaffected. Letters and digits only, and short, because
         * that is what both spellings are — the check is what keeps [EMPTY_SELECTION] impossible
         * to collide with, and it also means no wire can ever contain the separator.
         */
        internal fun usable(wire: String?): String? {
            val clean = wire?.trim()?.uppercase() ?: return null
            if (clean.isEmpty() || clean.length > MAX_WIRE_LENGTH) return null
            if (clean.any { it !in 'A'..'Z' && it !in '0'..'9' }) return null
            return clean
        }

        /** `MN1` is three characters and the longest custom interval, `1440`, is four. */
        private const val MAX_WIRE_LENGTH = 8
    }
}
