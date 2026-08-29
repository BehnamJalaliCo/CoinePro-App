package com.coinepro.feature.chart

import com.coinepro.core.chart.Drawing
import com.coinepro.core.datastore.DrawingTemplate

/**
 * The width a drawing is placed at when nobody has said otherwise.
 *
 * The same 1.6dp `Drawing` itself defaults to, restated here because this module has to be able to
 * *reset* to it — clearing a template has to put the width back, and a hard-coded literal at each
 * of the places that does so is three chances for them to disagree.
 */
const val DEFAULT_DRAWING_WIDTH_DP = 1.6f

/**
 * The narrowest and widest a saved style may be.
 *
 * Below the floor a line is a hairline that vanishes on a busy chart and looks like a rendering
 * fault; above the ceiling it is a band wide enough to hide the candles it was drawn against. The
 * range is generous inside those two — the point of a template is that somebody's own answer is
 * kept, not that the app has an opinion about it.
 */
const val MIN_DRAWING_WIDTH_DP = 0.5f

/** See [MIN_DRAWING_WIDTH_DP]. */
const val MAX_DRAWING_WIDTH_DP = 8f

/** The rail's eraser, which arms as a mode rather than as a tool. See [ChartController.arm]. */
internal const val ERASER_TOOL = "eraser"

/**
 * A width brought inside the bounds, with anything nonsensical sent back to the default.
 *
 * A NaN or a zero here is not hypothetical: a width arrives from a stored template written by an
 * older build, and a drawing placed at zero width is a drawing that is on the chart, is selectable,
 * is saved, and cannot be seen — which the reader reports as a drawing that was silently deleted.
 */
fun usableWidth(widthDp: Float): Float = when {
    // Zero and below go to the default rather than to the floor, and the difference matters: a
    // zero is not somebody asking for the thinnest line, it is a field that was never written —
    // an older record, a decode that fell through — and answering it with a hairline would be
    // guessing at an intention nobody expressed.
    widthDp.isNaN() || !widthDp.isFinite() || widthDp <= 0f -> DEFAULT_DRAWING_WIDTH_DP
    else -> widthDp.coerceIn(MIN_DRAWING_WIDTH_DP, MAX_DRAWING_WIDTH_DP)
}

/**
 * The drawings with one of them restyled, or the same list when nothing may change.
 *
 * A **locked** drawing is left exactly as it was, which is the same rule `DrawingActions.recolour`
 * follows and for the same reason: colour and width are edits, and the lock exists so that no
 * gesture can edit. The caller is expected to dim the control rather than rely on this, so the
 * refusal is visible before it happens; this is the guard that makes it true even when they forget.
 *
 * An id that is not in the list returns the list unchanged rather than throwing. A template applied
 * to a drawing the reader deleted a frame earlier is a race, not a fault.
 */
fun applyStyle(
    drawings: List<Drawing>,
    id: Long,
    colour: Long,
    widthDp: Float,
): List<Drawing> {
    val width = usableWidth(widthDp)
    var changed = false
    val next = drawings.map { drawing ->
        if (drawing.id != id || drawing.locked) {
            drawing
        } else {
            changed = true
            drawing.copy(colour = colour, widthDp = width)
        }
    }
    return if (changed) next else drawings
}

/**
 * Puts the reader's chosen width onto whatever was just placed.
 *
 * ### Why this is done here rather than by the drawing layer
 *
 * `DrawingState` carries a default colour and `DrawingActions` stamps it onto every drawing it
 * commits — but there is no matching field for width, and adding one would be a change to
 * `core:chart` for something only this module knows about, since a width comes out of a stored
 * template. So the placement is left exactly as it was and the width is stamped on the way back,
 * on the one drawing that is new.
 *
 * "New" is by id against the previous list, not by count. A commit can add a drawing and remove
 * another in the same state — undo followed by a fresh placement inside one frame — and a
 * count-based test would restyle the wrong one.
 */
fun stampWidth(
    next: List<Drawing>,
    previous: List<Drawing>,
    widthDp: Float,
): List<Drawing> {
    val width = usableWidth(widthDp)
    if (width == DEFAULT_DRAWING_WIDTH_DP) return next
    val known = previous.mapTo(HashSet(previous.size)) { it.id }
    var changed = false
    val stamped = next.map { drawing ->
        if (drawing.id in known || drawing.widthDp == width) {
            drawing
        } else {
            changed = true
            drawing.copy(widthDp = width)
        }
    }
    return if (changed) stamped else next
}

/**
 * Puts the hidden drawings back into the list the canvas handed over.
 *
 * The canvas is given the list with the hidden ones filtered out — see [ChartUiState.canvasDrawing]
 * — and hands its whole state straight back through `onDrawing`. Without this, every one of those
 * round trips would look exactly like the reader deleting everything they had hidden, and the
 * delete would be written to disk before the next frame.
 *
 * Each hidden drawing goes back at the index it held in [previous], which keeps z-order: a note
 * that was behind three trend lines is still behind them when it is shown again. Indices are
 * applied in ascending order so that each insertion leaves the ones after it where they were, and
 * an index past the end of the shorter list lands at the end rather than throwing — which is what
 * happens when the reader deletes visible drawings while others are hidden.
 */
fun mergeHidden(
    next: List<Drawing>,
    previous: List<Drawing>,
    hidden: Set<Long>,
): List<Drawing> {
    if (hidden.isEmpty()) return next
    val restored = previous.withIndex().filter { (_, drawing) -> drawing.id in hidden }
    if (restored.isEmpty()) return next
    val present = next.mapTo(HashSet(next.size)) { it.id }
    val merged = next.toMutableList()
    for ((index, drawing) in restored) {
        // A hidden drawing that somehow came back on the canvas's own list is left where the
        // canvas put it rather than added a second time. One drawing, one row.
        if (drawing.id in present) continue
        merged.add(index.coerceIn(0, merged.size), drawing)
    }
    return merged
}

/**
 * A new template capturing one drawing's style, under a name the reader typed.
 *
 * The id is generated from the clock in base thirty-six, the same way saved layouts are, because
 * the store keys on it and two templates are allowed to share a name — somebody who saves «قرمز»
 * twice has two templates, not one overwritten. [now] is passed in rather than read here so the
 * created date and the id come from one reading of the clock and so a test can assert both.
 */
fun templateOf(
    toolId: String,
    name: String,
    colour: Long,
    widthDp: Float,
    now: Long,
): DrawingTemplate = DrawingTemplate(
    id = "tpl_" + now.toString(TEMPLATE_ID_RADIX),
    toolId = toolId,
    name = name.trim(),
    colour = colour,
    widthDp = usableWidth(widthDp),
    createdAt = now,
)

/** Base thirty-six, so a millisecond clock becomes a short id rather than thirteen digits. */
private const val TEMPLATE_ID_RADIX = 36
