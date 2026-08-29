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
    /** Whether the drawing refuses to be moved, edited or deleted. See `Drawing.locked`. */
    val locked: Boolean = false,
    val direction: String,
    /**
     * Which OHLC channel each point was magnet-bound to, aligned with [points], or empty for none.
     *
     * A plain string rather than `core:chart`'s `PriceChannel`, because this module does not depend
     * on the chart engine and must not start: the mapper at the call site already converts every
     * other field and converts this one too. An empty list means no point on this drawing was
     * placed with the magnet on, which is also what every row written before channels existed reads
     * back as.
     *
     * Why the channel and not the price it landed on: a price is a snapshot of a number the feed may
     * revise, and "the low of that bar" is what the reader actually chose. A drawing anchored by
     * channel follows a corrected bar; one anchored by a frozen price drifts off the low it was
     * drawn against.
     */
    val channels: List<String?> = emptyList(),
    /**
     * The interval the mark was drawn on — "H1", "D1" — or null when nothing said.
     *
     * Null and not the app's current interval, because "nothing said" is the truth about every row
     * written before this field existed, and inventing an interval for one of those would put a
     * confident wrong label on somebody's old work.
     */
    val timeframe: String? = null,
    /**
     * How far the mark travels between layouts: `NONE`, `LAYOUT` or `GLOBAL`.
     *
     * A plain string for the same reason [channels] and [direction] are: the enum lives in
     * `core:chart` and the mapper at the call site converts. A value this build does not recognise
     * belongs to somebody's newer app, and the mapper reads it back as the ordinary case rather
     * than as corruption.
     */
    val sync: String = DEFAULT_SYNC,
    /** The layout the mark was placed under, or null for the plain working chart. */
    val layoutId: String? = null,
    /** How many standard deviations a regression channel's rails sit at. Ignored by every other tool. */
    val deviations: Double = DEFAULT_DEVIATIONS,
    /**
     * The colour the mark's words are written in, or null to follow [colour].
     *
     * Null is a *third* state and not a shade: it says the reader never chose, so the text follows
     * the line and keeps following it when the line is recoloured. See the codec's note on why null
     * is written as an empty field rather than as a number.
     */
    val textColour: Long? = null,
    /** The colour washed inside the mark, or null to follow [colour]. The same three states. */
    val fillColour: Long? = null,
    /** `SOLID`, `DOTTED`, `DASHED`, `LARGE_DASHED` or `SPARSE_DOTTED`. A string, as [sync] is. */
    val lineStyle: String = DEFAULT_LINE_STYLE,
    /**
     * When a demonstration mark stops being drawn, or null for an ordinary one.
     *
     * Carried on this type and **never written to disk**: [ChartDrawingCodec.encode] refuses a
     * drawing that has one. A mark made to point at something while talking is meant to die in
     * eight seconds, and a store that persisted it would bring it back on the next launch already
     * expired — so what the reader would actually see is a chart that silently drops rows every
     * time it opens. Refusing at the codec is the one place both the per-symbol store and the
     * layout blob pass through.
     */
    val fadesAtMillis: Long? = null,
) {
    companion object {
        /** Matches `Drawing.DEFAULT_DRAWING_COLOUR`; duplicated rather than depended on. */
        const val DEFAULT_COLOUR = 0xFFD8A848

        const val DEFAULT_WIDTH_DP = 1.6f

        /** Matches `DrawingSync.LAYOUT` — the ordinary case, and what an absent field means. */
        const val DEFAULT_SYNC = "LAYOUT"

        /** Matches `LineStyleKind.SOLID`, which itself means "whatever the tool draws by default". */
        const val DEFAULT_LINE_STYLE = "SOLID"

        /** Matches `Drawing.DEFAULT_DEVIATIONS`. Two, which is what every screenshot was taken at. */
        const val DEFAULT_DEVIATIONS = 2.0
    }
}

