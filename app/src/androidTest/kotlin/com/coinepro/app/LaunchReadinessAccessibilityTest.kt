package com.coinepro.app

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.coinepro.core.designsystem.CoineProTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The safety screen, on a real device: the copy is reachable and the two consent actions fire.
 *
 * ### Why every string here is read from resources
 *
 * They used to be typed out. Three of them were a sentence long, and they were the *old* sentences:
 * the safety copy was rewritten when the app took its own brand name, and these assertions went on
 * describing a paragraph that no longer existed. Every one of them failed on wording, which is the
 * worst way for a test to fail — it says nothing about the screen and it teaches whoever reads the
 * failure that this suite is noise.
 *
 * A copy edit is not a regression. What this screen must not lose is that the permission copy is on
 * screen *before* anything is requested, that the request happens only on the reader's own tap, and
 * that a denial still offers the way out through settings. Those are the assertions; the prose is
 * whatever `strings.xml` currently says, and it is looked up the same way the screen looks it up.
 */
@RunWith(AndroidJUnit4::class)
class LaunchReadinessAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun notificationPermissionIsRequestedOnlyAfterEducationAction() {
        var requests = 0

        composeRule.setContent {
            CoineProTheme {
                LaunchReadinessScreen(
                    notificationPermissionState = NotificationPermissionUiState.AVAILABLE_TO_REQUEST,
                    onRequestNotificationPermission = { requests += 1 },
                    onOpenNotificationSettings = {},
                    onSendFeedback = {},
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.safety_push_available))
            .performScrollTo()
            .assertIsDisplayed()
        // The whole point of the screen, asserted before the tap: nothing is requested by arriving.
        composeRule.runOnIdle { assertEquals(0, requests) }
        composeRule
            .onNodeWithText(context.getString(R.string.safety_enable_notifications))
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, requests) }
    }

    @Test
    fun deniedNotificationPermissionExposesSettingsRecovery() {
        var settingsActions = 0

        composeRule.setContent {
            CoineProTheme {
                LaunchReadinessScreen(
                    notificationPermissionState = NotificationPermissionUiState.DENIED,
                    onRequestNotificationPermission = {},
                    onOpenNotificationSettings = { settingsActions += 1 },
                    onSendFeedback = {},
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.safety_push_denied))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.safety_open_settings))
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, settingsActions) }
    }

    @Test
    fun launchSafetyKeepsRiskAndFeedbackReachable() {
        var feedbackActions = 0

        composeRule.setContent {
            CoineProTheme {
                LaunchReadinessScreen(
                    notificationPermissionState = NotificationPermissionUiState.NOT_CONFIGURED,
                    onRequestNotificationPermission = {},
                    onOpenNotificationSettings = {},
                    onSendFeedback = { feedbackActions += 1 },
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.safety_risk_body))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.safety_send_feedback))
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, feedbackActions) }
        composeRule
            .onNodeWithText(context.getString(R.string.safety_footer))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
