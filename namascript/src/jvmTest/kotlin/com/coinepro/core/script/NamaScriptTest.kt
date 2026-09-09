package com.coinepro.core.script

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.Indicators
import com.coinepro.core.chart.IndicatorsExt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The language's semantics, pinned.
 *
 * A scripting language is a promise that the same text means the same thing next release. Every
 * case here is a rule somebody could reasonably assume the other way round, and the assumption is
 * settled once, here, rather than in each reader's head.
 */
class NamaScriptTest {

    /* ---------------------------------------------------------------- fixtures */

    private fun bars(count: Int = 60, price: (Int) -> Double = { 100.0 + it }): CandleSeries =
        CandleSeries(
            List(count) { index ->
                val close = price(index)
                Candle(
                    t = 1_700_000_000L + index * 3_600L,
                    o = close - 0.5,
                    h = close + 1.0,
                    l = close - 1.0,
                    c = close,
                    v = 1_000.0 + index,
                )
            },
        )

    private fun run(source: String, series: CandleSeries = bars()) = NamaScript.run(source, series)

    /* ---------------------------------------------------------------- basics */

    @Test
    fun `a plotted constant is a flat line over every bar`() {
        val result = run("plot(42, title = \"forty two\")")

        assertTrue(result.ok)
        val plot = result.plots.single()
        assertEquals("forty two", plot.title)
        assertEquals(60, plot.values.size)
        assertEquals(42.0, plot.values[0]!!, 1e-9)
        assertEquals(42.0, plot.values[59]!!, 1e-9)
    }

    @Test
    fun `a scalar broadcasts against a series`() {
        val result = run("plot(close * 2)")

        val plot = result.plots.single()
        assertEquals(200.0, plot.values[0]!!, 1e-9)
        assertEquals(318.0, plot.values[59]!!, 1e-9)
    }

    @Test
    fun `ta functions agree exactly with the chart's own indicators`() {
        // The whole reason `ta.` delegates rather than reimplements. A second implementation
        // eventually disagrees with the first, and the reader is right to trust neither.
        val series = bars()
        val result = NamaScript.run("plot(ta.ema(close, 21))", series)
        val expected = Indicators.ema(series.close, 21)

        val plot = result.plots.single()
        for (index in 0 until series.bars.size) {
            assertEquals("bar $index", expected[index], plot.values[index])
        }
    }

    /* ---------------------------------------------------------------- offsets */

    @Test
    fun `an offset is absent before the series begins, never clamped to the first bar`() {
        // Clamping is how a script reports a crossover on the very first bar of every chart it is
        // ever run on.
        val result = run("plot(close[3])")
        val plot = result.plots.single()

        assertNull(plot.values[0])
        assertNull(plot.values[2])
        assertEquals(100.0, plot.values[3]!!, 1e-9)
    }

    @Test
    fun `an offset on a constant is the constant`() {
        val result = run("x = 5\nplot(x[10])")
        assertEquals(5.0, result.plots.single().values[0]!!, 1e-9)
    }

    /* ---------------------------------------------------------------- absence */

    @Test
    fun `division by zero leaves a hole rather than an infinity`() {
        // An infinity poisons every later calculation and draws a chart with no visible range.
        val result = run("plot(close / (close - close))")
        val plot = result.plots.single()

        assertTrue((0 until 60).all { plot.values[it] == null })
    }

    @Test
    fun `an indicator over a gapped series starts after the gap rather than absorbing it`() {
        // `close[5]` has five empty bars at the front. A running-sum SMA fed a zero there would
        // return a number, and that number would be wrong for the whole window.
        val result = run("plot(ta.sma(close[5], 10))")
        val plot = result.plots.single()

        // Five bars of shift, then ten of warm-up: the first real value is at bar 14.
        assertNull(plot.values[13])
        assertNotNull(plot.values[14])
        // And it is the mean of the five-bars-ago closes, not of anything containing a zero.
        val expected = (0..9).sumOf { 100.0 + (14 - 5 - it) } / 10.0
        assertEquals(expected, plot.values[14]!!, 1e-9)
    }

    @Test
    fun `a condition that could not be decided has not fired`() {
        val result = run("marker(close[80] > 0, title = \"never\")")
        assertEquals(0, result.markers.single().bars.size)
    }

    /* ---------------------------------------------------------------- crosses */

    @Test
    fun `a crossover fires once, on the bar the lines actually cross`() {
        // A V: falling for thirty bars, then rising. A fast average crosses a slow one exactly once.
        val series = bars(60) { index -> if (index < 30) 200.0 - index * 2.0 else 140.0 + (index - 30) * 2.0 }
        val result = NamaScript.run(
            """
            fast = ta.ema(close, 5)
            slow = ta.ema(close, 20)
            marker(ta.crossover(fast, slow), title = "up", style = "up")
            """.trimIndent(),
            series,
        )

        assertEquals(1, result.markers.single().bars.size)
    }

