package com.coinepro.core.chart

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Indicators that read other indicators.
 *
 * The tests that matter here are the ones about the *graph*: a chain that produces the same numbers
 * as computing the two steps by hand, a loop refused instead of recursed, and a shared node
 * evaluated once. The arithmetic itself is already covered against the web terminal in
 * `IndicatorParityTest`; what is new is the plumbing around it.
 */
class IndicatorChainTest {

    private fun series(size: Int = 120): CandleSeries = CandleSeries(
        (0 until size).map { index ->
            val close = 100.0 + kotlin.math.sin(index / 7.0) * 6 + index * 0.05
            Candle(
                t = 1_700_000_000L + index * 3600,
                o = close - 0.3,
                h = close + 0.8,
                l = close - 0.9,
                c = close,
                v = 500.0 + index,
            )
        },
    )

    private fun valuesOf(outcome: ChainOutcome, nodeId: String, output: String = IndicatorChain.VALUE): DoubleArray {
        val ready = outcome as ChainOutcome.Ready
        return ready.outputs.getValue(nodeId).getValue(output)
    }

    @Test
    fun `an EMA of an SMA is the EMA of the SMA and not of the close`() {
        val bars = series()
        val nodes = listOf(
            ChainedIndicator("base", "sma", period = 10),
            ChainedIndicator("top", "ema", period = 5, source = IndicatorSource.Output("base")),
        )
        val outcome = IndicatorChain.evaluate(bars, nodes)
        assertTrue("a two-link chain must resolve", outcome is ChainOutcome.Ready)

        // The same two steps done by hand: the SMA, its warm-up cut off, the EMA over what is left.
        val smaLine = Indicators.sma(bars.close, 10)
        val head = (0 until bars.size).first { smaLine.isPresent(it) }
        val trimmed = DoubleArray(bars.size - head) { smaLine.raw(it + head) }
        val expected = Indicators.ema(trimmed, 5)

        val actual = valuesOf(outcome, "top")
        val last = bars.size - 1
        assertTrue("the chained line must have a value at the right-hand edge", actual[last].isFinite())
        assertEquals(expected.raw(last - head), actual[last], 1e-9)

        // And it is emphatically not the EMA of the close, which is what a chain that ignored its
        // source would draw.
        val plain = Indicators.ema(bars.close, 5)
        assertTrue(
            "the chained value must differ from an EMA of the close",
            abs(plain.raw(last) - actual[last]) > 1e-6,
        )
    }

    @Test
    fun `a chained average survives its source's warm-up instead of going NaN`() {
        // The trap the whole trimming exists for: a running-sum average fed one NaN produces NaN
        // for every bar after it, so a naive chain draws nothing at all past the warm-up.
        val bars = series()
        val nodes = listOf(
            ChainedIndicator("base", "sma", period = 30),
            ChainedIndicator("top", "sma", period = 10, source = IndicatorSource.Output("base")),
        )
        val values = valuesOf(IndicatorChain.evaluate(bars, nodes), "top")
        assertTrue("the last bar must carry a real value", values[bars.size - 1].isFinite())
        assertTrue("the warm-up must stay absent", !values[0].isFinite())
    }

    @Test
    fun `a loop is refused with the nodes it runs through rather than recursed`() {
        val nodes = listOf(
            ChainedIndicator("a", "ema", period = 10, source = IndicatorSource.Output("b")),
            ChainedIndicator("b", "sma", period = 10, source = IndicatorSource.Output("a")),
        )
        val outcome = IndicatorChain.evaluate(series(), nodes)
        val refusal = outcome as? ChainOutcome.Refused
        assertNotNull("a cycle must be refused", refusal)
        assertEquals(ChainRefusal.CYCLE, refusal!!.reason)
        assertTrue("both nodes in the loop must be named", refusal.nodeIds.containsAll(listOf("a", "b")))
        assertTrue("the refusal must say something", refusal.message.isNotBlank())
    }

    @Test
    fun `a node that reads itself is a loop too`() {
        val nodes = listOf(ChainedIndicator("only", "ema", period = 9, source = IndicatorSource.Output("only")))
        val outcome = IndicatorChain.evaluate(series(), nodes) as ChainOutcome.Refused
        assertEquals(ChainRefusal.CYCLE, outcome.reason)
    }

