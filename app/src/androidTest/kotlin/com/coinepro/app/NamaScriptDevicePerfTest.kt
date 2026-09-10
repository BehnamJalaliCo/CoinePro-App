package com.coinepro.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.script.IncrementalRunner
import com.coinepro.core.script.NamaScript
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * The plan's NamaScript numbers, measured on the phone they were written for.
 *
 * The same 303-line script with ten `ta.` calls over 20 000 bars that `ScriptPerformanceTest`
 * times on the JVM — compile (lex, parse, type-check), a whole evaluation, and the realtime case
 * through the `IncrementalRunner` (one bar appended, one bar ticked), medians of twenty. The
 * figures go to logcat under `NamaScriptPerf` and to `namascript-perf.json` in the app's external
 * files directory; the plan's targets are compile < 50 ms, evaluate < 40 ms, realtime < 2 ms on
 * a Pixel 6a. Nothing here asserts a target — a phone that misses one should still report the
 * number rather than a red bar with no figure in it. From the repo root, device attached:
 *
 *     ./gradlew :app:connectedDebugAndroidTest \
 *         -Pandroid.testInstrumentationRunnerArguments.class=com.coinepro.app.NamaScriptDevicePerfTest
 *     adb logcat -d -s NamaScriptPerf
 */
@RunWith(AndroidJUnit4::class)
class NamaScriptDevicePerfTest {

    @Test
    fun compileEvaluateAndRealtimeOverTwentyThousandBars() {
        val series = walk(20_000)
        val source = script()
        // Warm-up: the first run pays for class loading and the JIT, which is not the number.
        NamaScript.run(source, series, timeBudgetMillis = BUDGET_MS).also { assertTrue(it.error?.messageEn ?: "", it.ok) }

        val compile = median(20) { NamaScript.check(source) }
        val evaluate = median(5) { NamaScript.run(source, series, timeBudgetMillis = BUDGET_MS) }

        val compiled = NamaScript.compile(source).script!!
        val runner = IncrementalRunner(compiled)
        runner.run(series, timeBudgetMillis = BUDGET_MS)
        var current = series
        val appends = DoubleArray(20)
        val ticks = DoubleArray(20)
        for (round in appends.indices) {
            val last = current.bars.last()
            current = CandleSeries(current.bars + Candle(last.t + 60L, last.c, last.c * 1.001, last.c * 0.999, last.c * 1.0005, last.v))
            appends[round] = timed { runner.run(current, timeBudgetMillis = BUDGET_MS) }
            val bars = current.bars.toMutableList()
            val forming = bars.last()
            bars[bars.size - 1] = forming.copy(c = forming.c * 1.0003, h = maxOf(forming.h, forming.c * 1.0003))
            current = CandleSeries(bars)
            ticks[round] = timed { runner.run(current, timeBudgetMillis = BUDGET_MS) }
        }
        appends.sort()
        ticks.sort()
        val append = appends[appends.size / 2]
        val tick = ticks[ticks.size / 2]

        val line = String.format(
            Locale.ROOT,
            "namascript on %s %s (Android %d): compile %.2f ms, evaluate %.1f ms, realtime append %.2f ms, tick %.2f ms — %d lines over %d bars; targets 50 / 40 / 2 / 2",
            android.os.Build.MANUFACTURER, android.os.Build.MODEL, android.os.Build.VERSION.SDK_INT,
            compile, evaluate, append, tick, source.lines().size, series.size,
        )
        Log.i(TAG, line)
        val out = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        if (out != null) {
            File(out, "namascript-perf.json").writeText(
                String.format(
                    Locale.ROOT,
                    "{\"device\":\"%s %s\",\"sdk\":%d,\"compileMs\":%.3f,\"evaluateMs\":%.3f,\"appendMs\":%.3f,\"tickMs\":%.3f,\"lines\":%d,\"bars\":%d}\n",
                    android.os.Build.MANUFACTURER, android.os.Build.MODEL, android.os.Build.VERSION.SDK_INT,
                    compile, evaluate, append, tick, source.lines().size, series.size,
                ),
            )
        }
        assertTrue(line, compile > 0.0 && evaluate > 0.0)
    }

    private inline fun timed(block: () -> Unit): Double {
        val started = System.nanoTime()
        block()
        return (System.nanoTime() - started) / 1_000_000.0
    }

    private inline fun median(rounds: Int, block: () -> Unit): Double {
        val times = DoubleArray(rounds) { timed(block) }
        times.sort()
        return times[times.size / 2]
    }

    private fun script(): String = buildString {
        appendLine("//@version=1")
        appendLine("fast = ta.ema(close, 12)")
        appendLine("slow = ta.ema(close, 26)")
        appendLine("r = ta.rsi(close, 14)")
        appendLine("a = ta.atr(14)")
        appendLine("bbu = ta.bb_upper(close, 20, 2)")
        appendLine("bbl = ta.bb_lower(close, 20, 2)")
        appendLine("k = ta.stoch_k(14, 3)")
        appendLine("adx = ta.adx(14)")
        appendLine("h = ta.highest(high, 50)")
        appendLine("m = ta.macd(close, 12, 26, 9)")
        for (index in 0 until 280) {
            appendLine("x$index = (fast - slow) * ${index % 7 + 1} + r / 100 - a * ${index % 3} + (bbu - bbl) / 2")
        }
        appendLine("cond = ta.crossover(fast, slow) and r < 70 and k > 20 and adx > 15")
        appendLine("marker(cond, title=\"go\", style=\"up\")")
        appendLine("plot(x279 + h * 0 + m * 0, title=\"sum\", pane=\"own\")")
        for (index in 0 until 8) appendLine("plot(x$index, title=\"x$index\", pane=\"own\")")
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

    private companion object {
        const val TAG = "NamaScriptPerf"
        const val BUDGET_MS = 60_000L
    }
}
