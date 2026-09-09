package com.coinepro.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The hinge, as far as the layout cares about it.
 *
 * A foldable reports a `FoldingFeature` through `androidx.window`; that library is the activity's
 * business (`MainActivity` observes it and provides this), and this value class is what every
 * screen reads so that `core:designsystem` and its plain-JVM tests do not take a dependency on a
 * window tracker that needs an `Activity` to answer. The three things the product decides on:
 *
 * - **[tableTop]** — the device is half-open on a table, hinge horizontal, like a laptop. The chart
 *   keeps its plot in the top half so the crease does not cut through the price scale, and the
 *   tools and readings take the bottom half, which is where the reader's hands already are.
 * - **[book]** — half-open with the hinge vertical. Nothing is placed across a vertical hinge; the
 *   two-pane layouts already split there because the window class says EXPANDED, so this is read
 *   only by the parity report and by a layout that would otherwise centre a dialog on the crease.
 * - **[hingeTopDp]** / **[hingeBottomDp]** — where the crease is, in dp from the top of the window,
 *   when it is horizontal. A layout that has to avoid it caps a height at [hingeTopDp].
 *
 * [Flat] is the value for every device without a hinge and for an open foldable: no posture,
 * no crease, and every layout is exactly what it is on a tablet of the same window class.
 */
@Immutable
data class CoineProFold(
    val tableTop: Boolean = false,
    val book: Boolean = false,
    val hingeTopDp: Int = 0,
    val hingeBottomDp: Int = 0,
) {
    /** Whether any half-open posture is in effect. */
    val halfOpened: Boolean get() = tableTop || book

    companion object {
        /** No hinge, or a hinge lying flat: the ordinary case. */
        val Flat = CoineProFold()

        /**
         * The posture from a window library's folding feature, kept free of that library's types
         * so the mapping can be asserted on the JVM. `orientation` is horizontal when the hinge runs
         * across the window (table-top), vertical when it runs down it (book); `separating` is the
         * feature's own word for a hinge that divides the window into two areas.
         */
        fun of(
            halfOpened: Boolean,
            horizontalHinge: Boolean,
            separating: Boolean,
            hingeTopDp: Int,
            hingeBottomDp: Int,
        ): CoineProFold {
            if (!halfOpened || !separating) return Flat
            return if (horizontalHinge) {
                CoineProFold(tableTop = true, hingeTopDp = hingeTopDp, hingeBottomDp = hingeBottomDp)
            } else {
                CoineProFold(book = true)
            }
        }
    }
}

/** The posture for the whole app. Provided by the activity; [CoineProFold.Flat] everywhere else. */
val LocalCoineProFold = staticCompositionLocalOf { CoineProFold.Flat }

@Composable
@ReadOnlyComposable
fun coineProFold(): CoineProFold = LocalCoineProFold.current
