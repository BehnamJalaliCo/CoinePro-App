package com.coinepro.core.script

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plan's performance target, measured where this build can measure it.
 *
 * A 300-line script with ten `ta.` calls over 20 000 bars: parse (the "compile" of a
 * vectorising interpreter) and evaluate, timed on the JVM. The plan's numbers are for a Pixel
 * 6a — compile < 50 ms, evaluate < 40 ms — which this environment cannot run; what is asserted
 * here is a generous JVM bound so that a regression of an order of magnitude fails the build,
 * and the measured figures are printed for `docs/engineering/REPORT.md`.
 */
class ScriptPerformanceTest {

    @Test
    fun `a three-hundred-line script over twenty thousand bars stays within budget`() {
        val series = walk(20_000)
        val source = buildString {
            appendLine("//@version=1")
            appendLine("fast = ta.ema(close, 12)")
            appendLine("slow = ta.ema(close, 26)")
            appendLine("r = ta.rsi(close, 14)")
            appendLine("a = ta.atr(14)")
            appendLine("bbu = ta.bb_upper(close, 20, 2)")
            appendLine("bbl = ta.bb_lower(close, 20, 2)")
            appendLine("k = ta.stoch_k(14, 3)")
            appendLine("adx = ta.adx(14)")
            appendLine("v = ta.vwap()")
            appendLine("m = ta.macd(close, 12, 26, 9)")
            // Two hundred and ninety lines of arithmetic over those ten series, the way a long
            // hand-written script accumulates conditions and derived lines.
            for (index in 0 until 280) {
                appendLine("x$index = (fast - slow) * ${index % 7 + 1} + r / 100 - a * ${index % 3} + (bbu - bbl) / 2")
            }
            appendLine("cond = ta.crossover(fast, slow) and r < 70 and k > 20 and adx > 15")
            appendLine("marker(cond, title=\"go\", style=\"up\")")
            appendLine("plot(x279 + v * 0 + m * 0, title=\"sum\", pane=\"own\")")
            for (index in 0 until 8) appendLine("plot(x$index, title=\"x$index\", pane=\"own\")")
        }
        assertTrue(source.lines().size >= 300)

        // Warm up once so the JIT's first-call cost is not the measurement.
        NamaScript.run(source, series, timeBudgetMillis = BENCH_BUDGET_MS).also { assertTrue(it.error?.messageEn ?: "", it.ok) }

        val parseStart = System.nanoTime()
        repeat(20) { NamaScript.check(source) }
        val parseMs = (System.nanoTime() - parseStart) / 20 / 1_000_000.0

        val runStart = System.nanoTime()
        val runs = 5
        repeat(runs) { NamaScript.run(source, series, timeBudgetMillis = BENCH_BUDGET_MS) }
        val runMs = (System.nanoTime() - runStart) / runs / 1_000_000.0

        println(String.format(java.util.Locale.ROOT, "namascript performance (JVM): parse %.1f ms, evaluate %.1f ms for %d lines over %d bars", parseMs, runMs, source.lines().size, series.bars.size))
        assertTrue("parse took $parseMs ms", parseMs < 500.0)
        assertTrue("evaluate took $runMs ms", runMs < 4_000.0)
    }

    /**
     * The realtime figure: the same script, one bar appended and one bar ticked, through the
     * `IncrementalRunner` that the studio uses — the tail is evaluated and spliced, the history is
     * not re-summed. The plan's target is < 2 ms on a Pixel 6a; this is the JVM's number.
     */
    @Test
    fun `an appended bar and a tick re-run the tail only`() {
        val series = walk(20_000)
        val source = buildString {
            appendLine("fast = ta.ema(close, 12)")
            appendLine("slow = ta.ema(close, 26)")
            appendLine("r = ta.rsi(close, 14)")
            appendLine("a = ta.atr(14)")
            appendLine("bbu = ta.bb_upper(close, 20, 2)")
            appendLine("bbl = ta.bb_lower(close, 20, 2)")
            appendLine("k = ta.stoch_k(14, 3)")
            appendLine("adx = ta.adx(14)")
            appendLine("m = ta.macd(close, 12, 26, 9)")
            appendLine("h = ta.highest(high, 50)")
            for (index in 0 until 280) {
                appendLine("x$index = (fast - slow) * ${index % 7 + 1} + r / 100 - a * ${index % 3} + (bbu - bbl) / 2")
            }
            appendLine("marker(ta.crossover(fast, slow) and r < 70 and k > 20 and adx > 15, title=\"go\", style=\"up\")")
            appendLine("plot(x279 + m * 0 + h * 0, title=\"sum\", pane=\"own\")")
        }
        val compiled = NamaScript.compile(source).script!!
        val runner = IncrementalRunner(compiled)
        runner.run(series, timeBudgetMillis = BENCH_BUDGET_MS).also { assertTrue(it.error?.messageEn ?: "", it.ok) }

        var current = series
        val appendTimes = LongArray(20)
        val tickTimes = LongArray(20)
        for (round in appendTimes.indices) {
            val last = current.bars.last()
            current = CandleSeries(current.bars + Candle(last.t + 60L, last.c, last.c * 1.001, last.c * 0.999, last.c * 1.0005, last.v))
            val appendStart = System.nanoTime()
            runner.run(current, timeBudgetMillis = BENCH_BUDGET_MS)
            appendTimes[round] = System.nanoTime() - appendStart
            assertTrue("append $round was not incremental", runner.lastRunWasIncremental)
            val bars = current.bars.toMutableList()
            val forming = bars.last()
            bars[bars.size - 1] = forming.copy(c = forming.c * 1.0003, h = maxOf(forming.h, forming.c * 1.0003))
            current = CandleSeries(bars)
            val tickStart = System.nanoTime()
            runner.run(current, timeBudgetMillis = BENCH_BUDGET_MS)
            tickTimes[round] = System.nanoTime() - tickStart
            assertTrue("tick $round was not incremental", runner.lastRunWasIncremental)
        }
        appendTimes.sort()
        tickTimes.sort()
        val appendMs = appendTimes[appendTimes.size / 2] / 1_000_000.0
        val tickMs = tickTimes[tickTimes.size / 2] / 1_000_000.0
        println(
            String.format(
                java.util.Locale.ROOT,
                "namascript realtime (JVM): append %.2f ms, tick %.2f ms (median of %d) for %d lines over %d bars, window %d bars",
                appendMs, tickMs, appendTimes.size, source.lines().size, current.bars.size,
                maxOf(IncrementalRunner.MIN_WINDOW, compiled.analysis.maxLookback * IncrementalRunner.WINDOW_FACTOR + 32),
            ),
        )
        assertTrue("append took $appendMs ms", appendMs < 500.0)
        assertTrue("tick took $tickMs ms", tickMs < 500.0)
    }

    private companion object {
        /** Above the sandbox's two seconds: this measures the interpreter, not the sandbox, and the
         * suite runs in parallel with the rest of the module's tests. */
        const val BENCH_BUDGET_MS = 60_000L
    }

    private fun walk(count: Int): CandleSeries {
        var seed = 7L
        fun random(): Double {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return ((seed ushr 11).toDouble() / (1L shl 53).toDouble())
        }
        var close = 100.0
        return CandleSeries(
            List(count) { index ->
                val open = close
                close = (open + (random() - 0.5) * 2.0).coerceAtLeast(10.0)
                Candle(1_600_000_000L + index * 60L, open, maxOf(open, close) + random(), minOf(open, close) - random(), close, 1_000.0 + random() * 500)
            },
        )
    }
}
