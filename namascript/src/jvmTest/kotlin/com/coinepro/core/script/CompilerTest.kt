package com.coinepro.core.script

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The typed pass, the compiled script and the incremental runner — item 5 of the 4.52 run.
 *
 * Three promises. The checker refuses before the run exactly what the run would refuse, with
 * the same code, and never refuses a script that runs. A compiled script runs many times without
 * re-parsing. The incremental runner, handed the same series with bars appended, produces the
 * result a whole run would — to the conformance tolerance on every bar — while touching only
 * the tail.
 */
class CompilerTest {

    private val series = ConformanceSuiteTest.fixture()

    /** A longer walk than the conformance fixture, so the runner's window fits with bars to spare. */
    private val long = walk(2_000)

    private fun walk(count: Int): CandleSeries {
        var seed = 11L
        fun random(): Double {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return ((seed ushr 11).toDouble() / (1L shl 53).toDouble())
        }
        var close = 100.0
        return CandleSeries(
            List(count) { index ->
                val open = close
                close = (open + (random() - 0.5) * 2.0).coerceAtLeast(10.0)
                Candle(1_700_000_000L + index * 3_600L, open, maxOf(open, close) + random(), minOf(open, close) - random(), close, 1_000.0 + random() * 500)
            },
        )
    }

    @Test
    fun `a type error is found without running`() {
        val failure = NamaScript.check("x = \"abc\" * 1\nplot(x)")
        assertNotNull(failure)
        assertEquals("E203", failure!!.code)
        assertEquals(1, failure.line)
    }

    @Test
    fun `an unknown function and an unknown name are found without running`() {
        assertEquals("E304", NamaScript.check("plot(ta.magic(close))")!!.code)
        assertEquals("E301", NamaScript.check("plot(closs)")!!.code)
        assertEquals("E303", NamaScript.check("x := 5")!!.code)
        assertEquals("E302", NamaScript.check("close = 5")!!.code)
    }

    @Test
    fun `not on text and a negative offset are refused before the run`() {
        assertEquals("E201", NamaScript.check("plot(not \"a\")")!!.code)
        assertEquals("E202", NamaScript.check("plot(close[-1])")!!.code)
    }

    @Test
    fun `a clean script checks clean and carries its analysis`() {
        val compiled = NamaScript.compile("fast = ta.ema(close, 12)\nslow = ta.ema(close, 26)\nplot(fast - slow)\nmarker(ta.crossover(fast, slow) and close[3] > open, title = \"x\")")
        assertNull(compiled.failure)
        val analysis = compiled.script!!.analysis
        assertEquals(26, analysis.maxLookback)
        assertTrue(analysis.incremental)
        assertFalse(analysis.usesSecurity)
        assertEquals(ScriptType.NUM_SERIES, analysis.types["fast"])
    }

    @Test
    fun `a cumulative function or another timeframe makes the script whole-run only`() {
        assertFalse(NamaScript.compile("plot(ta.obv())").script!!.analysis.incremental)
        assertFalse(NamaScript.compile("plot(request.security(\"240\", close))").script!!.analysis.incremental)
        assertTrue(NamaScript.compile("plot(request.security(\"240\", close))").script!!.analysis.usesSecurity)
    }

    @Test
    fun `the incremental runner matches a whole run as bars arrive`() {
        val source = """
            len = input(20, title = "len")
            fast = ta.ema(close, 12)
            slow = ta.ema(close, len)
            r = ta.rsi(close, 14)
            plot(fast - slow, title = "diff")
            plot(r, title = "rsi", pane = "own")
            plot(ta.sma(close, 50) - close[2], title = "sma")
            marker(ta.crossover(fast, slow), title = "up")
            bgcolor(r > 70, color.new(color.red, 80))
            alertcondition(r > 70, "hot")
        """.trimIndent()
        val compiled = NamaScript.compile(source).script!!
        val runner = IncrementalRunner(compiled)
        val series = long
        val total = series.size
        val initial = total - 12
        runner.run(CandleSeries(series.bars.subList(0, initial)))
        assertFalse(runner.lastRunWasIncremental)
        var incrementalRuns = 0
        for (size in initial + 1..total) {
            // Every other step also rewrites the last bar first, the way a tick does.
            if (size % 2 == 0) {
                val ticked = series.bars.subList(0, size - 1).toMutableList()
                val last = ticked.last()
                ticked[ticked.size - 1] = last.copy(c = last.c * 1.001, h = maxOf(last.h, last.c * 1.001))
                runner.run(CandleSeries(ticked))
            }
            val partial = CandleSeries(series.bars.subList(0, size))
            val incremental = runner.run(partial)
            if (runner.lastRunWasIncremental) incrementalRuns++
            val whole = compiled.run(partial)
            assertTrue(incremental.ok && whole.ok)
            assertEquals(whole.plots.size, incremental.plots.size)
            for ((index, plot) in whole.plots.withIndex()) {
                val other = incremental.plots[index]
                assertEquals(plot.values.size, other.values.size)
                for (bar in 0 until plot.values.size) {
                    val a = plot.values[bar]
                    val b = other.values[bar]
                    if (a == null || b == null) {
                        assertEquals("plot $index bar $bar presence", a == null, b == null)
                    } else {
                        assertTrue("plot $index bar $bar: $a vs $b", abs(a - b) <= 1e-6 * maxOf(1.0, abs(a)))
                    }
                }
            }
            assertEquals(whole.markers.map { it.bars }, incremental.markers.map { it.bars })
            assertEquals(whole.backgrounds.map { it.bars }, incremental.backgrounds.map { it.bars })
            assertEquals(whole.alerts.map { it.bars }, incremental.alerts.map { it.bars })
        }
        assertTrue("expected the tail to be re-used, incremental runs = $incrementalRuns", incrementalRuns >= 10)
    }

