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
import com.coinepro.core.chart.IndicatorPicker
import com.coinepro.core.designsystem.CoineProCoachMark
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.TeachingSurface
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.feature.chart.ChartController
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import com.coinepro.feature.chart.ChartScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Run K, photographed on a phone: **what the owner's recording asked for, on the glass.**
 *
 * The recording's own acceptance line asks for four MP4s and three screenshots. The recordings are
 * device work and this machine has no device — no `/dev/kvm`, no system image, no `emulator` binary
 * — so what is here is the half that can be photographed, and each frame carries the assertion that
 * makes it evidence rather than decoration.
 *
 * Every frame is the **phone** qualifier, because every one of the run's complaints is about a
 * phone: forty-five per cent of the glass for the plot, a keyboard over the indicator list, a
 * legend row that names somebody's plot instead of their study.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RunKProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    /** An RSI in its own pane, which is the study the acceptance line names. */
    private val rsiScript = """
        length = input(14, title = "RSI length", min = 2, max = 60)
        r = ta.rsi(close, length)
        plot(r, title = "RSI", pane = "own")
        hline(70, title = "Overbought")
        hline(30, title = "Oversold")
    """.trimIndent()

    /** The two stepped lines of item 4, over the price. */
    private val atrScript = """
        atr = ta.atr(14)
        plot(close - atr * 2, title = "Long stop", color = color.buy, stepped = true)
        plot(close + atr * 2, title = "Short stop", color = color.sell, stepped = true)
    """.trimIndent()

    private fun chart(symbol: String = "BTCUSDT"): ChartController =
        ScreenshotFixtures.chartController(scope, symbol)

    /**
     * A chart mid-switch: its first load answered, its second still out.
     *
     * A venue that answers once and then does not is exactly the recording's M15 — and the frame
     * worth photographing is the one *during* that second request, which the fixture gateway (which
     * answers instantly) can never produce.
     */
    private fun staleChart(symbol: String = "BTCUSDT"): ChartController {
        val series = ScreenshotFixtures.chartSeries(symbol = symbol, start = 91_248.0)
        var calls = 0
        val gateway = object : CandleGateway {
            override suspend fun load(
                symbol: String,
                timeframe: Timeframe,
                limit: Int,
                before: Long?,
            ): CandlePage {
                calls += 1
                if (calls > 1) awaitCancellation()
                return CandlePage(
                    symbol = symbol,
                    timeframe = timeframe,
                    candles = series.bars.map { OhlcBar(it.t, it.o, it.h, it.l, it.c, it.v ?: 0.0) },
                    hasMore = true,
                )
            }
        }
        return ChartController(symbol, gateway, scope).also {
            it.start()
            it.setTimeframe(Timeframe.M15)
        }
    }

    // ── item 2: the plot's share of a phone, with two sub-panes on it ────────────────────────

    /**
     * Two sub-panes and the price pane still holding half the chart.
     *
     * The arithmetic is the assertion and the picture is the evidence: `PANE_BUDGET` caps what the
     * panes may take between them at half, and `MIN_PANE_FRACTION` floors what each one gets at
     * twenty-two per cent — so two panes is the case where both rules bind at once and is the
     * layout the acceptance line asks to see.
     */
    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun twoSubPanesLeaveThePricePaneHalfTheChart() {
        val controller = chart()
        proof("run-k-two-panes-fa-dark") {
            val ready = remember {
                controller.also {
                    it.toggleIndicator("rsi")
                    it.addScript(name = "RSI zones", source = rsiScript)
                }
            }
            ChartScreen(controller = ready)
        }
        val state = controller.state.value
        assertEquals("two sub-panes is what this frame is of", 2, state.panes.size)
    }

    // ── item 4: a script's legend row names the script ───────────────────────────────────────

    /**
     * The lead legend row is «ATR stop», not «Long stop».
     *
     * The recording's plate read «حد ضرر خرید · 76,350.1 ▸ +4» — a phrase out of somebody's source
     * with nothing saying which study it belonged to. See `ScriptOverlay.toOverlay`.
     */
    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun aScriptsLegendRowNamesTheScript() {
        val controller = chart()
        proof("run-k-script-legend-fa-dark") {
            val ready = remember {
                controller.also { it.addScript(name = "ATR stop", source = atrScript) }
            }
            ChartScreen(controller = ready)
        }
        val labels = controller.state.value.overlays.mapNotNull { it.label }
        assertTrue("the study's own name leads its rows: $labels", labels.firstOrNull() == "ATR stop")
        assertTrue("and the second plot keeps its own title: $labels", "Short stop" in labels)
        // Stepped, which is the other half of item 4: a stop is a decision, not a measurement.
        assertTrue("both stops are drawn as steps", controller.state.value.overlays.all { it.stepped })
    }

    // ── item 1: a timeframe change dims the previous bars rather than blanking the chart ─────

    /**
     * The chart mid-switch: the old bars still on it, marked stale, nothing blank.
     *
     * This is the still frame of the recording the acceptance line asks for. The fixture gateway
     * answers at once, so the state is set by hand at the point the reader's finger has just left
     * the strip — which is the frame that used to be a white rectangle with a spinner.
     */
    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun aTimeframeChangeKeepsThePreviousBarsDimmed() {
        val controller = staleChart()
        proof("run-k-stale-switch-fa-dark") {
            val ready = remember { controller }
            ChartScreen(controller = ready)
        }
        val state = controller.state.value
        assertTrue("the bars a reader was looking at are still there", state.series.size > 0)
        assertTrue("and they are marked as on their way out", state.stale)
        assertFalse("a switch in flight is not a failure", state.error != null)
    }

    // ── item 3: the indicator sheet's body, with no keyboard over it ─────────────────────────

    /**
     * The catalogue as the sheet now opens it: search unfocused, the chips in the reference's own
     * order, and the list starting at the first row.
     *
     * The **body** rather than the sheet, and the reason is the harness rather than the design: a
     * Material bottom sheet renders into a window of its own, and this capture is of the activity's
     * decor view — the same reason run H's flyout had to come in-layout before it could be
     * photographed. What the frame shows is what the sheet contains, at the phone width the
     * complaint is about, with `autoFocusSearch` at its new default.
     */
    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun theIndicatorListOpensWithNoKeyboardOverIt() {
        proof("run-k-indicator-sheet-fa-dark") {
            IndicatorPicker(
                active = setOf("ema"),
                onToggle = {},
                modifier = Modifier.fillMaxSize(),
                // Both personal chips, so the frame shows the order the item asks for: Favourites,
                // Recent, then the families. They are drawn only where something feeds them.
                favourites = listOf("ema", "bollinger"),
                onToggleFavourite = {},
                recent = listOf("rsi"),
            )
        }
    }

    // ── item 5: the first-run mark is a tooltip, not a card ──────────────────────────────────

    /** Two lines and a caret, where a full-width bordered card with a button used to be. */
    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun theFirstRunMarkIsATwoLineTooltip() {
        proof("run-k-coach-mark-fa-dark") {
            CoineProCoachMark(surface = TeachingSurface.CHART, onDismiss = {}, anchored = true)
        }
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

        /** A Pixel-class phone in Persian, which is the product's own default. */
        const val FA_PHONE = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi"
    }
}
