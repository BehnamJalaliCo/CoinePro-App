package com.coinepro.feature.chart

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.marketdata.Timeframe

/**
 * Keyboard shortcuts, for the screens that have a keyboard.
 *
 * The web terminal has a hotkey map and the obvious reading is that a phone cannot use it. That is
 * only true of phones. This app runs on tablets, on Samsung DeX, on Chromebooks and on any Android
 * device with a Bluetooth keyboard attached, and on all of them a chart that ignores the arrow keys
 * feels like a phone app being tolerated rather than software.
 *
 * Nothing here is the only way to do anything. Every shortcut has a control on screen — this is a
 * faster path for somebody who has a keyboard, never a hidden feature, because a shortcut that is
 * the sole route to a function is a function most readers do not have.
 *
 * Key-down only. Android delivers both down and up, and acting on both fires every shortcut twice —
 * which on a timeframe key is invisible and on a step-forward key is two bars.
 */
fun Modifier.chartShortcuts(
    onTimeframe: (Timeframe) -> Unit,
    onReplayToggle: () -> Unit,
    onStep: () -> Unit,
    onStepBack: () -> Unit,
    onCancelDrawing: () -> Unit,
    onUndoDrawing: () -> Unit,
    onRedo: () -> Unit,
    /** One zoom notch, in (`true`) or out. Bound to `+`/`=` and `-`, on the row and the pad. */
    onZoom: ((zoomIn: Boolean) -> Unit)? = null,
    /** Arm a drawing tool by its catalogue id. Alt+H is the horizontal line, Alt+V the vertical. */
    onArmTool: ((id: String) -> Unit)? = null,
): Modifier = onKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
    // The two line tools sit on Alt so that a bare H or V stays free for the future; TradingView
    // binds them the same way, and a hand that learned it there should find it here.
    if (event.isAltPressed && onArmTool != null) {
        when (event.key) {
            Key.H -> { onArmTool(DrawingTools.HORIZONTAL_LINE); return@onKeyEvent true }
            Key.V -> { onArmTool(DrawingTools.VERTICAL_LINE); return@onKeyEvent true }
            else -> Unit
        }
    }
    when (event.key) {
        // Zoom, one notch per press. `=` is the unshifted `+` on every layout that has one.
        Key.Plus, Key.Equals, Key.NumPadAdd -> { onZoom?.invoke(true) ?: return@onKeyEvent false; true }
        Key.Minus, Key.NumPadSubtract -> { onZoom?.invoke(false) ?: return@onKeyEvent false; true }

        // The digits pick a timeframe, the way every terminal does it.
        //
        // Named constants rather than `entries[n]`. The ordinals moved the day M2, M3, M10, M45,
        // H2, H3 and MN1 were added, and a positional binding would silently have started putting
        // three-minute bars on the key a reader had learned meant fifteen. A shortcut whose meaning
        // drifts under a release is worse than no shortcut, because the hand does not check.
        Key.One -> { onTimeframe(Timeframe.M1); true }
        Key.Two -> { onTimeframe(Timeframe.M5); true }
        Key.Three -> { onTimeframe(Timeframe.M15); true }
        Key.Four -> { onTimeframe(Timeframe.H1); true }
        Key.Five -> { onTimeframe(Timeframe.H4); true }
        Key.Six -> { onTimeframe(Timeframe.D1); true }

        // Space plays and pauses replay, as it does in every player anybody has used.
        Key.Spacebar -> { onReplayToggle(); true }

        // Left and right step a bar. In a right-to-left interface these still mean back and
        // forward in *time*, not on screen: the chart's time axis runs left to right regardless of
        // the reading direction, because that is how every other terminal draws it and a trader
        // comparing two screens must not have to reverse one of them in their head.
        Key.DirectionRight -> { onStep(); true }
        Key.DirectionLeft -> { onStepBack(); true }

        Key.Escape -> { onCancelDrawing(); true }

        // Z takes a step back, Shift+Z puts it forward, and Y does the same as Shift+Z for the
        // hands that learned redo there. Deliberately *without* the control modifier: this chart
        // has no text field to compete with, the drawing rail's button is the same action, and a
        // reader on a tablet keyboard reaching for undo one-handed should not need two keys. Both
        // now walk the whole chart's history, not only the drawing layer — see `ChartHistory`.
        Key.Z -> { if (event.isShiftPressed) onRedo() else onUndoDrawing(); true }
        Key.Y -> { onRedo(); true }
        else -> false
    }
}
