package com.coinepro.feature.chart

import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartLine
import com.coinepro.core.chart.ChartMarker
import com.coinepro.core.chart.ChartPane
import com.coinepro.core.chart.Drawing
import com.coinepro.core.chart.PriceLevel
import com.coinepro.core.script.IncrementalRunner
import com.coinepro.core.script.NamaScript
import com.coinepro.core.script.ScriptAlert
import com.coinepro.core.script.ScriptFailure
import com.coinepro.core.script.ScriptInput
import com.coinepro.core.script.ScriptResult
import com.coinepro.core.script.ScriptStrategyReport
import com.coinepro.core.script.ScriptVerdict
import com.coinepro.core.script.toOverlay

/**
 * A NamaScript script running on the main chart, as an indicator.
 *
 * ### Why this type exists
 *
 * Before 4.73.0 a script could only be drawn on the studio's own little preview: two hundred bars,
 * no other indicators, no drawings, thrown away when the reader left. That is a good *sandbox* and
 * it was the wrong **destination** — TradingView's Pine editor has the same preview and it also has
 * «Add to chart», and everything a reader actually wants from a script they wrote happens after
 * that button: it sits in the legend beside EMA and Bollinger, its `input(...)`s become a settings
 * sheet, its `alertcondition`s become alerts, it survives a cold start, and it belongs to one pane
 * of a four-chart layout rather than to the app.
 *
 * So a script on the chart is an *instance* — this — and every mechanism the chart already has for
 * an indicator addresses it by [ownerId], which is a string exactly like `"ema"` is a string. The
 * legend's eye, its gear, its ×, the pane order, the merges, the separations, the colour and the
 * width all key by indicator id, so all of them work here with no second code path. That is the
 * whole design: **there is no script-shaped hole in the chart**.
 *
 * ### Identity
 *
 * [instanceId] is this *instance*: the same script added twice is two instances with two sets of
 * inputs, numbered (1) and (2) by [ordinal] the way TradingView numbers them. [scriptId] is what it
 * came *from* — a saved row's id, a preset's id — and is what a restored layout looks up when it
 * wants the source back. Neither is the other: a reader can delete the saved script and the
 * instance on the chart keeps working, because the source travels with the instance.
 */
data class ChartScript(
    /** Unique on this chart. Generated on add; stable across runs, edits and a cold start. */
    val instanceId: String,
    val name: String,
    val source: String,
    /** The saved row or preset this came from, for «Restore script» on a layout that lost it. */
    val scriptId: String? = null,
    /** The reader's values for the script's `input(...)`s, by the input's own title. */
    val overrides: Map<String, Double> = emptyMap(),
    /** 1 for the first copy of a script on this chart, 2 for the next — TradingView's numbering. */
    val ordinal: Int = 1,
    /** True for an instance a restored layout could not find the source of. Draws nothing. */
    val missing: Boolean = false,
) {
    /** How every other part of the chart addresses this script. See the class note. */
    val ownerId: String get() = OWNER_PREFIX + instanceId

    /** What the legend writes: «RSI Zones», or «RSI Zones (2)» for a second copy. */
    val displayName: String get() = if (ordinal <= 1) name else "$name ($ordinal)"

    companion object {
        /**
         * The prefix that makes a script's id impossible to confuse with a catalogue indicator's.
         *
         * A colon, which no catalogue id contains and which `SymbolChartStateStore` does not use as
         * a separator — so a script id can be written into the same indicator lists as `"ema"` and
         * read back out of them without a second field.
         */
        const val OWNER_PREFIX = "nama:"

        /** Whether an indicator id names a script instance rather than one of the built-ins. */
        fun owns(id: String): Boolean = id.startsWith(OWNER_PREFIX)

        /** The instance id inside an owner id, or null where the id is a built-in's. */
        fun instanceOf(id: String): String? = if (owns(id)) id.removePrefix(OWNER_PREFIX) else null
    }
}

/**
 * A script the reader could put on the chart: one they saved, or one that ships with the app.
 *
 * The indicator sheet's «My scripts» section is a list of these, and it exists so a script is
 * reachable from *where indicators are chosen* rather than only from the editor. A reader who has
 * written a study thinks of it as one of their studies from that moment on; making them go back
 * through a code editor to switch it on is the app disagreeing with them about what they made.
 *
 * The app fills the list — from `SavedScriptDao` and the shipped presets — because the chart module
 * has no database and should not grow one to draw a list.
 */
data class ChartScriptSource(
    val id: String,
    val name: String,
    val source: String,
    /** True for one of the app's own presets, so the list can say which are the reader's. */
    val preset: Boolean = false,
)

