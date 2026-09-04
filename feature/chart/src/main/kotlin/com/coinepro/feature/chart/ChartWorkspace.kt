package com.coinepro.feature.chart

import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProWindowClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/*
 * `ChartSplit` used to live here — the bounds, the clamp, the drag arithmetic and the row-fitted
 * starting position for the chart-and-watchlist divider. There is no divider now: the chart page
 * has the whole screen and the watchlist reaches it through `SymbolWheel`. See `ChartScreen` for
 * why the split was taken out, and `ChartWatchlistSplit` for what is left of that file.
 */

/**
 * Which of the panes' properties are tied together.
 *
 * Four independent switches rather than one "link" button, because the four answer four different
 * questions and a reader almost never wants all of them. Several charts of the *same* symbol on
 * different intervals wants the symbol tied and the interval free; several charts of *different*
 * symbols on one interval wants the opposite; comparing where two markets were at one moment wants
 * the crosshair tied and nothing else. A single toggle would serve one of those three and get in
 * the way of the other two.
 *
 * Everything defaults off. Panes that immediately overwrite each other's symbol the moment they
 * open would be one chart drawn several times, which is the opposite of why the reader split the
 * screen.
 *
 * A tie always runs **from the first pane outward**. With two panes "the other one" was unambiguous;
 * with eight it is not, and a tie that propagated from whichever pane was touched last would let two
 * readers of the same screen disagree about which chart is the subject. The first pane is the chart
 * the reader split from, so it is the one the rest follow.
 */
data class PaneSync(
    /** Choosing a symbol in the first pane puts it in all the others. */
    val symbol: Boolean = false,
    /** Changing the bar length in the first changes it in all the others. */
    val interval: Boolean = false,
    /** The crosshair in one draws its time in the rest, so one moment can be read across them. */
    val crosshair: Boolean = false,
    /** Panning or zooming one moves the rest to the same window of bars. */
    val timeRange: Boolean = false,
) {
    /** Whether one named field is tied. The switch row reads this rather than four properties. */
    fun isOn(field: PaneSyncField): Boolean = when (field) {
        PaneSyncField.SYMBOL -> symbol
        PaneSyncField.INTERVAL -> interval
        PaneSyncField.CROSSHAIR -> crosshair
        PaneSyncField.TIME_RANGE -> timeRange
    }

    /** The same set with one field set. Nothing else moves — that is the whole contract here. */
    fun with(field: PaneSyncField, on: Boolean): PaneSync = when (field) {
        PaneSyncField.SYMBOL -> copy(symbol = on)
        PaneSyncField.INTERVAL -> copy(interval = on)
        PaneSyncField.CROSSHAIR -> copy(crosshair = on)
        PaneSyncField.TIME_RANGE -> copy(timeRange = on)
    }

    /** The same set with one field flipped, for a switch that has no value of its own. */
    fun toggled(field: PaneSyncField): PaneSync = with(field, !isOn(field))

    /** Whether anything at all is tied, so the header can say so without listing four states. */
    val anyOn: Boolean get() = symbol || interval || crosshair || timeRange

    /**
     * Four characters, one per field, in [PaneSyncField]'s own order.
     *
     * Positional rather than named, because the alternative in a preferences string is four keys
     * that can disagree with each other about whether the record exists at all. A record written by
     * a build with fewer fields is short and the missing ones read as off, which is the right
     * answer: a tie the reader never asked for must never arrive by upgrade.
     */
    fun encode(): String = PaneSyncField.entries.joinToString("") { if (isOn(it)) "1" else "0" }

    companion object {
        /** Nothing tied. What a reader who has never touched the switches gets. */
        val OFF = PaneSync()

        /** Reads [encode] back, treating anything missing or unrecognised as off. */
        fun decode(stored: String): PaneSync {
            var sync = OFF
            PaneSyncField.entries.forEachIndexed { index, field ->
                if (stored.getOrNull(index) == '1') sync = sync.with(field, true)
            }
            return sync
        }
    }
}

/**
 * The four things panes can share, named so a switch row can be built by iterating them.
 *
 * The order is the order the switches are drawn in and the order [PaneSync.encode] writes, and it
 * is deliberately from the coarsest tie to the finest: which market, then which bar length, then
 * which moment, then which window. Reordering this enum silently reinterprets every stored record,
 * so new fields go on the end.
 */
enum class PaneSyncField(
    /**
     * What the switch is called.
     *
     * A resource rather than the Persian literal these two fields used to hold. The literals were
     * written when the screen had exactly two panes and every one of them said «دیگری» — *the other
     * one* — which stopped being true the moment a tablet could open eight. Rewriting them meant
     * touching the words anyway, and a word this app shows a reader belongs in both `values` and
     * `values-en` like every other.
     */
    @StringRes val labelRes: Int,
    /** One line saying what the tie actually does to the panes that are not the first. */
    @StringRes val noteRes: Int,
) {
    SYMBOL(R.string.pane_sync_symbol, R.string.pane_sync_symbol_note),
    INTERVAL(R.string.pane_sync_interval, R.string.pane_sync_interval_note),
    CROSSHAIR(R.string.pane_sync_crosshair, R.string.pane_sync_crosshair_note),
    TIME_RANGE(R.string.pane_sync_time_range, R.string.pane_sync_time_range_note),
}

