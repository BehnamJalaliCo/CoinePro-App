package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import com.coinepro.core.datastore.ReaderMode
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.feature.chart.ChartScreen
import kotlinx.coroutines.CoroutineScope
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
import com.coinepro.core.designsystem.R as DesignR

/**
 * **The «→» row above the chart is gone, and this is what says so** (run Ω-FIX item 1).
 *
 * ### Why this file exists at all
 *
 * Because the claim was already made once. `docs/runs/RUN_OMEGA/CHECKLIST.md` carried «ردیف →
 * بالای چارت حذف شد» with a ✅ beside it, and the owner's device found the row in **every portrait
 * frame** of two recordings. What had actually been removed was the bar's *contents*; the band
 * itself was still drawn, holding one arrow, above the one page in this app whose whole product is
 * the height of the plot.
 *
 * A claim that is checked by reading the code is a claim that can be wrong in exactly that way. So
 * there are three assertions here and one picture:
 *
 * 1. [showsTopBar] — the production function, called directly — says no for the chart route, and
 *    still says yes for the routes that keep a bar. That is the rule, tested without a renderer.
 * 2. The page composed inside the shell's own scaffold shape draws **no app bar node**, at phone
 *    width, in portrait, in each reader mode.
 * 3. The chart's own header — the legend, carrying the instrument's name and now the way back — is
 *    the first row under the status bar.
 *
 * The scaffold below is shaped like the shell's and is not the shell: what is production here is
 * the *decision*, [showsTopBar], which the wrapper calls rather than restates. If that function
 * ever goes back to answering yes for the chart, this test fails and the frame it writes shows the
 * band.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartTopBarTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun `the shell draws no app bar over the chart, and still draws one everywhere it should`() {
        assertFalse("the chart route is still asking for an app bar", showsTopBar(CHART_PATTERN, isSubScreen = true))
        assertFalse("home draws its own heading", showsTopBar(HOME_ROUTE, isSubScreen = false))
        // The scope of the change, stated. Taking the bar off the chart must not take it off the
        // screens that have nothing else to carry a title or an arrow.
        assertTrue("the profile lost its app bar", showsTopBar(PROFILE_ROUTE, isSubScreen = true))
        assertTrue("market search lost its app bar", showsTopBar(MARKET_SEARCH_ROUTE, isSubScreen = true))
        assertEquals("only the chart is bar-less", setOf(CHART_PATTERN), BARELESS)
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE_PORTRAIT)
    fun `the phone chart in portrait has the symbol header as its first row`() {
        val controller = ScreenshotFixtures.chartController(scope, "BTCUSDT").also {
            it.toggleIndicator("ema")
            it.toggleIndicator("rsi")
        }
        shell("omegafix-chart-portrait-no-topbar-fa") {
            val ready = remember { controller }
            ChartScreen(controller = ready, onBack = {}, readerMode = ReaderMode.TRADER)
        }
        assertEquals(
            "an app bar was drawn above the chart",
            0,
            composeRule.onAllNodesWithContentDescription(APP_BAR).fetchSemanticsNodes().size,
        )
        val back = composeRule
            .onAllNodesWithContentDescription(composeRule.activity.getString(DesignR.string.legend_back))
            .fetchSemanticsNodes()
        assertEquals("the chart's own way back is missing", 1, back.size)
        // The header is the first row: the back arrow's top is inside the band an app bar would
        // have occupied, which is the whole of what «the row is gone» means in points.
        val density = composeRule.activity.resources.displayMetrics.density
        val topDp = back.first().boundsInRoot.top / density
        assertTrue("the chart's header starts $topDp dp down the page", topDp < FIRST_ROW_DP)
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE_PORTRAIT)
    fun `the simple reader gets the same page with no bar over it either`() {
        // «all reader modes» is in the fix list because the bar was a property of the *route*, and a
        // fix that only held for one mode would be the same claim made narrowly.
        val controller = ScreenshotFixtures.chartController(scope, "XAUUSD")
        shell("omegafix-chart-portrait-simple-fa") {
            val ready = remember { controller }
            ChartScreen(controller = ready, onBack = {}, readerMode = ReaderMode.SIMPLE)
        }
        assertEquals(
            "an app bar was drawn above the simple chart",
            0,
            composeRule.onAllNodesWithContentDescription(APP_BAR).fetchSemanticsNodes().size,
        )
    }

    /**
     * The shell's scaffold shape, with the shell's own [showsTopBar] deciding the bar.
     *
     * The `TopAppBar` here is the one Material draws and is tagged so a test can find it. It is
     * composed only if the production rule says to — which on this route it does not, which is what
     * makes the emptiness of the assertion above meaningful rather than tautological.
     */
    private fun shell(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                CoineProTheme(darkTheme = true) {
                    Scaffold(
                        topBar = {
                            if (showsTopBar(CHART_PATTERN, isSubScreen = true)) {
                                TopAppBar(
                                    title = { Text("") },
                                    navigationIcon = {
                                        IconButton(onClick = {}) {
                                            Icon(
                                                painter = painterResource(CoineProIcons.Back),
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                    modifier = Modifier.semantics { contentDescription = APP_BAR },
                                )
                            }
                        },
                    ) { insets ->
                        Surface(modifier = Modifier.fillMaxSize().padding(insets)) { content() }
                    }
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

        /** A phone held upright, in Persian. The configuration the owner recorded on. */
        const val FA_PHONE_PORTRAIT = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi"

        /** What the wrapper's app bar would be called if it were drawn. */
        const val APP_BAR = "app-bar"

        /**
         * How far down the page the first row may start.
         *
         * A Material small top app bar is sixty-four points and the legend has its own inset and
         * plate padding inside that. Anything under this is the header sitting where the band used
         * to be; anything over it means something is above the chart again.
         */
        const val FIRST_ROW_DP = 64f
    }
}
