package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartDecoration
import com.coinepro.core.chart.ChartMarker
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.chart.MarkerDetail
import com.coinepro.core.chart.MarkerStyle
import com.coinepro.core.chart.SignalMarkers
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.feature.chart.ChartSignalEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * **«خرید» and «فروش» on the candles, at three zooms** (run Σ, S2).
 *
 * The owner asked for the word beside the triangle. The word is easy; what needs a picture is the
 * *density rule* that keeps it from becoming a smear — so the frames are the same chart at sixteen,
 * eight and four points a bar, which are the three answers: the label, the triangle alone, and the
 * thinned set.
 *
 * Phone and tablet, because the rule is in points a bar and a tablet showing the same hundred bars
 * is showing them at twice the size. A rule written in bars would look right on one of these two
 * frames and wrong on the other, which is exactly why it is not written in bars.
 *
 * `SignalMarkersTest` in `:chart-core` holds the arithmetic. These are the pictures of it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignalMarkerProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val series: CandleSeries = ScreenshotFixtures.chartSeries()

    /** The Signal Layer's own marks, from two studies, exactly as the chart page builds them. */
    private fun marks(styles: Map<String, MarkerStyle> = emptyMap()): List<ChartMarker> {
        val layer = ChartSignalEngine.evaluate(series = series, indicatorIds = listOf("ema", "rsi"))
        return ChartSignalEngine.markersFor(layer, series, styles = styles)
    }

    /**
     * How many bars fill the plot at [spacingDp] points a bar.
     *
     * Taken from the canvas less the price ladder, because that is what the renderer divides. It is
     * an estimate — the ladder's width depends on how wide the price labels are — and it is close
     * enough that the frame lands in the band it is named for, which the assertion checks.
     */
    private fun barsFor(spacingDp: Float, canvasDp: Float): Int =
        ((canvasDp - LADDER_DP) / spacingDp).toInt().coerceAtLeast(8)

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `at sixteen points a bar the marker carries its word`() {
        frame("sigma0-markers-16dp-phone-fa", barsFor(16f, PHONE_DP))
        assertEquals(MarkerDetail.LABEL, SignalMarkers.detailFor(16f))
        assertTrue("no marker was built at all", marks().isNotEmpty())
        assertTrue("the marks carry no word", marks().any { it.label != null })
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `at eight points a bar it is the triangle alone`() {
        frame("sigma0-markers-8dp-phone-fa", barsFor(8f, PHONE_DP))
        assertEquals(MarkerDetail.TRIANGLE, SignalMarkers.detailFor(8f))
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `at four points a bar the marks thin out`() {
        frame("sigma0-markers-4dp-phone-fa", barsFor(4f, PHONE_DP))
        assertEquals(MarkerDetail.THINNED, SignalMarkers.detailFor(4f))
        val all = marks()
        val thinned = SignalMarkers.thin(all, series)
        assertTrue("thinning kept every mark: ${all.size}", thinned.size <= all.size)
    }

    // One test per frame, because an activity's content can only be set once and each of these is a
    // separate composition. Three names rather than a loop, so a failure says which zoom broke.

    @Test
    @Config(sdk = [34], qualifiers = FA_TABLET)
    fun `sixteen points a bar on a tablet`() = frame("sigma0-markers-16dp-tablet-fa", barsFor(16f, TABLET_DP))

    @Test
    @Config(sdk = [34], qualifiers = FA_TABLET)
    fun `eight points a bar on a tablet`() = frame("sigma0-markers-8dp-tablet-fa", barsFor(8f, TABLET_DP))

    @Test
    @Config(sdk = [34], qualifiers = FA_TABLET)
    fun `four points a bar on a tablet`() = frame("sigma0-markers-4dp-tablet-fa", barsFor(4f, TABLET_DP))

    @Test
    @Config(sdk = [34], qualifiers = EN_PHONE)
    fun `the English chart says Buy and Sell`() {
        frame("sigma0-markers-16dp-phone-en", barsFor(16f, PHONE_DP), english = true)
        val layer = ChartSignalEngine.evaluate(
            series = series,
            indicatorIds = listOf("ema", "rsi"),
            english = true,
        )
        val labels = ChartSignalEngine.markersFor(layer, series).mapNotNull { it.label }.toSet()
        assertTrue("the English chart printed nothing", labels.isNotEmpty())
        for (label in labels) {
            assertTrue("a Persian word reached the English chart: $label", label.none { it in '؀'..'ۿ' })
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `a study set to triangles loses its word and a study set to off loses its marks`() {
        val triangles = marks(mapOf("ema" to MarkerStyle.TRIANGLES))
        assertTrue("ema kept its marks", triangles.any { it.colour == emaColour() })
        assertTrue(
            "ema kept its word after being set to triangles",
            triangles.filter { it.colour == emaColour() }.all { it.label == null },
        )
        val silenced = marks(mapOf("ema" to MarkerStyle.OFF))
        assertTrue("ema kept its marks after being switched off", silenced.none { it.colour == emaColour() })
        // And the other study is untouched, which is the whole reason this is per study.
        assertTrue("the setting leaked onto the other study", silenced.isNotEmpty())
        frame("sigma0-markers-triangles-phone-fa", barsFor(16f, PHONE_DP), styles = mapOf("ema" to MarkerStyle.TRIANGLES))
    }

    private fun emaColour(): Long =
        com.coinepro.core.chart.ChartCatalog.INDICATORS.first { it.id == "ema" }.colour

    private fun frame(
        name: String,
        bars: Int,
        english: Boolean = false,
        styles: Map<String, MarkerStyle> = emptyMap(),
    ) = proof(name) {
        val layer = ChartSignalEngine.evaluate(
            series = series,
            indicatorIds = listOf("ema", "rsi"),
            english = english,
        )
        CoineProChart(
            series = series,
            modifier = Modifier.fillMaxSize(),
            savedBarsPerView = bars,
            decoration = ChartDecoration(
                markers = ChartSignalEngine.markersFor(layer, series, styles = styles),
            ),
        )
    }

    private fun proof(name: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            CoineProTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) { content() }
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
        OUTPUT.mkdirs()
        File(OUTPUT, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        val OUTPUT = File("build/proof")
        const val FA_PHONE = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi"
        const val EN_PHONE = "en-rUS-w411dp-h914dp-xxhdpi"
        const val FA_TABLET = "fa-rIR-ldrtl-w1280dp-h800dp-xhdpi"

        /** The widths the frames are taken at, in points. */
        const val PHONE_DP = 411f
        const val TABLET_DP = 1280f

        /** What the price ladder takes off the canvas before the bars are laid out. */
        const val LADDER_DP = 64f
    }
}
