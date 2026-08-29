package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * How one symbol was last being looked at: its timeframe, its chart type, the indicators that were
 * switched on and the periods they were switched on with.
 *
 * ### Why this is per symbol, and why that is the whole point
 *
 * The loudest complaint about the large mobile charting apps is that chart settings are global:
 * change the timeframe or the indicators while reading one asset and every other asset changes
 * with it. Somebody who watches gold on the four-hour and a small cap on the five-minute has to
 * re-set the chart on every switch, forever. The desktop terminals people compare them to do not
 * behave that way, and readers say so. Keying the state by symbol is the fix, and it is cheap: one
 * row per instrument the reader has actually opened.
 *
 * ### Everything here is a plain String
 *
 * [timeframe], [chartType] and [scaleMode] are ids, not enums, and [indicators] is a list of ids
 * rather than of indicator objects. That is deliberate and it is not laziness: `core:datastore`
 * must not depend on `core:chart` or `core:marketdata`. A preferences module that knows the chart
 * engine's types cannot be read by anything that does not also drag the engine in — the widget
 * process, a worker, a plain unit test — and every one of those enums is a thing whose *set of
 * values* will change while the strings already on disk stay written. Resolving an id back to a
 * type is one function in the feature module, on the side of the boundary where both are already
 * on the classpath. An id this build no longer recognises stays on disk and is ignored, which is
 * exactly what should happen when somebody downgrades or when an indicator is renamed.
 *
 * ### Nullable, because "not chosen" is not "the default"
 *
 * A null [timeframe] means the reader never picked one for this symbol, so the chart should open
 * on whatever the app's default is. Storing the default instead would freeze it: change the app's
 * default later and every symbol would still open on the old one.
 */
data class SymbolChartState(
    /** The ticker, uppercase. Normalised on the way in, so `btcusdt` and `BTCUSDT` are one row. */
    val symbol: String,
    /** The timeframe id the reader last left this symbol on, or null if they never chose one. */
    val timeframe: String? = null,
    /** The chart type id — candles, line, Heikin-Ashi — or null for the app default. */
    val chartType: String? = null,
    /** The indicator ids that were switched on, in the order the reader added them. */
    val indicators: List<String> = emptyList(),
    /**
     * The period each indicator was configured with, keyed by indicator id.
     *
     * Separate from [indicators] rather than folded into it, because an indicator can be switched
     * off and back on and should come back with the period the reader set rather than the default.
     */
    val indicatorPeriods: Map<String, Int> = emptyMap(),
    /** The price-scale mode id — automatic, fitted, percentage — or null for the app default. */
    val scaleMode: String? = null,
    /** Whether the price axis was logarithmic. */
    val logScale: Boolean = false,
    /**
     * When this row was last written, in epoch milliseconds.
     *
     * Load-bearing rather than informational: it is what [SymbolChartStateStore] evicts by when
     * the cap is reached, so a caller that leaves it at zero is telling the store this row is the
     * first one to throw away.
     */
    val updatedAt: Long = 0L,
    /**
     * How hard a drawing tap was pulled onto a bar's open, high, low or close: `OFF`, `WEAK`,
     * `STRONG`.
     *
     * A string and not `core:chart`'s `MagnetMode`, for the reason the class note gives above and
     * for one more that is specific to this field: the magnet shipped with an enum, snap maths,
     * persistence and a rail control, and no call site passed the callback — so it was off for the
     * life of the app. A stored mode that the mapper resolves is what makes the rail control mean
     * something the next time the reader opens this symbol.
     */
    val magnetMode: String? = null,
    /**
     * Whether the drawing tool stays armed after a mark is finished.
     *
     * A Boolean rather than an id string, because unlike everything around it this is not a name
     * from another module's enum — it is a switch, and [logScale] already establishes how a switch
     * is stored here. False is both the default and what a row written before the field existed
     * reads back as, which is the behaviour every earlier build had.
     */
    val keepDrawing: Boolean = false,
    /**
     * The drawing tools the reader starred, by tool id.
     *
     * Per symbol like everything else in this row, and that is the honest reading of the request:
     * the tools somebody reaches for on gold are not the ones they reach for on a small cap. Ids,
     * so a tool this build no longer ships is ignored rather than crashing a reader who downgraded.
     */
    val toolFavourites: List<String> = emptyList(),
    /**
     * The candlestick patterns switched on for this symbol, by pattern id.
     *
     * Stored rather than recomputed from a global setting, because pattern detection is noisy on
     * some instruments and quiet on others, and a reader who turned the engulfing marks off on one
     * chart has not asked for them off everywhere.
     */
    val patterns: List<String> = emptyList(),
    /**
     * Which series each indicator reads, keyed by indicator id, and sparse.
     *
     * Only the indicators the reader redirected are in here — an absent entry means the indicator
     * reads the candles, which is what all of them do until somebody says otherwise. Both halves
     * are opaque strings: the source is an id the feature module resolves, on the same boundary
     * [indicators] is on.
     */
    val chainSources: Map<String, String> = emptyMap(),
)

