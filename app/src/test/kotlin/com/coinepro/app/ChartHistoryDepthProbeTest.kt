package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartDecoration
import com.coinepro.core.chart.ChartMarker
import com.coinepro.core.chart.ChartViewport
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.chart.MarkerGlyph
import com.coinepro.core.designsystem.CoineProTheme
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **What a frame costs as the chart's history grows** (run Υ, item 1).
 *
 * «چارت کُپ میکنه زمانی که به چپ و راست سوائپ میکنم.»
 *
 * Swiping right walks into history, and near the left edge the chart asks the archive for another
 * five thousand bars — up to a resident ceiling of fifty thousand. Run Τ widened a flick from about
 * one screen to two or three, so a reader working the chart back and forth crosses that margin far
 * more often than before and the series climbs towards the ceiling in a handful of gestures.
 *
 * A chart whose frame cost is a function of **bars on screen** does not care. One whose frame cost is
 * a function of **bars held** gets slower with every page, and the reader's word for that is «کُپ».
 * Which of the two this is cannot be argued from the source — the draw pass windows some things and
 * walks others — so it is measured.
 *
 * The figure is not milliseconds on a phone: this container has no GPU and Robolectric's Skia is not
 * the device's. It is the **ratio between two series lengths on one machine**, which is a property of
 * the code, and it is what says whether the history a reader has paged in is being paid for on every
 * frame.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartHistoryDepthProbeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val viewports = mutableListOf<ChartViewport>()

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
        view.draw(Canvas(Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)))
    }

    private val depth = mutableIntStateOf(SHALLOW)
    private val marked = mutableStateOf(false)
    private var started = false

    /**
     * What a structure study puts on a chart of [bars] bars.
     *
     * The chop band is one coloured mark per bar — see `ChartCatalog.structureFor` — so a chart
     * holding fifty thousand bars holds fifty thousand marks. That is the configuration the report
     * was made in, and a probe on a bare chart would measure a chart nobody was using.
     */
    private fun marksFor(series: CandleSeries): List<ChartMarker> = series.bars.mapIndexed { index, bar ->
        ChartMarker(
            time = bar.t,
            price = bar.l,
            above = false,
            colour = if (index % 3 == 0) 0xFF00B15C else 0xFFE5484D,
            glyph = MarkerGlyph.CIRCLE,
        )
    }

    /** Microseconds a drag frame costs on a chart holding [bars] bars. */
    private fun frameCost(bars: Int, marks: Boolean = false): Long {
        viewports.clear()
        if (!started) {
            started = true
            composeRule.setContent {
                CoineProTheme(darkTheme = true) {
                    val held = depth.intValue
                    val withMarks = marked.value
                    key(held, withMarks) {
                        val series = ScreenshotFixtures.chartSeries(bars = held)
                        CoineProChart(
                            series = series,
                            modifier = Modifier.fillMaxSize().testTag(TAG),
                            decoration = ChartDecoration(
                                markers = if (withMarks) marksFor(series) else emptyList(),
                            ),
                            onViewportChange = { viewports += it },
                        )
                    }
                }
            }
        }
        composeRule.runOnUiThread {
            depth.intValue = bars
            marked.value = marks
        }
        composeRule.waitForIdle()
        draw()
        val step = (PLOT_PX / viewports.first().barsPerView) * 2f
        val node = composeRule.onNodeWithTag(TAG)
        node.performTouchInput { down(Offset(width * 0.5f, height * 0.4f)) }
        composeRule.waitForIdle()
        // Warm the paths before the clock starts: the first frame of any drag pays for class
        // loading and a cold JIT, and that cost is about the machine rather than about the chart.
        repeat(WARM) { i ->
            node.performTouchInput { moveTo(Offset(width * 0.5f + step * (i + 1), height * 0.4f)) }
            composeRule.waitForIdle()
            draw()
        }
        var spent = 0L
        repeat(FRAMES) { i ->
            val started = System.nanoTime()
            node.performTouchInput {
                moveTo(Offset(width * 0.5f + step * (WARM + i + 1), height * 0.4f))
            }
            composeRule.waitForIdle()
            draw()
            spent += System.nanoTime() - started
        }
        node.performTouchInput { up() }
        composeRule.waitForIdle()
        return spent / FRAMES / 1_000
    }

    private fun ratio(of: Long, to: Long) = String.format(Locale.ROOT, "%.2f", of.toDouble() / to.toDouble())

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `a drag frame on a paged-in chart, bare and with a study's marks on it`() {
        // Warmed every way before anything is read. Robolectric's software rasteriser and the JIT
        // both improve over the first few hundred frames, and a sweep that measured the shallow
        // chart first would report that improvement as a property of the deep one.
        frameCost(SHALLOW)
        frameCost(DEEP)
        frameCost(SHALLOW, marks = true)
        frameCost(DEEP, marks = true)

        val bareShallow = frameCost(SHALLOW)
        val bareDeep = frameCost(DEEP)
        val markedShallow = frameCost(SHALLOW, marks = true)
        val markedDeep = frameCost(DEEP, marks = true)
        println(
            "drag frame: bare ${bareShallow}µs at $SHALLOW bars / ${bareDeep}µs at $DEEP " +
                "(ratio ${ratio(bareDeep, bareShallow)}); marked ${markedShallow}µs / ${markedDeep}µs " +
                "(ratio ${ratio(markedDeep, markedShallow)}) for ${DEEP / SHALLOW}× the history",
        )
        // **A probe, not a threshold.** The figures above are this container's, and they are worth
        // recording; what they are not is stable enough to gate a build on. Two runs of the same
        // measurement here differ by a factor of four, because the JVM is shared across the test
        // class and the rasteriser is software. The claim that a frame no longer pays for history
        // the reader is not looking at is made where it can be made honestly — in
        // `SignalMarkerThinningCostTest`, on the arithmetic, with no renderer in the way.
        //
        // What *is* asserted here is that the deep, marked chart draws at all. Fifty thousand marks
        // over fifty thousand bars used to build a fifty-thousand-entry index every frame; the
        // window is taken first now, and this is the case that says the whole path still works
        // end to end at that size.
        assertTrue("the deep chart with marks on it never drew a frame", markedDeep > 0)
        assertTrue("the deep chart never drew a frame", bareDeep > 0)
        assertTrue("the chart lost its window", viewports.isNotEmpty())
    }

    private companion object {
        const val TAG = "chart-depth-probe"
        const val PHONE = "fa-rIR-ldrtl-w411dp-h914dp-420dpi"
        const val WARM = 10
        const val FRAMES = 30
        const val SHALLOW = 2_500
        const val DEEP = 50_000
        const val PLOT_PX = (411f - 64f) * 420f / 160f
    }
}
