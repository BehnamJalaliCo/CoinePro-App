package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import com.coinepro.core.datastore.ReaderMode
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.feature.chart.BAND_INTERVAL_TAG
import com.coinepro.feature.chart.ChartScreen
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.coinepro.feature.chart.R as ChartR

/**
 * **The pencil is not a mode setting** (run Τ, item 2).
 *
 * The owner's toolbar read `[⛶][↶][•••][اندیکاتور][H1]` where 4.74's had a pencil between the
 * indicators and the timeframe — and turning the phone sideways brought it back, because landscape
 * builds its strip somewhere else. The cause was one condition: the draw button was hidden whenever
 * `showsAdvancedChrome` was false, which is what answering «تازه‌کارم» on the first run sets.
 *
 * That was Ω3's rule and the owner has overruled it, correctly. **Simple mode changes defaults; it
 * does not remove controls.** A beginner who cannot find the drawing tools does not think «I am in
 * the simple mode»; they think the app cannot draw a trend line, and the next thing they do is look
 * for one that can. Hiding a control is how a product teaches somebody it is missing something.
 *
 * Three modes, both orientations — six compositions, because the fault was *in one orientation of
 * one mode* and a test of the case that worked would have passed on the broken build.
 *
 * ### What «the pencil is there» means in landscape
 *
 * On a window wide enough for it, Trader and Pro put the fifty drawing tools in a **permanent
 * column** beside the plot, and the band drops its pencil so that one tap does not open a sheet
 * duplicating a column already on screen. That is not the bug: the bug was a reader who had neither.
 * So the assertion is the one the owner actually cares about — **the drawing tools are reachable
 * from the chart's own chrome, in every mode and both orientations** — satisfied by the band's
 * pencil or by the column, and by the band's pencil specifically wherever there is no column.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartToolbarTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private var started = false

    private fun toolbar(mode: ReaderMode, frame: String? = null) {
        val controller = ScreenshotFixtures.chartController(scope, "BTCUSDT")
        check(!started) { "one composition per test: an activity's content is set once" }
        started = true
        composeRule.setContent {
            CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                CoineProTheme(darkTheme = true) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val ready = remember { controller }
                        ChartScreen(controller = ready, onBack = {}, readerMode = mode)
                    }
                }
            }
        }
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
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
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        frame?.let {
            OUTPUT.mkdirs()
            File(OUTPUT, "$it.png").outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        }
        composeRule.waitForIdle()
    }

    /** How many nodes answer to [description]. */
    private fun count(description: String): Int =
        composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size

    /** The four the band always owes the reader, whatever else is on screen. */
    private fun assertBandIntact(where: String) {
        listOf(
            ChartR.string.chart_band_indicators,
            ChartR.string.chart_band_more,
            ChartR.string.chart_more_undo,
            ChartR.string.chart_band_fullscreen,
        ).forEach { label ->
            val text = composeRule.activity.getString(label)
            assertEquals("«$text» is missing from the toolbar $where", 1, count(text))
        }
    }

    /**
     * The drawing tools are reachable: the band's pencil, or the column that replaces it.
     *
     * Exactly one of the two, never neither — which is what the owner filmed — and never both,
     * which is the duplication the band's rule exists to avoid.
     */
    private fun assertDrawingReachable(where: String) {
        val pencil = count(composeRule.activity.getString(ChartR.string.chart_band_draw))
        val rail = count(TOOL_RAIL)
        assertEquals(
            "the drawing tools are unreachable $where (pencil=$pencil, rail=$rail)",
            1,
            pencil + rail,
        )
    }

    /** The horizontal centre of the one node answering to [description], or null where there is none. */
    private fun centreOf(description: String): Float? =
        composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes()
            .singleOrNull()?.boundsInRoot?.center?.x

    /**
     * Where the pencil sits: **between the timeframe chip and the indicators**, run Τ item 6.
     *
     * Stated as an order rather than a pixel, and read off the laid-out tree rather than the
     * source, because the band is laid out right-to-left in Persian and left-to-right in English
     * and «between» is the same sentence in both. Skipped only where there is no pencil to place —
     * a window wide enough for the permanent tool column, which `assertDrawingReachable` covers.
     */
    private fun assertPencilBetweenIntervalAndIndicators(where: String) {
        val pencil = centreOf(composeRule.activity.getString(ChartR.string.chart_band_draw)) ?: return
        val indicators = centreOf(composeRule.activity.getString(ChartR.string.chart_band_indicators))
        val interval = composeRule.onAllNodesWithTag(BAND_INTERVAL_TAG).fetchSemanticsNodes()
            .singleOrNull()?.boundsInRoot?.center?.x
        assertNotNull("the indicators button is missing $where", indicators)
        assertNotNull("the timeframe chip is missing $where", interval)
        val low = minOf(interval!!, indicators!!)
        val high = maxOf(interval, indicators)
        assertTrue(
            "the pencil is at $pencil, outside the chip at $interval and the indicators at " +
                "$indicators $where",
            pencil in low..high,
        )
    }

    private fun assertToolbar(where: String) {
        assertBandIntact(where)
        assertDrawingReachable(where)
        assertPencilBetweenIntervalAndIndicators(where)
    }

    @Test
    @Config(sdk = [34], qualifiers = PORTRAIT)
    fun `the simple reader keeps the pencil in portrait`() {
        // The exact case the owner filmed, and the only one of the six that was broken.
        toolbar(ReaderMode.SIMPLE, frame = "tau-toolbar-simple-portrait-fa")
        assertToolbar("in simple mode, portrait")
        // No column on a phone in Simple mode, so this one must be the band's own pencil — the
        // exact button that went missing.
        assertEquals(
            "the pencil is not on the band",
            1,
            count(composeRule.activity.getString(ChartR.string.chart_band_draw)),
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = PORTRAIT)
    fun `the trader keeps it too`() {
        toolbar(ReaderMode.TRADER, frame = "tau-toolbar-trader-portrait-fa")
        assertToolbar("in trader mode, portrait")
    }

    @Test
    @Config(sdk = [34], qualifiers = PORTRAIT)
    fun `and so does the pro`() {
        toolbar(ReaderMode.PRO, frame = "tau-toolbar-pro-portrait-fa")
        assertToolbar("in pro mode, portrait")
    }

    @Test
    @Config(sdk = [34], qualifiers = LANDSCAPE)
    fun `the simple reader keeps it sideways as well`() {
        toolbar(ReaderMode.SIMPLE, frame = "tau-toolbar-simple-landscape-fa")
        assertToolbar("in simple mode, landscape")
    }

    @Test
    @Config(sdk = [34], qualifiers = LANDSCAPE)
    fun `and the trader sideways`() {
        toolbar(ReaderMode.TRADER, frame = "tau-toolbar-trader-landscape-fa")
        assertToolbar("in trader mode, landscape")
    }

    @Test
    @Config(sdk = [34], qualifiers = LANDSCAPE)
    fun `and the pro sideways`() {
        toolbar(ReaderMode.PRO, frame = "tau-toolbar-pro-landscape-fa")
        assertToolbar("in pro mode, landscape")
    }

    private companion object {
        val OUTPUT = File("build/proof")
        /** What the permanent drawing column calls itself. See `ChartToolRailColumn`. */
        const val TOOL_RAIL = "chart-tool-rail"
        const val PORTRAIT = "fa-rIR-ldrtl-w411dp-h914dp-420dpi"
        const val LANDSCAPE = "fa-rIR-ldrtl-w914dp-h411dp-420dpi"
    }
}
