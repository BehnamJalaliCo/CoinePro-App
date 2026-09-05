package com.coinepro.core.script

import com.coinepro.core.common.AppLanguage
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
data class ScriptInput(
    val name: String,
    val value: Double,
    val minimum: Double?,
    val maximum: Double?,
)

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
) {
    val ok: Boolean get() = error == null

    /** Whether anything at all would be drawn. A script that runs and draws nothing is worth saying so. */
    val isEmpty: Boolean
        get() = plots.isEmpty() && levels.isEmpty() && markers.isEmpty() && setup == null
}

/**
 * A refusal, with the position to put a caret at.
 *
 * Both languages travel together rather than one being chosen here, because the interpreter has
 * no idea what language the app is in and should not: the screen that shows the caret picks with
 * [text].
 */
data class ScriptFailure(val message: String, val messageEn: String, val line: Int, val column: Int) {
    fun text(language: AppLanguage): String = if (language == AppLanguage.ENGLISH) messageEn else message
}