    @Test
    fun `a diamond evaluates the shared node once`() {
        // Two indicators reading one base, and a fourth reading both branches. The base must appear
        // exactly once in the evaluation order: it is the difference between one pass over the
        // series and two, on every redraw.
        val nodes = listOf(
            ChainedIndicator("base", "sma", period = 10),
            ChainedIndicator("left", "ema", period = 5, source = IndicatorSource.Output("base")),
            ChainedIndicator("right", "wma", period = 5, source = IndicatorSource.Output("base")),
            ChainedIndicator("tip", "ema", period = 3, source = IndicatorSource.Output("left")),
        )
        val ready = IndicatorChain.evaluate(series(), nodes) as ChainOutcome.Ready
        assertEquals("every node is evaluated", 4, ready.order.size)
        assertEquals("the shared base exactly once", 1, ready.order.count { it == "base" })
        assertTrue("a source is always evaluated before what reads it", ready.order.indexOf("base") < ready.order.indexOf("left"))
        assertTrue(ready.order.indexOf("left") < ready.order.indexOf("tip"))
    }

    @Test
    fun `a chain deeper than the cap is refused rather than truncated`() {
        val nodes = ArrayList<ChainedIndicator>()
        nodes += ChainedIndicator("n0", "ema", period = 5)
        for (step in 1..IndicatorChain.MAX_DEPTH) {
            nodes += ChainedIndicator("n$step", "ema", period = 5, source = IndicatorSource.Output("n${step - 1}"))
        }
        val outcome = IndicatorChain.evaluate(series(), nodes) as ChainOutcome.Refused
        assertEquals(ChainRefusal.TOO_DEEP, outcome.reason)

        // One link shallower is fine, which is what makes the cap a cap and not an off-by-one.
        val allowed = nodes.dropLast(1)
        assertTrue(IndicatorChain.evaluate(series(), allowed) is ChainOutcome.Ready)
    }

    @Test
    fun `an indicator that reads high and low cannot take another indicator as its source`() {
        // ATR reads three columns. Feeding it one series would quietly use it as the close and
        // leave the real high and low in place, which is not an ATR of anything.
        val nodes = listOf(
            ChainedIndicator("base", "ema", period = 10),
            ChainedIndicator("top", "atr", period = 14, source = IndicatorSource.Output("base")),
        )
        val outcome = IndicatorChain.evaluate(series(), nodes) as ChainOutcome.Refused
        assertEquals(ChainRefusal.NOT_CHAINABLE, outcome.reason)
        assertTrue("atr must not be offered as a chain link", !IndicatorChain.canChain("atr"))
        assertTrue("nor may a volume study", !IndicatorChain.canChain("obv"))
        assertTrue("nor a structure study", !IndicatorChain.canChain("zigzag"))
    }

    @Test
    fun `a missing source and an unknown output are each refused on their own terms`() {
        val missing = IndicatorChain.evaluate(
            series(),
            listOf(ChainedIndicator("top", "ema", period = 5, source = IndicatorSource.Output("gone"))),
        ) as ChainOutcome.Refused
        assertEquals(ChainRefusal.MISSING_SOURCE, missing.reason)

        val unknown = IndicatorChain.evaluate(
            series(),
            listOf(
                ChainedIndicator("base", "rsi", period = 14),
                ChainedIndicator("top", "ema", period = 5, source = IndicatorSource.Output("base", "signal")),
            ),
        ) as ChainOutcome.Refused
        assertEquals(ChainRefusal.UNKNOWN_OUTPUT, unknown.reason)

        val duplicate = IndicatorChain.evaluate(
            series(),
            listOf(ChainedIndicator("same", "ema", 5), ChainedIndicator("same", "sma", 5)),
        ) as ChainOutcome.Refused
        assertEquals(ChainRefusal.DUPLICATE_NODE, duplicate.reason)
    }

    @Test
    fun `a named output is followed rather than the main line`() {
        val bars = series()
        val nodes = listOf(
            ChainedIndicator("macd", "macd"),
            ChainedIndicator("onSignal", "sma", period = 4, source = IndicatorSource.Output("macd", IndicatorChain.SIGNAL)),
            ChainedIndicator("onLine", "sma", period = 4, source = IndicatorSource.Output("macd", IndicatorChain.VALUE)),
        )
        val outcome = IndicatorChain.evaluate(bars, nodes)
        val onSignal = valuesOf(outcome, "onSignal")
        val onLine = valuesOf(outcome, "onLine")
        val last = bars.size - 1
        assertTrue(onSignal[last].isFinite() && onLine[last].isFinite())
        assertTrue(
            "reading the signal must not produce the same series as reading the main line",
            abs(onSignal[last] - onLine[last]) > 1e-9,
        )
    }

