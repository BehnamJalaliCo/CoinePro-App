package com.coinepro.core.script

import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.Line

/**
 * A script parsed and type-checked once, ready to run against any series.
 *
 * The "compile" of a vectorising interpreter: the lexer, the parser and the [TypeChecker] have
 * done their work and every name and function is resolved; what is left per run is the
 * arithmetic over the bars. `NamaScript.compile` produces one; `ScriptController` keeps it for
 * as long as the source is unchanged and runs it through an [IncrementalRunner] as bars arrive.
 */
class CompiledScript internal constructor(
    internal val program: Program,
    val analysis: ScriptAnalysis,
) {
    fun run(
        series: CandleSeries,
        overrides: Map<String, Double> = emptyMap(),
        timeBudgetMillis: Long = Interpreter.MAX_MILLIS,
        indexBase: Int = 0,
        totalBars: Int = series.size,
    ): ScriptResult = NamaScript.guard {
        if (series.bars.isEmpty()) {
            throw ScriptError("برای اجرای اسکریپت، چارت باید کندل داشته باشد", "The chart needs bars before a script can run", code = "E404")
        }
        Interpreter(series, overrides, timeBudgetMillis, indexBase, totalBars).run(program)
    }
}

/**
 * Runs a [CompiledScript] over a series that grows, re-computing only its tail.
 *
 * Every value a windowed script produces at bar *i* depends on bars *i − L … i* for a lookback
 * *L* the checker bounded (`ScriptAnalysis.maxLookback`). When the series the chart hands over
 * is the previous one with bars appended, or with its last bar re-written by a tick, the runner
 * evaluates the script over the last `window` bars only and splices the new tail onto the
 * previous result: two thousand bars of history are not re-summed for one new close.
 *
 * The window is generous — [WINDOW_FACTOR] times the lookback plus a floor — so exponential
 * averages, whose dependence on old bars decays rather than ends, have settled to well under the
 * conformance tolerance before the first bar that is kept. A script the checker marked
 * non-incremental (a cumulative function, `request.security`) is re-run whole every time, as is
 * any series the runner cannot recognise as an extension of the last one.
 */
class IncrementalRunner(private val script: CompiledScript) {

    private var lastSeries: CandleSeries? = null
    private var lastResult: ScriptResult? = null
    private var lastOverrides: Map<String, Double> = emptyMap()

    /** Whether the most recent [run] re-used the previous result. Read by the tests and the studio. */
    var lastRunWasIncremental: Boolean = false
        private set

    fun run(series: CandleSeries, overrides: Map<String, Double> = emptyMap(), timeBudgetMillis: Long = Interpreter.MAX_MILLIS): ScriptResult {
        val previous = lastSeries
        val cached = lastResult
        val start = if (previous != null && cached != null && cached.ok && script.analysis.incremental && overrides == lastOverrides) {
            spliceStart(previous, series, overrides)
        } else {
            -1
        }
        val result = if (start > 0) {
            // The tail is evaluated from `start` so its averages have settled, but only the bars
            // the previous result could not know — the last one it had, which a tick may have
            // rewritten, and everything after — are taken from it. Every earlier bar's value is
            // final: nothing a script computes at bar i reads a bar after i.
            val keep = previous!!.size - 1
            val tail = CandleSeries(series.bars.subList(start, series.size))
            val partial = script.run(tail, overrides, timeBudgetMillis, indexBase = start, totalBars = series.size)
            val spliced = splice(cached!!, partial, start, keep, series.size)
            if (spliced != null) {
                lastRunWasIncremental = true
                spliced
            } else {
                lastRunWasIncremental = false
                script.run(series, overrides, timeBudgetMillis)
            }
        } else {
            lastRunWasIncremental = false
            script.run(series, overrides, timeBudgetMillis)
        }
        lastSeries = series
        lastResult = result
        lastOverrides = overrides
        return result
    }

    /** The first bar whose value has to be recomputed, or −1 when the whole series must be run. */
    private fun spliceStart(previous: CandleSeries, next: CandleSeries, overrides: Map<String, Double>): Int {
        if (next.size < previous.size || next.size - previous.size > MAX_APPENDED) return -1
        val common = previous.size - 1
        if (common < 1) return -1
        // The same history: first bar and the bar before the last one unchanged. A rewritten
        // last bar (a tick) is expected; an earlier bar rewritten is another series.
        if (next.bars[0] != previous.bars[0] || next.bars[common - 1] != previous.bars[common - 1]) return -1
        val lookback = maxOf(script.analysis.maxLookback, overrides.values.maxOfOrNull { it.toInt() } ?: 0)
        val window = maxOf(MIN_WINDOW, lookback * WINDOW_FACTOR + 32)
        val start = common - window
        return if (start <= 0) -1 else start
    }

    private fun splice(cached: ScriptResult, tail: ScriptResult, start: Int, keep: Int, size: Int): ScriptResult? {
        if (!tail.ok) return tail
        if (tail.plots.size != cached.plots.size || cached.plots.any { it.values.size < keep }) return null
        val plots = cached.plots.mapIndexed { index, old ->
            val fresh = tail.plots[index]
            if (fresh.values.size != size - start) return null
            fresh.copy(values = Line.of(size) { bar -> if (bar < keep) old.values[bar] else fresh.values[bar - start] })
        }
        fun bars(old: List<Int>, fresh: List<Int>) = old.filter { it < keep } + fresh.map { it + start }.filter { it >= keep }
        return tail.copy(
            plots = plots,
            markers = tail.markers.mapIndexed { index, marker ->
                marker.copy(bars = bars(cached.markers.getOrNull(index)?.bars.orEmpty(), marker.bars))
            },
            backgrounds = tail.backgrounds.mapIndexed { index, background ->
                background.copy(bars = bars(cached.backgrounds.getOrNull(index)?.bars.orEmpty(), background.bars))
            },
            alerts = tail.alerts.mapIndexed { index, alert ->
                alert.copy(bars = bars(cached.alerts.getOrNull(index)?.bars.orEmpty(), alert.bars))
            },
            setup = tail.setup?.let { it.copy(barIndex = it.barIndex + start) },
        )
    }

    internal companion object {
        const val MIN_WINDOW = 120
        const val WINDOW_FACTOR = 12
        const val MAX_APPENDED = 64
    }
}