    @Test
    fun `two lines that touch without separating do not cross`() {
        // Equal on both bars is not a cross. Without that rule a flat pair fires on every bar.
        val result = run("marker(ta.crossover(close, close), title = \"never\")")
        assertEquals(0, result.markers.single().bars.size)
    }

    /* ---------------------------------------------------------------- panes */

    @Test
    fun `an oscillator gets its own pane and a moving average does not`() {
        // Decided by measuring the values against the price, not by reading the title — otherwise
        // anything a reader called `myRSI` lands in the wrong place, and an RSI over the candles
        // flattens the price axis to a line.
        val result = run("plot(ta.rsi(close, 14))\nplot(ta.sma(close, 10))")

        assertTrue("rsi belongs in its own pane", result.plots[0].ownPane)
        assertFalse("a moving average belongs over the price", result.plots[1].ownPane)
    }

    @Test
    fun `the script can override the pane it was given`() {
        val result = run("plot(ta.rsi(close, 14), pane = \"price\")")
        assertFalse(result.plots.single().ownPane)
    }

    /* ---------------------------------------------------------------- setups */

    @Test
    fun `a signal is taken from the last bar it fired on, not the first`() {
        val result = run(
            """
            entry = close
            stop = close - 5
            signal(close > 120, entry = entry, stop = stop, target = close + 10)
            """.trimIndent(),
        )

        val setup = assertNotNull(result.setup).let { result.setup!! }
        assertEquals(59, setup.barIndex)
        assertEquals(159.0, setup.entry, 1e-9)
        assertEquals(154.0, setup.stop, 1e-9)
        assertEquals(2.0, setup.riskReward!!, 1e-9)
    }

    @Test
    fun `a long whose stop is above its entry is refused`() {
        // It would render as a negative risk and a nonsense reward ratio.
        val result = run("signal(true, entry = close, stop = close + 5)")

        assertFalse(result.ok)
        assertTrue(result.error!!.message.contains("حد ضرر"))
    }

    /* ---------------------------------------------------------------- inputs */

    @Test
    fun `an input declares itself and a supplied value wins over the default`() {
        val source = "length = input(14, title = \"دوره\", min = 2, max = 200)\nplot(ta.sma(close, length))"
        val plain = NamaScript.run(source, bars())
        val overridden = NamaScript.run(source, bars(), mapOf("دوره" to 30.0))

        assertEquals(14.0, plain.inputs.single().value, 1e-9)
        assertEquals(30.0, overridden.inputs.single().value, 1e-9)
        // Warm-up moves with it, which is the visible proof the value reached the indicator.
        assertNull(overridden.plots.single().values[28])
        assertNotNull(overridden.plots.single().values[29])
    }

    @Test
    fun `a supplied value outside the declared range is clamped, not obeyed`() {
        // A value stored against an earlier revision of the script must not take it out of bounds.
        val result = NamaScript.run(
            "length = input(14, title = \"دوره\", min = 2, max = 50)\nplot(ta.sma(close, length))",
            bars(),
            mapOf("دوره" to 9_999.0),
        )
        assertEquals(50.0, result.inputs.single().value, 1e-9)
    }

    /* ---------------------------------------------------------------- errors */

    @Test
    fun `every failure carries a line to put a caret on`() {
        val cases = listOf(
            "plot(",                       // unclosed call
            "x := 5",                      // reassigning something never declared
            "plot(unknown_name)",          // undefined
            "plot(ta.nosuch(close, 5))",   // no such function
            "close = 5",                   // redefining a built-in
            "plot(close > 5)",             // a condition where a number is required
        )
        for (source in cases) {
            val result = NamaScript.run(source, bars())
            assertFalse("«$source» should not run", result.ok)
            assertTrue("«$source» should name a line", result.error!!.line >= 1)
            assertTrue("«$source» should say something", result.error!!.message.isNotBlank())
        }
    }

    @Test
    fun `a syntax error is found without running the series`() {
        assertNull(NamaScript.check("plot(ta.sma(close, 14))"))
        assertEquals(1, NamaScript.check("plot(ta.sma(close, 14)")!!.line)
    }

    @Test
    fun `an empty chart is refused with a sentence rather than a crash`() {
        val result = NamaScript.run("plot(close)", CandleSeries(emptyList()))

        assertFalse(result.ok)
        assertTrue(result.error!!.message.contains("کندل"))
    }

    @Test
    fun `a runaway script is stopped by the node budget`() {
        // There are no loops in the language, so the only way to spend unbounded time is a very
        // large expression. It is capped rather than trusted.
        val huge = "plot(" + "close + ".repeat(60_000) + "close)"
        val result = NamaScript.run(huge, bars())
        assertFalse(result.ok)
    }

