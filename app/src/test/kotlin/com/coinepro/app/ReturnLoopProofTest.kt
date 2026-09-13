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
import com.coinepro.core.common.ChallengeSurface
import com.coinepro.core.common.ReturnLoop
import com.coinepro.core.common.SymbolMove
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.model.MarketPlatform
import com.coinepro.feature.home.HomeScreen
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **The reason to come back**, photographed (run Σ, S5; doctrine D7).
 *
 * `ReturnLoopTest` decides what a quiet morning is and what a loud one is; this decides what either
 * one looks like at the top of Home. Both are needed and neither covers the other: the rule that a
 * card must be absent on a quiet morning is a unit test, and the claim that the card which *does*
 * appear says something a reader can act on is a picture or it is nothing.
 *
 * Three frames, in the three states D7 names:
 *
 * * **something new** — back after a night, with the movers and the counts, in Persian;
 * * the same in English, because the sentence is composed at the screen rather than in the model
 *   and a composed sentence is exactly what goes wrong in the language nobody checks;
 * * **nothing new** — the same Home, the same fixture, four minutes since the last visit, and
 *   neither card. That frame is the one worth having: it is the only evidence that the card is
 *   conditional rather than decorative, and a card at the top of Home that appears every morning is
 *   the failure the whole of D7 is written against.
 *
 * The offline state is the quiet one by construction — no quotes means no movers — so it is the
 * third frame's own case rather than a fourth.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReturnLoopProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now = 1_726_000_000_000L
    private val hour = 3_600_000L

    /** A night away, with the reader's own list having done something worth a line. */
    private val loud = ReturnLoop.sinceLastVisit(
        lastVisitEpochMillis = now - 14 * hour,
        nowEpochMillis = now,
        movers = listOf(
            SymbolMove("BTCUSDT", 0.058),
            SymbolMove("XAUUSD", -0.031),
            SymbolMove("ETHUSDT", 0.024),
            SymbolMove("SOLUSDT", 0.004),
        ),
        signals = 3,
        alerts = 1,
    )

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `a night away shows what moved and one thing to do`() {
        assertNotNull("the fixture is quiet, so the frame would prove nothing", loud)
        assertEquals(ReturnLoop.MOVERS_SHOWN, loud?.movers?.size)
        proof("sigma3-return-loop-phone-fa", since = true)
    }

    @Test
    @Config(sdk = [34], qualifiers = EN_PHONE)
    fun `the same morning in English`() {
        proof("sigma3-return-loop-phone-en", since = true, english = true)
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `four minutes away shows neither card`() {
        // The same screen and the same movers: only the clock differs. `ReturnLoop` answers null,
        // so Home draws what it drew before any of this existed.
        assertNull(
            ReturnLoop.sinceLastVisit(
                lastVisitEpochMillis = now - 4 * 60_000L,
                nowEpochMillis = now,
                movers = listOf(SymbolMove("BTCUSDT", 0.058)),
                signals = 3,
            ),
        )
        proof("sigma3-return-loop-quiet-phone-fa", since = false)
    }

    private fun proof(name: String, since: Boolean, english: Boolean = false) {
        render {
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
                since = if (since) loud else null,
                challenge = if (since) ReturnLoop.challengeFor(19_980L, english = english) else null,
                streak = if (since) 6 else 0,
                onDoChallenge = { _: ChallengeSurface -> },
            )
        }
        write(name)
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

    private fun write(name: String) {
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
    }
}
