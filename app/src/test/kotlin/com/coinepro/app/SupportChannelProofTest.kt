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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.coinepro.core.common.BrandConfig
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.model.AvatarBase
import com.coinepro.core.model.AvatarMark
import com.coinepro.core.model.AvatarRing
import com.coinepro.core.model.AvatarSpec
import com.coinepro.core.datastore.StoredProfile
import com.coinepro.feature.profile.ProfileAction
import com.coinepro.feature.profile.ProfileScreen
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
import com.coinepro.core.designsystem.R as DesignR

/**
 * **A way to reach a person** (4.87.0, the owner's second item).
 *
 * `BrandConfig.SUPPORT_URL` had been in the source since the brand file was written and **nothing
 * in the app opened it**. That is the failure worth naming: not a wrong address, an address with no
 * door. A reader who wanted to ask a question found «پشتیبانی و بازخورد», which composed a message
 * and handed it to whatever app they picked — a good way to file a report and a poor way to ask
 * something, because it ends in their own outbox with no sign that it arrived anywhere.
 *
 * So the row is now a chat, it is above the report rather than instead of it, it asks for what the
 * owner wants readers to send — «نظرها، پیشنهادها و انتقادهایتان» — and it carries Telegram's own
 * mark. The mark is the point of the frame: a brand glyph is how somebody finds a
 * row without reading it, and this list draws every other glyph in the app's own ink — a Telegram
 * logo tinted grey is a grey circle. `ProfileAction.brandMark` is the exception, and a render is
 * the only thing that can say whether it survived the tint.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SupportChannelProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the support handle is the product's queue, over https, and reachable`() {
        // The owner's own handle, verbatim. A test on a constant is worth writing exactly once: the
        // day somebody edits this to a personal account or an http link, the edit is the bug.
        assertEquals("ProChart_Sup", BrandConfig.SUPPORT_HANDLE)
        assertEquals("https://t.me/ProChart_Sup", BrandConfig.SUPPORT_URL)
        assertTrue("support does not open over https", BrandConfig.SUPPORT_URL.startsWith("https://"))
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `the support row wears Telegram's own mark and says it reaches a person`() {
        proof("support-profile-phone-fa") {
            ProfileScreen(
                profile = StoredProfile(
                    displayName = "بهنام",
                    avatar = AvatarSpec(AvatarBase.Mark(AvatarMark.TREND), AvatarRing.ANALYSIS),
                ),
                accountName = "بهنام جلالی",
                platformLabel = "فارکس",
                actions = listOf(
                    ProfileAction(
                        label = "پشتیبانی در تلگرام",
                        note = "نظرها، پیشنهادها و انتقادهایتان را برای ما بفرستید.",
                        icon = DesignR.drawable.logo_telegram,
                        brandMark = true,
                    ) {},
                    ProfileAction(
                        label = "ارسال بازخورد",
                        note = "یک گزارش، با اپ دلخواه خودتان.",
                        icon = DesignR.drawable.tv_help_circle,
                    ) {},
                ),
            )
        }
        // Two rows, two errands, and the chat above the report — which is the half of this that a
        // reordering would quietly undo.
        val chat = composeRule.onNodeWithText("پشتیبانی در تلگرام", substring = true)
        val report = composeRule.onNodeWithText("ارسال بازخورد", substring = true)
        chat.assertExists()
        report.assertExists()
        assertTrue(
            "the report sits above the chat",
            report.fetchSemanticsNode().positionInRoot.y > chat.fetchSemanticsNode().positionInRoot.y,
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `the safety screen leads with the chat and keeps the report beside it`() {
        proof(
            name = "support-safety-phone-fa",
            // The card is the last on a long screen, so a frame of the first fold would prove
            // nothing about it. Scrolled to before the shutter, which is also the assertion that
            // the button is reachable rather than merely present in the tree.
            prepare = { composeRule.onNodeWithText(SAFETY_BUTTON).performScrollTo() },
        ) {
            LaunchReadinessScreen(
                notificationPermissionState = NotificationPermissionUiState.GRANTED,
                onRequestNotificationPermission = {},
                onOpenNotificationSettings = {},
                onSendFeedback = {},
                onOpenSupportChat = {},
                versionLabel = "4.87.0 (48700000)",
            )
        }
        composeRule.onNodeWithText(SAFETY_BUTTON).assertExists()
    }

    private fun proof(name: String, prepare: () -> Unit = {}, content: @Composable () -> Unit) {
        composeRule.setContent {
            CoineProTheme(darkTheme = true) {
                CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                    Surface(modifier = Modifier.fillMaxSize()) { content() }
                }
            }
        }
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
        prepare()
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
        const val FA_PHONE = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi"

        /**
         * The button's own words, exactly.
         *
         * A substring match on «تلگرام» used to be enough and stopped being enough the moment the
         * card's body started naming the chat too — which is the right copy and an ambiguous
         * selector. The whole label is the only thing that means the button.
         */
        const val SAFETY_BUTTON = "گفت‌وگو با پشتیبانی در تلگرام"
        val OUTPUT = File("build/proof")
    }
}
