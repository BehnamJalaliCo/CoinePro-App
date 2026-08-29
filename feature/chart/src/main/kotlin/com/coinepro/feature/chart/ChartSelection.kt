package com.coinepro.feature.chart

import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.DrawingState

/**
 * The arithmetic behind selecting more than one drawing, and behind putting a copy somewhere the
 * reader can see it.
 *
 * Both are pure and both are here rather than in the controller for the same reason the drawing
 * state machine is pure: "the second tap added the wrong thing to the selection" and "the pasted
 * line landed exactly under the original" are bugs that are obvious in a unit test and nearly
 * invisible on a device.
 */

/**
 * The selection the reader actually meant, given that the canvas cannot express it.
 *
 * `CoineProChart` selects by calling `DrawingActions.tapSnapped`, which selects with
 * `additive = false` — by design, since the canvas has no modifier key and no idea whether the
 * reader is collecting things. So the multi-select latch lives on this side and this function
 * re-applies it to whatever the canvas hands back.
 *
 * ### What it does and does not touch
 *
 * It runs only on a state that is a *selection change and nothing else*: a tool is not armed, and
 * the drawings themselves are unchanged. A placement, a delete, a drag of a handle and an erase
 * all come through `onDrawing` too, and every one of them arrives carrying a selection the drawing
 * layer set for its own reasons — a freshly placed drawing selects itself, an erase selects the
 * pieces. Widening any of those would be this function inventing an intention.
 *
 * ### Tapping something already in the selection removes it
 *
 * Which is what every multi-select on every platform does, and it is the only way back out of a
 * mis-tap without clearing the whole set and starting again. [DrawingState.selectedId] follows to
 * whatever is left, because that is the drawing whose handles are drawn and whose drag would move
 * something; pointing it at a drawing the reader has just deselected would leave handles on an
 * object that is no longer part of the group.
 *
 * ### A tap on empty space still clears
 *
 * Even with the latch on. "Nothing is under my finger" is not an item to add, and a latch that made
 * empty space unclickable would be a mode the reader cannot leave by the obvious gesture.
 */
fun widenSelection(previous: DrawingState, next: DrawingState, multiSelect: Boolean): DrawingState {
    if (!multiSelect) return next
    if (next.tool != null) return next
    if (next.drawings != previous.drawings) return next
    val id = next.selectedId ?: return next
    // Anything the drawing layer already treated as a multiple is left alone: this exists to widen
    // a single replacing selection, not to second-guess one that arrived wider.
    if (next.selection != setOf(id)) return next
    val widened = if (id in previous.selection) previous.selection - id else previous.selection + id
    return next.copy(selection = widened, selectedId = if (id in widened) id else widened.lastOrNull())
}

/**
 * How far a pasted copy is moved from its original, in chart space.
 *
 * ### Why the offset is not zero
 *
 * `DrawingActions.paste` allows zero and says so — a paste onto a *different* symbol wants it. On
 * the same chart it is the wrong answer: the copy lands exactly under the original, the reader
 * sees nothing happen, and they paste three more times before looking at the object tree.
 *
 * ### Why it is not a fixed number either
 *
 * "A little to the right" is a number of seconds that depends entirely on the bar length — three
 * bars is nine hundred seconds on the five-minute and nine days on the daily — and "a little up"
 * is a number of dollars that depends on the instrument, where gold moves in tens and a token
 * moves in millionths. Both are therefore measured in the chart's own terms: bars for the one,
 * a fraction of the visible high-low span for the other.
 *
 * ### Why both axes move
 *
 * A time-only offset is invisible on the tools that ignore time. A horizontal line drawn at a price
 * renders across the whole plot however far along the x-axis its anchor sits, so a copy shifted
 * three bars to the right is a copy drawn on top of the original — which is the failure this
 * function exists to avoid, arriving on exactly the tool a reader duplicates most.
 *
 * An empty series gives a zero price offset rather than a guess. There is no span to take a
 * fraction of, and a chart with no bars has nothing to hide a copy behind either.
 */
fun pasteOffset(series: CandleSeries, intervalSeconds: Long): Pair<Long, Double> {
    val time = intervalSeconds.coerceAtLeast(1L) * PASTE_BARS
    if (series.isEmpty) return time to 0.0
    val span = series.high.max() - series.low.min()
    val price = if (span.isFinite() && span > 0.0) span * PASTE_PRICE_FRACTION else 0.0
    return time to price
}

/**
 * Three bars to the right.
 *
 * Far enough that the copy and the original are two objects to a thumb — a finger is about eight
 * bars wide at the default zoom — and near enough that the copy is still on screen, which a paste
 * that landed off the edge would not be.
 */
private const val PASTE_BARS = 3

/**
 * And one-fiftieth of the visible price span upward.
 *
 * Two per cent of the plot's height. Enough to separate two horizontal lines at a glance, small
 * enough that a duplicated trend line is still obviously a duplicate of the one under it rather
 * than a different level.
 */
private const val PASTE_PRICE_FRACTION = 0.02
