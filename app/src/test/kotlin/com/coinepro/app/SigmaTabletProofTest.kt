package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.coinepro.core.common.ReturnLoop
import com.coinepro.core.common.SymbolMove
import com.coinepro.core.designsystem.CoineProSheetBody
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.designsystem.SHEET_DIALOG_MAX_WIDTH
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.script.ScriptPaste
import com.coinepro.core.script.ScriptPromptKit
import com.coinepro.feature.home.HomeScreen
import com.coinepro.feature.script.ScriptMinePanelPreview
import com.coinepro.feature.script.ScriptPasteBody
import com.coinepro.feature.script.ScriptPromptBody
import com.coinepro.feature.script.ScriptScreen
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Everything Σ built, on the big glass** (run Σ, S8).
 *
 * S8's line is «all of the above on tablet». Σ1 and Σ3 added four surfaces the parity matrix had
 * never seen — the paste panel, the prompt kit, «مال خودم», and the two cards at the top of Home —
 * and a surface that exists only at 411 dp is a surface that has not been checked, however many
 * phone frames it has.
 *
 * Two devices and both foldings, in both languages:
 *
 * * **Pixel Tablet**, 1280×800 at `xhdpi` — the landscape workbench;
 * * **Galaxy Tab S9 Ultra**, 1973×1232 at `hdpi` — the widest panel the plan names, and the one
 *   that finds a `fillMaxWidth` nobody meant;
 * * **Pixel Fold**, open and closed, because the same build has to be both within a second.
 *
 * The two sheets are rendered as the **dialog** they become on an expanded window, not as the
 * bottom strip the phone gets: `CoineProSheet` already swaps shape at `showsTwoPanes`, and
 * `SheetShapeTest` asserts the cap. What a dialog window cannot do is be photographed — Robolectric
 * draws it into its own window and the activity capture comes back empty — so these frames put the
 * same body inside the same cap, centred the way the dialog centres it. The width in the picture is
 * the width the reader gets; only the scrim behind it is missing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SigmaTabletProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private val now = 1_726_000_000_000L

    /** What an assistant returns when it was asked for «a NamaScript indicator». */
    private val messy = """
        //@version=5
        indicator("EMA Cross", overlay=true)
        fastLen = input.int(9, "Fast", minval=1)
        fast = ema(close, fastLen)
        slow = ta.rma(close, 21)
        plot(fast, "Fast", color=color.orange, linewidth=2)
        plotshape(fast > slow && close(1) < slow, style=shape.triangleup)
    """.trimIndent()

    // ── Home, with the return loop on it ─────────────────────────────────────────────────────

    @Composable
    private fun Home(english: Boolean) {
        HomeScreen(
            state = ScreenshotFixtures.marketState(),
            onRetry = {},
            displayName = if (english) "Behnam" else "بهنام",
            briefing = ScreenshotFixtures.homeBriefing,
            portfolio = ScreenshotFixtures.homePortfolio,
            platforms = MarketPlatform.entries,
            activePlatform = MarketPlatform.TRADEYAR,
            onToggleBalanceHidden = {},
            onOpenPortfolio = {},
            onOpenTools = {},
            onOpenActivity = {},
            onOpenNews = {},
            watchlist = listOf("BTCUSDT", "XAUUSD", "ETHUSDT"),
            since = ReturnLoop.sinceLastVisit(
                lastVisitEpochMillis = now - 14 * 3_600_000L,
                nowEpochMillis = now,
                movers = listOf(
                    SymbolMove("BTCUSDT", 0.058),
                    SymbolMove("XAUUSD", -0.031),
                    SymbolMove("ETHUSDT", 0.024),
                ),
                signals = 3,
                alerts = 1,
            ),
            challenge = ReturnLoop.challengeFor(19_980L, english = english),
            streak = 6,
            onDoChallenge = {},
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun homeReturnLoopPixelTabletFaDark() = proof("sigma-home-loop-pixel-tablet-fa-dark") { Home(english = false) }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun homeReturnLoopPixelTabletEnLight() =
        proof("sigma-home-loop-pixel-tablet-en-light", darkTheme = false) { Home(english = true) }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun homeReturnLoopTabS9UltraFaDark() = proof("sigma-home-loop-tab-s9-ultra-fa-dark") { Home(english = false) }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun homeReturnLoopTabS9UltraEnLight() =
        proof("sigma-home-loop-tab-s9-ultra-en-light", darkTheme = false) { Home(english = true) }

    @Test
    @Config(sdk = [34], qualifiers = FA_TABLET_PORTRAIT)
    fun homeReturnLoopTabletPortraitFaDark() = proof("sigma-home-loop-tablet-portrait-fa-dark") { Home(english = false) }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_OPEN)
    fun homeReturnLoopFoldOpenFaDark() = proof("sigma-home-loop-fold-open-fa-dark") { Home(english = false) }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_CLOSED)
    fun homeReturnLoopFoldClosedFaDark() = proof("sigma-home-loop-fold-closed-fa-dark") { Home(english = false) }

    // ── the paste panel ──────────────────────────────────────────────────────────────────────

    @Composable
    private fun Paste() {
        val paste = remember { ScriptPaste.read(messy) }
        Capped {
            CoineProSheetBody(
                title = if (inEn()) "Paste a script" else "چسباندن اسکریپت",
                subtitle = if (inEn()) "read as TradingView and translated" else "به عنوان اسکریپت تریدینگ‌ویو خوانده و ترجمه شد",
            ) {
                ScriptPasteBody(paste = paste, onKeep = {}, onUndo = {}, onUseTemplate = {})
            }
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun pastePixelTabletFaDark() = proof("sigma-paste-pixel-tablet-fa-dark") { Paste() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun pasteTabS9UltraEnLight() = proof("sigma-paste-tab-s9-ultra-en-light", darkTheme = false) { Paste() }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_OPEN)
    fun pasteFoldOpenFaDark() = proof("sigma-paste-fold-open-fa-dark") { Paste() }

    // ── the prompt kit ───────────────────────────────────────────────────────────────────────

    @Composable
    private fun Prompt() {
        val english = inEn()
        val prompt = remember(english) {
            ScriptPromptKit.prompt(symbol = "XAUUSD", timeframe = "H1", english = english)
        }
        Capped {
            CoineProSheetBody(
                title = if (english) "Ask an assistant" else "پرسیدن از دستیار",
                subtitle = if (english) "copy this, paste the answer back" else "این را کپی کنید، جواب را برگردانید",
            ) {
                ScriptPromptBody(prompt = prompt, onOpenAssistant = {}, onPasteResult = {})
            }
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun promptPixelTabletFaDark() = proof("sigma-prompt-pixel-tablet-fa-dark") { Prompt() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun promptTabS9UltraEnLight() = proof("sigma-prompt-tab-s9-ultra-en-light", darkTheme = false) { Prompt() }

    // ── «مال خودم», and the studio it lives in ───────────────────────────────────────────────

    @Composable
    private fun Mine() {
        val english = inEn()
        val controller = remember(english) {
            ScreenshotFixtures.scriptController(scope).also {
                it.setSeries(ScreenshotFixtures.chartSeries())
                it.openPreset(com.coinepro.core.script.ScriptPresets.byId("rsi-zones")!!)
                it.rename(if (english) "My RSI" else "آر‌اس‌آی من")
                it.describe(
                    if (english) "Buys the thirty line, sells the seventy" else "روی خط سی می‌خرد، روی هفتاد می‌فروشد",
                )
                it.setTags(if (english) "momentum, mine" else "مومنتوم، مال خودم")
            }
        }
        Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScriptMinePanelPreview(controller)
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun minePixelTabletFaDark() = proof("sigma-mine-pixel-tablet-fa-dark") { Mine() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun mineTabS9UltraEnLight() = proof("sigma-mine-tab-s9-ultra-en-light", darkTheme = false) { Mine() }

    @Composable
    private fun Studio() {
        val series = ScreenshotFixtures.chartSeries()
        val controller = remember {
            ScreenshotFixtures.scriptController(scope).also {
                it.setSeries(series)
                it.openPreset(com.coinepro.core.script.ScriptPresets.byId("rsi-zones")!!)
            }
        }
        ScriptScreen(controller = controller, symbol = "XAUUSD", series = series)
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun studioPixelTabletFaDark() = proof("sigma-studio-pixel-tablet-fa-dark") { Studio() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun studioTabS9UltraEnLight() = proof("sigma-studio-tab-s9-ultra-en-light", darkTheme = false) { Studio() }

    @Test
    @Config(sdk = [34], qualifiers = FA_TABLET_PORTRAIT)
    fun studioTabletPortraitFaDark() = proof("sigma-studio-tablet-portrait-fa-dark") { Studio() }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_OPEN)
    fun studioFoldOpenFaDark() = proof("sigma-studio-fold-open-fa-dark") { Studio() }

    @Test
    @Config(sdk = [34], qualifiers = FA_FOLD_CLOSED)
    fun studioFoldClosedFaDark() = proof("sigma-studio-fold-closed-fa-dark") { Studio() }

    // ── the rig ──────────────────────────────────────────────────────────────────────────────

    /**
     * The shape `CoineProSheet` takes on an expanded window: capped at [SHEET_DIALOG_MAX_WIDTH],
     * nine tenths of the height, centred — the dialog's own modifiers, in the same order, and
     * deliberately **without** a scroll of its own. That is the whole of what S8 found here: the
     * dialog used to scroll, a scrolling container measures its child unbounded, and every sheet
     * whose body scrolls itself threw the moment it was opened on a tablet. These three frames
     * were the first thing ever to render one at 1280 dp.
     */
    @Composable
    private fun Capped(content: @Composable () -> Unit) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .widthIn(max = SHEET_DIALOG_MAX_WIDTH)
                    .fillMaxHeight(0.9f)
                    .padding(CoineProSpacing.Two),
            ) {
                content()
            }
        }
    }

    @Composable
    private fun inEn(): Boolean = com.coinepro.core.designsystem.inEnglish()

    private fun proof(name: String, darkTheme: Boolean = true, content: @Composable () -> Unit) {
        composeRule.setContent {
            CoineProTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                    Surface(modifier = Modifier.fillMaxSize()) { content() }
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
        OUTPUT.mkdirs()
        File(OUTPUT, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        val OUTPUT = File("build/proof")
        const val FA_1280 = "fa-rIR-ldrtl-sw800dp-w1280dp-h800dp-xhdpi"
        const val EN_1280 = "en-rUS-ldltr-sw800dp-w1280dp-h800dp-xhdpi"
        const val FA_S9U = "fa-rIR-ldrtl-sw1232dp-w1973dp-h1232dp-hdpi"
        const val EN_S9U = "en-rUS-ldltr-sw1232dp-w1973dp-h1232dp-hdpi"
        const val FA_TABLET_PORTRAIT = "fa-rIR-ldrtl-sw800dp-w800dp-h1280dp-xhdpi"
        const val FA_FOLD_OPEN = "fa-rIR-ldrtl-sw775dp-w930dp-h775dp-xhdpi"
        const val FA_FOLD_CLOSED = "fa-rIR-ldrtl-w411dp-h797dp-xxhdpi"
    }
}
