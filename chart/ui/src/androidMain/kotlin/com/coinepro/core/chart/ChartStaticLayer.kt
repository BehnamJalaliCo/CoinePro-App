package com.coinepro.core.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * A layer of the chart, drawn once and blitted until something under it changes.
 *
 * Three layers make a frame (item 4 of the 4.52 run, completed in 4.60.0): the **series layer** —
 * the grid, the bars, the volume, the overlays, the comparisons, the levels and the markers,
 * everything whose picture is a function of the viewport and the data, keyed on [StaticLayerKey];
 * the **drawings layer** — the reader's marks, keyed on [OverlayLayerKey], which is what positions
 * them and nothing else, so a tick that rewrites the last bar re-renders the series and blits the
 * drawings; and the **cursor layer**, which is its own `Canvas` and always was. A crosshair sweep,
 * a legend hover or a hover over a handle used to repaint two thousand candles per frame; now it
 * blits two bitmaps. `ChartLayerInvalidationTest` counts it: sixty cursor frames, zero misses.
 *
 * The cache is keyed on **everything the layer reads** rather than on the invalidation level
 * alone, because a decoration that changes without an `invalidate(…)` call would otherwise be
 * drawn from a stale picture. During a pan, a fling or a handle drag the key changes every frame
 * (the viewport moves, or the mark does) and the layer is redrawn every frame, into the same
 * bitmap: no worse than before, one blit more. It is the frames *between* gestures that this is for.
 */
internal class StaticLayerCache {
    private var bitmap: ImageBitmap? = null
    private var key: Any? = null

    /** How many frames were served from the bitmap; the draw pass counts hits for the benchmark. */
    var hits: Int = 0
        private set
    var misses: Int = 0
        private set

    /**
     * Blit the layer for [key], redrawing it through [content] first when the key moved.
     *
     * [topLeft] is where the layer sits in the caller's coordinate space — the caller is usually
     * already translated to the plot's left edge and passes the negative of that. The key is a
     * [StaticLayerKey] for the bars' layer and an [OverlayLayerKey] for the drawings' layer; the
     * cache only ever compares it with the last one by equality.
     */
    fun DrawScope.drawCached(
        key: Any,
        density: Density,
        layoutDirection: LayoutDirection,
        topLeft: Offset,
        content: DrawScope.() -> Unit,
    ) {
        val width = size.width.roundToInt().coerceAtLeast(1)
        val height = size.height.roundToInt().coerceAtLeast(1)
        var target = bitmap
        if (target == null || target.width != width || target.height != height) {
            target = ImageBitmap(width, height)
            bitmap = target
            this@StaticLayerCache.key = null
        }
        if (this@StaticLayerCache.key != key) {
            misses++
            val canvas = Canvas(target)
            CanvasDrawScope().draw(density, layoutDirection, canvas, Size(width.toFloat(), height.toFloat())) {
                drawRect(Color.Transparent, blendMode = BlendMode.Clear)
                content()
            }
            this@StaticLayerCache.key = key
        } else {
            hits++
        }
        drawImage(target, topLeft = topLeft)
    }

    fun clear() {
        bitmap = null
        key = null
    }
}

/**
 * What the bottom layer is a function of. Two equal keys draw the same picture; that is the whole
 * contract, and every field is a plain value or a reference the draw pass reads by identity.
 */
internal data class StaticLayerKey(
    val view: ChartViewport,
    val plotWidth: Float,
    val plotHeight: Float,
    val type: ChartType,
    val palette: Any,
    val decoration: Any,
    val shown: Any,
    val hidden: Any,
    val comparisonsRebased: Any?,
    val baseline: Any?,
    val ticks: Any?,
    val timeTicks: Any?,
    val densityScale: Float,
    val conflateGap: Any?,
)

/** What positions a drawing on the plot; a tick that only rewrites the last bar changes none of it. */
internal data class OverlayLayerKey(
    val firstVisibleTime: Long,
    val barsPerView: Int,
    val offset: Int,
    val pixelShift: Float,
    val priceZoom: Float,
    val priceRange: ClosedFloatingPointRange<Double>,
    val plotWidth: Float,
    val plotHeight: Float,
    val marks: Any,
    val highlighted: Long?,
    val grabbed: Int,
    val grabProgress: Float,
    val palette: Any,
    val densityScale: Float,
)

/**
 * The layers' hit and miss counts, read once per draw pass by a test.
 *
 * [frames] is the number of draw passes since it was attached; [seriesMisses] and [overlayMisses]
 * how many of them re-rendered the bars' bitmap and the drawings' bitmap. A cursor move should
 * add a frame and no miss; a tick a frame and one series miss; a pan a frame and both.
 */
class ChartLayerCounters {
    var frames: Int = 0
        private set
    var seriesMisses: Int = 0
        private set
    var overlayMisses: Int = 0
        private set

    internal fun record(series: StaticLayerCache, overlay: StaticLayerCache) {
        frames++
        seriesMisses = series.misses
        overlayMisses = overlay.misses
    }
}