    @Test
    fun `an average of an oscillator is drawn in the oscillator's pane and not over the price`() {
        // The trap: an EMA is a price-scale indicator, so the obvious rule puts an EMA of an RSI
        // over the candles, where a value of 46 drags a gold chart's axis to the floor.
        val bars = series()
        val nodes = listOf(
            ChainedIndicator("rsi", "rsi", period = 14),
            ChainedIndicator("smoothed", "ema", period = 5, source = IndicatorSource.Output("rsi")),
        )
        val ready = IndicatorChain.evaluate(bars, nodes) as ChainOutcome.Ready
        val plot = IndicatorChain.plot(ready, nodes)
        assertTrue("nothing from this chain belongs on the price axis", plot.priceLines.isEmpty())
        assertEquals("one pane, owned by the RSI", 1, plot.panes.size)
        assertEquals("both lines are drawn in it", 2, plot.panes.first().lines.size)
        assertTrue(
            "the pane keeps the RSI's reference levels",
            plot.panes.first().levels.map { it.price }.containsAll(listOf(70.0, 30.0)),
        )
    }

    @Test
    fun `an average of an average stays on the price axis`() {
        val bars = series()
        val nodes = listOf(
            ChainedIndicator("base", "sma", period = 20),
            ChainedIndicator("top", "ema", period = 5, source = IndicatorSource.Output("base")),
        )
        val ready = IndicatorChain.evaluate(bars, nodes) as ChainOutcome.Ready
        val plot = IndicatorChain.plot(ready, nodes)
        assertEquals(2, plot.priceLines.size)
        assertTrue(plot.panes.isEmpty())
        val label = plot.priceLines.last().label
        assertNotNull(label)
        assertTrue("the label must say what it is reading: $label", label!!.contains("SMA 20"))
    }

    @Test
    fun `an indicator that cannot be chained still passes through untouched`() {
        val bars = series()
        val nodes = listOf(ChainedIndicator("atr", "atr", period = 14))
        val ready = IndicatorChain.evaluate(bars, nodes) as ChainOutcome.Ready
        assertEquals(listOf("atr"), ready.unchained)
        assertTrue("and produces nothing through the chain", ready.outputs.isEmpty())
    }

    @Test
    fun `every chainable indicator computes real values from the close`() {
        // Behaviour rather than a count: whatever is in the table must actually produce a series
        // with a value near the right-hand edge, or it is a source a reader can pick that draws
        // nothing.
        val bars = series(400)
        for ((id, spec) in IndicatorChain.CHAINABLE) {
            val nodes = listOf(ChainedIndicator("only", id))
            val outcome = IndicatorChain.evaluate(bars, nodes)
            val ready = outcome as? ChainOutcome.Ready
            assertNotNull("$id was refused", ready)
            val produced = ready!!.outputs["only"]
            assertNotNull("$id produced nothing", produced)
            assertEquals("$id must publish what it claims", spec.outputs.toSet(), produced!!.keys)
            for (name in spec.outputs) {
                val values = produced.getValue(name)
                assertEquals("$id/$name must align with the bars", bars.size, values.size)
                assertTrue(
                    "$id/$name has no value in the last twenty bars",
                    (bars.size - 20 until bars.size).any { values[it].isFinite() },
                )
            }
        }
    }

    @Test
    fun `every chainable indicator is a real catalogue row`() {
        for (id in IndicatorChain.CHAINABLE.keys) {
            assertNotNull(
                "$id can be chained but is not in the catalogue, so nothing can switch it on",
                ChartCatalog.INDICATORS.firstOrNull { it.id == id },
            )
        }
    }

    @Test
    fun `a source column other than the close is actually read`() {
        val bars = series()
        val onClose = valuesOf(IndicatorChain.evaluate(bars, listOf(ChainedIndicator("a", "sma", 10))), "a")
        val onHigh = valuesOf(
            IndicatorChain.evaluate(
                bars,
                listOf(ChainedIndicator("a", "sma", 10, source = IndicatorSource.Bars(BarField.HIGH))),
            ),
            "a",
        )
        val last = bars.size - 1
        assertTrue("an average of the highs must sit above one of the closes", onHigh[last] > onClose[last])
    }

    @Test
    fun `an unknown indicator is refused instead of silently dropped`() {
        val outcome = IndicatorChain.evaluate(series(), listOf(ChainedIndicator("x", "no_such_indicator")))
        assertEquals(ChainRefusal.UNKNOWN_INDICATOR, (outcome as ChainOutcome.Refused).reason)
    }

    @Test
    fun `an empty series produces an empty chain rather than an exception`() {
        val nodes = listOf(
            ChainedIndicator("base", "sma", 10),
            ChainedIndicator("top", "ema", 5, source = IndicatorSource.Output("base")),
        )
        val ready = IndicatorChain.evaluate(CandleSeries.EMPTY, nodes) as ChainOutcome.Ready
        assertEquals(2, ready.order.size)
        assertEquals(0, ready.outputs.getValue("top").getValue(IndicatorChain.VALUE).size)
        assertNull(IndicatorChain.plot(ready, nodes).panes.firstOrNull())
    }
}
