package com.coinepro.core.chart

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * One drawing as a row in the object tree.
 *
 * A flattened view rather than the [Drawing] itself, because everything the list needs is settled
 * once here — the Persian name, the distinguishing detail, whether the row is hidden — and a list
 * that recomputed those per frame while the reader scrolls would be doing the catalogue lookup and
 * the number formatting forty times a scroll.
 *
 * [hidden] is not a property of a drawing. It lives in the chart's state as a set of ids, so it is
 * passed into [ObjectTree.treeOf] and folded in here; a row that reported the drawing's own fields
 * only would have no way to draw the closed eye beside it.
 */
data class ObjectNode(
    val id: Long,
    val toolId: String,
    /** What the row reads: the tool's Persian name, plus a detail when the tool has one. */
    val label: String,
    val colour: Long,
    val locked: Boolean,
    val hidden: Boolean,
    val group: ToolGroup,
)

/**
 * One heading in the tree and the rows under it.
 *
 * Groups rather than one flat list, because the rail already teaches the reader that a trend line
 * is a «خط» and a Fibonacci retracement is a «فیبوناچی», and a tree that filed them differently
 * would be a second taxonomy to learn for no gain.
 */
data class ObjectGroup(val group: ToolGroup, val nodes: List<ObjectNode>)

/**
 * The drawings on a chart, as a list somebody can actually work with.
 *
 * ### Why this exists
 *
 * Everything a reader could do to a drawing went through the canvas: to lock a line they had to
 * tap it, and to tap it they had to find it. On a chart with forty objects on it — which is a
 * normal week's work on one instrument — finding a specific one by tapping is a search through
 * overlapping hit boxes, and the drawings hidden behind other drawings cannot be reached at all.
 * A list reaches every one of them in one scroll, and it is the only route to an object that is
 * off-screen or underneath something else.
 *
 * ### The order is the canvas's order, and that is the whole contract
 *
 * Groups come in the rail's own order ([DrawingTools.GROUPS]), and inside a group the rows are in
 * z-order with the **topmost first** — because the topmost is the one a tap on an overlap selects
 * ([DrawingHitTest.at] searches newest first), and a tree whose first row is not the object the
 * canvas would give you is worse than no tree: it teaches the reader a false model of their own
 * chart and every restack afterwards fights it.
 *
 * ### Pure Kotlin
 *
 * No Compose here. The tree is a projection of a list of values onto another list of values, which
 * makes every rule above a unit test rather than a screenshot.
 */
object ObjectTree {

    /**
     * The tree for one chart's drawings.
     *
     * [drawings] is the state's own list, oldest first — that order *is* z-order — and [hiddenIds]
     * is the set of ids the reader has switched off. A drawing whose tool this build does not know
     * is left out: the catalogue is the only source of both its group and its name, and a tool
     * that is not in the catalogue draws nothing on the canvas either, so a row for it would point
     * at empty space.
     *
     * A group with nothing in it is dropped rather than rendered as an empty heading.
     */
    fun treeOf(drawings: List<Drawing>, hiddenIds: Set<Long> = emptySet()): List<ObjectGroup> {
        if (drawings.isEmpty()) return emptyList()
        val nodes = drawings.mapNotNull { drawing ->
            val tool = DrawingTools[drawing.toolId] ?: return@mapNotNull null
            ObjectNode(
                id = drawing.id,
                toolId = drawing.toolId,
                label = labelOf(drawing),
                colour = drawing.colour,
                locked = drawing.locked,
                hidden = drawing.id in hiddenIds,
                group = tool.group,
            )
        }
        return DrawingTools.GROUPS.mapNotNull { group ->
            // Reversed, so the topmost drawing leads the group. `drawings` arrives oldest first
            // and the last one painted is the one on top.
            val inGroup = nodes.filter { it.group == group }.asReversed()
            if (inGroup.isEmpty()) null else ObjectGroup(group, inGroup)
        }
    }

    /**
     * What one row reads.
     *
     * The tool's Persian name alone is not enough on a chart carrying nine horizontal lines: nine
     * rows reading «خط افقی» identify nothing, and the reader is back to tapping. So each tool
     * contributes whatever distinguishes one of its instances from another — a level names its
     * price, a two-ended tool names when it starts and ends, a text tool says what it says — and a
     * tool with nothing to distinguish it is left with its name.
     *
     * Prices and dates are Latin digits under [Locale.US]. The device locale is Persian, and a
     * price formatted against it comes out in Persian digits with a Persian decimal separator,
     * which is not what a market figure looks like anywhere else in this app.
     */
    fun labelOf(drawing: Drawing): String {
        val name = DrawingTools[drawing.toolId]?.label ?: drawing.toolId
        val detail = detailOf(drawing)
        return if (detail == null) name else "$name $detail"
    }

