package com.coinepro.feature.chart

import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.coinepro.core.chart.ChartType
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.marketdata.Timeframe

/**
 * Keyboard shortcuts, for the screens that have a keyboard.
 *
 * The web terminal has a hotkey map and the obvious reading is that a phone cannot use it. That is
 * only true of phones. This app runs on tablets, on Samsung DeX, on Chromebooks and on any Android
 * device with a Bluetooth keyboard attached, and on all of them a chart that ignores the arrow keys
 * feels like a phone app being tolerated rather than software. And since 5.10 it runs in the
 * browser, where a keyboard is the rule.
 *
 * Nothing here is the only way to do anything. Every shortcut has a control on screen — this is a
 * faster path for somebody who has a keyboard, never a hidden feature, because a shortcut that is
 * the sole route to a function is a function most readers do not have.
 *
 * Key-down only. Android delivers both down and up, and acting on both fires every shortcut twice —
 * which on a timeframe key is invisible and on a step-forward key is two bars.
 *
 * ### The map (5.14.0)
 *
 * The first fifteen bindings were this app's own. The rest are Pro-Chart's terminal's
 * (`hotkeys.js`), key for key, so a hand that learned them on the site finds them here: the Alt
 * letters arm the drawing tools, Ctrl+Alt reaches the chart-wide switches, Alt+digit picks the
 * chart type, Shift+digit the terminal's five timeframes, and «?» lists the lot — see
 * [ChartKeyAction], which is both the dispatch table and the help dialog's contents, so the list
 * the reader is shown cannot drift from what the keys do.
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
    /** `/` — the symbol search, as on every terminal keyboard. */
    onSearch: (() -> Unit)? = null,
    /**
     * Everything else in [ChartKeyAction] — the terminal's map. Answers whether it acted, so a key
     * the screen has nothing for right now (Delete with nothing selected) goes on to the platform.
     */
    onAction: ((ChartKeyAction.Hit) -> Boolean)? = null,
): Modifier = onKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
    val hit = ChartKeyAction.of(
        key = event.key,
        ctrl = event.isCtrlPressed || event.isMetaPressed,
        alt = event.isAltPressed,
        shift = event.isShiftPressed,
    ) ?: return@onKeyEvent false
    when (hit.action) {
        // The digits pick a timeframe, the way every terminal does it.
        //
        // Named constants rather than `entries[n]`. The ordinals moved the day M2, M3, M10, M45,
        // H2, H3 and MN1 were added, and a positional binding would silently have started putting
        // three-minute bars on the key a reader had learned meant fifteen. A shortcut whose meaning
        // drifts under a release is worse than no shortcut, because the hand does not check.
        ChartKeyAction.TIMEFRAME_DIGIT -> { onTimeframe(DIGIT_TIMEFRAMES[hit.index]); true }
        ChartKeyAction.TIMEFRAME_SHIFT_DIGIT -> { onTimeframe(SHIFT_DIGIT_TIMEFRAMES[hit.index]); true }

        // Space plays and pauses replay, as it does in every player anybody has used.
        ChartKeyAction.REPLAY_PLAY -> { onReplayToggle(); true }

        // Left and right step a bar. In a right-to-left interface these still mean back and
        // forward in *time*, not on screen: the chart's time axis runs left to right regardless of
        // the reading direction, because that is how every other terminal draws it and a trader
        // comparing two screens must not have to reverse one of them in their head.
        ChartKeyAction.REPLAY_STEP -> { onStep(); true }
        ChartKeyAction.REPLAY_STEP_BACK -> { onStepBack(); true }

        ChartKeyAction.CANCEL -> { onCancelDrawing(); true }

        // Z takes a step back, Shift+Z puts it forward, and Y does the same as Shift+Z for the
        // hands that learned redo there. Deliberately also *without* the control modifier: this
        // chart has no text field to compete with, the drawing rail's button is the same action,
        // and a reader on a tablet keyboard reaching for undo one-handed should not need two keys.
        ChartKeyAction.UNDO -> { onUndoDrawing(); true }
        ChartKeyAction.REDO -> { onRedo(); true }

        // Zoom, one notch per press. `=` is the unshifted `+` on every layout that has one.
        ChartKeyAction.ZOOM_IN -> { onZoom?.invoke(true) ?: return@onKeyEvent false; true }
        ChartKeyAction.ZOOM_OUT -> { onZoom?.invoke(false) ?: return@onKeyEvent false; true }

        // `/` opens the symbol search — the one key every terminal binds the same way.
        ChartKeyAction.SEARCH -> { onSearch?.invoke() ?: return@onKeyEvent false; true }

        else -> {
            val tool = hit.action.tool
            if (tool != null) {
                onArmTool?.invoke(tool) ?: return@onKeyEvent false
                true
            } else {
                onAction?.invoke(hit) ?: false
            }
        }
    }
}

