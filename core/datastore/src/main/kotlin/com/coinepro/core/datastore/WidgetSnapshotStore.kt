package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * One market as the home-screen widget needs it.
 *
 * ### Why the widget stores its own copy of a price
 *
 * Because a widget is drawn by a process that is not this app. `AppWidgetProvider.onUpdate` runs in
 * a broadcast receiver with about five seconds and no coroutine scope worth the name; it cannot
 * open a socket, wait for a quote and build a view. Every serious widget on Android works this way:
 * something else fetches, writes a snapshot, and the provider renders whatever is there.
 *
 * ### Formatted at write time, not at render time
 *
 * [priceText] and [changeText] are strings, not numbers, and that is deliberate. The formatting
 * rules in this app are not trivial — Latin digits for market figures, decimals that follow the
 * instrument's magnitude, a real minus sign rather than a hyphen — and they live in composables and
 * in the chart's canvas. Re-implementing them inside a `RemoteViews` builder is how the widget ends
 * up showing a number the app spells differently, which is worse than showing no widget.
 *
 * So the writer formats, using the same code every other surface uses, and the provider only
 * places text.
 */
data class WidgetMarket(
    /** The ticker, uppercase. Also the deep link's argument. */
    val symbol: String,
    /** What the app calls it — the Persian name where there is one, the ticker otherwise. */
    val name: String,
    /** The price, already formatted. See the class note. */
    val priceText: String,
    /** The change, already formatted and already carrying its sign. Empty when the feed sent none. */
    val changeText: String,
    /**
     * Which way it moved: 1 up, −1 down, 0 flat or unknown.
     *
     * A number rather than a colour, because the colour depends on a setting the widget reads at
     * render time — a reader on the East Asian convention draws a rise in red. See
     * [MarketColorScheme].
     */
    val direction: Int,
)

/**
 * What the widget last knew, and when.
 *
 * [capturedAtEpochMillis] is the honest part. A widget that shows a price with no time on it is a
 * widget that shows yesterday's price exactly as confidently as this second's, and the reader has
 * no way to tell the difference — which on a trading app is not a cosmetic problem.
 */
data class WidgetSnapshot(
    val markets: List<WidgetMarket> = emptyList(),
    val capturedAtEpochMillis: Long = 0L,
    /** Whether the last refresh failed. The widget says so rather than silently showing old prices. */
    val stale: Boolean = false,
) {
    val isEmpty: Boolean get() = markets.isEmpty()
}

/**
 * Where the widget's snapshot lives between the process that fetches it and the process that draws
 * it.
 *
 * ### The encoding
 *
 * The same delimited-string scheme [ChartDrawingStore] and [ChartLayoutStore] use, for the same
 * reason: the alternative is a serialisation library in a preferences module. Two separators —
 * ASCII's group separator between markets, its record separator between one market's fields. Both
 * are control characters, so no ticker or formatted number can contain one; a field that somehow
 * does is dropped rather than written, because a record that parses back as different fields would
 * put one market's price against another market's name.
 *
 * Decoding never throws. A record from an older build, or half-written when the process died, is
 * skipped — a widget that renders four markets instead of five is a small failure, and one that
 * crashes the launcher's host process is not.
 */
class WidgetSnapshotStore(private val dataStore: DataStore<Preferences>) {

    val snapshot: Flow<WidgetSnapshot> = dataStore.data.map { preferences ->
        decode(
            preferences[MARKETS].orEmpty(),
            preferences[CAPTURED_AT].orEmpty(),
            preferences[STALE].orEmpty(),
        )
    }

    /** A single read, for the provider — which has no lifecycle to collect a flow in. */
    suspend fun read(): WidgetSnapshot = snapshot.first()

    suspend fun write(snapshot: WidgetSnapshot) {
        dataStore.edit { preferences ->
            preferences[MARKETS] = encode(snapshot.markets)
            preferences[CAPTURED_AT] = snapshot.capturedAtEpochMillis.toString()
            preferences[STALE] = if (snapshot.stale) "1" else "0"
        }
    }

    /**
     * Mark what is stored as stale without discarding it.
     *
     * What a failed refresh does. Throwing the prices away and showing an empty widget would be
     * the wrong trade: an hour-old price labelled as an hour old is useful, and a blank rectangle
     * on somebody's home screen is not.
     */
    suspend fun markStale() {
        dataStore.edit { preferences -> preferences[STALE] = "1" }
    }

    companion object {
        internal val MARKETS = stringPreferencesKey("widget_markets")
        internal val CAPTURED_AT = stringPreferencesKey("widget_captured_at")
        internal val STALE = stringPreferencesKey("widget_stale")

        /** Between markets. */
        internal const val GROUP = "\u001D"

        /** Between one market's fields. */
        internal const val RECORD = "\u001E"

        /**
         * As many as the largest widget can show, plus a little.
         *
         * The five-by-four widget draws eight rows. Storing a few more costs nothing and means a
         * reader who resizes the widget larger sees the extra rows immediately rather than after
         * the next refresh.
         */
        const val MAX_MARKETS = 12

        internal fun encode(markets: List<WidgetMarket>): String = markets
            .take(MAX_MARKETS)
            .mapNotNull { market ->
                val fields = listOf(
                    market.symbol,
                    market.name,
                    market.priceText,
                    market.changeText,
                    market.direction.toString(),
                )
                // A field carrying a separator would parse back as two fields and shift every
                // field after it — one market's price under another's name. Dropped instead.
                if (fields.any { it.contains(GROUP) || it.contains(RECORD) }) null else fields.joinToString(RECORD)
            }
            .joinToString(GROUP)

        internal fun decode(markets: String, capturedAt: String, stale: String): WidgetSnapshot = WidgetSnapshot(
            markets = markets.split(GROUP)
                .filter(String::isNotBlank)
                .mapNotNull(::decodeMarket),
            capturedAtEpochMillis = capturedAt.toLongOrNull() ?: 0L,
            stale = stale == "1",
        )

        private fun decodeMarket(record: String): WidgetMarket? {
            val parts = record.split(RECORD)
            if (parts.size != 5) return null
            val symbol = parts[0].takeIf(String::isNotBlank) ?: return null
            return WidgetMarket(
                symbol = symbol,
                name = parts[1].takeIf(String::isNotBlank) ?: symbol,
                priceText = parts[2],
                changeText = parts[3],
                // An unreadable direction is flat, not a crash and not a guess at a colour.
                direction = parts[4].toIntOrNull()?.coerceIn(-1, 1) ?: 0,
            )
        }
    }
}
