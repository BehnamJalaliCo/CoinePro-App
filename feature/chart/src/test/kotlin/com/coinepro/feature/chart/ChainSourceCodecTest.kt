package com.coinepro.feature.chart

import com.coinepro.core.chart.BarField
import com.coinepro.core.chart.IndicatorSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How an indicator's source survives a round trip through the template store.
 *
 * `IndicatorTemplateStore` stores the source as an opaque string and deliberately does not
 * understand it — it is a preferences module and chaining is not its business — so the spelling is
 * this module's, and the one property that matters is that a template saved today reads back as the
 * same chain tomorrow. The failure is quiet in exactly the way this app cannot afford: an indicator
 * that looks computed and is reading something the reader did not choose.
 */
class ChainSourceCodecTest {

    @Test
    fun `every column of a bar survives the round trip`() {
        for (field in BarField.entries) {
            val source: IndicatorSource = IndicatorSource.Bars(field)
            assertEquals(source, decodeChainSource(encodeChainSource(source)))
        }
    }

    @Test
    fun `a chained main line survives the round trip`() {
        val source: IndicatorSource = IndicatorSource.Output("rsi")
        assertEquals(source, decodeChainSource(encodeChainSource(source)))
    }

    @Test
    fun `a chained named output survives the round trip`() {
        val source: IndicatorSource = IndicatorSource.Output("macd", "signal")
        assertEquals(source, decodeChainSource(encodeChainSource(source)))
    }

    @Test
    fun `the default source is the close and encodes as such`() {
        assertEquals("bars:CLOSE", encodeChainSource(IndicatorSource.CANDLES))
        assertEquals(IndicatorSource.CANDLES, decodeChainSource("bars:CLOSE"))
    }

    @Test
    fun `anything this build cannot read answers null rather than guessing`() {
        // Null is the whole safety property. Substituting the close for a source that will not parse
        // would draw an indicator that is not the one the reader saved, with nothing on screen to
        // say so — which is the exact failure `IndicatorChain` refuses for the multi-column studies.
        assertNull(decodeChainSource(""))
        assertNull(decodeChainSource("bars"))
        assertNull(decodeChainSource("bars:MEDIAN"))
        assertNull(decodeChainSource("out:"))
        assertNull(decodeChainSource("wat:rsi"))
        assertNull(decodeChainSource("out:a:b:c"))
    }

    @Test
    fun `a named output that matches the main line still round-trips as a named one`() {
        // The encoder does not second-guess the caller: `IndicatorChain` treats a named output that
        // happens to be the first as the first, and normalising it here would mean two spellings of
        // one chain that a test of equality could not tell apart.
        val source: IndicatorSource = IndicatorSource.Output("rsi", "value")
        assertEquals(source, decodeChainSource(encodeChainSource(source)))
    }
}