/** The plain digits, one to six: this app's own ladder since 4.58. */
internal val DIGIT_TIMEFRAMES = listOf(Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.H4, Timeframe.D1)

/** Shift with one to five: the terminal's five, which start at five minutes. */
internal val SHIFT_DIGIT_TIMEFRAMES = listOf(Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.H4, Timeframe.D1)

/** Alt with one to five: the terminal's chart types, in its order. */
internal val ALT_DIGIT_TYPES = listOf(ChartType.CANDLES, ChartType.BARS, ChartType.LINE, ChartType.AREA, ChartType.HEIKIN_ASHI)

/** One key with the modifiers it must be pressed with — exactly those, no more. */
data class KeyChord(val key: Key, val ctrl: Boolean = false, val alt: Boolean = false, val shift: Boolean = false)

/** The help dialog's sections, in the terminal's order. */
enum class ChartKeyGroup(@StringRes val title: Int) {
    TOOLS(R.string.keys_group_tools),
    EDIT(R.string.keys_group_edit),
    TIMEFRAME(R.string.keys_group_timeframe),
    CHART_TYPE(R.string.keys_group_type),
    NAVIGATION(R.string.keys_group_navigation),
    VIEW(R.string.keys_group_view),
    TRADE(R.string.keys_group_trade),
    REPLAY(R.string.keys_group_replay),
}

/**
 * Every shortcut on the chart: what it is called, how it is written in the list, and the chords
 * that fire it. [of] is the dispatch; [ChartKeyGroup] and [combo] are the «?» dialog. One table for
 * both, so a key cannot be added without being listed or listed without being bound.
 *
 * [combo] is the key as printed on a keyboard and stays Latin in both languages.
 */
