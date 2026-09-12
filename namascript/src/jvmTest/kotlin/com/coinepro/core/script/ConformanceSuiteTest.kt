package com.coinepro.core.script

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * The conformance suite: every `.nama` file under `src/jvmTest/resources/conformance`, run over
 * one fixed 240-bar walk, held to the expectations written in its own header.
 *
 * A script's header is comment lines of three kinds:
 *
 * ```
 * // expect: plot <index> <bar> <value>      -- a plotted value, to 1e-6
 * // expect: plots <count>                    -- how many lines were plotted
 * // expect: markers <index> <count>          -- how many bars a marker set holds
 * // expect: drawings <count>                 -- how many labels, lines and boxes were placed
 * // expect: trades <count>                   -- how many trades the strategy closed
 * // expect: error <code>                     -- the script must refuse with this code
 * // expect: ok                               -- it must run, whatever it draws
 * ```
 *
 * Two kinds of script live here. The **generated** ones (`gen_*`) are one per built-in — every
 * `ta.`, `math.`, plot and input form — with their plotted values recorded from the engine by
 * this test run with `-Dnamascript.conformance.record=true` and committed. They pin the
 * *binding* between the language and `:chart-core`; the arithmetic behind them is held to an
 * outside reference by `IndicatorReferenceTest`. The **hand-written** ones (`sem_*`) carry
 * expectations a person worked out: absence, history, broadcasting, precedence, diagnostics.
 * Recording never touches those — a `sem_` file with a recorded value would be a test that
 * asserts what the engine did rather than what it should do.
 *
 * `scripts/quality/gen_namascript_conformance.py` writes the generated files from the builtin
 * table; regenerate, then record, then read the diff.
 */
class ConformanceSuiteTest {

    private val series = fixture()
    private val recording = System.getProperty("namascript.conformance.record") == "true"

    @Test
    fun `every script in the suite meets its expectations`() {
        val files = scripts()
        assertTrue("expected the conformance suite under src/jvmTest/resources/conformance, found ${files.size}", files.size >= 300)
        val failures = mutableListOf<String>()
        var recorded = 0
        for (file in files) {
            val text = file.readText()
            val expectations = text.lines().filter { it.trim().startsWith(EXPECT) }.map { it.trim().removePrefix(EXPECT).trim() }
            val result = NamaScript.run(text, series)
            if (recording && file.name.startsWith("gen_") && expectations.none { it.startsWith("error") }) {
                file.writeText(record(text, result))
                recorded++
                continue
            }
            if (expectations.isEmpty()) {
                failures += "${file.name}: no expectations in the header"
                continue
            }
            for (expectation in expectations) {
                check(file.name, expectation, result)?.let { failures += it }
            }
        }
        if (recording) println("recorded ${recorded} generated scripts")
        assertTrue(failures.joinToString("\n", prefix = "${failures.size} failure(s):\n"), failures.isEmpty())
    }

    @Test
    fun `the suite is the size the plan asked for, and named by kind`() {
        val files = scripts()
        val generated = files.count { it.name.startsWith("gen_") }
        val semantic = files.count { it.name.startsWith("sem_") }
        assertTrue("generated=$generated", generated >= 200)
        assertTrue("semantic=$semantic", semantic >= 40)
        assertEquals(files.size, generated + semantic)
    }

    @Test
    fun `every ta function in the reference has a script of its own`() {
        val names = (ScriptReference.ALL_GROUPS.flatMap { it.functions })
            .map { ScriptReferenceEn.nameOf(it.signature) }
            .filter { it.startsWith("ta.") }
            .toSet()
        val files = scripts().map { it.name }
        val missing = names.filter { name ->
            val stem = name.replace('.', '_')
            files.none { it.startsWith("gen_$stem") || it.startsWith("sem_$stem") }
        }
        assertTrue("ta functions with no conformance script: $missing", missing.isEmpty())
        assertTrue("names=${names.size}", names.size >= 100)
    }

