package com.coinepro.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
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

    /**
     * A body that scrolls itself opens on a tablet (run Σ, S8).
     *
     * The dialog branch used to wrap the body in a `verticalScroll`, and a scrolling container
     * measures its child with an unbounded height. Every sheet whose body scrolls — the script
     * paste panel, the alert editor, the screener's filters, the webhook sheet — therefore threw
     * «Vertically scrollable component was measured with an infinity maximum height» the moment it
     * was opened on a tablet, while being perfectly fine on the phone. Nothing caught it, because
     * the sheet bodies were only ever rendered at phone widths.
     *
     * The phone's `ModalBottomSheet` does not scroll its content either: a body either scrolls
     * itself or is short enough to fit. This is that same contract, asserted at the width that
     * broke it.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-sw800dp-w1280dp-h800dp-xhdpi")
    fun `a sheet whose body scrolls itself opens on a tablet`() {
        rule.setContent {
            CoineProTheme {
                Box(Modifier.fillMaxSize()) {
                    CoineProSheet(title = TITLE, onDismiss = {}, modifier = Modifier.testTag(TAG)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            repeat(40) { Text("row $it") }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText(TITLE).assertIsDisplayed()
    }

    private companion object {
        const val TAG = "sheet"
        const val TITLE = "گزینه‌ها"
    }
}
