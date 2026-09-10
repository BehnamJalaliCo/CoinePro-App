package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartDecoration
import com.coinepro.core.chart.ChartLayerCounters
import com.coinepro.core.chart.ChartPoint
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.chart.Drawing
import com.coinepro.core.designsystem.CoineProTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * Draw-phase-only invalidation, counted — item 4 of the 4.58 run.
 *
 * Three layers: the bars' bitmap (grid, series, overlays, levels, markers), the drawings' bitmap,
 * and the cursor drawn live. The proof is in what each kind of frame costs: sixty cursor moves
 * re-render neither bitmap; a tick that rewrites the last bar re-renders the bars and blits the
 * drawings; a new bar moves the window and re-renders both. The same rig times a cursor frame on
 * this machine's JVM — not a phone, but a number that moves when the draw pass regresses.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-sw800dp-w1280dp-h800dp-xhdpi")
class ChartLayerInvalidationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val counters = ChartLayerCounters()
    private var series by mutableStateOf(walk(400))

    private fun compose() {
        composeRule.setContent {
            CoineProTheme {
                CoineProChart(
                    series = series,
                    modifier = Modifier.fillMaxSize().testTag(TAG),
                    decoration = ChartDecoration(drawings = drawings(series)),
                    layerCounters = counters,
                )
            }
        }
        composeRule.waitForIdle()
        draw()
        composeRule.waitForIdle()
        // The first pass sizes the plot; the second is the first steady frame.
        draw()
    }

    @Test
    fun `a cursor move re-renders no layer, a tick the bars only, a new bar both`() {
        compose()
        val seriesAfterFirst = counters.seriesMisses
        val overlayAfterFirst = counters.overlayMisses
        assertTrue(seriesAfterFirst >= 1 && overlayAfterFirst >= 1)

        // Sixty hover frames: the cursor is drawn live, nothing else re-renders.
        composeRule.onNodeWithTag(TAG).performMouseInput { enter(Offset(200f, 300f)) }
        for (step in 0 until 60) {
            composeRule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(200f + step * 6f, 300f)) }
            composeRule.waitForIdle()
            draw()
        }
        assertEquals("cursor moves re-rendered the bars", seriesAfterFirst, counters.seriesMisses)
        assertEquals("cursor moves re-rendered the drawings", overlayAfterFirst, counters.overlayMisses)
        assertTrue("the cursor frames were not drawn", counters.frames >= 60)

        // A tick: the last bar's close moves. The bars re-render, the drawings are blitted.
        series = ticked(series)
        composeRule.waitForIdle()
        draw()
        assertEquals("a tick did not re-render the bars", seriesAfterFirst + 1, counters.seriesMisses)
        assertEquals("a tick re-rendered the drawings", overlayAfterFirst, counters.overlayMisses)

        // A new bar: the window moves, both re-render.
        series = appended(series)
        composeRule.waitForIdle()
        draw()
        assertEquals(seriesAfterFirst + 2, counters.seriesMisses)
        assertEquals(overlayAfterFirst + 1, counters.overlayMisses)
    }

    @Test
    fun `a cursor frame and a tick frame are timed, and both numbers are printed`() {
        compose()
        composeRule.onNodeWithTag(TAG).performMouseInput { enter(Offset(150f, 280f)) }
        // Warm the JIT on both kinds of frame before either is timed, then interleave them so
        // that whatever else this machine is doing lands on both alike.
        repeat(WARM_UP) { step ->
            hover(step)
            draw()
            series = ticked(series)
            composeRule.waitForIdle()
            draw()
        }
        val cursor = LongArray(FRAMES)
        val tick = LongArray(FRAMES)
        for (step in 0 until FRAMES) {
            hover(step)
            cursor[step] = timed()
            series = ticked(series)
            composeRule.waitForIdle()
            tick[step] = timed()
        }
        cursor.sort()
        tick.sort()
        val cursorP50 = cursor[cursor.size / 2] / 1_000_000.0
        val cursorP95 = cursor[(cursor.size * 95) / 100] / 1_000_000.0
        val tickP50 = tick[tick.size / 2] / 1_000_000.0
        val tickP95 = tick[(tick.size * 95) / 100] / 1_000_000.0
        println(
            String.format(
                Locale.ROOT,
                "chart frame (JVM, Robolectric native graphics, 1280×800 dp at xhdpi, 400 bars, 3 drawings, %d frames each): " +
                    "cursor p50 %.2f ms p95 %.2f ms; tick p50 %.2f ms p95 %.2f ms",
                FRAMES, cursorP50, cursorP95, tickP50, tickP95,
            ),
        )
        // The number is the proof, not the gate: this JVM rasterises a 2560×1600 frame in software
        // and shares its cores with every other test in the run, so the ratio is read from an
        // isolated run and recorded in the report. What is asserted here is that neither kind of
        // frame is pathological — the miss counts in the test above are what pin the caching.
        assertTrue("a cursor frame took $cursorP50 ms", cursorP50 < 1_000.0)
        assertTrue("a tick frame took $tickP50 ms", tickP50 < 1_000.0)
    }

    private fun hover(step: Int) {
        composeRule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(150f + (step % 100) * 4f, 280f)) }
        composeRule.waitForIdle()
    }

    private fun timed(): Long {
        val started = System.nanoTime()
        draw()
        return System.nanoTime() - started
    }

    private fun draw() {
        val view = composeRule.activity.window.decorView
        val metrics = composeRule.activity.resources.displayMetrics
        if (view.width == 0 || view.height == 0) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
    }

    private fun drawings(series: CandleSeries): List<Drawing> {
        val at = { fraction: Double -> series.time[(series.size * fraction).toInt().coerceIn(0, series.size - 1)] }
        val price = { fraction: Double -> series.close[(series.size * fraction).toInt().coerceIn(0, series.size - 1)] }
        return listOf(
            Drawing(id = 1, toolId = "trend", points = listOf(ChartPoint(at(0.6), price(0.6) * 0.99), ChartPoint(at(0.95), price(0.95) * 1.01))),
            Drawing(id = 2, toolId = "hline", points = listOf(ChartPoint(at(0.5), price(0.5)))),
            Drawing(id = 3, toolId = "rect", points = listOf(ChartPoint(at(0.7), price(0.7) * 1.01), ChartPoint(at(0.9), price(0.9) * 0.99))),
        )
    }

    private fun ticked(series: CandleSeries): CandleSeries {
        val bars = series.bars.toMutableList()
        val last = bars.last()
        bars[bars.size - 1] = Candle(last.t, last.o, maxOf(last.h, last.c * 1.0005), last.l, last.c * 1.0005, last.v)
        return CandleSeries(bars)
    }

    private fun appended(series: CandleSeries): CandleSeries {
        val last = series.bars.last()
        return CandleSeries(series.bars + Candle(last.t + 3_600L, last.c, last.c * 1.002, last.c * 0.998, last.c * 1.001, last.v))
    }

    private fun walk(count: Int): CandleSeries {
        var seed = 5L
        fun random(): Double {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return ((seed ushr 11).toDouble() / (1L shl 53).toDouble())
        }
        var close = 2_600.0
        return CandleSeries(
            List(count) { index ->
                val open = close
                close = open + (random() - 0.5) * 6.0
                Candle(1_700_000_000L + index * 3_600L, open, maxOf(open, close) + random(), minOf(open, close) - random(), close, 1_000.0 + random() * 500)
            },
        )
    }

    private companion object {
        const val TAG = "chart"
        const val FRAMES = 60
        const val WARM_UP = 10
    }
}