/**
 * The delimited-text codec for a drawing, separators and all.
 *
 * Its own object rather than arithmetic private to [ChartDrawingStore], because a drawing is written
 * in two places now: on its own, keyed by symbol, and nested inside a [ChartLayout] that was saved
 * with the marks drawn on it. One codec with the separators passed in rather than two that drift —
 * a field added to one copy and not the other is a layout whose drawings come back subtly different
 * from the same drawings on the working chart.
 *
 * ### Three separators, and a second set for nesting
 *
 * ASCII's group separator joins drawings, its record separator joins one drawing's fields, its unit
 * separator joins one point's halves. A layout record already spends all three on its own
 * structure, so a drawing nested inside one is written with [NESTED_GROUP], [NESTED_RECORD] and
 * [NESTED_UNIT] instead. All six are control characters no keyboard produces, and [isUsable]
 * refuses a field containing *any* of them whichever set is in play — so a note that can be written
 * to the working chart can also be written into a layout, which is the property that stops a mark
 * disappearing when a layout is saved.
 *
 * ### The tolerances, and they are all one tolerance
 *
 * A record is **seven to fifteen** fields, and a point is **two or three** halves. The short forms
 * are what earlier builds wrote — seven fields before the lock, eight before the timeframe, two
 * halves before [StoredDrawing.channels] — and every one of them is read back with the missing
 * fields at their defaults rather than discarded. A reader who updates the app does not expect
 * their chart to come back empty, and a codec that only accepts what the current build writes turns
 * every added field into exactly that.
 *
 * The rule this file follows, and the one anything added later has to follow too: a new field goes
 * on the **end**, its absence has a meaning, and the length check becomes a range rather than an
 * equality. Nothing already written is ever reinterpreted.
 *
 * ### A null colour is an empty field, not a number
 *
 * [StoredDrawing.textColour] and [StoredDrawing.fillColour] are nullable, and their null means
 * "follow the line colour" — a different fact from any colour, `0L` included, which is a real one:
 * transparent black. So null is written as an **empty field** and a colour as its digits, and the
 * decoder tells them apart by `toLongOrNull` answering null on the empty string. A sentinel number
 * would have had to be a value nobody could ever choose, and there is no such value in a 32-bit
 * ARGB word.
 *
 * Decoding never throws. A record written by an older version, or half-written when the process
 * died, is skipped; the alternative is an app that cannot open a chart because of a stored string.
 */
internal object ChartDrawingCodec {

    /** Between two drawings. ASCII group separator. */
    const val GROUP = "\u001D"

    /** Between one drawing's fields. ASCII record separator. */
    const val RECORD = "\u001E"

    /** Between one point's time and price. ASCII unit separator. */
    const val UNIT = "\u001F"

    /** Between two drawings nested in another store's record. ASCII file separator. */
    const val NESTED_GROUP = "\u001C"

    /** Between one nested drawing's fields. ASCII synchronous idle, used as a fourth delimiter. */
    const val NESTED_RECORD = "\u0016"

    /** Between one nested point's halves. ASCII end-of-transmission-block, likewise. */
    const val NESTED_UNIT = "\u0017"

    /**
     * Every character that means something to either separator set.
     *
     * Checked as a whole rather than per set, so that a drawing which can be saved on the working
     * chart can also be saved into a layout. Checking only the set in play would let a note
     * carrying a file separator through the per-symbol store and then drop that same note when the
     * reader saved a layout — a mark that vanishes for no reason the reader can see.
     */
    private val SEPARATORS = charArrayOf(
        GROUP[0],
        RECORD[0],
        UNIT[0],
        NESTED_GROUP[0],
        NESTED_RECORD[0],
        NESTED_UNIT[0],
    )

    /**
     * Whether a free string is safe to write.
     *
     * Blank is not usable and is stored as an empty field, which reads back as "nothing said" — the
     * same answer by a shorter route. A value carrying a separator would parse back as a different
     * field entirely, so it is refused rather than sanitised: silently rewriting somebody's note is
     * worse than declining to write it.
     */
    fun isUsable(value: String?): Boolean =
        value != null && value.isNotBlank() && value.none { it in SEPARATORS }

    /** The value if it is usable, or null. */
    fun usable(value: String?): String? = value?.takeIf { isUsable(it) }