    private fun check(name: String, expectation: String, result: ScriptResult): String? {
        val parts = expectation.split(Regex("\\s+"))
        return when (parts[0]) {
            "ok" -> if (result.ok) null else "$name: expected to run, refused ${result.error!!.code} ${result.error.messageEn}"
            "error" -> when {
                result.ok -> "$name: expected $expectation, but the script ran"
                result.error!!.code != parts[1] -> "$name: expected error ${parts[1]}, got ${result.error.code} (${result.error.messageEn})"
                else -> null
            }
            "plots" -> if (result.ok && result.plots.size == parts[1].toInt()) null else "$name: expected ${parts[1]} plots, got ${result.error?.code ?: result.plots.size}"
            "markers" -> {
                val marker = result.markers.getOrNull(parts[1].toInt())
                if (marker != null && marker.bars.size == parts[2].toInt()) null else "$name: expected marker ${parts[1]} on ${parts[2]} bars, got ${marker?.bars?.size}"
            }
            "backgrounds" -> if (result.backgrounds.getOrNull(parts[1].toInt())?.bars?.size == parts[2].toInt()) null else "$name: expected background ${parts[1]} on ${parts[2]} bars"
            "alerts" -> if (result.alerts.getOrNull(parts[1].toInt())?.bars?.size == parts[2].toInt()) null else "$name: expected alert ${parts[1]} on ${parts[2]} bars"
            "drawings" -> if (result.ok && result.drawings.size == parts[1].toInt()) null else "$name: expected ${parts[1]} drawings, got ${result.error?.code ?: result.drawings.size}"
            "trades" -> if (result.ok && (result.strategy?.closedCount ?: 0) == parts[1].toInt()) null else "$name: expected ${parts[1]} closed trades, got ${result.error?.code ?: result.strategy?.closedCount}"
            // `verdicts N` — how many short-form `signal(...)` calls the script made, and
            // `verdict I BARS side` — that one's bar count and which way it points (run Ω1).
            "verdicts" -> if (result.ok && result.verdicts.size == parts[1].toInt()) null
            else "$name: expected ${parts[1]} verdicts, got ${result.error?.code ?: result.verdicts.size}"
            "verdict" -> {
                val verdict = result.verdicts.getOrNull(parts[1].toInt())
                    ?: return "$name: no verdict ${parts[1]}"
                val side = if (verdict.buy) "buy" else "sell"
                when {
                    verdict.bars.size != parts[2].toInt() ->
                        "$name: verdict ${parts[1]} expected on ${parts[2]} bars, got ${verdict.bars.size}"
                    parts.size > 3 && side != parts[3] -> "$name: verdict ${parts[1]} expected $side to be ${parts[3]}"
                    else -> null
                }
            }
            "plot" -> {
                if (!result.ok) return "$name: expected a plot, refused ${result.error!!.code} ${result.error.messageEn}"
                val plot = result.plots.getOrNull(parts[1].toInt()) ?: return "$name: no plot ${parts[1]}"
                val bar = parts[2].toInt()
                val got = plot.values[bar]
                if (parts[3] == "na") {
                    if (got == null) null else "$name: plot ${parts[1]}[$bar] expected absent, got $got"
                } else {
                    val want = parts[3].toDouble()
                    if (got != null && abs(got - want) <= TOLERANCE * maxOf(1.0, abs(want))) null else "$name: plot ${parts[1]}[$bar] expected $want, got $got"
                }
            }
            else -> "$name: unknown expectation «$expectation»"
        }
    }

    /** The header rewritten with what the engine produced: the last three bars of every plot. */
    private fun record(text: String, result: ScriptResult): String {
        val body = text.lines().filterNot { it.trim().startsWith(EXPECT) }.joinToString("\n").trimStart('\n')
        val header = StringBuilder()
        if (!result.ok) {
            header.appendLine("$EXPECT error ${result.error!!.code}")
        } else {
            header.appendLine("$EXPECT ok")
            header.appendLine("$EXPECT plots ${result.plots.size}")
            result.plots.forEachIndexed { index, plot ->
                for (bar in listOf(series.bars.size - 3, series.bars.size - 2, series.bars.size - 1)) {
                    val value = plot.values[bar]
                    header.appendLine("$EXPECT plot $index $bar ${value?.let { formatValue(it) } ?: "na"}")
                }
            }
            result.markers.forEachIndexed { index, marker -> header.appendLine("$EXPECT markers $index ${marker.bars.size}") }
            result.backgrounds.forEachIndexed { index, background -> header.appendLine("$EXPECT backgrounds $index ${background.bars.size}") }
            result.alerts.forEachIndexed { index, alert -> header.appendLine("$EXPECT alerts $index ${alert.bars.size}") }
        }
        return header.toString() + body.trimEnd() + "\n"
    }

    private fun formatValue(value: Double): String = String.format(java.util.Locale.US, "%.10g", value)

    private fun scripts(): List<File> {
        val root = File("src/jvmTest/resources/conformance")
        assertTrue("run from the module directory: ${root.absolutePath}", root.isDirectory)
        return root.listFiles { file -> file.extension == "nama" }!!.sortedBy { it.name }
    }

    companion object {
        const val EXPECT = "// expect:"
        const val TOLERANCE = 1e-6

        /**
         * A deterministic 240-bar walk with volume, the same shape the golden chart uses: enough
         * bars for every default length (the longest, 55-bar Klinger, needs a hundred to settle).
         */
        fun fixture(): CandleSeries {
            var seed = 20_260_909L
            fun random(): Double {
                seed = (seed * 6364136223846793005L + 1442695040888963407L)
                return ((seed ushr 11).toDouble() / (1L shl 53).toDouble())
            }
            var close = 100.0
            val bars = List(240) { index ->
                val open = close
                close = (open + (random() - 0.5) * 2.4).coerceAtLeast(50.0)
                val high = maxOf(open, close) + random() * 1.2
                val low = minOf(open, close) - random() * 1.2
                Candle(t = 1_700_000_000L + index * 3_600L, o = open, h = high, l = low, c = close, v = 1_000.0 + random() * 9_000.0)
            }
            return CandleSeries(bars)
        }
    }
}
