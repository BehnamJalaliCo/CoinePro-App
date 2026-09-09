package com.coinepro.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.SHEET_DIALOG_MAX_WIDTH
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The same sheet is a strip on a phone and a capped dialog on a tablet.
 *
 * A bottom sheet across a twelve-inch window is a wall of controls; §4 of the plan caps it at
 * [SHEET_DIALOG_MAX_WIDTH]. Robolectric cannot photograph a dialog window into a golden, so the
 * shape is asserted here instead: on the tablet the sheet's surface is no wider than the cap and
 * its title is on screen; on the phone it is the full width of the glass, as it always was.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
class SheetShapeTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun compose() {
        rule.setContent {
            CoineProTheme {
                Box(Modifier.fillMaxSize()) {
                    CoineProSheet(title = TITLE, onDismiss = {}, modifier = Modifier.testTag(TAG)) {
                        Text("row")
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w840dp-h1280dp-xhdpi")
    fun `on a tablet the sheet is a dialog no wider than the cap`() {
        compose()
        rule.onNodeWithText(TITLE).assertIsDisplayed()
        val width = rule.onNodeWithTag(TAG).getUnclippedBoundsInRoot().width
        assertTrue("sheet dialog is $width wide", width <= SHEET_DIALOG_MAX_WIDTH)
        assertTrue("sheet dialog is $width wide", width >= 400.dp)
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun `on a phone the sheet is the full width of the glass`() {
        compose()
        rule.onNodeWithText(TITLE).assertIsDisplayed()
        val width = rule.onNodeWithTag(TAG).getUnclippedBoundsInRoot().width
        assertTrue("sheet is $width wide", width >= 400.dp)
    }

    private companion object {
        const val TAG = "sheet"
        const val TITLE = "گزینه‌ها"
    }
}
