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
 * The chart's bottom layer, drawn once and blitted until something under it changes.
 *
 * Three layers make a frame (item 4 of the 4.52 run): this one — the grid, the bars, the volume,
 * the overlays, the comparisons, the levels and the markers, everything whose picture is a
 * function of the viewport and the data; the annotation layer — the reader's drawings and the
 * indicator panes — drawn live because a handle being dragged moves every frame; and the cursor
 * layer, which is its own `Canvas` and always was. A crosshair sweep, a legend hover or a drawing
 * being edited used to repaint two thousand candles per frame; now it repaints a bitmap.
 *
 * The cache is keyed on **everything the bottom layer reads** — see [StaticLayerKey] — rather than
 * on the invalidation level alone, because a decoration that changes without an `invalidate(…)`
 * call would otherwise be drawn from a stale picture. During a pan or a fling the key changes
 * every frame (the viewport moves) and the layer is redrawn every frame, into the same bitmap:
 * no worse than before, one blit more. It is the frames *between* gestures that this is for.
 */
internal class StaticLayerCache {
    private var bitmap: ImageBitmap? = null
    private var key: StaticLayerKey? = null

    /** How many frames were served from the bitmap; the draw pass counts hits for the benchmark. */
    var hits: Int = 0
        private set
    var misses: Int = 0
        private set

    /**
     * Blit the layer for [key], redrawing it through [content] first when the key moved.
     *
     * [topLeft] is where the layer sits in the caller's coordinate space — the caller is usually
     * already translated to the plot's left edge and passes the negative of that.
     */
    fun DrawScope.drawCached(
        key: StaticLayerKey,
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
