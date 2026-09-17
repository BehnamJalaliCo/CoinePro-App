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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartDecoration
import com.coinepro.core.chart.ChartLegendTarget
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import com.coinepro.feature.chart.ChartController
import com.coinepro.feature.chart.ChartUiState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **The study a reader could not take off the chart** (run Υ, item 2).
 *
 * «زمانی که یه اندیکاتور رو روی چارت میندازیم و ضرب در روی صفحه چارت رو میزنیم ولی هنوز اندیکاتوره
 * هست … گزینه‌های روی چارت اندیکاتور نمیادش که من حذفش بکنم از صفحه.»
 *
 * Support and resistance draws horizontal levels, and only levels. The legend addressed a study by
 * where its row sat — «the fourth line on the price scale», «the second strip» — and a study of that
 * shape has neither, so it had no row: no ×, no gear, no eye. Seven of the catalogue's studies are
 * that shape; `IndicatorRemovalTest` sweeps all eighty-odd and this is the picture of one.
 *
 * The frame is not drawn from a decoration this file wrote. It comes off a **real controller** with
 * the study switched on through the same call the picker makes, and the × is a real tap on the real
 * legend. If the row goes away again, the first frame comes back with levels on the chart and
 * nothing in the legend that names them — which is exactly what the reader saw.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IndicatorRemovalProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val series: CandleSeries = ScreenshotFixtures.chartSeries()

    private class FixtureGateway(private val series: CandleSeries) : CandleGateway {
        override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?): CandlePage =
            CandlePage(
                symbol,
                timeframe,
                series.bars.map { OhlcBar(t = it.t, o = it.o, h = it.h, l = it.l, c = it.c, v = it.v ?: 0.0) },
            )
    }

    /** A controller with [study] switched on, and the state it settled into. */
    private fun withStudy(study: String): Pair<ChartController, ChartUiState> {
        var held: Pair<ChartController, ChartUiState>? = null
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val controller = ChartController(SYMBOL, FixtureGateway(series), scope)
            controller.start()
            advanceUntilIdle()
            controller.toggleIndicator(study)
            advanceUntilIdle()
            held = controller to controller.state.value
            scope.cancel()
        }
        return held!!
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `a levels-only study is named in the legend and its cross takes it off`() {
        val (controller, on) = withStudy(STUDY)

        assertTrue("the study drew nothing at all", on.levels.isNotEmpty())
        assertTrue(
            "the study owns no line and no strip, which is why it needs a row of its own",
            on.overlays.none { it.label.orEmpty().isNotBlank() && on.indicatorFor(ChartLegendTarget.Overlay(0)) == STUDY },
        )
        val row = on.studyRows.singleOrNull { it.key == STUDY }
        assertTrue("the legend was told nothing about it: ${on.studyRows}", row != null)

        var removed: ChartLegendTarget? = null
        render {
            CoineProChart(
                series = series,
                modifier = Modifier.fillMaxSize(),
                savedBarsPerView = BARS,
                seriesLabel = SYMBOL,
                decoration = ChartDecoration(levels = on.levels, studies = on.studyRows),
                onSeriesSettings = {},
                onRemoveSeries = { target -> removed = target },
            )
        }
        // The disclosure — the first of the two taps a reader makes — and then the picture, which is
        // the one that answers the report: the study named, with an eye, a gear and a × beside it.
        composeRule.onAllNodesWithContentDescription(OPEN_CONTROLS)[0].performClick()
        composeRule.waitForIdle()
        capture("upsilon-study-row-open-phone-fa")

        val crosses = composeRule.onAllNodesWithContentDescription(REMOVE)
        assertTrue("the open legend offers no × at all", crosses.fetchSemanticsNodes().isNotEmpty())
        crosses[crosses.fetchSemanticsNodes().size - 1].performClick()
        composeRule.waitForIdle()

        assertEquals("the × reported a row that is not this study's", ChartLegendTarget.Study(STUDY), removed)

        // And what `ChartScreen` does with that target actually takes the study off.
        val target = removed!!
        assertEquals(STUDY, on.indicatorFor(target))
        var after: ChartUiState? = null
        runTest {
            controller.toggleIndicator(on.indicatorFor(target)!!)
            after = controller.state.value
        }
        assertTrue("the study is still on the chart", STUDY !in after!!.activeIndicators)
        assertTrue("and its levels are still being drawn", after!!.levels.isEmpty())
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `and the chart it leaves behind`() {
        // The other half of the pair. Put the two frames side by side and the difference is a study
        // a reader can get rid of.
        val (_, gone) = withStudy(STUDY).let { (controller, _) ->
            var state: ChartUiState? = null
            runTest {
                controller.toggleIndicator(STUDY)
                state = controller.state.value
            }
            controller to state!!
        }
        assertTrue(gone.studyRows.isEmpty())
        proof("upsilon-study-row-removed-phone-fa") {
            CoineProChart(
                series = series,
                modifier = Modifier.fillMaxSize(),
                savedBarsPerView = BARS,
                seriesLabel = SYMBOL,
                decoration = ChartDecoration(levels = gone.levels, studies = gone.studyRows),
                onSeriesSettings = {},
                onRemoveSeries = {},
            )
        }
    }

    private fun proof(name: String, content: @Composable () -> Unit) {
        render(content)
        capture(name)
    }

    private fun render(content: @Composable () -> Unit) {
        composeRule.setContent {
            CoineProTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) { content() }
            }
        }
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
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
        const val FA_PHONE = "fa-rIR-ldrtl-w411dp-h914dp-420dpi"
        const val SYMBOL = "BTCUSDT"
        const val STUDY = "sr"
        const val BARS = 60
        const val OPEN_CONTROLS = "کنترل‌های اندیکاتورها"
        const val REMOVE = "حذف"
    }
}