    /**
     * Move one drawing to another place in the z-order, keeping every other drawing.
     *
     * [toIndex] indexes the list that came in — 0 is the back of the chart, `lastIndex` the front —
     * and is clamped rather than rejected, so a drag that overshoots the end of the list lands at
     * the end instead of doing nothing. The tree renders each group reversed, so a caller
     * reordering by dragging a row converts the row's position back to a z index before calling.
     *
     * Nothing is dropped and nothing is duplicated: an id that is not in the list returns the list
     * unchanged. That is the property the tests hold this to, because a restack that silently lost
     * a drawing would look exactly like a restack that worked.
     */
    fun reorder(drawings: List<Drawing>, id: Long, toIndex: Int): List<Drawing> {
        val from = drawings.indexOfFirst { it.id == id }
        if (from < 0) return drawings
        val target = toIndex.coerceIn(0, drawings.size - 1)
        if (target == from) return drawings
        val rest = drawings.toMutableList()
        val subject = rest.removeAt(from)
        rest.add(target, subject)
        return rest
    }

    /**
     * Put one drawing on top — which is also what makes it the one a tap on an overlap finds.
     *
     * The same operation [DrawingActions.bringToFront] performs on a whole state; this one works on
     * the list, so the tree can restack without holding a [DrawingState].
     */
    fun bringToFront(drawings: List<Drawing>, id: Long): List<Drawing> =
        reorder(drawings, id, drawings.size - 1)

    /** Put one drawing behind everything else. The way out of "my note is covering my chart". */
    fun sendToBack(drawings: List<Drawing>, id: Long): List<Drawing> = reorder(drawings, id, 0)

    /**
     * The detail that follows the tool's name, or null when the tool has none worth printing.
     *
     * Text wins over geometry when a drawing has text, because a note the reader typed is a better
     * name than the note's coordinates — that is the whole reason they typed it.
     */
    private fun detailOf(drawing: Drawing): String? {
        val text = drawing.text?.trim().orEmpty()
        if (text.isNotEmpty()) return truncate(text)
        val points = drawing.points
        if (points.isEmpty()) return null
        return when {
            drawing.toolId in PRICE_LEVEL_TOOLS -> priceText(points.first().price)
            drawing.toolId in TIME_TOOLS -> formatMoment(points.first().time)
            points.size >= 2 -> {
                val from = points.first().time
                val to = points.last().time
                // The same span rule the time axis uses: two moments inside a day are told apart by
                // their clock, and two further apart than that by their date. A trend line drawn
                // across a morning would read as one date twice if the date were always printed.
                val span = abs(to - from)
                "${formatTime(from, span)} $RANGE_DASH ${formatTime(to, span)}"
            }
            else -> formatMoment(points.first().time)
        }
    }

    /** A price at the precision its magnitude deserves, in Latin digits. */
    private fun priceText(price: Double): String =
        if (price.isFinite()) formatPrice(price, decimalsFor(price)) else NO_PRICE

    /**
     * A moment, in the reader's own zone.
     *
     * [Locale.US] formats the month name too, so a Persian device reads «12 Mar» rather than a
     * Gregorian month written in Persian script — which would look like a Jalali date and be wrong
     * by eleven days.
     */
    private fun formatTime(epochSeconds: Long, spanSeconds: Long): String = format(
        epochSeconds,
        when {
            spanSeconds >= MULTI_YEAR_SECONDS -> "MMM yy"
            spanSeconds >= MULTI_DAY_SECONDS -> "d MMM"
            else -> "HH:mm"
        },
    )

    /**
     * A single anchor's moment, with both its date and its clock.
     *
     * A one-point tool has no span to reason from, so neither half can be dropped: a vertical line
     * labelled only «14:30» says nothing on a daily chart, and one labelled only «12 Mar» says
     * nothing on a five-minute chart. Two-ended tools do have a span and use it.
     */
    private fun formatMoment(epochSeconds: Long): String = format(epochSeconds, "d MMM HH:mm")

    private fun format(epochSeconds: Long, pattern: String): String =
        Instant.ofEpochSecond(epochSeconds)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern(pattern, Locale.US))

    /**
     * A label is a row in a list, not the note itself.
     *
     * Cut on a whole line as well as on length: a note with three paragraphs in it must not push
     * the rows below it off the screen, and its first line is what the reader wrote first.
     */
    private fun truncate(text: String): String {
        val firstLine = text.lineSequence().first().trim()
        return if (firstLine.length <= MAX_DETAIL_LENGTH) {
            firstLine
        } else {
            firstLine.take(MAX_DETAIL_LENGTH).trimEnd() + ELLIPSIS
        }
    }

    /** The tools whose single point is a price level, so the price is what names them. */
    private val PRICE_LEVEL_TOOLS = setOf("hline", "hray", "pricelabel", "pricenote")

    /** The tools whose single point is a moment, so the moment is what names them. */
    private val TIME_TOOLS = setOf("vline", "crossline")

    /** How much of a note's first line a row shows before it stops being a row. */
    private const val MAX_DETAIL_LENGTH = 24

    private const val ELLIPSIS = "…"

    /** Between the two ends of a two-ended tool. An en dash, not a hyphen. */
    private const val RANGE_DASH = "–"

    /** What a non-finite price prints as, rather than «NaN» in the middle of a Persian list. */
    private const val NO_PRICE = "—"

    /** Past this much between two anchors, a label is a date rather than a clock. */
    private const val MULTI_DAY_SECONDS = 60L * 60 * 24 * 3

    /** And past this, a month and a year rather than a day. */
    private const val MULTI_YEAR_SECONDS = 60L * 60 * 24 * 400
}
