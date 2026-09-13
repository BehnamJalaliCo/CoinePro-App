package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.coinepro.core.chart.ChartViewport
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.datastore.ReaderMode
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.feature.chart.ChartScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Two fingers on the chart** (run Σ, S1).
 *
 * ### What the owner reported
 *
 * «pinch افقی روی چارت کار نمی‌کند، عمودی کار می‌کند.» Horizontal did nothing; vertical worked. Both
 * halves came out of one line: the time axis was driven by `1.0025.pow(spanX − lastX)` and then put
 * through a one-per-cent dead zone, so on a 120 Hz phone a brisk separation produced a ratio of
 * about 1.005 every frame and was refused every frame. The price axis used a plain span ratio, which
 * cleared the same dead zone easily. One axis worked and the other was arithmetically unreachable.
 *
 * ### Why this is an injected gesture and not a unit test
 *
 * Because the arithmetic was never the thing that was wrong in a way anybody could see — it was the
 * arithmetic *reached through a real event stream*, with a dead zone in front of it and a routing
 * rule beside it. A test of the ratio would have passed on the broken build. These push two pointers
 * through the same pipeline a thumb does and read what the chart publishes.
 *
 * `PinchZoneTest` in `:chart-ui` holds the routing rule on its own, over every point of the canvas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartPinchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val viewports = mutableListOf<ChartViewport>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    /**
     * Bumped to throw the chart away and compose a fresh one.
     *
     * An activity's content can only be set once, and one test here needs two charts that both start
     * from rest. `key()` on a generation counter is how a test gets a second one: the old chart is
     * disposed with its viewport and the new one composes at the resting window.
     */
    private val generation = mutableIntStateOf(0)
    private var started = false

    private fun chart() {
        viewports.clear()
        if (!started) {
            started = true
            composeRule.setContent {
                CoineProTheme(darkTheme = true) {
                    key(generation.intValue) {
                        CoineProChart(
                            series = ScreenshotFixtures.chartSeries(),
                            modifier = Modifier.fillMaxSize().testTag(TAG),
                            onViewportChange = { viewports += it },
                        )
                    }
                }
            }
        } else {
            composeRule.runOnUiThread { generation.intValue++ }
        }
        composeRule.waitForIdle()
        // Robolectric composes but does not run the render pipeline, and the chart learns its frame
        // — which gutter is where, and therefore which zone a finger landed in — in its draw pass.
        // One draw into an off-screen bitmap gives it that. See `ChartDeskPointerTest`.
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
        composeRule.waitForIdle()
    }

    /** The window the chart was on before the gesture, which is what every assertion is against. */
    private fun resting(): ChartViewport = viewports.first()

    private fun latest(): ChartViewport = viewports.last()

    /**
     * Two pointers from [fromA]/[fromB] to [toA]/[toB], moved together.
     *
     * Both pointers are updated and then one `move()` is sent, so each frame carries both — which is
     * what a pinch is. Moving them one at a time would send a stream of single-pointer moves and the
     * handler would be measuring a span that is half stale on every other event.
     */
    private fun pinch(fromA: Offset, fromB: Offset, toA: Offset, toB: Offset, steps: Int = 16) {
        composeRule.onNodeWithTag(TAG).performTouchInput {
            down(0, fromA)
            down(1, fromB)
            repeat(steps) { step ->
                val t = (step + 1f) / steps
                updatePointerTo(0, lerp(fromA, toA, t))
                updatePointerTo(1, lerp(fromB, toB, t))
                move()
            }
            up(0)
            up(1)
        }
        composeRule.waitForIdle()
    }

    private fun TouchInjectionScope.lerp(from: Offset, to: Offset, t: Float) =
        Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)

    /** The bar under a point [share] of the way across the plot, which a pinch must hold still. */
    private fun ChartViewport.barUnder(share: Float): Int =
        firstVisible + (share * (visibleCount + blankSlots)).toInt()

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `fingers moving apart horizontally on the plot show fewer bars`() {
        chart()
        val before = resting()
        val width = composeRule.onNodeWithTag(TAG).fetchSemanticsNode().size.width.toFloat()
        val height = composeRule.onNodeWithTag(TAG).fetchSemanticsNode().size.height.toFloat()
        val y = height * 0.4f
        pinch(
            fromA = Offset(width * 0.30f, y),
            fromB = Offset(width * 0.45f, y),
            toA = Offset(width * 0.10f, y),
            toB = Offset(width * 0.65f, y),
        )
        assertTrue(
            "a horizontal pinch left the bar count at ${latest().barsPerView}",
            latest().barsPerView < before.barsPerView,
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the bar under the fingers stays under the fingers`() {
        chart()
        val before = resting()
        val width = composeRule.onNodeWithTag(TAG).fetchSemanticsNode().size.width.toFloat()
        val height = composeRule.onNodeWithTag(TAG).fetchSemanticsNode().size.height.toFloat()
        val y = height * 0.4f
        val share = 0.375f
        pinch(
            fromA = Offset(width * 0.30f, y),
            fromB = Offset(width * 0.45f, y),
            toA = Offset(width * 0.15f, y),
            toB = Offset(width * 0.60f, y),
        )
        // A fraction of the window rather than a fixed count, and the reason is the measurement
        // rather than the code: this share is taken against the *canvas* and the handler takes its
        // own against the *plot*, which is the canvas less a sixty-four point ladder. The two
        // differ by about a seventh, and on a window of a hundred and fifty bars a seventh of the
        // difference is a few slots. What is being asserted is that the anchor holds — a chart that
        // zoomed about its right edge instead would move this bar by a third of the window.
        val moved = latest().barUnder(share) - before.barUnder(share)
        val slack = (before.barsPerView * ANCHOR_SLACK_SHARE).toInt().coerceAtLeast(2)
        assertTrue(
            "the anchor bar moved by $moved of ${before.barsPerView} slots",
            kotlin.math.abs(moved) <= slack,
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `where the fingers are decides what stays on screen`() {
        // The anchor, proved by contrast rather than by tolerance. The same pinch at the left of the
        // plot and at the right cannot produce the same window: zooming in about the left keeps the
        // old bars and pushes the live edge away, zooming in about the right keeps the present.
        chart()
        val width = composeRule.onNodeWithTag(TAG).fetchSemanticsNode().size.width.toFloat()
        val glass = composeRule.onNodeWithTag(TAG).fetchSemanticsNode().size.height.toFloat()
        val y = glass * 0.4f
        pinch(
            fromA = Offset(width * 0.14f, y),
            fromB = Offset(width * 0.26f, y),
            toA = Offset(width * 0.04f, y),
            toB = Offset(width * 0.36f, y),
        )
        val leftAnchored = latest()

        chart()
        pinch(
            fromA = Offset(width * 0.54f, y),
            fromB = Offset(width * 0.66f, y),
            toA = Offset(width * 0.44f, y),
            toB = Offset(width * 0.76f, y),
        )
        val rightAnchored = latest()

        assertTrue(
            "both pinches should have zoomed in: ${leftAnchored.barsPerView} / ${rightAnchored.barsPerView}",
            leftAnchored.barsPerView < viewports.first().barsPerView,
        )
        assertTrue(
            "the anchor made no difference: offset ${leftAnchored.offset} either way",
            leftAnchored.offset > rightAnchored.offset,
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `fingers moving apart vertically on the plot also show fewer bars`() {
        // TradingView's behaviour, and the brief's: on the candles a pinch is about the picture,
        // whichever way the fingers went. The price axis is a gesture of its own, in its own gutter.
        chart()
        val before = resting()
        val width = composeRule.onNodeWithTag(TAG).fetchSemanticsNode().size.width.toFloat()
        val height = composeRule.onNodeWithTag(TAG).fetchSemanticsNode().size.height.toFloat()
        val x = width * 0.35f
        pinch(
            fromA = Offset(x, height * 0.35f),
            fromB = Offset(x, height * 0.45f),
            toA = Offset(x, height * 0.15f),
            toB = Offset(x, height * 0.65f),
        )
        assertTrue(
            "a vertical pinch on the plot left the bar count at ${latest().barsPerView}",
            latest().barsPerView < before.barsPerView,
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `fingers in the price ladder scale the price and leave the bars alone`() {
        chart()
        val before = resting()
        val width = composeRule.onNodeWithTag(TAG).fetchSemanticsNode().size.width.toFloat()
        val height = composeRule.onNodeWithTag(TAG).fetchSemanticsNode().size.height.toFloat()
        // Well inside the ladder — a few points off the canvas edge, on both pointers.
        val x = width - 12f
        pinch(
            fromA = Offset(x, height * 0.35f),
            fromB = Offset(x, height * 0.45f),
            toA = Offset(x, height * 0.15f),
            toB = Offset(x, height * 0.65f),
        )
        assertEquals(
            "a gesture in the ladder changed the bar count",
            before.barsPerView,
            latest().barsPerView,
        )
        assertTrue(
            "a gesture in the ladder left the price scale at ${latest().priceZoom}",
            latest().priceZoom != before.priceZoom,
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `one finger is a pan and never a zoom`() {
        chart()
        val before = resting()
        val width = composeRule.onNodeWithTag(TAG).fetchSemanticsNode().size.width.toFloat()
        val height = composeRule.onNodeWithTag(TAG).fetchSemanticsNode().size.height.toFloat()
        composeRule.onNodeWithTag(TAG).performTouchInput {
            down(0, Offset(width * 0.6f, height * 0.4f))
            repeat(10) { step ->
                updatePointerTo(0, Offset(width * (0.6f - step * 0.03f), height * 0.4f))
                move()
            }
            up(0)
        }
        composeRule.waitForIdle()
        assertEquals(
            "a one-finger drag changed the zoom",
            before.barsPerView,
            latest().barsPerView,
        )
        assertEquals(
            "a one-finger drag changed the price scale",
            before.priceZoom,
            latest().priceZoom,
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `nothing stacked above the plot steals the second finger`() {
        // The interception test the brief asks for, and it is the one that cannot be written against
        // `CoineProChart` alone: the chart is at the bottom of a page that also carries the symbol
        // wheel's drag, the watchlist row's swipe, a predictive-back edge and, on two panes, a
        // pager. Any of those consuming a pointer that landed on the candles would take the pinch
        // away, and it would look exactly like the bug that was just fixed.
        //
        // So this one drives the whole page through the root, with no test tag to aim at, and asks
        // the controller what window it ended on.
        val controller = ScreenshotFixtures.chartController(scope, "BTCUSDT")
        composeRule.setContent {
            CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                CoineProTheme(darkTheme = true) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val ready = remember { controller }
                        ChartScreen(controller = ready, onBack = {}, readerMode = ReaderMode.TRADER)
                    }
                }
            }
        }
        started = true
        composeRule.waitForIdle()
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
        composeRule.waitForIdle()

        // Null until the reader has zoomed this symbol at this bar length, which is the state the
        // page opens in; the assertion below is «the chart now has a stored zoom, and it is a
        // smaller window than the default it was showing».
        val before = controller.state.value.barsPerView ?: ChartViewport.DEFAULT_BARS_PER_VIEW
        val width = view.width.toFloat()
        // A quarter of the way down the glass is candles on every reader mode: the plot takes about
        // seventy per cent of the page and starts at the top.
        val y = view.height * 0.25f
        composeRule.onRoot().performTouchInput {
            down(0, Offset(width * 0.30f, y))
            down(1, Offset(width * 0.45f, y))
            repeat(16) { step ->
                val t = (step + 1f) / 16f
                updatePointerTo(0, Offset(width * (0.30f - 0.20f * t), y))
                updatePointerTo(1, Offset(width * (0.45f + 0.20f * t), y))
                move()
            }
            up(0)
            up(1)
        }
        composeRule.waitForIdle()
        val after = controller.state.value.barsPerView
        assertTrue(
            "the page swallowed the pinch: the chart never reported a zoom",
            after != null,
        )
        assertTrue(
            "the page swallowed the pinch: still $after bars against $before",
            after!! < before,
        )
    }

    private companion object {
        const val TAG = "pinch-chart"
        const val PHONE = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi"

        /**
         * How far the anchored bar may drift, as a share of the window it started in.
         *
         * The focal share is measured against the canvas in this file and against the plot inside
         * the handler; the two differ by the ladder's width, about a seventh. Five per cent of the
         * window covers that and the whole-bar rounding beside it. A chart that was not anchored at
         * all would move this bar by a third of the window — see the test below, which proves the
         * anchor by contrast rather than by tolerance.
         */
        const val ANCHOR_SLACK_SHARE = 0.05f
    }
}
