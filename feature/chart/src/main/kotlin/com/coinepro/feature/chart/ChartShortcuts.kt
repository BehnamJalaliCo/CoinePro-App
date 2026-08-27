package com.coinepro.feature.chart

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
internal fun Modifier.chartShortcuts(
    onTimeframe: (Timeframe) -> Unit,
    onReplayToggle: () -> Unit,
    onStep: () -> Unit,
    onStepBack: () -> Unit,
    onCancelDrawing: () -> Unit,
    onUndoDrawing: () -> Unit,
): Modifier = onKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
    when (event.key) {
        // The digits pick a timeframe by position, the way every terminal does it.
        Key.One -> Timeframe.entries.getOrNull(0)?.let(onTimeframe) != null
        Key.Two -> Timeframe.entries.getOrNull(1)?.let(onTimeframe) != null
        Key.Three -> Timeframe.entries.getOrNull(2)?.let(onTimeframe) != null
        Key.Four -> Timeframe.entries.getOrNull(3)?.let(onTimeframe) != null
        Key.Five -> Timeframe.entries.getOrNull(4)?.let(onTimeframe) != null
        Key.Six -> Timeframe.entries.getOrNull(5)?.let(onTimeframe) != null

        // Space plays and pauses replay, as it does in every player anybody has used.
        Key.Spacebar -> { onReplayToggle(); true }

        // Left and right step a bar. In a right-to-left interface these still mean back and
        // forward in *time*, not on screen: the chart's time axis runs left to right regardless of
        // the reading direction, because that is how every other terminal draws it and a trader
        // comparing two screens must not have to reverse one of them in their head.
        Key.DirectionRight -> { onStep(); true }
        Key.DirectionLeft -> { onStepBack(); true }

        Key.Escape -> { onCancelDrawing(); true }
        Key.Z -> { onUndoDrawing(); true }
        else -> false
    }
}
