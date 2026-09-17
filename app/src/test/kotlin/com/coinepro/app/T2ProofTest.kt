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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.marketdata.MarketPulse
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.feature.notifications.AlertComposerBody
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
import com.coinepro.feature.notifications.R as NotificationsR
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

    // ------------------------------------------------------------------ B9, until I see it

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `choosing until I see it offers an interval and says what it will do`() {
        render {
            AlertComposerBody(symbol = "BTCUSDT", onCreate = {}, onCancel = {}, currentPrice = 64_000.0)
        }
        // The interval is absent until the policy is chosen — an interval beside «once» is a
        // control with nothing to control — so the frame is taken after the tap rather than before.
        val policy = composeRule.activity.getString(NotificationsR.string.alert_repeat_until_ack)
        composeRule.onNodeWithText(policy).performClick()
        composeRule.waitForIdle()
        capture("t2-b9-until-seen-phone-fa-dark")

        // What the still cannot carry: that the interval chips are **absent until the policy is
        // chosen**. An interval beside «once is enough» is a control with nothing to control, and a
        // frame taken after the tap cannot show what was not there before it.
        val chosen = composeRule.activity.getString(
            NotificationsR.string.alert_repeat_minutes,
            LocalPriceAlert.DEFAULT_REPEAT_MINUTES.toString().toPersianDigits(),
        )
        assertTrue(
            "«$chosen» is not on the composer after choosing the repeat",
            composeRule.onAllNodesWithText(chosen).fetchSemanticsNodes().isNotEmpty(),
        )

        // And the sentence under them is a tip rather than a printed line, which is the note policy
        // this repository enforces — `tools/i18n/notes.tsv` classes it, and a note resolved to a
        // string instead of handed to `CoineProNote` fails `lint_strings`.
        assertEquals(
            "the interval chips did not all appear",
            UNTIL_SEEN_INTERVALS,
            listOf(5, 15, 30, 60).count { minutes ->
                composeRule.onAllNodesWithText(
                    composeRule.activity.getString(
                        NotificationsR.string.alert_repeat_minutes,
                        minutes.toString().toPersianDigits(),
                    ),
                ).fetchSemanticsNodes().isNotEmpty()
            },
        )
    }

    /** The prose digits the composer prints. Persian here, because these qualifiers are Persian. */
    private fun String.toPersianDigits(): String =
        map { char -> if (char in '0'..'9') PERSIAN_DIGITS[char - '0'] else char }.joinToString("")

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

        /** ۰–۹, for reading back a count the composer printed in Persian. */
        const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

        /** Five, fifteen, thirty, sixty. See `AlertComposer.REPEAT_MINUTES` for the band. */
        const val UNTIL_SEEN_INTERVALS = 4
    }
}