/**
 * Where each symbol's chart settings live between sessions.
 *
 * ### One preferences entry, not one per symbol
 *
 * [ChartDrawingStore] keys by symbol because drawings are read one chart at a time and a reader
 * can accumulate hundreds of them on a single instrument. This store does the opposite and packs
 * every symbol into one string, because its two other jobs — [all], and evicting the least
 * recently updated row — both need to see every row at once, and a preferences file cannot
 * enumerate keys by prefix without reading the whole file anyway.
 *
 * ### The encoding
 *
 * The delimited-string scheme [ChartDrawingStore] and [ChartLayoutStore] use, for the same reason:
 * the alternative is a serialisation library in a preferences module. ASCII's group separator
 * between symbols, its record separator between one symbol's fields, its unit separator inside the
 * indicator list and the period map. All three are control characters, so no id the app generates
 * can contain one.
 *
 * ### Decoding never throws, and that rule has a shape
 *
 * A row written by an older build is *short* — fewer fields than this version writes — and every
 * missing field takes its default rather than discarding the row. A row written by a newer build
 * carries fields this version has never heard of, and those are ignored rather than treated as
 * corruption. The only thing that disqualifies a row is a missing symbol, because a row with no
 * symbol belongs to nothing. The failure this avoids is the one that matters: an app that cannot
 * open a chart because of a string it wrote itself last month.
 */
class SymbolChartStateStore(private val dataStore: DataStore<Preferences>) {

    /**
     * What is stored for one symbol, or null if the reader has never configured it.
     *
     * Distinct-until-changed, because this is collected by an open chart while other symbols' rows
     * are being written next to it, and an unrelated symbol's update must not re-emit here.
     */
    fun state(symbol: String): Flow<SymbolChartState?> {
        val wanted = symbol.uppercase()
        return dataStore.data
            .map { preferences ->
                decodeAll(preferences[STATES].orEmpty()).firstOrNull { it.symbol == wanted }
            }
            .distinctUntilChanged()
    }

    /**
     * Every stored symbol, most recently updated first.
     *
     * The order is the useful one for a "recently charted" list, and it is also the order eviction
     * works down from — so what a reader sees at the bottom of that list is what will go first.
     */
    fun all(): Flow<List<SymbolChartState>> = dataStore.data
        .map { preferences -> decodeAll(preferences[STATES].orEmpty()) }
        .distinctUntilChanged()

    /**
     * Writes one symbol's state, replacing whatever was there.
     *
     * Not a merge. The caller holds the whole chart state and passes all of it; merging field by
     * field here would make "the reader switched every indicator off" indistinguishable from "the
     * caller did not mention indicators", and the first of those has to be storable.
     */
    suspend fun put(state: SymbolChartState) {
        val row = state.copy(symbol = state.symbol.uppercase())
        if (encode(row) == null) return
        dataStore.edit { preferences ->
            val kept = decodeAll(preferences[STATES].orEmpty()).filterNot { it.symbol == row.symbol }
            preferences[STATES] = (kept + row)
                .sortedByDescending(SymbolChartState::updatedAt)
                .take(MAX_SYMBOLS)
                .mapNotNull { encode(it) }
                .joinToString(GROUP)
        }
    }

    /** Forgets one symbol and leaves every other row alone. What "reset this chart" writes. */
    suspend fun clear(symbol: String) {
        val wanted = symbol.uppercase()
        dataStore.edit { preferences ->
            val kept = decodeAll(preferences[STATES].orEmpty()).filterNot { it.symbol == wanted }
            if (kept.isEmpty()) {
                // Removed rather than stored as an empty string, so a reader who clears the last
                // symbol leaves nothing behind for the next version to have to parse.
                preferences.remove(STATES)
            } else {
                preferences[STATES] = kept.mapNotNull { encode(it) }.joinToString(GROUP)
            }
        }
    }

