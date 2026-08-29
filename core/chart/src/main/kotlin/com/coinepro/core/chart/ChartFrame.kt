package com.coinepro.core.chart

import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min

/**
 * Where the plot actually sits inside the canvas, once the price gutters have been taken off it.
 *
 * ### Why this is a type rather than two local floats
 *
 * Because [ScaleSide] was stored on [ChartViewport] for a whole wave and never read: the canvas
 * measured *every* gutter against the right edge — the plot rectangle, the labels, the tag chips,
 * and eight gesture handlers that each wrote their own `size.width - axisWidth` — so a reader who
 * moved the axis to the left got a chart that looked identical and a long press that opened the
 * axis menu on the wrong side of the screen. One rectangle, computed once and handed to both the
 * draw pass and the gestures, is what makes the four sides impossible to half-implement: nothing
 * downstream is allowed to know where the gutter is except by asking this.
 *
 * Everything here is in **canvas** pixels. The draw pass translates by [left] and from that point
 * on works in *plot* pixels, which is the space [ChartViewport.xOf] has always spoken; the two
 * gutter x's below are given in that translated space for exactly that reason, so a draw function
 * never has to un-translate itself to find the strip it is painting into.
 */
internal data class PlotFrame(
    /** The plot's left edge in canvas pixels — non-zero only when a gutter sits to its left. */
    val left: Float,
    /** How wide the plot is, after both gutters are removed. */
    val width: Float,
    /** The left gutter's width, or zero when there is not one. */
    val leftGutter: Float,
    /** The right gutter's width, or zero when there is not one. */
    val rightGutter: Float,
) {
    /** The plot's right edge in canvas pixels. */
    val right: Float get() = left + width

    /**
     * Whether the gutter that carries the tags — the live price, the countdown, the crosshair — is
     * the right-hand one.
     *
     * With gutters on both sides the tags go on the right, and that is not arbitrary: the right of
     * the plot is the live edge, and a price tag is a statement about the newest bar. The ladder of
     * gridline labels is drawn in both, because that is what asking for two axes means.
     */
    val tagsOnRight: Boolean get() = rightGutter > 0f

    /** The left edge of the tag gutter, in the translated plot space the draw pass works in. */
    val tagGutterX: Float get() = if (tagsOnRight) width else -leftGutter

    /** How wide the tag gutter is. Zero when the axis is switched off entirely. */
    val tagGutterWidth: Float get() = if (tagsOnRight) rightGutter else leftGutter
}

/**
 * The plot rectangle for a canvas [canvasWidth] wide with a gutter [axisWidth] across.
 *
 * [ScaleSide.MERGED] resolves to the same rectangle as [ScaleSide.RIGHT], and that is a decision
 * rather than an omission: this canvas maps every series it draws — the overlays, the comparisons,
 * the levels — onto the one price axis already, so "merged" is the arrangement it has always been
 * in, and what the mode buys is that a caller storing it gets the right-hand gutter rather than
 * whatever the enum's first entry happens to be.
 *
 * With the axis switched off — a thumbnail, a list row — there is no gutter at all and the plot is
 * the whole canvas. That has to be decided here rather than by each caller subtracting a width it
 * was told to ignore, which is the shape the bug took the first time.
 */
internal fun plotFrame(
    canvasWidth: Float,
    axisWidth: Float,
    side: ScaleSide,
    axes: Boolean,
): PlotFrame {
    if (!axes || axisWidth <= 0f) {
        return PlotFrame(left = 0f, width = max(0f, canvasWidth), leftGutter = 0f, rightGutter = 0f)
    }
    return when (side) {
        ScaleSide.RIGHT, ScaleSide.MERGED -> PlotFrame(
            left = 0f,
            width = max(0f, canvasWidth - axisWidth),
            leftGutter = 0f,
            rightGutter = axisWidth,
        )
        ScaleSide.LEFT -> PlotFrame(
            left = min(axisWidth, canvasWidth),
            width = max(0f, canvasWidth - axisWidth),
            leftGutter = min(axisWidth, canvasWidth),
            rightGutter = 0f,
        )
        // Both gutters, and the plot pays for both. On a phone that is most of a thumb's width of
        // chart given up, which is why nothing selects this by default — it is a tablet's mode.
        ScaleSide.BOTH -> {
            val gutter = min(axisWidth, canvasWidth / 2f)
            PlotFrame(
                left = gutter,
                width = max(0f, canvasWidth - gutter * 2),
                leftGutter = gutter,
                rightGutter = gutter,
            )
        }
    }
}

/**
 * Whether a touch at canvas x [x] counts as landing in a price gutter.
 *
 * [reachPx] widens the strip *into* the plot, because the labels are what a reader aims at and the
 * gutter alone is under the minimum tap target once its padding is taken off. It is deliberately
 * not symmetrical: the strip grows inward only, so a finger that has left the canvas edge is still
 * on the axis and a finger a thumb's width into the candles is not.
 */
internal fun PlotFrame.inGutter(x: Float, reachPx: Float): Boolean =
    (rightGutter > 0f && x >= right - reachPx) || (leftGutter > 0f && x <= left + reachPx)

/** Whether a touch at canvas x [x] is on the plot rather than in either gutter. */
internal fun PlotFrame.onPlot(x: Float): Boolean = x >= left && x < right

/**
 * A canvas position expressed in the plot space every [ChartViewport] conversion speaks.
 *
 * Every gesture goes through this and none of them subtracts the offset itself. That is the whole
 * discipline: a handler that measured its own x is a handler that will be correct for the
 * right-hand gutter and quietly wrong for the other three.
 */
internal fun PlotFrame.toPlot(position: Offset): Offset = Offset(position.x - left, position.y)