    /* ---------------------------------------------------------------- comments and layout */

    @Test
    fun `comments, blank lines and a continuation do not change meaning`() {
        val result = run(
            """
            // a moving average
            length = 10

            average = ta.sma( \
                close, \
                length \
            )
            plot(average, title = "SMA")
            """.trimIndent(),
        )

        assertTrue(result.error?.message ?: "", result.ok)
        assertEquals("SMA", result.plots.single().title)
    }

    /* ---------------------------------------------------------------- second-wave builtins */

    @Test
    fun `the second wave of ta functions delegates to the chart's indicators too`() {
        val series = bars()
        val cases = mapOf(
            "ta.williams_r(14)" to Indicators.williamsR(series.high, series.low, series.close, 14),
            "ta.obv()" to Indicators.obv(series.close, series.volume),
            "ta.keltner_upper(20, 2)" to Indicators.keltner(series.high, series.low, series.close, 20, 2.0).upper,
            "ta.momentum(close, 10)" to IndicatorsExt.momentum(series.close, 10),
            "ta.smma(close, 14)" to IndicatorsExt.smma(series.close, 14),
            "ta.bb_percent(close, 20, 2)" to IndicatorsExt.bollingerPercent(series.close, 20, 2.0),
            "ta.ultimate(7, 14, 28)" to IndicatorsExt.ultimateOscillator(series.high, series.low, series.close, 7, 14, 28),
            "ta.chop(14)" to Indicators.choppiness(series.high, series.low, series.close, 14),
        )
        for ((source, expected) in cases) {
            val result = NamaScript.run("plot($source)", series)
            assertTrue("$source: ${result.error?.message}", result.ok)
            val plot = result.plots.single()
            for (index in 0 until series.bars.size) {
                assertEquals("$source bar $index", expected[index], plot.values[index])
            }
        }
    }

    @Test
    fun `volume builtins are empty rather than zero on a feed with no volume`() {
        val silent = CandleSeries(bars().bars.map { it.copy(v = null) })
        val result = NamaScript.run("plot(ta.obv())", silent)
        assertTrue(result.error?.message ?: "", result.ok)
        assertTrue(result.plots.single().values.toList().all { it == null })
    }

    @Test
    fun `rising and falling read consecutive bars`() {
        // Prices go up one a bar, so every bar from the third is rising over two and none is falling.
        val result = run(
            """
            marker(ta.rising(close, 2), title = "up", style = "up")
            marker(ta.falling(close, 2), title = "down", style = "down")
            """.trimIndent(),
        )
        assertTrue(result.error?.message ?: "", result.ok)
        val up = result.markers.single { it.title == "up" }
        val down = result.markers.single { it.title == "down" }
        assertEquals(58, up.bars.size)
        assertEquals(0, down.bars.size)
    }

    @Test
    fun `barssince counts from the last bar the condition held`() {
        // The condition fires on bar 10 only.
        val result = run("plot(ta.barssince(bar_index == 10))")
        assertTrue(result.error?.message ?: "", result.ok)
        val values = result.plots.single().values
        assertNull(values[9])
        assertEquals(0.0, values[10]!!, 1e-9)
        assertEquals(5.0, values[15]!!, 1e-9)
        assertEquals(49.0, values[59]!!, 1e-9)
    }

    @Test
    fun `valuewhen holds the source from the bar the condition last held`() {
        val result = run("plot(ta.valuewhen(bar_index == 10 or bar_index == 20, close, 0))")
        assertTrue(result.error?.message ?: "", result.ok)
        val values = result.plots.single().values
        assertNull(values[9])
        assertEquals(110.0, values[15]!!, 1e-9)
        assertEquals(120.0, values[25]!!, 1e-9)
        val previous = run("plot(ta.valuewhen(bar_index == 10 or bar_index == 20, close, 1))")
        assertEquals(110.0, previous.plots.single().values[25]!!, 1e-9)
    }

    @Test
    fun `cum is a running total`() {
        val result = run("plot(ta.cum(1))")
        assertEquals(60.0, result.plots.single().values[59]!!, 1e-9)
    }

    @Test
    fun `a pivot is reported on the bar that confirms it and nowhere else`() {
        // A single peak at bar 30 in an otherwise flat series.
        val series = bars { index -> if (index == 30) 150.0 else 100.0 }
        val result = NamaScript.run("plot(ta.pivothigh(3, 3))", series)
        assertTrue(result.error?.message ?: "", result.ok)
        val values = result.plots.single().values
        assertEquals(151.0, values[33]!!, 1e-9)
        assertEquals(1, values.toList().count { it != null })
    }

    private fun <T> assertNotNull(value: T?): T {
        org.junit.Assert.assertNotNull(value)
        return value!!
    }
}
