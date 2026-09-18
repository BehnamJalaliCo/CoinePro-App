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
import androidx.compose.ui.test.onAllNodesWithText
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.update.AppRelease
import com.coinepro.core.update.AppUpdateStatus
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

/**
 * **The update card, which exists because there is no app store** (run א).
 *
 * Google Play does not serve Iran, so the app is installed from a downloaded APK and nothing ever
 * tells a reader that a newer build exists. `LaunchReadinessScreen` now does, and the three claims
 * worth pinning are all invisible from a screenshot on their own:
 *
 * 1. the card **appears** when there is a newer build,
 * 2. it **does not appear** otherwise — and «nothing there» is the one state a frame cannot
 *    distinguish from «the test rendered the wrong screen»,
 * 3. it is drawn at a tablet width too, on the day it was written rather than the run after, which
 *    is `RUN_PSI/RESUME.md`'s own instruction to the next session.
 *
 * Frames land in `app/build/proof/` and are named in `docs/runs/RUN_ALEF/CHECKLIST.md`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlefProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val newer = AppRelease(
        versionCode = 50_100_000L,
        versionName = "5.1.0",
        url = "https://pro-chart.com/download/pro-chart-5.1.0.apk",
        sha256 = "3b1f" + "0".repeat(60),
        notesFa = "چارت روی تبلت اصلاح شد و خبر نسخه‌ی تازه از این به بعد داخل خود برنامه می‌آید.",
        notesEn = "The chart on a tablet is fixed, and the app now tells you when a newer build is out.",
        mandatory = false,
    )

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `a newer build puts a card at the top of the safety screen`() {
        render { SafetyScreen(update = AppUpdateStatus.Available(newer)) }
        assertOnce("نسخه‌ی تازه‌ای منتشر شده")
        // The version alone rather than the whole line: the card wraps it in the bidi isolates
        // every Latin figure on a right-to-left screen gets, and asserting the invisible characters
        // would be asserting `BidiText` rather than the card.
        assertOnce("5.1.0")
        // The digest, so a reader can check the file they downloaded. Lowercased by the card.
        assertOnce(newer.sha256.lowercase())
        assertOnce("دریافت نسخه‌ی تازه")
        capture("alef-update-available-fa")
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `nothing is drawn when the running build is the newest`() {
        render { SafetyScreen(update = AppUpdateStatus.Current) }
        // Asserted against a screen that is definitely the right one: the safety title has to be
        // there, or «no update card» would be satisfied by an empty render.
        assertOnce("ایمنی و انتشار")
        assertAbsent("نسخه‌ی تازه‌ای منتشر شده")
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `nothing is drawn when the check never answered`() {
        render { SafetyScreen(update = AppUpdateStatus.Unknown) }
        assertOnce("ایمنی و انتشار")
        assertAbsent("نسخه‌ی تازه‌ای منتشر شده")
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `a build the owner marked important says so, and still opens nothing by itself`() {
        render { SafetyScreen(update = AppUpdateStatus.Available(newer.copy(mandatory = true))) }
        assertOnce("این یکی را همین حالا نصب کنید.")
        // The doors are all still there: the card is one more row on a screen that scrolls, not a
        // wall. `AppUpdate`'s note is the argument; this is the pixels agreeing with it.
        assertOnce("ایمنی و انتشار")
        capture("alef-update-mandatory-fa")
    }

    @Test
    @Config(sdk = [34], qualifiers = PIXEL_TABLET)
    fun `the card is drawn at a tablet width on the day it was written`() {
        render { SafetyScreen(update = AppUpdateStatus.Available(newer)) }
        assertOnce("نسخه‌ی تازه‌ای منتشر شده")
        capture("alef-update-available-pixel-tablet-fa")
    }

    @Test
    @Config(sdk = [34], qualifiers = ENGLISH)
    fun `the English reader gets the English notes`() {
        render(dark = false) { SafetyScreen(update = AppUpdateStatus.Available(newer)) }
        assertOnce("A newer version is out")
        assertOnce(newer.notesEn)
        assertAbsent(newer.notesFa)
        capture("alef-update-available-en-light")
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the install's own certificate is on the screen, so the fingerprint need not be guessed`() {
        render {
            SafetyScreen(
                update = AppUpdateStatus.Unknown,
                fingerprints = listOf("SHA-1  $SHA1", "SHA-256  $SHA256"),
            )
        }
        assertOnce("گواهی این نصب")
        assertOnce("SHA-256  $SHA256")
        capture("alef-signing-certificate-fa")
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `a build that could not read its own certificate draws no card rather than an empty one`() {
        render { SafetyScreen(update = AppUpdateStatus.Unknown, fingerprints = emptyList()) }
        assertOnce("ایمنی و انتشار")
        assertAbsent("گواهی این نصب")
    }

    // ---------------------------------------------------------------- the rig

    @Composable
    private fun SafetyScreen(
        update: AppUpdateStatus,
        fingerprints: List<String> = emptyList(),
    ) {
        LaunchReadinessScreen(
            notificationPermissionState = NotificationPermissionUiState.GRANTED,
            onRequestNotificationPermission = {},
            onOpenNotificationSettings = {},
            onSendFeedback = {},
            signingFingerprints = fingerprints,
            update = update,
        )
    }

    private fun render(dark: Boolean = true, content: @Composable () -> Unit) {
        composeRule.setContent {
            CoineProTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) { content() }
            }
        }
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
    }

    private fun assertOnce(text: String) {
        assertEquals(
            "expected exactly one «$text»",
            1,
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size,
        )
    }

    private fun assertAbsent(text: String) {
        assertTrue(
            "«$text» should not be on this screen",
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty(),
        )
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
        const val ENGLISH = "en-rUS-ldltr-w411dp-h914dp-420dpi"
        const val PIXEL_TABLET = "fa-rIR-ldrtl-sw800dp-w1280dp-h800dp-xhdpi"

        /** Invented, and shaped like the real thing: colon-separated hex, as the platform prints it. */
        const val SHA1 = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD"
        const val SHA256 =
            "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:" +
                "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
    }
}
