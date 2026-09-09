package com.coinepro.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.coinepro.app.GoldenScreenshot.assertMatchesGolden
import com.coinepro.core.chart.ChartType
import com.coinepro.feature.chart.ChartScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every series type, on a phone and on a tablet, as pixels.
 *
 * The chart screen with the golden fixture's two hundred bars, switched to each of the eighteen
 * types the catalogue offers. A transform that silently stopped emitting, a painter that lost its
 * wicks, a Renko brick drawn a pixel off its neighbour: none of those fail a numerical test, and
 * all of them fail this one. The tablet variant is the layout at 840dp — the width past which the
 * chart takes the two-pane shape — and it is the one the plan's tablet work is judged on.
 *
 * Record with `-Dcoinepro.golden.record=true`; the rig is [GoldenScreenshot].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartTypeGoldenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun golden(name: String, type: ChartType) = composeRule.assertMatchesGolden(name) {
        ChartScreen(controller = ScreenshotFixtures.chartController(scope).also { it.setChartType(type) })
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun candlesPhone() = golden("chart-type-candles-fa-411", ChartType.CANDLES)

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun candlesTablet() = golden("chart-type-candles-fa-840", ChartType.CANDLES)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun hollowPhone() = golden("chart-type-hollow-fa-411", ChartType.HOLLOW)

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun hollowTablet() = golden("chart-type-hollow-fa-840", ChartType.HOLLOW)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun heikinAshiPhone() = golden("chart-type-heikin-ashi-fa-411", ChartType.HEIKIN_ASHI)

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun heikinAshiTablet() = golden("chart-type-heikin-ashi-fa-840", ChartType.HEIKIN_ASHI)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun barsPhone() = golden("chart-type-bars-fa-411", ChartType.BARS)

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun barsTablet() = golden("chart-type-bars-fa-840", ChartType.BARS)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun linePhone() = golden("chart-type-line-fa-411", ChartType.LINE)

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun lineTablet() = golden("chart-type-line-fa-840", ChartType.LINE)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun areaPhone() = golden("chart-type-area-fa-411", ChartType.AREA)

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun areaTablet() = golden("chart-type-area-fa-840", ChartType.AREA)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun renkoPhone() = golden("chart-type-renko-fa-411", ChartType.RENKO)

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun renkoTablet() = golden("chart-type-renko-fa-840", ChartType.RENKO)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun rangePhone() = golden("chart-type-range-fa-411", ChartType.RANGE)

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun rangeTablet() = golden("chart-type-range-fa-840", ChartType.RANGE)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun lineBreakPhone() = golden("chart-type-line-break-fa-411", ChartType.LINE_BREAK)

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun lineBreakTablet() = golden("chart-type-line-break-fa-840", ChartType.LINE_BREAK)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun kagiPhone() = golden("chart-type-kagi-fa-411", ChartType.KAGI)

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun kagiTablet() = golden("chart-type-kagi-fa-840", ChartType.KAGI)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun pointAndFigurePhone() = golden("chart-type-point-and-figure-fa-411", ChartType.POINT_AND_FIGURE)

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun pointAndFigureTablet() = golden("chart-type-point-and-figure-fa-840", ChartType.POINT_AND_FIGURE)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun baselinePhone() = golden("chart-type-baseline-fa-411", ChartType.BASELINE)

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun baselineTablet() = golden("chart-type-baseline-fa-840", ChartType.BASELINE)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun hlcAreaPhone() = golden("chart-type-hlc-area-fa-411", ChartType.HLC_AREA)

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun hlcAreaTablet() = golden("chart-type-hlc-area-fa-840", ChartType.HLC_AREA)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun stepLinePhone() = golden("chart-type-step-line-fa-411", ChartType.STEP_LINE)

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun stepLineTablet() = golden("chart-type-step-line-fa-840", ChartType.STEP_LINE)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun lineMarkersPhone() = golden("chart-type-line-markers-fa-411", ChartType.LINE_MARKERS)

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun lineMarkersTablet() = golden("chart-type-line-markers-fa-840", ChartType.LINE_MARKERS)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun volumeCandlesPhone() = golden("chart-type-volume-candles-fa-411", ChartType.VOLUME_CANDLES)

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun volumeCandlesTablet() = golden("chart-type-volume-candles-fa-840", ChartType.VOLUME_CANDLES)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun footprintPhone() = golden("chart-type-footprint-fa-411", ChartType.FOOTPRINT)

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun footprintTablet() = golden("chart-type-footprint-fa-840", ChartType.FOOTPRINT)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun tpoPhone() = golden("chart-type-tpo-fa-411", ChartType.TPO)

    @Test
    @Config(sdk = [34], qualifiers = FA_840)
    fun tpoTablet() = golden("chart-type-tpo-fa-840", ChartType.TPO)

    private companion object {
        const val FA_411 = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi"
        const val FA_840 = "fa-rIR-ldrtl-w840dp-h1280dp-xhdpi"
    }
}
