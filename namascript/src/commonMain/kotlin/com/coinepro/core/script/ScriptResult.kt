package com.coinepro.core.script

import com.coinepro.core.chart.Line

/** A line the script asked to be drawn. */
data class ScriptPlot(
    val title: String,
    val values: Line,
    val colour: Long,
    val widthDp: Float,
    /**
     * Whether this belongs over the price or in its own pane.
     *
     * Decided by the script through `pane =`, and defaulted by **measuring the values against the
     * price** rather than by guessing from the title. An RSI plotted over the candles is not a
     * cosmetic mistake: it flattens the price axis to a line, and the chart becomes unreadable.
     */
    val ownPane: Boolean,
    val dashed: Boolean = false,
)

/** A horizontal level — `hline(30)` under an oscillator, or a target over the price. */
data class ScriptLevel(
    val price: Double,
    val title: String?,
    val colour: Long,
    val ownPane: Boolean,
)

enum class ScriptMarkerStyle { ARROW_UP, ARROW_DOWN, CIRCLE }

/** A mark on every bar where a condition held. */
data class ScriptMarker(
    val title: String,
    /** Bar indices, ascending. */
    val bars: List<Int>,
    val style: ScriptMarkerStyle,
    val colour: Long,
)

/**
 * A trade idea the script produced on the last bar it fired.
 *
 * Deliberately not a list of every historical firing: this is the thing the reader might act on
 * now, and a screen that offered to execute a setup from three weeks ago would be offering
 * something that has already played out. History is what the markers are for.
 */
data class ScriptSetup(
    val buy: Boolean,
    val entry: Double,
    val stop: Double,
    val target: Double?,
    val barIndex: Int,
) {
    /** Reward over risk, or null where the script named no target. */
    val riskReward: Double?
        get() {
            val risk = kotlin.math.abs(entry - stop)
            val reward = target?.let { kotlin.math.abs(it - entry) } ?: return null
            return if (risk <= 0.0) null else reward / risk
        }
}

/** An `input(...)` the script declared, so the editor can offer it as a control. */
/** How an input is set from the panel; what control the studio draws for it. */
enum class ScriptInputKind { NUMBER, INTEGER, BOOL, TEXT, SOURCE, COLOUR, TIMEFRAME }

/**
 * One knob the script exposed.
 *
 * Every kind is carried as a `Double` [value] so the overrides the controller stores stay one
 * map: a number is itself, a switch is 0/1, a choice (text, source, timeframe) is the index into
 * [options], a colour is its ARGB as a whole number.
 */
data class ScriptInput(
    val name: String,
    val value: Double,
    val minimum: Double?,
    val maximum: Double?,
    val kind: ScriptInputKind = ScriptInputKind.NUMBER,
    val options: List<String> = emptyList(),
    val step: Double? = null,
)

/** What a script drew with `label.new`, `line.new` or `box.new`; bar indices and prices, in the chart's vocabulary once `toOverlay` runs. */
enum class ScriptDrawingKind { LABEL, LINE, BOX }

data class ScriptDrawing(
    val kind: ScriptDrawingKind,
    /** One bar for a label, two for a line (first, second) and a box (left, right). */
    val bars: List<Int>,
    /** One price for a label, two for a line (at each bar) and a box (top, bottom). */
    val prices: List<Double>,
    val text: String? = null,
    val colour: Long,
    val textColour: Long? = null,
    val widthDp: Float = 1.6f,
)

/**
 * One round trip the strategy simulator closed, or the position still open on the last bar.
 *
 * Fills are at the **open of the bar after** the signal, the way Pine fills a market order: a
 * signal computed on a bar's close cannot be acted on at that close.
 */
data class ScriptTrade(
    val id: String,
    val long: Boolean,
    val entryBar: Int,
    val exitBar: Int,
    val entryPrice: Double,
    val exitPrice: Double,
    /** True for the position the run ended inside; [exitPrice] is then the last close. */
    val open: Boolean = false,
) {
    /** The trade's return on its entry price, in percent, signed by direction. */
    val returnPercent: Double
        get() = if (entryPrice == 0.0) 0.0 else (if (long) 1.0 else -1.0) * (exitPrice - entryPrice) / entryPrice * 100.0
}

/** What `strategy.entry` / `strategy.close` produced, simulated over the whole series. */
data class ScriptStrategyReport(
    val trades: List<ScriptTrade>,
    /** The sum of every closed trade's return, in percent — simple, not compounded. */
    val netPercent: Double,
    val winRate: Double,
    /** Gross wins over gross losses; null when nothing was lost. */
    val profitFactor: Double?,
    /** The deepest fall of the cumulative return from its high, in percent points. */
    val maxDrawdownPercent: Double,
) {
    val closedCount: Int get() = trades.count { !it.open }
}

/**
 * Everything one run produced.
 *
 * A run that failed carries [error] and nothing else — partial output from a script that then threw
 * is worse than none, because half a chart looks like a whole one.
 */
data class ScriptResult(
    val plots: List<ScriptPlot> = emptyList(),
    val levels: List<ScriptLevel> = emptyList(),
    val markers: List<ScriptMarker> = emptyList(),
    val setup: ScriptSetup? = null,
    val inputs: List<ScriptInput> = emptyList(),
    /** Lines the script printed with `log(...)`, newest last. Capped; see the interpreter. */
    val log: List<String> = emptyList(),
    val error: ScriptFailure? = null,
    /** Where `bgcolor(...)` laid a colour behind the bars. */
    val backgrounds: List<ScriptBackground> = emptyList(),
    /** The conditions `alertcondition(...)` named, with the bars they held on. */
    val alerts: List<ScriptAlert> = emptyList(),
    /** Labels, lines and boxes the script placed (4.61.0). */
    val drawings: List<ScriptDrawing> = emptyList(),
    /** The strategy simulation, when the script placed an order (4.61.0). */
    val strategy: ScriptStrategyReport? = null,
    /** How long the run took, in milliseconds, read by the studio's console. */
    val elapsedMillis: Long = 0,
) {
    val ok: Boolean get() = error == null

    /** Whether anything at all would be drawn. A script that runs and draws nothing is worth saying so. */
    val isEmpty: Boolean
        get() = plots.isEmpty() && levels.isEmpty() && markers.isEmpty() && setup == null && backgrounds.isEmpty() &&
            drawings.isEmpty() && strategy == null
}

/**
 * A refusal, with the position to put a caret at.
 *
 * Both languages travel together rather than one being chosen here, because the interpreter has
 * no idea what language the app is in and should not: the screen that shows the caret picks with
 * [text].
 */
data class ScriptFailure(
    val message: String,
    val messageEn: String,
    val line: Int,
    val column: Int,
    /** The diagnostic's code — `E301`, say. See `docs/namascript/SPEC.md` §7. */
    val code: String = "E000",
) {
    /** The message in one language: English when [english], Persian otherwise. */
    fun text(english: Boolean): String = if (english) messageEn else message

    /** The one-line fix, or empty where the message is the fix. */
    fun hint(english: Boolean): String = if (english) ScriptDiagnostics.hintEn(code) else ScriptDiagnostics.hint(code)
}

/** A colour laid behind the bars where a condition held: `bgcolor(cond, color.gold)`. */
data class ScriptBackground(val bars: List<Int>, val colour: Long)

/** A named condition a reader can attach an alert to: `alertcondition(cond, "cross")`. */
data class ScriptAlert(val title: String, val bars: List<Int>) {
    /** Whether the condition holds on the last bar — what an alert on this script would fire on. */
    fun firing(barCount: Int): Boolean = bars.lastOrNull() == barCount - 1
}