/**
 * Why a script instance stopped drawing.
 *
 * Separate from an ordinary failure because the two deserve different words and different
 * recoveries: a compile error is «you wrote this wrong, here is the line», and a budget breach is
 * «this runs, and it is too expensive to run on every frame» — the second pauses *that instance*
 * and touches nothing else on the chart, which is the only acceptable behaviour when a reader's own
 * code meets a scrolling chart.
 */
data class ChartScriptPause(val line: Int, val code: String)

/**
 * Everything the scripts on one chart drew, committed as one batch.
 *
 * **Atomically**, which is the point of it being a single value: a script's overlays, its pane, its
 * levels and its owners have to arrive together or the legend resolves an index to the wrong study
 * for a frame. The controller computes this off the UI thread and swaps the whole object in.
 */
data class ChartScriptDraw(
    val overlays: List<ChartLine> = emptyList(),
    /** Aligned with [overlays], index for index — a [ChartScript.ownerId] each. */
    val overlayOwners: List<String> = emptyList(),
    val panes: List<ChartPane> = emptyList(),
    val paneOwners: List<String> = emptyList(),
    val levels: List<PriceLevel> = emptyList(),
    /** Aligned with [levels]. A level carries no owner of its own, and the eye needs one. */
    val levelOwners: List<String> = emptyList(),
    val markers: List<ChartMarker> = emptyList(),
    /** Aligned with [markers]. */
    val markerOwners: List<String> = emptyList(),
    val drawings: List<Drawing> = emptyList(),
    /** Aligned with [drawings]. */
    val drawingOwners: List<String> = emptyList(),
    /** The `alertcondition`s each instance declared, by owner id. */
    val alerts: Map<String, List<ScriptAlert>> = emptyMap(),
    /** The `strategy.*` simulation each instance produced, by owner id. */
    val strategies: Map<String, ScriptStrategyReport> = emptyMap(),
    /** The `input(...)`s each instance declares, by owner id — what the settings sheet draws. */
    val inputs: Map<String, List<ScriptInput>> = emptyMap(),
    /**
     * What each instance's own `signal(...)` calls said, by owner id (4.75.0, run Ω1).
     *
     * Carried rather than re-derived because it is the one thing about a script the app cannot work
     * out for itself: a line can be read, an author's verdict has to be reported. See
     * `ChartSignalEngine`.
     */
    val verdicts: Map<String, List<ScriptVerdict>> = emptyMap(),
    /** The diagnostic behind the legend's red dot, by owner id. The last good draw stays up. */
    val failures: Map<String, ScriptFailure> = emptyMap(),
    /** The instances that ran out of budget, by owner id. See [ChartScriptPause]. */
    val paused: Map<String, ChartScriptPause> = emptyMap(),
    /** How long the whole batch took, for the console and the report. */
    val elapsedMillis: Long = 0,
) {
    val isEmpty: Boolean
        get() = overlays.isEmpty() && panes.isEmpty() && levels.isEmpty() &&
            markers.isEmpty() && drawings.isEmpty()

    /**
     * [items] less the ones belonging to a script the reader has switched the eye off on.
     *
     * The owners list is what makes this possible without a second evaluation: a `PriceLevel` and a
     * `ChartMarker` belong to `core:chart` and know nothing about scripts, so whose they are is
     * carried beside them, the same way `ChartDerived` carries `overlayOwners`.
     */
    fun <T> shown(items: List<T>, owners: List<String>, hidden: Set<String>): List<T> {
        if (items.isEmpty() || hidden.isEmpty()) return items
        if (owners.size != items.size) return items
        return items.filterIndexed { index, _ -> owners[index] !in hidden }
    }

    companion object {
        val EMPTY = ChartScriptDraw()
    }
}

/**
 * The thing that actually runs the scripts on a chart.
 *
 * One [IncrementalRunner] per instance, kept between evaluations, which is what makes a new bar
 * cost the tail rather than the series — see `IncrementalRunner.spliceStart`. Held by the
 * controller and never touched from composition: [evaluate] is called on a worker dispatcher and
 * its answer is committed to the state in one assignment.
 *
 * ### What is kept when a run fails
 *
 * The **last good result**, per instance. A reader editing a script in the studio types a bracket
 * and the chart would otherwise lose the study they were reading mid-sentence; instead the drawing
 * stays exactly as it was and the legend grows a red dot. This mirrors the studio's own rule for
 * its preview and is the same reasoning: a compile error is a statement about the text, not about
 * the picture.
 */
internal class ChartScriptEngine {

    private class Slot(var source: String, var runner: IncrementalRunner?, var compileFailure: ScriptFailure?) {
        var lastGood: ScriptResult? = null
    }

