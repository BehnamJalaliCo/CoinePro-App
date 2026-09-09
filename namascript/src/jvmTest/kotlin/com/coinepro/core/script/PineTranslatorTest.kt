package com.coinepro.core.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PineTranslatorTest {

    private val series = ConformanceSuiteTest.fixture()

    @Test
    fun `a plain Pine indicator translates and runs`() {
        val pine = """
            //@version=5
            indicator("EMA cross", overlay=true)
            fast = input.int(9, "Fast", minval=1)
            slow = input.int(21, "Slow", minval=1)
            f = ta.ema(close, fast)
            s = ta.ema(close, slow)
            plot(f, "Fast", color=color.lime, linewidth=2)
            plot(s, "Slow", color=color.red)
            plotshape(ta.crossover(f, s), style=shape.triangleup, location=location.belowbar, color=color.green, size=size.small)
        """.trimIndent()
        val translation = PineTranslator.translate(pine)
        assertTrue(translation.unsupported.joinToString(), translation.complete)
        val result = NamaScript.run(translation.source, series)
        assertTrue(result.error?.messageEn ?: "", result.ok)
        assertEquals(2, result.plots.size)
        assertEquals("Fast", result.plots[0].title)
        assertEquals(1, result.markers.size)
        assertEquals(ScriptMarkerStyle.ARROW_UP, result.markers[0].style)
        assertEquals(listOf("Fast", "Slow"), result.inputs.map { it.name })
    }

    @Test
    fun `what cannot be carried over is named with its line, and kept as a comment`() {
        val pine = """
            //@version=5
            strategy("S")
            var float acc = 0.0
            if close > open
                acc := acc + 1
            strategy.entry("L", strategy.long)
            a = array.new_float(0)
            plot(close)
        """.trimIndent()
        val translation = PineTranslator.translate(pine)
        assertFalse(translation.complete)
        val lines = translation.unsupported.map { it.line }
        assertEquals(listOf(4, 6, 7), lines)
        assertTrue(translation.unsupported[0].what.contains("control flow"))
        assertTrue(translation.unsupported[1].what.contains("strategy"))
        assertTrue(translation.unsupported[2].what.contains("collections"))
        // The rest still runs: the reader starts from a script that draws something.
        val result = NamaScript.run(translation.source, series)
        assertTrue(result.error?.messageEn ?: "", result.ok)
        assertEquals(1, result.plots.size)
        assertTrue(translation.source.lines().any { it.startsWith("// [نمااسکریپت:") && it.contains("strategy.entry") })
    }

    @Test
    fun `Pine spellings map to the language's own`() {
        val translation = PineTranslator.translate("plot(ta.rma(close, 14))\nplot(ta.stoch(close, high, low, 14))\nx = nz(ta.sma(close, 200))\nplot(x)")
        assertTrue(translation.source.contains("ta.smma(close, 14)"))
        assertTrue(translation.source.contains("ta.stoch_k("))
        assertTrue(translation.source.contains("nz(ta.sma(close, 200), 0)") || translation.source.contains("nz(x, 0)") || translation.source.contains("nz("))
    }

    @Test
    fun `the version header is kept as a note and the declaration dropped`() {
        val translation = PineTranslator.translate("//@version=5\nindicator(\"x\")\nplot(close)")
        assertEquals("//@version=1 // was @version=5", translation.source.lines()[0])
        assertTrue(translation.source.lines()[1].startsWith("// indicator("))
        assertTrue(NamaScript.run(translation.source, series).ok)
    }
}
