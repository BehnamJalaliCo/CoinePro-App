package com.coinepro.core.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The bottom layer is drawn once per key and blitted after.
 *
 * Two frames with the same key: one miss, one hit, and the second frame's pixels are the first's.
 * A frame with another key: a miss, and the picture follows the new content. This is what makes
 * a crosshair sweep cost a blit rather than a repaint of every candle — item 4 of the 4.52 run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StaticLayerCacheTest {

    private val testDensity = Density(1f)
    private val series = CandleSeries(List(3) { Candle(it * 60L, 1.0, 2.0, 0.5, 1.5, 10.0) })

    private fun key(offset: Int) = StaticLayerKey(
        view = ChartViewport(series, barsPerView = 3, offset = offset),
        plotWidth = 40f, plotHeight = 40f, type = ChartType.CANDLES, palette = "p", decoration = "d",
        shown = "s", hidden = emptySet<Any>(), comparisonsRebased = null, baseline = null,
        ticks = null, timeTicks = null, densityScale = 1f, conflateGap = null,
    )

    private fun frame(cache: StaticLayerCache, key: StaticLayerKey, colour: Color): ImageBitmap {
        val target = ImageBitmap(40, 40)
        CanvasDrawScope().draw(testDensity, LayoutDirection.Ltr, Canvas(target), Size(40f, 40f)) {
            with(cache) {
                drawCached(key, testDensity, LayoutDirection.Ltr, Offset.Zero) {
                    drawRect(colour, size = Size(40f, 40f))
                }
            }
        }
        return target
    }

    @Test
    fun `the same key is drawn once and blitted after`() {
        val cache = StaticLayerCache()
        val first = frame(cache, key(0), Color.Red)
        val second = frame(cache, key(0), Color.Blue)   // content would be blue, but the cache holds red
        assertEquals(1, cache.misses)
        assertEquals(1, cache.hits)
        assertEquals(first.toPixelMap()[20, 20], second.toPixelMap()[20, 20])
        assertEquals(Color.Red, second.toPixelMap()[20, 20])
    }

    @Test
    fun `a new key is a new picture`() {
        val cache = StaticLayerCache()
        frame(cache, key(0), Color.Red)
        val moved = frame(cache, key(1), Color.Blue)
        assertEquals(2, cache.misses)
        assertEquals(0, cache.hits)
        assertEquals(Color.Blue, moved.toPixelMap()[20, 20])
    }
}
