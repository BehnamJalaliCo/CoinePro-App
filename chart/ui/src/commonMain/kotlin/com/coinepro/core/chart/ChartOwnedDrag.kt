package com.coinepro.core.chart

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange

/** Which way a zone's drag is allowed to run. See [awaitOwnedDrag]. */
internal enum class ChartDragAxis { HORIZONTAL, VERTICAL, ANY }

/**
 * A drag that **decides whether it wants the gesture before it consumes any of it** (run Ξ, item 4).
 *
 * ### The defect this exists to make unrepeatable
 *
 * Compose's `detectDragGestures`, `detectHorizontalDragGestures` and `detectVerticalDragGestures`
 * consume unconditionally: the lambda that crosses the touch slop calls `change.consume()`, and the
 * drag loop after it calls `it.consume()` on every event. The callbacks — `onDragStart`, `onDrag` —
 * run *after* that. So a handler written as «install the detector, then work out in `onDrag`
 * whether this gesture was mine and return early if it was not» has already taken the gesture from
 * everything else on the chart by the time it decides it did not want it.
 *
 * Four handlers on this canvas were written exactly that way, and two of them alone were enough to
 * kill panning: a drag in the middle of the plot was consumed by the **time-axis** scale handler,
 * whose `if (!onTimeAxis) return` sat three lines below the consumption. `detectTransformGestures`
 * — the pan — ends its gesture on the first consumed event and does not start another until the
 * next `DOWN`, which is why the owner's recordings show a burst of one or two frames and then
 * seconds of nothing with the finger still on the glass. `docs/runs/RUN_XI/trace.md` has the
 * bisection.
 *
 * ### The shape
 *
 * [claims] is asked once, at the **down**, about the position the finger actually landed on. A
 * gesture it refuses is dropped without a single `consume()` — `awaitEachGesture` drains the
 * remaining pointers on the final pass — so the pan, the pinch and the tap detectors see it exactly
 * as though this handler were not in the tree. A gesture it accepts is consumed from the slop
 * onwards, which is what gives it the same protection in the other direction.
 *
 * Asked at the down rather than at the drag start, and that is a deliberate difference from the
 * detectors this replaces: Compose reports `onDragStart` at the post-slop position, so a finger
 * that landed a few pixels off the price gutter and then slid into it used to be claimed by the
 * gutter. Where the finger **landed** is the answer a reader would give.
 */
internal suspend fun PointerInputScope.awaitOwnedDrag(
    axis: ChartDragAxis,
    claims: (Offset) -> Boolean,
    onStart: (Offset) -> Unit = {},
    onEnd: () -> Unit = {},
    onCancel: () -> Unit = {},
    onDrag: (PointerInputChange, Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // Nothing above this line consumes, and nothing below it runs unless the zone said yes.
        if (!claims(down.position)) return@awaitEachGesture
        onStart(down.position)
        var overSlop = Offset.Zero
        val first = when (axis) {
            ChartDragAxis.HORIZONTAL -> awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                change.consume()
                overSlop = Offset(over, 0f)
            }
            ChartDragAxis.VERTICAL -> awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                change.consume()
                overSlop = Offset(0f, over)
            }
            ChartDragAxis.ANY -> awaitTouchSlopOrCancellation(down.id) { change, over ->
                change.consume()
                overSlop = over
            }
        }
        if (first == null) {
            onCancel()
            return@awaitEachGesture
        }
        // The travel past the slop is spent, not discarded (run Ξ, item 6): the first delta this
        // reports is what the finger moved *beyond* the threshold, so a drag of slop-plus-one-pixel
        // moves the picture by one pixel rather than by nothing.
        onDrag(first, overSlop)
        val completed = when (axis) {
            ChartDragAxis.HORIZONTAL -> horizontalDrag(first.id) { change ->
                onDrag(change, Offset(change.positionChange().x, 0f))
                change.consume()
            }
            ChartDragAxis.VERTICAL -> verticalDrag(first.id) { change ->
                onDrag(change, Offset(0f, change.positionChange().y))
                change.consume()
            }
            ChartDragAxis.ANY -> drag(first.id) { change ->
                onDrag(change, change.positionChange())
                change.consume()
            }
        }
        if (completed) onEnd() else onCancel()
    }
}
