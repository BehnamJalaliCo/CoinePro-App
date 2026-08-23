package com.coinepro.app

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coinepro.core.designsystem.CoineProTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchReadinessAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

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
            .onNodeWithText("Notifications can alert you to server-provided signal and activity updates. Permission is optional and is requested only after you choose Enable notifications here.")
            .assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, requests) }
        composeRule.onNodeWithText("Enable notifications").assertHasClickAction().performClick()
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
            .onNodeWithText("Notification permission was denied. CoinePro remains usable without it. You can change this later in Android notification settings.")
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Open notification settings")
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
            .onNodeWithText("Trading and investment involve risk of loss. Signals, analysis and AI output are not guaranteed outcomes. Execution depends on external providers, account permissions, market conditions and server/provider confirmation. Historical or displayed results do not guarantee future performance. Review every order before confirming it.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Send feedback").performScrollTo().assertHasClickAction().performClick()
        composeRule.runOnIdle { assertEquals(1, feedbackActions) }
        composeRule
            .onNodeWithText("Production connectivity, provider whitelisting and live execution readiness are separate operational gates and are not implied by this screen.")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