    companion object {
        internal val STATES = stringPreferencesKey("symbol_chart_states")

        /** Between symbols. ASCII group separator. */
        private const val GROUP = "\u001D"

        /** Between one symbol's fields. ASCII record separator. */
        private const val RECORD = "\u001E"

        /** Inside the indicator list and the period map. ASCII unit separator. */
        private const val UNIT = "\u001F"

        /**
         * How many symbols keep their own chart state.
         *
         * A cap rather than none, because this whole string is parsed on every chart open and a
         * reader who has browsed a thousand tickers over a year should not pay for all of them on
         * every launch. Two hundred is far past the number of instruments anybody actually
         * watches, so in practice the eviction never runs; it exists so that unbounded browsing
         * cannot turn into an unbounded read.
         */
        const val MAX_SYMBOLS = 200

        internal fun encode(state: SymbolChartState): String? {
            val symbol = state.symbol.takeIf { it.isNotBlank() && !hasSeparator(it) } ?: return null
            // An id carrying a separator is dropped on its own rather than taking the row with it:
            // losing one indicator is a smaller failure than losing the symbol's timeframe, and no
            // id the app itself generates can contain a control character anyway.
            val indicators = state.indicators.filterNot { hasSeparator(it) }
            val periods = state.indicatorPeriods
                .filterKeys { !hasSeparator(it) }
                .flatMap { (id, period) -> listOf(id, period.toString()) }
            // Alternating id and source, the same shape the periods take, so one decoder pattern
            // covers both and neither can grow a second way of being read.
            val sources = state.chainSources
                .filterKeys { !hasSeparator(it) }
                .filterValues { !hasSeparator(it) }
                .flatMap { (id, source) -> listOf(id, source) }
            return listOf(
                symbol,
                blankIfSeparated(state.timeframe.orEmpty()),
                blankIfSeparated(state.chartType.orEmpty()),
                indicators.joinToString(UNIT),
                periods.joinToString(UNIT),
                blankIfSeparated(state.scaleMode.orEmpty()),
                if (state.logScale) "1" else "0",
                state.updatedAt.toString(),
                blankIfSeparated(state.magnetMode.orEmpty()),
                if (state.keepDrawing) "1" else "0",
                state.toolFavourites.filterNot { hasSeparator(it) }.joinToString(UNIT),
                state.patterns.filterNot { hasSeparator(it) }.joinToString(UNIT),
                sources.joinToString(UNIT),
            ).joinToString(RECORD)
        }

        internal fun decodeAll(stored: String): List<SymbolChartState> = stored
            .split(GROUP)
            .filter(String::isNotBlank)
            .mapNotNull { decode(it) }
            .sortedByDescending(SymbolChartState::updatedAt)

        internal fun decode(record: String): SymbolChartState? {
            val parts = record.split(RECORD)
            // Only the symbol is required. A field this build has not got takes its default; a
            // field it does not recognise, written by a newer build, is simply never read.
            val symbol = parts.getOrNull(0)?.takeIf(String::isNotBlank)?.uppercase() ?: return null
            val periodParts = parts.getOrNull(4).orEmpty().split(UNIT).filter(String::isNotBlank)
            val sourceParts = parts.getOrNull(12).orEmpty().split(UNIT).filter(String::isNotBlank)
            return SymbolChartState(
                symbol = symbol,
                timeframe = parts.getOrNull(1)?.takeIf(String::isNotBlank),
                chartType = parts.getOrNull(2)?.takeIf(String::isNotBlank),
                indicators = parts.getOrNull(3).orEmpty().split(UNIT).filter(String::isNotBlank),
                indicatorPeriods = periodParts
                    // Alternating id and period. A trailing id with no number — a half-written
                    // record, or one truncated by an older writer — drops that entry and keeps
                    // the rest, rather than shifting every pair after it by one.
                    .chunked(2)
                    .mapNotNull { pair ->
                        if (pair.size != 2) return@mapNotNull null
                        val period = pair[1].toIntOrNull() ?: return@mapNotNull null
                        pair[0] to period
                    }
                    .toMap(),
                scaleMode = parts.getOrNull(5)?.takeIf(String::isNotBlank),
                logScale = parts.getOrNull(6) == "1",
                updatedAt = parts.getOrNull(7)?.toLongOrNull() ?: 0L,
                magnetMode = parts.getOrNull(8)?.takeIf(String::isNotBlank),
                keepDrawing = parts.getOrNull(9) == "1",
                toolFavourites = parts.getOrNull(10).orEmpty().split(UNIT).filter(String::isNotBlank),
                patterns = parts.getOrNull(11).orEmpty().split(UNIT).filter(String::isNotBlank),
                chainSources = sourceParts
                    // Alternating id and source. A trailing id with no source drops that entry
                    // rather than shifting every pair after it by one.
                    .chunked(2)
                    .mapNotNull { pair -> if (pair.size == 2) pair[0] to pair[1] else null }
                    .toMap(),
            )
        }

        private fun hasSeparator(value: String) =
            value.contains(GROUP) || value.contains(RECORD) || value.contains(UNIT)

        /** A separator inside a free field would shift every field after it. Blanked instead. */
        private fun blankIfSeparated(value: String) = if (hasSeparator(value)) "" else value
    }
}