    /**
     * One drawing as a record, or null when it must not be written.
     *
     * Two reasons to refuse, and both are honest failures rather than sanitisation: a field that
     * carries a separator would parse back as different fields, and a demonstration mark has an
     * expiry — see [StoredDrawing.fadesAtMillis].
     */
    fun encode(
        drawing: StoredDrawing,
        record: String = RECORD,
        unit: String = UNIT,
    ): String? {
        if (drawing.fadesAtMillis != null) return null
        val text = drawing.text.orEmpty()
        if (text.isNotEmpty() && !isUsable(text)) return null
        if (!isUsable(drawing.toolId)) return null
        // Two halves for a point the reader placed freehand, three for one the magnet bound to a
        // bar's open, high, low or close. Written per point rather than per drawing because a
        // reader who turns the magnet on halfway through a polyline gets exactly that: some anchors
        // bound and the rest left where their finger was.
        val points = drawing.points.mapIndexed { index, (time, price) ->
            val channel = usable(drawing.channels.getOrNull(index))
            if (channel == null) "$time$unit$price" else "$time$unit$price$unit$channel"
        }.joinToString(",")
        return listOf(
            drawing.id.toString(),
            drawing.toolId,
            points,
            drawing.colour.toString(),
            drawing.widthDp.toString(),
            text,
            // Guarded like every other free field, though it only ever holds an enum name: this
            // record now also goes inside a layout's, where one stray separator would take the
            // whole layout with it rather than one drawing.
            usable(drawing.direction) ?: "UP",
            if (drawing.locked) "1" else "0",
            usable(drawing.timeframe).orEmpty(),
            usable(drawing.sync).orEmpty(),
            usable(drawing.layoutId).orEmpty(),
            drawing.deviations.toString(),
            // Empty for "follow the line colour". See the class note: `0` is a real colour.
            drawing.textColour?.toString().orEmpty(),
            drawing.fillColour?.toString().orEmpty(),
            usable(drawing.lineStyle).orEmpty(),
        ).joinToString(record)
    }

    /** One record back, or null when nothing addressable is left of it. */
    @Suppress("ReturnCount")
    fun decode(
        row: String,
        record: String = RECORD,
        unit: String = UNIT,
    ): StoredDrawing? {
        val parts = row.split(record)
        // Seven to fifteen. Fifteen is this version; the shorter forms are every drawing saved
        // before the lock, the timeframe, the sync, the layout, the deviations, the two extra
        // colours and the line style existed, and each is read back with its missing fields at
        // their defaults rather than discarded.
        if (parts.size !in 7..15) return null
        val id = parts[0].toLongOrNull() ?: return null
        val toolId = parts[1].takeIf(String::isNotBlank) ?: return null
        val decoded = parts[2]
            .split(",")
            .filter(String::isNotBlank)
            .mapNotNull { pair ->
                val halves = pair.split(unit)
                // Two or three. Two is a row written by the shipped build, before a point could
                // carry a channel, and it decodes to a point with no binding — which is the truth
                // about it: it was placed with the magnet off, or by a build that had no magnet
                // worth the name. Rejecting it would empty every chart drawn so far.
                if (halves.size !in 2..3) return@mapNotNull null
                val time = halves[0].toLongOrNull() ?: return@mapNotNull null
                val price = halves[1].toDoubleOrNull() ?: return@mapNotNull null
                Triple(time, price, usable(halves.getOrNull(2)))
            }
        // A drawing with no points is not a drawing: it would render as nothing and sit in the list
        // as a row nobody can select or delete.
        if (decoded.isEmpty()) return null
        return StoredDrawing(
            id = id,
            toolId = toolId,
            points = decoded.map { (time, price, _) -> time to price },
            colour = parts[3].toLongOrNull() ?: StoredDrawing.DEFAULT_COLOUR,
            widthDp = parts[4].toFloatOrNull() ?: StoredDrawing.DEFAULT_WIDTH_DP,
            text = parts[5].takeIf(String::isNotBlank),
            direction = parts[6].takeIf(String::isNotBlank) ?: "UP",
            locked = parts.getOrNull(7) == "1",
            // Collapsed to nothing when no point bound to anything, so a drawing that was placed
            // with the magnet off is equal to the one that was saved rather than differing by a
            // list of nulls nobody can see.
            channels = decoded.map { (_, _, channel) -> channel }
                .takeIf { list -> list.any { it != null } }
                .orEmpty(),
            timeframe = parts.getOrNull(8)?.takeIf(String::isNotBlank),
            sync = parts.getOrNull(9)?.takeIf(String::isNotBlank) ?: StoredDrawing.DEFAULT_SYNC,
            layoutId = parts.getOrNull(10)?.takeIf(String::isNotBlank),
            deviations = parts.getOrNull(11)?.toDoubleOrNull() ?: StoredDrawing.DEFAULT_DEVIATIONS,
            // An empty field is "follow the line colour"; digits are a colour, `0` included.
            textColour = parts.getOrNull(12)?.toLongOrNull(),
            fillColour = parts.getOrNull(13)?.toLongOrNull(),
            lineStyle = parts.getOrNull(14)?.takeIf(String::isNotBlank)
                ?: StoredDrawing.DEFAULT_LINE_STYLE,
        )
    }

