package com.coinepro.core.script

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * The shipped content, checked against the language that has to run it.
 *
 * A preset that does not compile is worse than no preset: it is the first thing a reader opens, and
 * it teaches them that the language is broken. A reference entry naming a function the interpreter
 * does not have teaches them an error in their own hand. Both are caught here rather than on a
 * device.
 */
class ScriptLibraryTest {

    /**
     * A series with shape.
     *
     * A straight ramp would let a broken indicator pass — every oscillator pins to one end of its
     * range and every cross never happens. This one turns, so a crossover has somewhere to occur
     * and a Bollinger band has a width that changes.
     */
    private fun waves(count: Int = 400): CandleSeries = CandleSeries(
        List(count) { index ->
            val base = 100.0 + sin(index / 9.0) * 6 + sin(index / 31.0) * 14 + index * 0.03
            Candle(
                t = 1_700_000_000L + index * 3_600L,
                o = base - 0.4,
                h = base + 1.1,
                l = base - 1.2,
                c = base,
                v = 900.0 + (index % 17) * 40,
            )
        },
    )

    @Test
    fun `every shipped preset runs`() {
        for (preset in ScriptPresets.ALL) {
            val result = NamaScript.run(preset.source, waves())
            assertNull(
                "«${preset.title}» با خطا متوقف شد: ${result.error?.message} (خط ${result.error?.line})",
                result.error,
            )
        }
    }

    @Test
    fun `every shipped preset draws something`() {
        // A preset that runs and draws nothing looks broken to the only person it was written for.
        for (preset in ScriptPresets.ALL) {
            val result = NamaScript.run(preset.source, waves())
            assertFalse("«${preset.title}» چیزی رسم نکرد", result.isEmpty)
        }
    }

    @Test
    fun `every preset id is unique and stable`() {
        val ids = ScriptPresets.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (id in ids) assertNotNull(ScriptPresets.byId(id))
    }

    @Test
    fun `the blank script runs`() {
        assertNull(NamaScript.run(ScriptPresets.BLANK, waves()).error)
    }

    @Test
    fun `every preset declares inputs the panel can offer`() {
        // Not every one has to, but the ones that name `input(` must produce a matching control —
        // an input the panel never shows is an input the reader cannot reach.
        for (preset in ScriptPresets.ALL.filter { "input(" in it.source }) {
            val result = NamaScript.run(preset.source, waves())
            assertTrue("«${preset.title}» ورودی اعلام نکرد", result.inputs.isNotEmpty())
        }
    }

    @Test
    fun `every function in the reference exists in the interpreter`() {
        // The reference is a promise. Calling each name with the arity its own signature shows
        // proves the promise is kept — a missing function fails with «تابع ... وجود ندارد», which
        // is the one error this test is looking for.
        val series = waves()
        for (group in ScriptReference.GROUPS) {
            for (function in group.functions) {
                val result = NamaScript.run(function.callable(), series)
                val message = result.error?.message.orEmpty()
                assertFalse(
                    "${function.signature} در مفسر وجود ندارد",
                    "وجود ندارد" in message,
                )
            }
        }
    }

    @Test
    fun `every built-in series in the reference resolves`() {
        val series = waves()
        for (function in ScriptReference.SERIES) {
            val result = NamaScript.run("plot(${function.signature} + 0)", series)
            assertNull("${function.signature} شناخته نشد", result.error)
        }
    }

    @Test
    fun `every colour the reference offers is one the language accepts`() {
        val series = waves()
        for (name in ScriptReference.COLOUR_NAMES) {
            val result = NamaScript.run("plot(close, color = $name)", series)
            assertNull("$name پذیرفته نشد", result.error)
        }
    }

    @Test
    fun `every lesson example runs`() {
        val series = waves()
        for (lesson in ScriptLessons.ALL) {
            val example = lesson.example ?: continue
            val result = NamaScript.run(example, series)
            assertNull(
                "مثال درس «${lesson.title}» خطا داد: ${result.error?.message}",
                result.error,
            )
        }
    }

    @Test
    fun `the lessons are ordered and uniquely identified`() {
        val ids = ScriptLessons.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    /**
     * A signature turned into something runnable.
     *
     * The output builtins already are; the rest are wrapped in a `plot(...)` so the interpreter has
     * a reason to evaluate them. A bare expression statement would be parsed and then discarded.
     */
    private fun ScriptFunction.callable(): String = when {
        signature.startsWith("plot") || signature.startsWith("hline") ||
            signature.startsWith("marker") || signature.startsWith("signal") ||
            signature.startsWith("log") || signature.startsWith("input") -> preamble() + signature
        else -> preamble() + "plot($signature)"
    }

    /** What the example signatures assume already exists. */
    private fun preamble(): String = """
        a = ta.ema(close, 9)
        b = ta.ema(close, 21)
        x = close
        condition = ta.crossover(a, b)
        entry = close
        stop = close - ta.atr(14)
        target = close + ta.atr(14) * 2
        series = ta.rsi(close, 14)

    """.trimIndent() + "\n"
}
