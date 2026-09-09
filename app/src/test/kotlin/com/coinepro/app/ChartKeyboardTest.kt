package com.coinepro.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.marketdata.Timeframe
import com.coinepro.feature.chart.chartShortcuts
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The keyboard, on the screens that have one.
 *
 * `chartShortcuts` is the whole hotkey map for a tablet with a keyboard, a Chromebook and DeX;
 * every binding here is also a control on screen, and this pins that the keys reach the same
 * actions — in particular that a press fires once (down only, not down and up), that the digits
 * pick the timeframe by name rather than by position, and that the plan's two new bindings
 * (`+`/`-` to zoom, Alt+H / Alt+V to arm the line tools) are wired.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-sw800dp-w1280dp-h800dp-xhdpi")
class ChartKeyboardTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val log = mutableListOf<String>()

    private fun compose() {
        rule.setContent {
            val focus = remember { FocusRequester() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(TAG)
                    .chartShortcuts(
                        onTimeframe = { log += "tf:${it.name}" },
                        onReplayToggle = { log += "replay" },
                        onStep = { log += "step" },
                        onStepBack = { log += "back" },
                        onCancelDrawing = { log += "cancel" },
                        onUndoDrawing = { log += "undo" },
                        onRedo = { log += "redo" },
                        onZoom = { log += if (it) "zoom-in" else "zoom-out" },
                        onArmTool = { log += "arm:$it" },
                    )
                    .focusRequester(focus)
                    .focusable(),
            )
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
        rule.waitForIdle()
    }

    @Test
    fun `a digit picks its timeframe by name, and a press fires once`() {
        compose()
        rule.onNodeWithTag(TAG).performKeyInput { pressKey(Key.Four) }
        assertEquals(listOf("tf:${Timeframe.H1.name}"), log)
    }

    @Test
    fun `plus and minus are one zoom notch each, on the row and the pad`() {
        compose()
        rule.onNodeWithTag(TAG).performKeyInput {
            pressKey(Key.Plus)
            pressKey(Key.Equals)
            pressKey(Key.Minus)
            pressKey(Key.NumPadAdd)
            pressKey(Key.NumPadSubtract)
        }
        assertEquals(listOf("zoom-in", "zoom-in", "zoom-out", "zoom-in", "zoom-out"), log)
    }

    @Test
    fun `Alt with H and V arms the two line tools`() {
        compose()
        rule.onNodeWithTag(TAG).performKeyInput {
            withKeyDown(Key.AltLeft) { pressKey(Key.H) }
            withKeyDown(Key.AltLeft) { pressKey(Key.V) }
        }
        assertEquals(listOf("arm:${DrawingTools.HORIZONTAL_LINE}", "arm:${DrawingTools.VERTICAL_LINE}"), log)
    }

    @Test
    fun `the arrows step in time, space toggles replay, escape cancels, Z and Y walk the history`() {
        compose()
        rule.onNodeWithTag(TAG).performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionLeft)
            pressKey(Key.Spacebar)
            pressKey(Key.Escape)
            pressKey(Key.Z)
            withKeyDown(Key.ShiftLeft) { pressKey(Key.Z) }
            pressKey(Key.Y)
        }
        assertEquals(listOf("step", "back", "replay", "cancel", "undo", "redo", "redo"), log)
    }

    private companion object {
        const val TAG = "keys"
    }
}
