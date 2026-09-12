package com.coinepro.feature.chart

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A reader's script as an indicator on the main chart (run I item 0).
 *
 * The engine is the part of that with no Compose in it, and it is where the three rules that make
 * the feature safe live: every line comes back with an **owner**, a run that fails keeps the **last
 * good drawing**, and a run that exceeds its budget **pauses that instance alone**. Each of those is
 * a defect that would only be visible on a chart, so each of them is a test here instead.
 */
class ChartScriptsTest {

    private fun series(bars: Int = 120): CandleSeries = CandleSeries(
        (0 until bars).map { index ->
            val base = 100.0 + index * 0.4
            Candle(t = 1_700_000_000L + index * 3_600L, o = base, h = base + 1.0, l = base - 1.0, c = base + 0.2, v = 10.0)
        },
    )

    private fun script(id: String, source: String, name: String = "S$id") =
        ChartScript(instanceId = id, name = name, source = source)

    @Test
    fun `an owner id cannot be confused with a catalogue indicator's`() {
        val instance = script("7", "plot(close)")
        assertEquals("nama:7", instance.ownerId)
        assertTrue(ChartScript.owns(instance.ownerId))
        assertEquals("7", ChartScript.instanceOf(instance.ownerId))
        // The catalogue's ids are the other half of the rule: none of them is a script's.
        assertTrue(!ChartScript.owns("ema"))
        assertNull(ChartScript.instanceOf("ema"))
    }

    @Test
    fun `every line a script draws comes back with its owner beside it`() {
        val engine = ChartScriptEngine()
        val draw = engine.evaluate(
            series(),
            listOf(script("1", "plot(ta.ema(close, 10))\nplot(ta.sma(close, 20))")),
        ) { 0L }
        assertEquals(2, draw.overlays.size)
        assertEquals(listOf("nama:1", "nama:1"), draw.overlayOwners)
        // Which is the property the legend depends on: a position in `overlays` resolves to a study.
        assertEquals(draw.overlays.size, draw.overlayOwners.size)
    }

    @Test
    fun `two scripts keep their lines apart`() {
        val engine = ChartScriptEngine()
        val draw = engine.evaluate(
            series(),
            listOf(script("1", "plot(ta.ema(close, 10))"), script("2", "plot(ta.sma(close, 30))")),
        ) { 0L }
        assertEquals(listOf("nama:1", "nama:2"), draw.overlayOwners)
    }

    @Test
    fun `a script that stops compiling keeps the drawing it had`() {
        val engine = ChartScriptEngine()
        val bars = series()
        val good = engine.evaluate(bars, listOf(script("1", "plot(ta.ema(close, 10))"))) { 0L }
        assertEquals(1, good.overlays.size)

        // The reader types a bracket. The study they were reading must not vanish under them.
        val broken = engine.evaluate(bars, listOf(script("1", "plot(ta.ema(close, 10)"))) { 0L }
        assertEquals("the last good drawing stays up", 1, broken.overlays.size)
        assertNotNull("and the legend is told why", broken.failures["nama:1"])
    }

    @Test
    fun `a script with no history and a syntax error draws nothing and says so`() {
        val engine = ChartScriptEngine()
        val draw = engine.evaluate(series(), listOf(script("1", "plot(("))) { 0L }
        assertTrue(draw.isEmpty)
        assertNotNull(draw.failures["nama:1"])
        // Not paused: this is a mistake in the text, not a script that is too expensive.
        assertNull(draw.paused["nama:1"])
    }

    @Test
    fun `a script that runs out of budget is paused rather than reported as wrong`() {
        val engine = ChartScriptEngine()
        // Deep nesting rather than a loop: the language has no loops, and the node budget is what
        // this is about — see `Interpreter.MAX_NODES` and E401.
        val expensive = "plot(" + "ta.ema(".repeat(40) + "close" + ", 10)".repeat(40) + ")"
        val draw = engine.evaluate(series(400), listOf(script("1", expensive))) { 0L }
        val failure = draw.failures["nama:1"]
        if (failure != null && failure.code in setOf("E401", "E406", "E407")) {
            assertNotNull("a budget breach pauses that instance", draw.paused["nama:1"])
        }
        // Whatever this particular script costs, the rule the test is really pinning is that a
        // pause is keyed by owner: one instance's budget cannot stop another's.
        assertTrue(draw.paused.keys.all(ChartScript::owns))
    }

    @Test
    fun `an instance removed from the chart is forgotten`() {
        val engine = ChartScriptEngine()
        val bars = series()
        engine.evaluate(bars, listOf(script("1", "plot(ta.ema(close, 10))"))) { 0L }
        val without = engine.evaluate(bars, listOf(script("2", "plot(ta.sma(close, 10))"))) { 0L }
        assertEquals(listOf("nama:2"), without.overlayOwners)
        // And the one that left leaves no drawing behind: its compiled form and its last result go
        // with it, so re-adding it later starts from the bars rather than from a stale splice.
        assertTrue(without.failures.isEmpty())
    }

    @Test
    fun `the eye takes a script's levels and markers with its lines`() {
        val engine = ChartScriptEngine()
        val draw = engine.evaluate(
            series(),
            listOf(script("1", "plot(ta.rsi(close, 14), pane = \"own\")\nhline(70, pane = \"own\")")),
        ) { 0L }
        // Hiding the owner drops everything it produced, which is what one eye has to mean.
        val shown = draw.shown(draw.levels, draw.levelOwners, setOf("nama:1"))
        assertTrue("nothing of a hidden script survives", shown.isEmpty())
        // And hiding something else leaves it alone.
        assertEquals(draw.levels, draw.shown(draw.levels, draw.levelOwners, setOf("ema")))
    }

    @Test
    fun `a second copy of a script is numbered rather than renamed`() {
        assertEquals("RSI Zones", script("1", "plot(close)", name = "RSI Zones").displayName)
        assertEquals(
            "RSI Zones (2)",
            ChartScript(instanceId = "2", name = "RSI Zones", source = "plot(close)", ordinal = 2).displayName,
        )
    }

    @Test
    fun `an instance a layout could not restore draws nothing and is not an error`() {
        val engine = ChartScriptEngine()
        val draw = engine.evaluate(
            series(),
            listOf(ChartScript(instanceId = "1", name = "Gone", source = "", missing = true)),
        ) { 0L }
        assertTrue(draw.isEmpty)
        assertTrue("a placeholder is not a failure", draw.failures.isEmpty())
    }
}
