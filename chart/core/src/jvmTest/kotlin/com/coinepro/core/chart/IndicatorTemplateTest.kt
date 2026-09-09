package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Indicator templates: the set, its periods and its chain sources, and nothing else.
 *
 * The tests are mostly about what does **not** survive the round trip — a source spelling this
 * build cannot read, an indicator it does not have, a chain whose root went missing. Every one of
 * those has a wrong answer that looks right on screen, which is why each is asserted rather than
 * trusted.
 */
class IndicatorTemplateTest {

    @Test
    fun `a set with a chain in it round-trips through the store's three columns`() {
        val nodes = listOf(
            ChainedIndicator("ema", "ema", period = 20),
            ChainedIndicator("rsi", "rsi", period = 14, source = IndicatorSource.Output("ema")),
            ChainedIndicator("sma", "sma", period = 50, source = IndicatorSource.Bars(BarField.HL2)),
        )
        val stored = IndicatorTemplates.toStorage(nodes)
        assertEquals(listOf("ema", "rsi", "sma"), stored.indicators)
        assertEquals(mapOf("ema" to 20, "rsi" to 14, "sma" to 50), stored.periods)
        assertEquals("a node on the close needs no source entry", setOf("rsi", "sma"), stored.sources.keys)
        assertEquals(
            IndicatorTemplates.nodesFrom(stored.indicators, stored.periods, stored.sources),
            nodes,
        )
    }

    @Test
    fun `a template written before chaining existed reads back unchanged`() {
        // The store has held id lists and period maps since before any of this. Those rows must
        // come back as ordinary indicators on the close, not as anything needing repair.
        val nodes = IndicatorTemplates.nodesFrom(listOf("sma", "macd"), mapOf("sma" to 200))
        assertEquals(2, nodes.size)
        assertEquals(200, nodes.first().period)
        assertTrue(nodes.all { it.source == IndicatorSource.CANDLES })
        assertTrue("and nothing is written back into the source column", IndicatorTemplates.toStorage(nodes).sources.isEmpty())
    }

    @Test
    fun `applying a set yields indicators and periods and says nothing about the chart`() {
        val applied = IndicatorTemplates.apply(
            indicators = listOf("ema", "rsi"),
            periods = mapOf("ema" to 20, "rsi" to 9),
            sources = mapOf("rsi" to "@ema"),
        )
        assertNull(applied.refusal)
        assertEquals(2, applied.indicators.size)
        // The plain path can only carry the roots: a chained RSI is not an indicator on the close
        // and must not be switched on as one, or it would draw twice and read the wrong series.
        assertEquals(setOf("ema"), applied.activeIds)
        assertEquals(mapOf("ema" to 20), applied.periods)
        assertTrue(applied.dropped.isEmpty())
    }

    @Test
    fun `an indicator this build does not have takes its dependants with it`() {
        val applied = IndicatorTemplates.apply(
            indicators = listOf("no_such_indicator", "ema"),
            sources = mapOf("ema" to "@no_such_indicator"),
        )
        assertTrue("both must be reported", applied.dropped.containsAll(listOf("no_such_indicator", "ema")))
        assertTrue("and nothing is left pointing at a hole", applied.indicators.isEmpty())
    }

    @Test
    fun `a source spelling this build cannot read drops the node rather than falling back to close`() {
        // The failure this avoids: an indicator that looks computed, is labelled as the reader's,
        // and is quietly reading something they did not choose.
        val applied = IndicatorTemplates.apply(indicators = listOf("ema"), sources = mapOf("ema" to "typical3"))
        assertEquals(listOf("ema"), applied.dropped)
        assertTrue(applied.indicators.isEmpty())
        assertNull(IndicatorTemplates.parseSource("typical3"))
        assertNull(IndicatorTemplates.parseSource("@"))
    }

    @Test
    fun `a source out of order is kept rather than dropped for being early`() {
        // A stored list may perfectly well name the chained indicator before the one it reads.
        val applied = IndicatorTemplates.apply(
            indicators = listOf("rsi", "ema"),
            sources = mapOf("rsi" to "@ema"),
        )
        assertTrue(applied.dropped.isEmpty())
        assertEquals(2, applied.indicators.size)
    }

    @Test
    fun `a stored loop is refused when the template is checked, not when it is drawn`() {
        val applied = IndicatorTemplates.apply(
            indicators = listOf("ema", "sma"),
            sources = mapOf("ema" to "@sma", "sma" to "@ema"),
        )
        assertNotNull("a loop must come back as a refusal", applied.refusal)
        assertEquals(ChainRefusal.CYCLE, applied.refusal!!.reason)
    }

    @Test
    fun `checking a set needs no bars at all`() {
        val sound = IndicatorTemplates.check(
            listOf(
                ChainedIndicator("ema", "ema", 20),
                ChainedIndicator("rsi", "rsi", 14, IndicatorSource.Output("ema")),
            ),
        )
        assertTrue(sound is ChainOutcome.Ready)
        val looped = IndicatorTemplates.check(
            listOf(ChainedIndicator("ema", "ema", 20, IndicatorSource.Output("ema"))),
        )
        assertEquals(ChainRefusal.CYCLE, (looped as ChainOutcome.Refused).reason)
    }

    @Test
    fun `every source token this build writes is one it can read back`() {
        for (field in BarField.entries) {
            val token = IndicatorTemplates.sourceToken(IndicatorSource.Bars(field))
            assertEquals(IndicatorSource.Bars(field), IndicatorTemplates.parseSource(token))
        }
        val named = IndicatorSource.Output("macd", IndicatorChain.SIGNAL)
        assertEquals(named, IndicatorTemplates.parseSource(IndicatorTemplates.sourceToken(named)))
        val plain = IndicatorSource.Output("macd")
        assertEquals(plain, IndicatorTemplates.parseSource(IndicatorTemplates.sourceToken(plain)))
    }

    @Test
    fun `a duplicate row is dropped rather than shadowing the first`() {
        val applied = IndicatorTemplates.apply(listOf("ema", "ema"), mapOf("ema" to 20))
        assertEquals(1, applied.indicators.size)
        assertEquals(listOf("ema"), applied.dropped)
    }
}
