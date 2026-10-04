package com.coinepro.core.chart

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Where the chart's own controls are on the glass, for the coach (5.21.0).
 *
 * The plot, the price column, the `A`/`L` keys, the «»» key and the legend are drawn inside the
 * chart rather than laid out as nodes a screen can put a modifier on, so the chart reports them
 * here and the screen hands them to the coach. Every rectangle is in root pixels. Provided by the
 * screen through [LocalChartCoachAnchors] only where a coach is listening; a chart that is not
 * given one reports nothing and pays nothing.
 */
@Stable
class ChartCoachAnchors {
    /** The candles' rectangle, the price column and time axis excluded. */
    var plot by mutableStateOf<Rect?>(null)
        private set

    /** The price column beside the plot. */
    var gutter by mutableStateOf<Rect?>(null)
        private set

    /** The strip along the foot of the plot where event and signal marks sit. */
    var events by mutableStateOf<Rect?>(null)
        private set

    /** The `A` and `L` keys. */
    var minis by mutableStateOf<Rect?>(null)
        internal set

    /** The «»» key, while it shows. */
    var realtime by mutableStateOf<Rect?>(null)
        internal set

    /** The legend's head rows at the plot's top-left corner. */
    var legend by mutableStateOf<Rect?>(null)
        private set

    /** The chart's own top-left corner in root pixels, from its layout. */
    internal var origin: Offset = Offset.Zero

    /** From the draw pass: the plot and the gutter in canvas pixels. Writes only on a change. */
    internal fun publish(plotLocal: Rect, gutterLocal: Rect?, eventsHeight: Float, legendWidth: Float, legendHeight: Float) {
        val plotRoot = plotLocal.translate(origin)
        if (plotRoot != plot) plot = plotRoot
        val gutterRoot = gutterLocal?.translate(origin)
        if (gutterRoot != gutter) gutter = gutterRoot
        val strip = Rect(plotRoot.left, plotRoot.bottom - eventsHeight, plotRoot.right, plotRoot.bottom)
        if (strip != events) events = strip
        val head = Rect(
            plotRoot.left,
            plotRoot.top,
            plotRoot.left + minOf(legendWidth, plotRoot.width * 0.7f),
            plotRoot.top + minOf(legendHeight, plotRoot.height * 0.3f),
        )
        if (head != legend) legend = head
    }
}

/** The anchors a screen wants filled. Null by default: no coach, no reporting. */
val LocalChartCoachAnchors = staticCompositionLocalOf<ChartCoachAnchors?> { null }
