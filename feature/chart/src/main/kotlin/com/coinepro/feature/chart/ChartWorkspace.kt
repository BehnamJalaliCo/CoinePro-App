package com.coinepro.feature.chart

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Where the divider between the chart and the watchlist may sit, and what a drag does to it.
 *
 * ### Why the chart and the watchlist are on one screen at all
 *
 * It is the third-most-common structural complaint about the large mobile terminal, in its own
 * users' words: *"in current UI, you can see either chart or watchlist, not simultaneously. huge
 * slowdown. feels completely handicapped."* Somebody comparing four instruments on a phone
 * currently pays a navigation, a scroll and a wait per look, and does it dozens of times an hour.
 * A strip under the chart removes all of it.
 *
 * ### Why the ratio is a fraction and not a height
 *
 * A stored height in density-independent pixels is wrong on the next phone and wrong again after a
 * rotation: 300dp is two thirds of a small phone held upright and a fifth of a tablet held
 * sideways. A fraction of whatever room there is means the reader's choice — "about two thirds
 * chart" — survives every one of those.
 *
 * The bounds are not decoration. Below [MIN] the chart is a band too short to read structure in,
 * which is the state this whole feature exists to escape; above [MAX] the strip is thinner than one
 * row and the reader can no longer tell it is there, so the handle looks broken rather than
 * dragged. Clamping means a fling can never leave the layout somewhere it cannot be recovered from
 * by dragging the other way.
 */
object ChartSplit {

    /** The least of the screen the chart may keep. Below this it stops being a chart. */
    const val MIN = 0.34f

    /** The most it may keep, leaving the strip at least one legible row. */
    const val MAX = 0.86f

    /**
     * Where the divider starts: a little under two thirds.
     *
     * The chart gets the room because it is what the reader came for, and the remainder is three
     * or four watchlist rows — enough that the strip is obviously a list rather than a stray row,
     * and few enough that it never reads as the main event.
     */
    const val DEFAULT = 0.62f

    /**
     * Below this the strip stops being a list and becomes a single scrolling row of tickers.
     *
     * Measured against the space this layout actually has rather than the window, because a chart
     * screen on a short phone in a multi-window split has the same problem as a chart screen on a
     * small phone. At 560dp a 62% chart is about 350dp — already the floor for reading a hundred
     * candles — and whatever is left cannot hold a row with a price on it as well as a divider. A
     * horizontal ticker row costs 44dp, keeps every symbol one tap away, and does not pretend to
     * be a table.
     */
    val COMPACT_HEIGHT: Dp = 560.dp

    /**
     * A ratio brought inside the bounds, with anything nonsensical sent back to the default.
     *
     * The NaN branch is not defensive noise. The ratio is arrived at by dividing a drag by a
     * measured height, and a layout pass that reports zero height — which happens for one frame
     * on the way in — makes that division produce a NaN that `coerceIn` propagates rather than
     * fixes. One such frame written back to storage would leave the reader with a divider that
     * never moves again on any device, so it is caught here, at the one place every value passes
     * through.
     */
    fun clamp(ratio: Float): Float =
        if (ratio.isNaN() || !ratio.isFinite()) DEFAULT else ratio.coerceIn(MIN, MAX)

    /**
     * Where the divider lands after a drag of [dragPx] within a pane [totalPx] tall.
     *
     * Positive [dragPx] is downwards, which grows the chart — the finger and the boundary move
     * together, which is the only arrangement that does not feel inverted. A zero or negative
     * height means the layout has not been measured yet and the drag is ignored rather than
     * dividing by it.
     */
    fun after(current: Float, dragPx: Float, totalPx: Float): Float =
        if (totalPx <= 0f) clamp(current) else clamp(current + dragPx / totalPx)
}

/**
 * Which of the two panes' properties are tied together.
 *
 * Four independent switches rather than one "link" button, because the four answer four different
 * questions and a reader almost never wants all of them. Two charts of the *same* symbol on two
 * intervals wants the symbol tied and the interval free; two charts of *different* symbols on the
 * same interval wants the opposite; comparing where two markets were at one moment wants the
 * crosshair tied and nothing else. A single toggle would serve one of those three and get in the
 * way of the other two.
 *
 * Everything defaults off. Two panes that immediately overwrite each other's symbol the moment they
 * open would be one chart drawn twice, which is the opposite of why the reader split the screen.
 */
data class PaneSync(
    /** Choosing a symbol in one pane puts it in the other. */
    val symbol: Boolean = false,
    /** Changing the bar length in one changes it in the other. */
    val interval: Boolean = false,
    /** The crosshair in one draws its time in the other, so one moment can be read on both. */
    val crosshair: Boolean = false,
    /** Panning or zooming one moves the other to the same window of bars. */
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
 * The four things two panes can share, named so a switch row can be built by iterating them.
 *
 * The order is the order the switches are drawn in and the order [PaneSync.encode] writes, and it
 * is deliberately from the coarsest tie to the finest: which market, then which bar length, then
 * which moment, then which window. Reordering this enum silently reinterprets every stored record,
 * so new fields go on the end.
 */
enum class PaneSyncField(
    /** What the switch is called, in Persian. */
    val label: String,
    /** One line saying what the tie actually does to the second pane. */
    val note: String,
) {
    SYMBOL("نماد", "انتخاب نماد در یکی، همان نماد را در دیگری باز می‌کند."),
    INTERVAL("بازهٔ زمانی", "تغییر بازه در یکی، بازهٔ دیگری را هم عوض می‌کند."),
    CROSSHAIR("نشانگر", "نشانگر روی یک نمودار، همان لحظه را روی نمودار دیگر هم نشان می‌دهد."),
    TIME_RANGE("محدودهٔ زمانی", "جابه‌جایی و بزرگ‌نمایی یکی، دیگری را روی همان کندل‌ها می‌برد."),
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

    /**
     * Where the divider sits, as the chart's share of the room.
     *
     * Clamped on the way out as well as on the way in, so a record written by a build with
     * different bounds — or edited by hand — cannot produce a layout with no chart in it.
     */
    val splitRatio: Flow<Float> = dataStore.data
        .map { preferences -> ChartSplit.clamp(preferences[SPLIT_RATIO] ?: ChartSplit.DEFAULT) }
        .distinctUntilChanged()

    /** Records where the reader let the divider go. */
    suspend fun setSplitRatio(ratio: Float) {
        dataStore.edit { it[SPLIT_RATIO] = ChartSplit.clamp(ratio) }
    }

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

    private companion object {
        val SPLIT_RATIO = floatPreferencesKey("chart_split_ratio")
        val PANE_SYNC = stringPreferencesKey("chart_pane_sync")
        val SECOND_PANE = stringPreferencesKey("chart_second_pane_symbol")
    }
}