    /** A list of drawings as one string, dropping any the codec refuses. */
    fun encodeAll(
        drawings: List<StoredDrawing>,
        group: String = GROUP,
        record: String = RECORD,
        unit: String = UNIT,
    ): String = drawings
        .take(MAX_DRAWINGS)
        .mapNotNull { encode(it, record, unit) }
        .joinToString(group)

    /** The list back, skipping anything unreadable rather than failing the lot. */
    fun decodeAll(
        stored: String,
        group: String = GROUP,
        record: String = RECORD,
        unit: String = UNIT,
    ): List<StoredDrawing> = stored
        .split(group)
        .filter(String::isNotBlank)
        .mapNotNull { decode(it, record, unit) }

    /** The same list, written to sit inside another store's record. See the class note. */
    fun encodeNested(drawings: List<StoredDrawing>): String =
        encodeAll(drawings, NESTED_GROUP, NESTED_RECORD, NESTED_UNIT)

    /** A nested blob back. */
    fun decodeNested(stored: String): List<StoredDrawing> =
        decodeAll(stored, NESTED_GROUP, NESTED_RECORD, NESTED_UNIT)

    /**
     * How many drawings one symbol — or one layout — may keep.
     *
     * A cap rather than none, because this is a preferences file read whole on every chart open: a
     * reader who has drawn four hundred lines over a year should not pay for all of them on every
     * launch. Generous enough that nobody working on one chart reaches it.
     */
    const val MAX_DRAWINGS = 120
}

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
 * than bar indices precisely so it can be. [StoredDrawing.timeframe] records which interval the
 * reader was looking at as *provenance*: a label on the mark, never a filter on it.
 *
 * ### The encoding
 *
 * [ChartDrawingCodec], which also carries the tolerance rules and the reason a null colour is
 * written as an empty field rather than as a number.
 */
class ChartDrawingStore(private val dataStore: DataStore<Preferences>) {

    fun drawings(symbol: String): Flow<List<StoredDrawing>> = dataStore.data.map { preferences ->
        ChartDrawingCodec.decodeAll(preferences[key(symbol)].orEmpty())
    }

    suspend fun save(symbol: String, drawings: List<StoredDrawing>) {
        dataStore.edit { preferences ->
            val encoded = ChartDrawingCodec.encodeAll(drawings)
            if (encoded.isEmpty()) {
                // Removed rather than stored as an empty string, so a reader who clears a chart —
                // or who drew nothing but demonstration marks, which are never written — leaves
                // nothing behind for the next version to have to parse.
                preferences.remove(key(symbol))
            } else {
                preferences[key(symbol)] = encoded
            }
        }
    }

    private fun key(symbol: String) = stringPreferencesKey(PREFIX + symbol.uppercase())

    private companion object {
        const val PREFIX = "chart_drawings_"
    }
}
