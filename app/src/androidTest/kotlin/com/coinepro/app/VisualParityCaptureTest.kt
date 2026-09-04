package com.coinepro.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.navigation.AppDestination
import com.coinepro.feature.menu.MenuAccess
import com.coinepro.feature.menu.MenuCatalogue
import com.coinepro.feature.menu.MenuScreen
import com.coinepro.feature.menu.MenuTestTags
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The CoinePro half of the TradingView comparison, taken on a real device.
 *
 * ### Why this cannot be a Robolectric capture
 *
 * Layer A's goldens are rendered at `xxhdpi`, which is 480 dpi. The canonical device is a Pixel 6,
 * which is 420. A frame that is 411 dp wide is 1233 physical pixels on one and 1080 on the other,
 * and **those two images can never be compared** — the fact that both are "411 dp" is exactly the
 * trap. So the parity capture is taken through `UiAutomation.takeScreenshot()`, off the real
 * framebuffer, at the real density, exactly as an `adb screencap` of TradingView would be.
 *
 * ### What it writes
 *
 * Into the app's own files directory, one per screen:
 *
 * * `<name>.png` — the device frame;
 * * `<name>-elements.json` — every tagged element's box **in physical screen pixels**, which is
 *   what makes a measurement possible without anybody guessing a coordinate;
 * * `capture-manifest.json` — the device's own answers about itself.
 *
 * ### The device is checked, not assumed
 *
 * Density, resolution and font scale are read back off the device and checked against the
 * canonical profile before anything is captured. They are **assumptions rather than assertions**,
 * deliberately: on the wrong device this test should not write a capture at all, and it should not
 * fail either — a capture taken at another font scale looks perfectly fine and compares against
 * nothing, so producing one is the harm. Skipping says so, in the report, with the number the
 * device actually gave; and `capture-manifest.json` is written first, so the reason survives.
 *
 * ### What is not captured yet, and why
 *
 * The watchlist, Explore and Ideas need the fixture web that lives in the Robolectric source set —
 * a catalogue gateway, an intel gateway, a signal gateway, a preferences store. Duplicating nine
 * hundred lines of it here would create a second set of fixtures that drifts from the first, so
 * those screens are honestly absent: the comparator reports `ACTUAL_MISSING` for them rather than
 * comparing something approximate. What is here is what can be built from the app's own public
 * types: the bar in every state, and the menu in both account states.
 */