    private val slots = LinkedHashMap<String, Slot>()

    /** Forgets an instance's compiled script and its history. Called when it leaves the chart. */
    fun forget(instanceId: String) {
        slots.remove(instanceId)
    }

    /** Forgets everything — a new symbol is a new chart, and none of the histories transfer. */
    fun reset() {
        slots.clear()
    }

    /**
     * Runs every instance over [series] and returns the one batch the chart should draw.
     *
     * Runs them in order, so the legend's rows follow the order the reader added them, and gives
     * each its own budget: one expensive script pauses itself and the rest of the chart is
     * untouched, which is 0.3's rule and the reason the loop does not share a clock.
     */
    fun evaluate(series: CandleSeries, scripts: List<ChartScript>, now: () -> Long): ChartScriptDraw {
        if (scripts.isEmpty()) {
            slots.clear()
            return ChartScriptDraw.EMPTY
        }
        val started = now()
        val overlays = mutableListOf<ChartLine>()
        val overlayOwners = mutableListOf<String>()
        val panes = mutableListOf<ChartPane>()
        val paneOwners = mutableListOf<String>()
        val levels = mutableListOf<PriceLevel>()
        val levelOwners = mutableListOf<String>()
        val markers = mutableListOf<ChartMarker>()
        val markerOwners = mutableListOf<String>()
        val drawings = mutableListOf<Drawing>()
        val drawingOwners = mutableListOf<String>()
        val alerts = LinkedHashMap<String, List<ScriptAlert>>()
        val strategies = LinkedHashMap<String, ScriptStrategyReport>()
        val verdicts = LinkedHashMap<String, List<ScriptVerdict>>()
        val inputs = LinkedHashMap<String, List<ScriptInput>>()
        val failures = LinkedHashMap<String, ScriptFailure>()
        val paused = LinkedHashMap<String, ChartScriptPause>()

        val live = scripts.mapTo(HashSet()) { it.instanceId }
        slots.keys.retainAll(live)

        for (script in scripts) {
            if (script.missing || script.source.isBlank()) continue
            val slot = slots.getOrPut(script.instanceId) { Slot(script.source, null, null) }
            if (slot.runner == null || slot.source != script.source) {
                val compilation = NamaScript.compile(script.source)
                slot.source = script.source
                slot.runner = compilation.script?.let { IncrementalRunner(it) }
                slot.compileFailure = compilation.failure
            }
            val runner = slot.runner
            val failure = slot.compileFailure
            val result = when {
                runner == null -> null
                else -> runner.run(series, script.overrides)
            }
            val good = when {
                result != null && result.ok -> result.also { slot.lastGood = it }
                else -> slot.lastGood
            }
            val error = failure ?: result?.error
            if (error != null) {
                failures[script.ownerId] = error
                if (error.code in BUDGET_CODES) paused[script.ownerId] = ChartScriptPause(error.line, error.code)
            }
            val drawn = good ?: continue
            inputs[script.ownerId] = drawn.inputs
            if (drawn.alerts.isNotEmpty()) alerts[script.ownerId] = drawn.alerts
            drawn.strategy?.let { strategies[script.ownerId] = it }
            if (drawn.verdicts.isNotEmpty()) verdicts[script.ownerId] = drawn.verdicts

            val overlay = drawn.toOverlay(series, script.displayName)
            overlay.overlays.forEach { line ->
                overlays += line
                overlayOwners += script.ownerId
            }
            overlay.pane?.let { pane ->
                panes += pane
                paneOwners += script.ownerId
            }
            overlay.levels.forEach { levels += it; levelOwners += script.ownerId }
            overlay.markers.forEach { markers += it; markerOwners += script.ownerId }
            overlay.drawings.forEach { drawings += it; drawingOwners += script.ownerId }
        }

        return ChartScriptDraw(
            overlays = overlays,
            overlayOwners = overlayOwners,
            panes = panes,
            paneOwners = paneOwners,
            levels = levels,
            levelOwners = levelOwners,
            markers = markers,
            markerOwners = markerOwners,
            drawings = drawings,
            drawingOwners = drawingOwners,
            alerts = alerts,
            strategies = strategies,
            verdicts = verdicts,
            inputs = inputs,
            failures = failures,
            paused = paused,
            elapsedMillis = now() - started,
        )
    }

    private companion object {
        /**
         * The three diagnostics that mean «too expensive», not «wrong».
         *
         * E401 is the node budget, E406 the wall clock and E407 the retained-series budget — see
         * `Interpreter`. Any of them pauses that instance with a line number rather than putting an
         * error where the reader expects a mistake they can see in their own code.
         */
        val BUDGET_CODES = setOf("E401", "E406", "E407")
    }
}
