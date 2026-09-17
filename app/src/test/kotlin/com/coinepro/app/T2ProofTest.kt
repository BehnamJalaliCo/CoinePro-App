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
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.marketdata.MarketPulse
import com.coinepro.core.model.MarketPlatform
import com.coinepro.feature.menu.MENU_NO_TRADING_TAG
import com.coinepro.feature.menu.MenuAccess
import com.coinepro.feature.menu.MenuScreen
import com.coinepro.feature.search.MarketPulseCell
import com.coinepro.feature.search.MarketPulseRow
import com.coinepro.feature.search.MarketPulseSheet
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.coinepro.feature.menu.R as MenuR
import com.coinepro.feature.search.R as SearchR

/**
 * **The frames phase B owes** (run Τ2).
 *
 * Each case takes a still *and* pins the one claim a still cannot make on its own — that the pulse
 * row prints figures and no prose, that the sentence about what this app is is the prominent one.
 *
 * The qualifiers are the owner's phone: 411 dp at 420 dpi, Persian, right to left, and the same in
 * English for the surfaces whose copy is the point. Frames land in `app/build/proof/` and are named
 * in `docs/runs/RUN_T2/CHECKLIST.md`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class T2ProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // ------------------------------------------------------------------ B5, the pulse's ⓘ

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the pulse row prints figures and carries the explanation behind a mark`() {
        render { MarketPulseRow(pulse = PULSE, onOpen = {}) }
        capture("t2-b5-pulse-row-phone-fa-dark")

        // The claim the frame cannot make: that none of the explanation is *on* the row. The four
        // `_why` sentences are paragraphs, and a row that carried even one of them would be a
        // different row from the one in the picture.
        listOf(
            SearchR.string.markets_pulse_cap_why,
            SearchR.string.markets_pulse_dominance_why,
            SearchR.string.markets_pulse_mood_why,
            SearchR.string.markets_pulse_venue_only,
        ).forEach { why ->
            val text = composeRule.activity.getString(why)
            assertEquals(
                "the pulse row is printing «${text.take(24)}…» rather than keeping it behind the mark",
                0,
                composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size,
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the mark opens the sentence that was kept off the row`() {
        render { MarketPulseSheet(cell = MarketPulseCell.MARKET_CAP, pulse = PULSE, onDismiss = {}) }
        capture("t2-b5-pulse-sheet-phone-fa-dark")

        val why = composeRule.activity.getString(SearchR.string.markets_pulse_cap_why)
        assertTrue(
            "the sheet did not carry the explanation the row gave up",
            composeRule.onAllNodesWithText(why, substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    // ------------------------------------------------------------------ B10, what this app is not

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the menu says what this app does not do, in Persian`() {
        render { menu() }
        capture("t2-b10-menu-phone-fa-dark")
        assertSentenceIsDrawn()
    }

    @Test
    @Config(sdk = [34], qualifiers = ENGLISH)
    fun `and in English`() {
        render(dark = false) { menu() }
        capture("t2-b10-menu-phone-en-light")
        assertSentenceIsDrawn()
        assertEquals(
            "the English string drifted from the store listing's",
            "This app does not trade and takes no commission.",
            composeRule.activity.getString(MenuR.string.menu_no_trading),
        )
    }

    private fun assertSentenceIsDrawn() {
        assertTrue(
            "the sentence about what this app is is not on the menu",
            composeRule.onAllNodesWithTag(MENU_NO_TRADING_TAG).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Composable
    private fun menu() {
        MenuScreen(
            access = MenuAccess(platform = MarketPlatform.TRADEYAR, signedIn = false),
            onOpen = {},
            onSignIn = {},
        )
    }

    // ------------------------------------------------------------------ the rig

    private fun render(dark: Boolean = true, content: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                CoineProTheme(darkTheme = dark) {
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

        /** The same phone, in the language `values/` holds. See the locale-inversion note. */
        const val ENGLISH = "en-rUS-ldltr-w411dp-h914dp-420dpi"

        /**
         * One figure and three dashes, which is what this build actually has.
         *
         * Deliberately not four figures: the row the owner will see is this one, and a proof frame
         * of a state the app cannot reach is a picture of a different product.
         */
        val PULSE = MarketPulse(turnover24h = 1_430_000_000.0, breadth = 54, markets = 312)
    }
}
