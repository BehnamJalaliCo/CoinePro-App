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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.symbols.BundledUniverse
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolUniverse
import com.coinepro.feature.search.ChangeBand
import com.coinepro.feature.search.MarketFilter
import com.coinepro.feature.search.MarketFilterBody
import com.coinepro.feature.search.MarketsScreen
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.coinepro.feature.search.R as SearchR

/**
 * **Every market the venue serves, on a screen** (run ΤΦΥ, F1–F3).
 *
 * The universe stopped being «what has a logo in this repository» and became «what is listed», which
 * on the crypto side is the difference between 178 markets and 1 333. `SymbolUniverseTest` holds the
 * arithmetic; this holds the picture, because two of the three things F1 changed can only be seen:
 * that a market with no artwork now draws as a monogram rather than not at all, and that a list of
 * hundreds still opens on a first page rather than composing all of them.
 *
 * The rows are the **bundled table**, which is what the app carries when a venue answers with
 * nineteen symbols — so this frame is also the answer to «what does the markets tab look like on
 * CoinePro-FX», which is the case the owner will be looking at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UniverseProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private var started = false

    /** The markets tab over the bundled universe — three hundred coins and forty-nine other markets. */
    private fun markets() {
        check(!started) { "one composition per test: an activity's content is set once" }
        started = true
        val controller = MarketSearchController(
            gateway = ScreenshotFixtures.emptyCatalog(),
            scope = scope,
            universe = com.coinepro.core.marketdata.BundledSymbolUniverseGateway,
        )
        render {
            val ready = remember { controller.also { it.start() } }
            MarketsScreen(
                controller = ready,
                sparklines = ScreenshotFixtures.sparklineStore(scope),
                onOpenSymbol = {},
                onOpenSearch = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the markets tab lists the whole universe, monograms and all`() {
        markets()
        capture("phi-markets-universe-phone-fa")

        // The rows a reader can see are the *first page*, not the universe: F2's whole point is
        // that a list of thousands opens on two hundred. What is asserted is the shape rather than
        // an exact count — a `LazyColumn` composes what fits plus a little, and the number that
        // fits is this container's window, not a rule.
        val universe = SymbolUniverse.bundled().size
        assertTrue("the bundled universe shrank to $universe", universe >= BundledUniverse.CRYPTO.size)
        assertTrue("a page is not two hundred", SymbolUniverse.PAGE == 200)

        // A market this repository has no mark for is **on the screen**. That is the reversal: the
        // catalogue used to filter it out, and a monogram is what replaced the blank square.
        val unmarked = SymbolUniverse.bundled().map { it.base.orEmpty() }
            .filterNot { com.coinepro.core.symbols.SymbolArtwork.covers(it + "USDT") }
        assertTrue("every bundled coin has artwork — the case F1 is about is not in this fixture", unmarked.isNotEmpty())
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the filter offers four questions, with one of them answered`() {
        // **The sheet's body, not the sheet.** A `ModalBottomSheet` draws into a window of its own
        // and this harness captures the activity's decor view, so a frame taken with the sheet up
        // is a frame of the list behind it — which is what the first attempt at this produced, two
        // byte-identical PNGs. `MarketFilterBody` is the same composition the sheet puts in front
        // of a reader; that the *button* opens it is asserted below, on the real screen.
        var filter = MarketFilter(change = ChangeBand.UP_5)
        render {
            MarketFilterBody(
                filter = filter,
                types = listOf(SymbolCategory.CRYPTO, SymbolCategory.METAL, SymbolCategory.FOREX),
                venues = listOf("LBank", "CoinePro FX"),
                onChange = { filter = it },
            )
        }
        capture("phi-markets-filter-phone-fa")

        // Every one of the four questions, on screen at once.
        listOf(
            SearchR.string.markets_filter_type,
            SearchR.string.markets_filter_venue,
            SearchR.string.markets_filter_change,
            SearchR.string.markets_filter_turnover,
            SearchR.string.markets_filter_up_5,
            SearchR.string.markets_filter_turnover_10m,
            // Present because one band is chosen — an untouched sheet has nothing to clear.
            SearchR.string.markets_filter_clear,
        ).forEach { label ->
            val text = composeRule.activity.getString(label)
            assertTrue(
                "«$text» is missing from the filter",
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the markets tab has a button that opens the filter`() {
        markets()
        val filter = composeRule.activity.getString(SearchR.string.markets_filter)
        val button = composeRule.onAllNodesWithContentDescription(filter)
        assertTrue("the markets tab has no filter button", button.fetchSemanticsNodes().isNotEmpty())
        button[0].performClick()
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
        // The sheet is in another window, so this reads the merged semantics tree rather than the
        // pixels: the title is the sheet's own and nothing else in the app carries it.
        assertTrue(
            "the filter button opened nothing",
            composeRule.onAllNodesWithText(
                composeRule.activity.getString(SearchR.string.markets_filter_title),
            ).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun render(content: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                CoineProTheme(darkTheme = true) {
                    Surface(modifier = Modifier.fillMaxSize()) { content() }
                }
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
        const val PHONE = "fa-rIR-ldrtl-w411dp-h914dp-420dpi"
    }
}