/**
 * The parts of the chart workspace that outlive one visit: where the divider sits, what the two
 * panes share, and which symbol the second pane was left on.
 *
 * ### Why this is not in `core:datastore` with the others
 *
 * Everything here is a property of *this screen's layout* rather than of the reader's data. A split
 * position means nothing outside the chart, is read by nothing else, and would sit in a storage
 * module as a field that module cannot explain. The preferences file is the same one — it is handed
 * in — so this is one more key in the store the app already opens, not a second file.
 *
 * ### Why the divider is saved at all
 *
 * Because a reader moves it once. Somebody who prefers four watchlist rows to three sets that
 * ratio the first afternoon and never thinks about it again, and a layout that forgets it is a
 * layout they have to re-make on every cold start — which is exactly the kind of small repeated
 * cost this screen exists to remove. Written on the *end* of a drag rather than on every frame:
 * a preferences write per frame of a divider drag is sixty writes a second for a value nobody is
 * reading until the next launch.
 */
class ChartWorkspaceStore(private val dataStore: DataStore<Preferences>) {

    /** What the two panes share. See [PaneSync] for why all four default to off. */
    val paneSync: Flow<PaneSync> = dataStore.data
        .map { preferences -> PaneSync.decode(preferences[PANE_SYNC].orEmpty()) }
        .distinctUntilChanged()

    /** Records the switch row. One write for all four, because they are stored as one string. */
    suspend fun setPaneSync(sync: PaneSync) {
        dataStore.edit { it[PANE_SYNC] = sync.encode() }
    }

    /**
     * The instrument the second pane was last showing, or null on a reader who has never split.
     *
     * Only the second one. The first pane's symbol is whatever chart the reader opened the panes
     * *from*, and restoring a remembered symbol over it would take them somewhere they did not ask
     * to go — which is the failure mode a saved layout has to avoid above every other.
     */
    val secondPaneSymbol: Flow<String?> = dataStore.data
        .map { preferences -> preferences[SECOND_PANE].orEmpty().takeIf(String::isNotBlank) }
        .distinctUntilChanged()

    /** Records the second pane's instrument, uppercased so two spellings are not two symbols. */
    suspend fun setSecondPaneSymbol(symbol: String) {
        val ticker = symbol.trim().uppercase()
        if (ticker.isEmpty()) return
        dataStore.edit { it[SECOND_PANE] = ticker }
    }

    /**
     * The instruments every pane **after the first** was left on, in pane order.
     *
     * The first pane is still deliberately absent, for the reason [secondPaneSymbol] gives: it is
     * whatever chart the reader split *from*, and restoring a remembered symbol over it would take
     * them somewhere they did not ask to go.
     *
     * Falls back to [secondPaneSymbol] when this key has never been written, so a reader upgrading
     * from the two-pane build keeps the pane they had rather than opening on a duplicate of the
     * first. [setExtraPaneSymbols] writes both keys for the same reason in the other direction — a
     * downgrade, or a build that only knows about two panes, still finds a second pane it can read.
     *
     * Capped at seven entries on the way out as well as on the way in. A hand-edited or corrupted
     * record is otherwise a list this screen would faithfully turn into that many live controllers,
     * each with its own websocket.
     */
    val extraPaneSymbols: Flow<List<String>> = dataStore.data
        .map { preferences ->
            val stored = preferences[PANE_SYMBOLS]
            val symbols = if (stored != null) {
                stored.split(PANE_SEPARATOR)
            } else {
                listOfNotNull(preferences[SECOND_PANE])
            }
            symbols.map { it.trim().uppercase() }.filter(String::isNotEmpty).take(MAX_EXTRA_PANES)
        }
        .distinctUntilChanged()

    /** Records where the panes after the first were left. One write for the whole row. */
    suspend fun setExtraPaneSymbols(symbols: List<String>) {
        val tickers = symbols.map { it.trim().uppercase() }
            .filter(String::isNotEmpty)
            .take(MAX_EXTRA_PANES)
        dataStore.edit { preferences ->
            preferences[PANE_SYMBOLS] = tickers.joinToString(PANE_SEPARATOR.toString())
            // The legacy key, kept in step. See [extraPaneSymbols].
            tickers.firstOrNull()?.let { preferences[SECOND_PANE] = it }
        }
    }

    /**
     * How many panes the reader last had open.
     *
     * Clamped to at least two on the way out, because a stored one is a screen whose whole reason
     * for existing is a second chart, and the single-chart screen is one tap away for anybody who
     * wants that. The upper bound is not applied here: it depends on the glass this record is being
     * read on, and a tablet layout stored on a tablet must not be truncated by a phone that happens
     * to read it first — the screen coerces against its own window instead.
     */
    val paneCount: Flow<Int> = dataStore.data
        .map { preferences -> (preferences[PANE_COUNT] ?: MIN_PANES).coerceAtLeast(MIN_PANES) }
        .distinctUntilChanged()

    /** Records how many panes the reader chose. */
    suspend fun setPaneCount(count: Int) {
        dataStore.edit { it[PANE_COUNT] = count.coerceAtLeast(MIN_PANES) }
    }

    private companion object {
        val PANE_SYNC = stringPreferencesKey("chart_pane_sync")
        val SECOND_PANE = stringPreferencesKey("chart_second_pane_symbol")
        val PANE_SYMBOLS = stringPreferencesKey("chart_pane_symbols")
        val PANE_COUNT = intPreferencesKey("chart_pane_count")

        /**
         * A comma, which is safe here because a wire symbol is letters and digits — `SymbolCatalog`
         * has never carried one with punctuation in it — and because every entry is trimmed and
         * uppercased on both sides of the store.
         */
        const val PANE_SEPARATOR = ','

        /** The first pane is not stored, so this is [CoineProWindowClass.TABLET_MAX_PANES] less one. */
        const val MAX_EXTRA_PANES = CoineProWindowClass.TABLET_MAX_PANES - 1

        /** Two is what makes this screen a pane screen at all. */
        const val MIN_PANES = CoineProWindowClass.PHONE_MAX_PANES
    }
}