enum class ChartKeyAction(
    val group: ChartKeyGroup,
    @StringRes val label: Int,
    val combo: String,
    /** The drawing tool this arms, for the Alt letters. */
    val tool: String?,
    vararg val chords: KeyChord,
) {
    // ── tools ────────────────────────────────────────────────────────────────────────────
    CANCEL(ChartKeyGroup.TOOLS, R.string.keys_cancel, "Esc", null, KeyChord(Key.Escape)),
    TREND(ChartKeyGroup.TOOLS, R.string.keys_trend, "Alt+T", "trend", KeyChord(Key.T, alt = true)),
    HLINE(ChartKeyGroup.TOOLS, R.string.keys_hline, "Alt+H", DrawingTools.HORIZONTAL_LINE, KeyChord(Key.H, alt = true)),
    VLINE(ChartKeyGroup.TOOLS, R.string.keys_vline, "Alt+V", DrawingTools.VERTICAL_LINE, KeyChord(Key.V, alt = true)),
    RAY(ChartKeyGroup.TOOLS, R.string.keys_ray, "Alt+R", "ray", KeyChord(Key.R, alt = true)),
    RECT(ChartKeyGroup.TOOLS, R.string.keys_rect, "Alt+E", "rect", KeyChord(Key.E, alt = true)),
    FIB(ChartKeyGroup.TOOLS, R.string.keys_fib, "Alt+F", "fib", KeyChord(Key.F, alt = true)),
    TEXT(ChartKeyGroup.TOOLS, R.string.keys_text, "Alt+X", "text", KeyChord(Key.X, alt = true)),
    MAGNET(ChartKeyGroup.TOOLS, R.string.keys_magnet, "Ctrl+Alt+M", null, KeyChord(Key.M, ctrl = true, alt = true)),
    KEEP_DRAWING(ChartKeyGroup.TOOLS, R.string.keys_keep_drawing, "Ctrl+Alt+D", null, KeyChord(Key.D, ctrl = true, alt = true)),

    // ── edit ─────────────────────────────────────────────────────────────────────────────
    UNDO(ChartKeyGroup.EDIT, R.string.keys_undo, "Ctrl+Z · Z", null, KeyChord(Key.Z, ctrl = true), KeyChord(Key.Z)),
    REDO(
        ChartKeyGroup.EDIT, R.string.keys_redo, "Ctrl+Y · Shift+Z", null,
        KeyChord(Key.Y, ctrl = true), KeyChord(Key.Z, ctrl = true, shift = true), KeyChord(Key.Z, shift = true), KeyChord(Key.Y),
    ),
    DELETE(ChartKeyGroup.EDIT, R.string.keys_delete, "Delete", null, KeyChord(Key.Delete), KeyChord(Key.Backspace)),
    CLONE(ChartKeyGroup.EDIT, R.string.keys_clone, "Ctrl+D", null, KeyChord(Key.D, ctrl = true)),
    COPY(ChartKeyGroup.EDIT, R.string.keys_copy, "Ctrl+C", null, KeyChord(Key.C, ctrl = true)),
    PASTE(ChartKeyGroup.EDIT, R.string.keys_paste, "Ctrl+V", null, KeyChord(Key.V, ctrl = true)),
    LOCK(ChartKeyGroup.EDIT, R.string.keys_lock, "Ctrl+L", null, KeyChord(Key.L, ctrl = true)),
    REMOVE_ALL(ChartKeyGroup.EDIT, R.string.keys_remove_all, "Ctrl+Alt+Backspace", null, KeyChord(Key.Backspace, ctrl = true, alt = true)),
    HIDE_ALL(ChartKeyGroup.EDIT, R.string.keys_hide_all, "Ctrl+Alt+H", null, KeyChord(Key.H, ctrl = true, alt = true)),

    // ── timeframe ────────────────────────────────────────────────────────────────────────
    TIMEFRAME_DIGIT(
        ChartKeyGroup.TIMEFRAME, R.string.keys_timeframe_digits, "1 … 6", null,
        KeyChord(Key.One), KeyChord(Key.Two), KeyChord(Key.Three), KeyChord(Key.Four), KeyChord(Key.Five), KeyChord(Key.Six),
    ),
    TIMEFRAME_SHIFT_DIGIT(
        ChartKeyGroup.TIMEFRAME, R.string.keys_timeframe_shift_digits, "Shift+1 … 5", null,
        KeyChord(Key.One, shift = true), KeyChord(Key.Two, shift = true), KeyChord(Key.Three, shift = true),
        KeyChord(Key.Four, shift = true), KeyChord(Key.Five, shift = true),
    ),
    TIMEFRAME_NEXT(ChartKeyGroup.TIMEFRAME, R.string.keys_timeframe_next, ",", null, KeyChord(Key.Comma)),
    TIMEFRAME_PREVIOUS(ChartKeyGroup.TIMEFRAME, R.string.keys_timeframe_previous, ".", null, KeyChord(Key.Period)),

    // ── chart type ───────────────────────────────────────────────────────────────────────
    CHART_TYPE(
        ChartKeyGroup.CHART_TYPE, R.string.keys_chart_types, "Alt+1 … 5", null,
        KeyChord(Key.One, alt = true), KeyChord(Key.Two, alt = true), KeyChord(Key.Three, alt = true),
        KeyChord(Key.Four, alt = true), KeyChord(Key.Five, alt = true),
    ),

    // ── navigation ───────────────────────────────────────────────────────────────────────
    ZOOM_IN(
        ChartKeyGroup.NAVIGATION, R.string.keys_zoom_in, "+ · ↑", null,
        KeyChord(Key.Plus), KeyChord(Key.Plus, shift = true), KeyChord(Key.Equals), KeyChord(Key.Equals, shift = true),
        KeyChord(Key.NumPadAdd), KeyChord(Key.DirectionUp),
    ),
    ZOOM_OUT(ChartKeyGroup.NAVIGATION, R.string.keys_zoom_out, "- · ↓", null, KeyChord(Key.Minus), KeyChord(Key.NumPadSubtract), KeyChord(Key.DirectionDown)),
    NEWEST(ChartKeyGroup.NAVIGATION, R.string.keys_newest, "End", null, KeyChord(Key.MoveEnd)),
    OLDEST(ChartKeyGroup.NAVIGATION, R.string.keys_oldest, "Home", null, KeyChord(Key.MoveHome)),
    GO_TO_DATE(ChartKeyGroup.NAVIGATION, R.string.keys_go_to_date, "Alt+G", null, KeyChord(Key.G, alt = true)),
    INVERT(ChartKeyGroup.NAVIGATION, R.string.keys_invert, "Alt+I", null, KeyChord(Key.I, alt = true)),
    PERCENT(ChartKeyGroup.NAVIGATION, R.string.keys_percent, "Alt+P", null, KeyChord(Key.P, alt = true)),
    LOG(ChartKeyGroup.NAVIGATION, R.string.keys_log, "Alt+L", null, KeyChord(Key.L, alt = true)),

    // ── view ─────────────────────────────────────────────────────────────────────────────
    SEARCH(ChartKeyGroup.VIEW, R.string.keys_search, "/ · Ctrl+K", null, KeyChord(Key.Slash), KeyChord(Key.K, ctrl = true)),
    INDICATORS(ChartKeyGroup.VIEW, R.string.keys_indicators, "Ctrl+I", null, KeyChord(Key.I, ctrl = true)),
    SETTINGS(ChartKeyGroup.VIEW, R.string.keys_settings, "Ctrl+,", null, KeyChord(Key.Comma, ctrl = true)),
    FULLSCREEN(ChartKeyGroup.VIEW, R.string.keys_fullscreen, "Ctrl+Alt+F", null, KeyChord(Key.F, ctrl = true, alt = true)),
    DATA_WINDOW(ChartKeyGroup.VIEW, R.string.keys_data_window, "Alt+D", null, KeyChord(Key.D, alt = true)),
    SCREENSHOT(ChartKeyGroup.VIEW, R.string.keys_screenshot, "Alt+S", null, KeyChord(Key.S, alt = true)),
    HELP(ChartKeyGroup.VIEW, R.string.keys_help, "?", null, KeyChord(Key.Slash, shift = true)),

    // ── trade ────────────────────────────────────────────────────────────────────────────
    ALERT(ChartKeyGroup.TRADE, R.string.keys_alert, "Alt+A", null, KeyChord(Key.A, alt = true)),
    TRADE(ChartKeyGroup.TRADE, R.string.keys_trade, "Shift+B · Shift+S", null, KeyChord(Key.B, shift = true), KeyChord(Key.S, shift = true)),

    // ── replay ───────────────────────────────────────────────────────────────────────────
    REPLAY_ENTER(ChartKeyGroup.REPLAY, R.string.keys_replay_enter, "Ctrl+Alt+P", null, KeyChord(Key.P, ctrl = true, alt = true)),
    REPLAY_PLAY(ChartKeyGroup.REPLAY, R.string.keys_replay_play, "Space", null, KeyChord(Key.Spacebar)),
    REPLAY_STEP(ChartKeyGroup.REPLAY, R.string.keys_replay_step, "→ · Shift+→", null, KeyChord(Key.DirectionRight), KeyChord(Key.DirectionRight, shift = true)),
    REPLAY_STEP_BACK(ChartKeyGroup.REPLAY, R.string.keys_replay_step_back, "← · Shift+←", null, KeyChord(Key.DirectionLeft), KeyChord(Key.DirectionLeft, shift = true)),
    REPLAY_TEN_FORWARD(ChartKeyGroup.REPLAY, R.string.keys_replay_ten_forward, "Ctrl+→", null, KeyChord(Key.DirectionRight, ctrl = true)),
    REPLAY_TEN_BACK(ChartKeyGroup.REPLAY, R.string.keys_replay_ten_back, "Ctrl+←", null, KeyChord(Key.DirectionLeft, ctrl = true)),
    ;

    /** An action and which of its chords fired — the digit's position, for the digit rows. */
    data class Hit(val action: ChartKeyAction, val index: Int)

    companion object {
        /** The action for a key with exactly these modifiers, or null. */
        fun of(key: Key, ctrl: Boolean, alt: Boolean, shift: Boolean): Hit? {
            for (action in entries) {
                val index = action.chords.indexOfFirst { it.key == key && it.ctrl == ctrl && it.alt == alt && it.shift == shift }
                if (index >= 0) return Hit(action, index)
            }
            return null
        }
    }
}