    @Test
    fun `a changed input re-runs the whole series once, then the tail again`() {
        val compiled = NamaScript.compile("plot(ta.sma(close, input(10, title = \"n\")))").script!!
        val runner = IncrementalRunner(compiled)
        val a = CandleSeries(long.bars.subList(0, long.size - 2))
        val b = CandleSeries(long.bars.subList(0, long.size - 1))
        runner.run(a)
        runner.run(b)
        assertTrue(runner.lastRunWasIncremental)
        runner.run(long, mapOf("n" to 30.0))
        assertFalse(runner.lastRunWasIncremental)
    }

    @Test
    fun `request security reads the last completed higher bar and never the forming one`() {
        // Hourly bars → four-hour buckets. On the bar that closes a bucket the bucket's own close
        // is visible; on the bars inside a bucket the previous bucket's close is.
        val result = NamaScript.run("plot(request.security(\"240\", close))", series)
        assertTrue(result.error?.messageEn ?: "", result.ok)
        val plot = result.plots[0].values
        val aggregation = Timeframes.aggregate(series, 240 * 60)
        for (index in 0 until series.size) {
            val bucket = aggregation.bucketOf[index]
            val expected = if (aggregation.closesBucket[index]) bucket else bucket - 1
            val want = if (expected < 0) null else aggregation.series.close[expected]
            assertEquals("bar $index", want, plot[index])
        }
        // The higher bar is the bucket's own OHLC.
        val first = aggregation.series.bars[1]
        val members = series.bars.filterIndexed { index, _ -> aggregation.bucketOf[index] == 1 }
        assertEquals(members.first().o, first.o, 0.0)
        assertEquals(members.maxOf { it.h }, first.h, 0.0)
        assertEquals(members.minOf { it.l }, first.l, 0.0)
        assertEquals(members.last().c, first.c, 0.0)
    }

    @Test
    fun `request security refuses a finer or non-multiple timeframe`() {
        assertEquals("E210", NamaScript.run("plot(request.security(\"15\", close))", series).error!!.code)
        assertEquals("E210", NamaScript.run("plot(request.security(\"90\", close))", series).error!!.code)
        assertEquals("E210", NamaScript.run("plot(request.security(\"x9\", close))", series).error!!.code)
        assertEquals("E210", NamaScript.run("plot(request.security(240, close))", series).error!!.code)
        // The chart's own timeframe is itself.
        assertTrue(NamaScript.run("plot(request.security(\"60\", close))", series).ok)
    }

    @Test
    fun `timeframe spellings`() {
        assertEquals(240L * 60, Timeframes.seconds("240"))
        assertEquals(4L * 3600, Timeframes.seconds("H4"))
        assertEquals(86_400L, Timeframes.seconds("D"))
        assertEquals(86_400L, Timeframes.seconds("1D"))
        assertEquals(7L * 86_400, Timeframes.seconds("W"))
        assertEquals(15L * 60, Timeframes.seconds("M15"))
        assertNull(Timeframes.seconds("abc"))
        assertEquals(3600L, Timeframes.baseSeconds(series))
    }

    @Test
    fun `the full input set records its kind and options`() {
        val result = NamaScript.run(
            """
            k = input.string("ema", title = "kind", options = "ema,sma")
            s = input.source("close", title = "src")
            c = input.color(color.gold, title = "col")
            t = input.timeframe("240", title = "tf")
            b = input.bool(true, title = "on")
            len = input.int(14, title = "len", min = 2, max = 50, step = 2)
            plot(s, color = c)
            """.trimIndent(),
            series,
        )
        assertTrue(result.error?.messageEn ?: "", result.ok)
        val byName = result.inputs.associateBy { it.name }
        assertEquals(ScriptInputKind.TEXT, byName.getValue("kind").kind)
        assertEquals(listOf("ema", "sma"), byName.getValue("kind").options)
        assertEquals(ScriptInputKind.SOURCE, byName.getValue("src").kind)
        assertEquals(Builtins.SOURCE_OPTIONS, byName.getValue("src").options)
        assertEquals(ScriptInputKind.COLOUR, byName.getValue("col").kind)
        assertEquals(ScriptInputKind.TIMEFRAME, byName.getValue("tf").kind)
        assertEquals(ScriptInputKind.BOOL, byName.getValue("on").kind)
        assertEquals(ScriptInputKind.INTEGER, byName.getValue("len").kind)
        assertEquals(2.0, byName.getValue("len").step)
        // An override picks by index.
        val chosen = NamaScript.run("s = input.source(\"close\", title = \"src\")\nplot(s)", series, mapOf("src" to 1.0))
        assertEquals(series.open[10], chosen.plots[0].values[10])
    }

    @Test
    fun `every case Builtins answers to is a name the checker knows`() {
        val source = java.io.File("src/commonMain/kotlin/com/coinepro/core/script/Builtins.kt").readText()
        val bound = Regex("""^\s+"([a-z_.]+)"(?:, "([a-z_.]+)")? ->""", RegexOption.MULTILINE)
            .findAll(source).flatMap { listOfNotNull(it.groupValues[1], it.groupValues[2].takeIf(String::isNotEmpty)) }.toSet()
        val unknown = bound - Builtins.NAMES - setOf("triangleup", "arrowup", "labelup", "triangledown", "arrowdown", "labeldown")
        assertEquals(emptySet<String>(), unknown)
    }

    private fun Candle.copy(c: Double, h: Double) = Candle(t, o, h, l, c, v)
}
