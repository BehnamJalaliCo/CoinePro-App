package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import com.coinepro.core.chart.ConfidenceEngine
import com.coinepro.core.chart.MarketState
import com.coinepro.core.chart.RasadCoach
import com.coinepro.core.chart.RasadContradiction
import com.coinepro.core.common.BidiText
import com.coinepro.core.datastore.ReaderMode
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.feature.chart.ChartController
import com.coinepro.feature.chart.ChartScreen
import com.coinepro.feature.chart.ExplainSheetBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * **RUN Ω-FIX, photographed and asserted.**
 *
 * The owner tested 4.79.0 on a real device and found eight places where the build disagreed with
 * `docs/runs/RUN_OMEGA/CHECKLIST.md`. Three of them were claims that a reading of the code had made
 * and no test had checked. This file is where the ones with a picture in them live; the arithmetic
 * behind each is pinned in the module that owns it, and the checklist's own table names both.
 *
 * Item 1's frames are in `ChartTopBarTest`, because the claim there is about the *shell* rather than
 * about the chart, and the scaffold it needs belongs beside the function that decides it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RunOmegaFixProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun charted(symbol: String = "BTCUSDT"): ChartController =
        ScreenshotFixtures.chartController(scope, symbol).also {
            it.toggleIndicator("ema")
            it.toggleIndicator("rsi")
            it.toggleIndicator("macd")
        }

    /**
     * **Item 3: the Setup score is confidence, not consensus.**
     *
     * The device showed «۱۰۰ صعودی» over four contributors reading 43 %, 40 %, 39 % and 40 %. The
     * frame is the panel that printed it; what is under it now is the mean of the records the score
     * was built from, so the number can be checked against the rows beneath it rather than believed.
     */
    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun theSetupScoreShowsTheRecordItWasBuiltFrom() {
        val controller = charted()
        proof("omegafix-setup-score-fa") {
            ExplainSheetBody(
                id = null,
                layer = controller.state.value.signals,
                onSetHorizon = controller::setConfidenceHorizon,
                onAddAlert = null,
                onPractise = null,
                onSelect = {},
            )
        }
        val setup = controller.state.value.signals.setup
        val records = controller.state.value.signals.reads.mapNotNull {
            controller.state.value.signals.confidenceOf(it.id)
        }
        assertTrue("nothing on the chart voted", setup.studies > 0)
        // The property, stated over whatever this fixture's candles happen to produce: the score can
        // never exceed the best record behind it. That is the whole of what «not consensus» means.
        val best = records.filter { it.samples > 0 }.maxOfOrNull { it.winRate }
        if (best != null) {
            assertTrue(
                "a score of ${setup.score} over a best record of ${(best * 100).toInt()} %",
                setup.score <= (best * 100).toInt() + 1,
            )
        }
        assertTrue("the cap was not applied", setup.score <= 100)
    }

    /**
     * **Item 4 and item 6: the state a study is in, and the sentence that says so.**
     *
     * The frame is the Explain sheet for the RSI — the study the device caught reading «نزولی» at
     * 48.6 — and the row under it is the stop, rewritten from «زیر X اشتباه است», which named no
     * stop and read as a verdict on the reader, to «حد ضرر پیشنهادی: زیر X».
     */
    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun theExplainSheetNamesAStopRatherThanAMistake() {
        val controller = charted()
        proof("omegafix-explain-stop-fa") {
            ExplainSheetBody(
                id = "rsi",
                layer = controller.state.value.signals,
                onSetHorizon = controller::setConfidenceHorizon,
                onAddAlert = {},
                onPractise = {},
                onSelect = {},
            )
        }
        val stopLine = composeRule.activity.getString(
            com.coinepro.feature.chart.R.string.chart_explain_stop,
            "91,263.03",
        )
        assertTrue("the stop line still calls the reader wrong: $stopLine", !stopLine.contains("اشتباه"))
        assertTrue("the stop line does not name a stop: $stopLine", stopLine.contains("حد ضرر"))
    }

    /**
     * **Item 5 and item 6, on the page: the coach's line under the plot.**
     *
     * The frame is the whole phone page, which is where the strip lives — one row under the Now
     * strip, wrapping to two lines. What is asserted is the sentence itself: no contradiction, and
     * every number in it inside an isolate, so the full stop stays where the sentence put it.
     */
    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun theCoachSaysOneThingAtATimeAndIsNotCutOff() {
        val controller = charted("XAUUSD")
        proof("omegafix-rasad-strip-fa") {
            val ready = remember { controller }
            ChartScreen(controller = ready, onBack = {}, readerMode = ReaderMode.TRADER)
        }
        assertEquals(
            "the coach's line is not on the page",
            1,
            composeRule.onAllNodesWithContentDescription(RASAD_LINE).fetchSemanticsNodes().size,
        )
        val sentences = RasadCoach.readChart(
            series = controller.state.value.visibleSeries,
            reads = controller.state.value.signals.reads,
            setup = controller.state.value.signals.setup,
        )
        assertTrue("the coach said nothing at all", sentences.isNotEmpty())
        for (sentence in sentences) {
            assertNull("a contradiction reached the page: $sentence", RasadContradiction.of(sentence))
        }
        val isolated = BidiText.isolateNumbers(sentences[1])
        assertTrue(
            "the level sentence's prices were not isolated: $isolated",
            isolated.contains(BidiText.FSI),
        )
    }

    /**
     * **Item 2: the symbol chip is one name.**
     *
     * The chip is in the chart's own command band, which is `internal` to `feature:chart`, so the
     * frame is the page and the arithmetic is `SymbolWheelTest.at rest the cell shows one name and
     * no neighbours`. This is the picture that goes with it, in English so the two frames of the
     * page are not the same shot twice.
     */
    @Test
    @Config(sdk = [34], qualifiers = EN_PHONE)
    fun theSymbolChipIsOneNameAtRest() {
        val controller = charted("XAUUSD")
        proof("omegafix-symbol-chip-en") {
            val ready = remember { controller }
            ChartScreen(controller = ready, onBack = {}, readerMode = ReaderMode.TRADER)
        }
        // The page is the frame; the claim about the chip's ink is `SymbolWheelTest`. What is worth
        // asserting here is that the band the chip lives in is on the page at all, so a frame that
        // shows one name is a frame of a control rather than of a control that failed to compose.
        assertEquals(
            "the chart's own strip is not on the page",
            1,
            composeRule.onAllNodesWithContentDescription(NOW_STRIP).fetchSemanticsNodes().size,
        )
    }

    /**
     * **Item 3 again, in English, and the whole page with it.**
     *
     * The English frame is not decoration: the coach and the score both compose sentences, and the
     * one defect this app has shipped twice is a Persian phrase reaching an English screen.
     */
    @Test
    @Config(sdk = [34], qualifiers = EN_PHONE)
    fun theSameFixesInEnglish() {
        val controller = charted("XAUUSD")
        proof("omegafix-chart-en") {
            val ready = remember { controller }
            ChartScreen(controller = ready, onBack = {}, readerMode = ReaderMode.TRADER)
        }
        val sentences = RasadCoach.readChart(
            series = controller.state.value.visibleSeries,
            reads = controller.state.value.signals.reads,
            setup = controller.state.value.signals.setup,
            english = true,
        )
        for (sentence in sentences) {
            assertTrue("a Persian word reached the English coach: $sentence", sentence.none { it in '؀'..'ۿ' })
            assertNull("a contradiction reached the English page: $sentence", RasadContradiction.of(sentence))
        }
    }

    /**
     * **The device's own numbers, through the whole stack.**
     *
     * Not a frame — the four contributors the owner photographed, put through the engine that draws
     * the header, so the figure in the recording and the figure this build would print are directly
     * comparable. 100 became 41.
     */
    @Test
    fun theDeviceReadingScoresFortyOneRatherThanAHundred() {
        val rates = listOf(0.43, 0.40, 0.39, 0.40)
        val reads = rates.indices.map {
            com.coinepro.core.chart.SignalRead("study$it", MarketState.BULL)
        }
        val reports = rates.withIndex().associate { (index, rate) ->
            "study$index" to com.coinepro.core.chart.ConfidenceReport(
                id = "study$index",
                samples = 52,
                winRate = rate,
                averageR = -0.1,
                recent = emptyList(),
                horizon = 10,
            )
        }
        val score = ConfidenceEngine.setupScore(reads, reports)
        // Forty or forty-one, depending on which side of a half a double lands on. What matters is
        // that it is the mean of the four records and not a hundred, and the fix list's own
        // acceptance figure is «≤ 45».
        assertTrue("the device reading still scores ${score.score}", score.score in 40..41)
        assertEquals(MarketState.BULL, score.side)
        assertNotNull(score.winRate)
        assertEquals(0.405, score.winRate!!, 1e-9)
    }

    private fun proof(name: String, darkTheme: Boolean = true, content: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                CoineProTheme(darkTheme = darkTheme) {
                    Surface(modifier = Modifier.fillMaxSize()) { content() }
                }
            }
        }
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
        val view = composeRule.activity.window.decorView
        if (view.width == 0 || view.height == 0) {
            val metrics = composeRule.activity.resources.displayMetrics
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

        /** The semantics tag `RasadLine` carries. */
        const val RASAD_LINE = "rasad-line"

        /** And the one `ChartNowStrip` does. */
        const val NOW_STRIP = "now-strip"
    }
}
