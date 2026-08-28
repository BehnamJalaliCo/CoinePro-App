package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * One drawing, flattened to the fields that survive a restart.
 *
 * A mirror of `core:chart`'s `Drawing` rather than that type itself, because `core:datastore` has no
 * business depending on the chart engine and the chart engine has no business knowing about
 * DataStore. The mapping is one function each way and it lives where both are already on the
 * classpath.
 */
data class StoredDrawing(
    val id: Long,
    val toolId: String,
    /** Time and price pairs, in the order the reader placed them. */
    val points: List<Pair<Long, Double>>,
    val colour: Long,
    val widthDp: Float,
    val text: String?,
    val direction: String,
)

/**
 * The reader's drawings, kept per symbol.
 *
 * ### Why this exists
 *
 * Nothing anywhere serialised a drawing. A trend line survived exactly as long as the composition
 * that held it: rotating the phone lost it, switching symbol lost it, opening the studio and coming
 * back lost it, and closing the app lost it. Somebody who spent two minutes marking up a chart had
 * two minutes of work with the lifetime of a scroll position.
 *
 * ### Per symbol, and that is not an implementation detail
 *
 * A drawing is anchored to one instrument's price and time. The same trend line on another symbol
 * is a line through unrelated numbers, so the key is the symbol and there is no "apply to all".
 * This is the same reasoning [ChartLayoutStore] gives for the opposite decision: a *layout* is the
 * apparatus a reader looks through and travels between symbols; a *drawing* is a mark on one chart.
 *
 * Timeframes are deliberately **not** in the key. A trend line drawn on the hourly is the same line
 * on the daily — that is what makes it a trend line, and its points are times and prices rather
 * than bar indices precisely so it can be.
 *
 * ### The encoding
 *
 * The same delimited-string scheme [ChartLayoutStore] uses, for the same reason: the alternative is
 * a serialisation library in a preferences module. Three separators rather than two, because a
 * drawing holds a list of points — ASCII's group separator joins drawings, the record separator
 * joins a drawing's fields, and the unit separator joins one point's two numbers. All three are
 * control characters, so no tool id or note text can contain one, and [encode] drops a drawing
 * whose text does rather than writing a record that would parse back as different fields.
 *
 * Decoding never throws. A record written by an older version, or half-written when the process
 * died, is skipped; the alternative is an app that cannot open a chart because of a stored string.
 */
class ChartDrawingStore(private val dataStore: DataStore<Preferences>) {

    fun drawings(symbol: String): Flow<List<StoredDrawing>> = dataStore.data.map { preferences ->
        preferences[key(symbol)].orEmpty()
            .split(GROUP)
            .filter(String::isNotBlank)
            .mapNotNull(::decode)
    }

    suspend fun save(symbol: String, drawings: List<StoredDrawing>) {
        dataStore.edit { preferences ->
            if (drawings.isEmpty()) {
                // Removed rather than stored as an empty string, so a reader who clears a chart
                // leaves nothing behind for the next version to have to parse.
                preferences.remove(key(symbol))
            } else {
                preferences[key(symbol)] = drawings
                    .take(MAX_DRAWINGS)
                    .mapNotNull(::encode)
                    .joinToString(GROUP)
            }
        }
    }

    private fun key(symbol: String) = stringPreferencesKey(PREFIX + symbol.uppercase())

    private companion object {
        const val PREFIX = "chart_drawings_"

        /** Between two drawings. ASCII group separator. */
        const val GROUP = "\u001D"

        /** Between one drawing's fields. ASCII record separator. */
        const val RECORD = "\u001E"

        /** Between one point's time and price. ASCII unit separator. */
        const val UNIT = "\u001F"

        /**
         * How many drawings one symbol may keep.
         *
         * A cap rather than none, because this is a preferences file read whole on every chart
         * open: a reader who has drawn four hundred lines over a year should not pay for all of
         * them on every launch. Generous enough that nobody working on one chart reaches it.
         */
        const val MAX_DRAWINGS = 120

        fun encode(drawing: StoredDrawing): String? {
            val text = drawing.text.orEmpty()
            if (text.any { it == GROUP[0] || it == RECORD[0] || it == UNIT[0] }) return null
            if (drawing.toolId.isBlank()) return null
            val points = drawing.points.joinToString(",") { (time, price) -> "$time$UNIT$price" }
            return listOf(
                drawing.id.toString(),
                drawing.toolId,
                points,
                drawing.colour.toString(),
                drawing.widthDp.toString(),
                text,
                drawing.direction,
            ).joinToString(RECORD)
        }

        fun decode(record: String): StoredDrawing? {
            val parts = record.split(RECORD)
            if (parts.size != 7) return null
            val id = parts[0].toLongOrNull() ?: return null
            val toolId = parts[1].takeIf(String::isNotBlank) ?: return null
            val points = parts[2]
                .split(",")
                .filter(String::isNotBlank)
                .mapNotNull { pair ->
                    val halves = pair.split(UNIT)
                    if (halves.size != 2) return@mapNotNull null
                    val time = halves[0].toLongOrNull() ?: return@mapNotNull null
                    val price = halves[1].toDoubleOrNull() ?: return@mapNotNull null
                    time to price
                }
            // A drawing with no points is not a drawing: it would render as nothing and sit in the
            // list as a row nobody can select or delete.
            if (points.isEmpty()) return null
            return StoredDrawing(
                id = id,
                toolId = toolId,
                points = points,
                colour = parts[3].toLongOrNull() ?: DEFAULT_COLOUR,
                widthDp = parts[4].toFloatOrNull() ?: DEFAULT_WIDTH_DP,
                text = parts[5].takeIf(String::isNotBlank),
                direction = parts[6].takeIf(String::isNotBlank) ?: "UP",
            )
        }

        /** Matches `Drawing.DEFAULT_DRAWING_COLOUR`; duplicated rather than depended on. */
        const val DEFAULT_COLOUR = 0xFFD8A848

        const val DEFAULT_WIDTH_DP = 1.6f
    }
}
