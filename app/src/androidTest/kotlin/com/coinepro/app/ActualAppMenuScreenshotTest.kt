package com.coinepro.app

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.marketdata.MarketConnectionState
import com.coinepro.core.marketdata.MarketDataState
import com.coinepro.core.navigation.AppDestination
import com.coinepro.feature.home.HomeScreen
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActualAppMenuScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun captureActualRenderedHomeMenu() {
        composeRule.setContent {
            CoineProTheme {
                Scaffold(
                    topBar = { TopAppBar(title = { Text("CoinePro") }) },
                    bottomBar = {
                        NavigationBar(modifier = Modifier.testTag(BAR_TAG)) {
                            AppDestination.entries.forEach { destination ->
                                NavigationBarItem(
                                    selected = destination == AppDestination.entries.first(),
                                    onClick = {},
                                    icon = { Text(destination.mark) },
                                    label = { Text(stringResource(destination.labelRes)) },
                                )
                            }
                        }
                    },
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        HomeScreen(
                            state = MarketDataState(connection = MarketConnectionState.OFFLINE),
                            onRetry = {},
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()

        // A capture with no assertion passes on a blank screen, which is the one outcome it exists
        // to catch: this test's whole job is to prove the real navigation bar renders on a real
        // device, and a PNG nobody looks at proves nothing. Every destination's label is checked by
        // the string the app actually shows, so a destination added without a translation fails
        // here rather than shipping as an empty tab.
        //
        // **Inside the bar, and that is not tidiness.** The page underneath is a real screen with
        // real words on it, and one of them is «Chart» — the home screen's own chart shortcut. So
        // the unscoped lookup matched two nodes and the assertion died on the ambiguity rather than
        // on anything being wrong, which is a test failing at its own question. Anchored to the
        // bar, the label being looked for is the label the bar draws.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppDestination.entries.forEach { destination ->
            composeRule
                .onNode(
                    hasText(context.getString(destination.labelRes)) and
                        hasAnyAncestor(hasTestTag(BAR_TAG)),
                )
                .assertIsDisplayed()
        }

        // **Written somewhere that outlives this test, and verified before the test lets go.**
        //
        // ### The bug this replaces, because it is a good one
        //
        // The capture was written into the app's own files directory and the workflow collected it
        // with `adb run-as com.coinepro.app cat files/…`. That is the right directory for `run-as`
        // — and it never worked, because **Gradle uninstalls the app when
        // `connectedDebugAndroidTest` finishes**. By the time the workflow ran `run-as`, the
        // package was gone, so what it captured was the shell's own error text:
        //
        //     run-as: unknown package: com.coinepro.app
        //
        // Forty-two bytes. The workflow's `test -s` asks only whether the file is *non-empty*, and
        // an error message is non-empty, so the job went green and uploaded an artifact named "the
        // real rendered app menu" that was a sentence about failure. Nine releases of a green
        // check that checked nothing — the same shape as a menu row documented at fifty that was a
        // floor, and it is why this pass exists.
        //
        // ### So: the shell writes it, and to a path that survives the uninstall
        //
        // `/sdcard` belongs to the device, not to the package, so it is still there afterwards.
        // `takeScreenshot` cannot be used for the file itself — the bitmap it returns lives in the
        // app's process and the app's storage dies with it — but it is still taken first, because
        // a `null` from it means the framebuffer is not readable at all and that is worth failing
        // on separately from anything to do with files.
        //
        // The capture is then read back **through the shell** and its size checked here, so a test
        // that passed is a test that left a real picture behind. The workflow checks the PNG magic
        // bytes on top of that; `test -s` is what let a sentence through.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        assertNotNull(
            "UiAutomation returned no screenshot — the framebuffer is not readable.",
            instrumentation.uiAutomation.takeScreenshot(),
        )

        shell("screencap -p $CAPTURE_PATH")
        val size = shell("stat -c %s $CAPTURE_PATH").trim().toLongOrNull() ?: 0L
        assertTrue(
            "The capture at $CAPTURE_PATH is $size bytes. A screen of this app is tens of " +
                "kilobytes; anything this small is an error message, which is exactly what the " +
                "old `test -s` check was waving through.",
            size > MIN_CAPTURE_BYTES,
        )
    }

    /**
     * Run a shell command as the shell user and hand back everything it printed.
     *
     * Draining the descriptor is not tidiness — it is what makes the call **synchronous**.
     * `executeShellCommand` returns as soon as the command is spawned, so closing the pipe without
     * reading it races the command that is still writing the file.
     */
    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
        ).use { it.readBytes().toString(Charsets.UTF_8) }

    private companion object {
        /** What anchors the label lookups to the bar rather than to the page behind it. */
        const val BAR_TAG = "navigation-bar"

        /** The device's own storage, which outlives the package. See the note above. */
        const val CAPTURE_PATH = "/sdcard/coinepro-menu-render.png"

        /** Below this it is not a screen, it is a sentence. */
        const val MIN_CAPTURE_BYTES = 10_000L
    }
}