@RunWith(AndroidJUnit4::class)
class VisualParityCaptureTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * What the one composition is showing right now.
     *
     * A Compose test rule takes its content **once**, and every screen here needs the same rule —
     * so the screens are swapped through a state holder rather than by calling `setContent` again,
     * which would throw. It also happens to be the more honest arrangement: it is one running
     * activity being navigated, which is what a reader has.
     */
    private val slot = mutableStateOf<@Composable () -> Unit>({})
    private var started = false

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private val output: File by lazy {
        File(instrumentation.targetContext.filesDir, "visual-parity").apply { mkdirs() }
    }

    @Test
    fun captureBottomBar() {
        assertCanonicalDevice()
        val tags = buildList {
            add(AppChromeTestTags.BOTTOM_BAR)
            add(AppChromeTestTags.BOTTOM_BAR_DIVIDER)
            add(AppChromeTestTags.BOTTOM_BAR_CONTENT)
        }
        AppDestination.entries.forEach { destination ->
            capture("bottom-bar-${destination.route}-dark", tags) {
                CoineProBottomBar(
                    currentRoute = destination.route,
                    onSelect = {},
                    modifier = Modifier.testTag(AppChromeTestTags.BOTTOM_BAR),
                )
            }
        }
    }

    @Test
    fun captureMenu() {
        assertCanonicalDevice()
        listOf(true, false).forEach { signedIn ->
            val access = MenuAccess(platform = MarketPlatform.TRADEYAR, signedIn = signedIn)
            val tags = MenuCatalogue.sections(access)
                .flatMap { section -> section.items.map { MenuTestTags.row(it.entry.id) } }
            val name = if (signedIn) "menu-dark" else "menu-guest-dark"
            capture(name, tags) {
                MenuScreen(
                    access = access,
                    onOpen = {},
                    name = if (signedIn) "بهنام" else null,
                    email = if (signedIn) "trader@example.com" else null,
                    planLabel = if (signedIn) "حرفه‌ای" else null,
                    platformLabel = "کریپتو",
                    watchlistCount = 12,
                    onSignIn = if (signedIn) null else ({}),
                )
            }
        }
    }

    /* ------------------------------------------------------------------ the machinery */

    /**
     * Render, freeze, photograph the screen, and write down where everything is.
     *
     * The screenshot is of the **device**, not of the composition: it carries the status bar and
     * whatever the system draws at the foot, exactly as a capture of TradingView would. Those are
     * the system's and are masked in the specs; cropping them out here would mean the two sides
     * were cropped by different hands, which is its own way of not comparing like with like.
     */
    private fun capture(name: String, tags: List<String>, content: @Composable () -> Unit) {
        slot.value = content
        if (!started) {
            started = true
            composeRule.setContent {
                CoineProTheme(darkTheme = true) {
                    Box(modifier = Modifier.fillMaxSize().background(CoineProColors.Stage)) {
                        slot.value()
                    }
                }
            }
        }
        composeRule.waitForIdle()
        instrumentation.waitForIdleSync()

        val shot = instrumentation.uiAutomation.takeScreenshot()
        assertTrue(
            "UiAutomation returned no screenshot for '$name'. Without one there is nothing to " +
                "compare and nothing may be substituted for it.",
            shot != null,
        )
        File(output, "$name.png").outputStream().use { stream ->
            shot.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        val boxes = JSONObject()
        tags.forEach { tag ->
            val nodes = composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes()
            if (nodes.isNotEmpty()) {
                boxes.put(tag, JSONArray(screenBox(nodes.first())))
            }
        }
        boxes.put("__frame__", JSONArray(listOf(0, 0, shot.width, shot.height)))
        File(output, "$name-elements.json").writeText(boxes.toString(2))
    }

    /**
     * An element's box in **screen** pixels, which is the only coordinate space that means
     * anything here.
     *
     * `boundsInRoot` is relative to the composition, and the composition does not start at the top
     * of the screen — the status bar is above it. Adding the window's own position is what makes a
     * number in this file comparable to a number read off a full-device capture of another app.
     */
    private fun screenBox(node: SemanticsNode): List<Int> {
        val window = IntArray(2)
        composeRule.activity.window.decorView.getLocationOnScreen(window)
        val bounds = node.boundsInRoot
        return listOf(
            (bounds.left + window[0]).toInt(),
            (bounds.top + window[1]).toInt(),
            bounds.width.toInt(),
            bounds.height.toInt(),
        )
    }

    /**
     * The device's own answers, asserted and then written down.
     *
     * Written down because a capture without its provenance is the same problem this whole
     * pipeline exists to refuse on the TradingView side, and it would be strange to demand it of
     * somebody else's screenshots and not of our own.
     */
    private fun assertCanonicalDevice() {
        val metrics = instrumentation.targetContext.resources.displayMetrics
        val configuration = instrumentation.targetContext.resources.configuration
        val manifest = JSONObject()
            .put("model", android.os.Build.MODEL)
            .put("api_level", android.os.Build.VERSION.SDK_INT)
            .put("resolution", JSONArray(listOf(metrics.widthPixels, metrics.heightPixels)))
            .put("density_dpi", metrics.densityDpi)
            .put("font_scale", configuration.fontScale.toDouble())
            .put("orientation", if (configuration.orientation == 1) "portrait" else "landscape")
            .put("locale", configuration.locales[0].toLanguageTag())
        File(output, "capture-manifest.json").writeText(manifest.toString(2))

        assumeTrue(
            "Font scale is ${configuration.fontScale} and the canonical profile is 1.0. A capture " +
                "taken at another scale looks perfectly fine and compares against nothing: run " +
                "`adb shell settings put system font_scale 1.0` first.",
            kotlin.math.abs(configuration.fontScale - 1.0f) < 0.001f,
        )
        assumeTrue(
            "Density is ${metrics.densityDpi} dpi and the canonical device is $CANONICAL_DPI. " +
                "A Pixel 6 frame and an xxhdpi frame are different sizes at the same logical " +
                "width, and nothing is scaled to make them agree. See " +
                "docs/design/TRADINGVIEW_VISUAL_PARITY.md.",
            metrics.densityDpi == CANONICAL_DPI,
        )
        assumeTrue(
            "Screen is ${metrics.widthPixels}×${metrics.heightPixels} and the canonical device is " +
                "$CANONICAL_WIDTH×$CANONICAL_HEIGHT.",
            metrics.widthPixels == CANONICAL_WIDTH,
        )
    }

    private companion object {
        const val CANONICAL_DPI = 420
        const val CANONICAL_WIDTH = 1080
        const val CANONICAL_HEIGHT = 2400
    }
}
