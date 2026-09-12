package com.coinepro.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A reader's script survives the preferences string it is packed into (4.73.0).
 *
 * The whole reason this codec exists is that every other field in `SymbolChartStateStore` is an id
 * the app chose and can filter, and this one is *arbitrary text*. So the cases below are the ones
 * that would have lost somebody's work: a script containing the store's own separators, a script
 * with newlines and quotes in it, and one corrupt row among good ones.
 */
class ChartScriptCodecTest {

    /** The three control characters `SymbolChartStateStore` frames its records with. */
    private val storeSeparators = "\u001D\u001E\u001F"

    /** And the three this codec frames its own rows with. */
    private val codecSeparators = "\u0001\u0002\u0003"

    /** The row separator, so the test can build a record with a bad row in it. */
    private val ROW = "\u0001"

    private fun row(source: String, name: String = "S") = ChartScriptRow(
        instanceId = "1",
        name = name,
        source = source,
        overrides = mapOf("Length" to 14.0),
    )

    @Test
    fun `a script round-trips through the field it is stored in`() {
        val original = listOf(
            row("plot(ta.ema(close, 20))\n// a comment\nhline(30)"),
            ChartScriptRow(instanceId = "2", name = "Two", source = "plot(close)", ordinal = 2),
        )
        assertEquals(original, ChartScriptCodec.decode(ChartScriptCodec.encode(original)))
    }

    @Test
    fun `a script containing the store's own separators survives`() {
        // A filter would have dropped this script and the reader would have lost it. Base64 means
        // the payload cannot contain the frame around it, so there is nothing to filter.
        val hostile = "plot(close) // $storeSeparators end"
        val decoded = ChartScriptCodec.decode(ChartScriptCodec.encode(listOf(row(hostile))))
        assertEquals(hostile, decoded.single().source)
    }

    @Test
    fun `a script containing this codec's own separators survives too`() {
        val hostile = "plot(close) // $codecSeparators end"
        val decoded = ChartScriptCodec.decode(ChartScriptCodec.encode(listOf(row(hostile))))
        assertEquals(hostile, decoded.single().source)
    }

    @Test
    fun `the reader's inputs come back as numbers`() {
        val decoded = ChartScriptCodec.decode(ChartScriptCodec.encode(listOf(row("plot(close)"))))
        assertEquals(mapOf("Length" to 14.0), decoded.single().overrides)
    }

    @Test
    fun `a Persian name and a Persian string inside the script both survive`() {
        val persian = row("plot(close, title = \"تند\")", name = "تقاطع دو میانگین")
        val decoded = ChartScriptCodec.decode(ChartScriptCodec.encode(listOf(persian))).single()
        assertEquals("تقاطع دو میانگین", decoded.name)
        assertTrue(decoded.source.contains("تند"))
    }

    @Test
    fun `a blank field, a corrupt field and a script with no source are all dropped quietly`() {
        assertEquals(emptyList<ChartScriptRow>(), ChartScriptCodec.decode(null))
        assertEquals(emptyList<ChartScriptRow>(), ChartScriptCodec.decode(""))
        assertEquals(emptyList<ChartScriptRow>(), ChartScriptCodec.decode("not base64 at all"))
        // A script with no text is not a script: writing one would restore a chart carrying an
        // instance that can never draw and can never be explained.
        assertEquals("", ChartScriptCodec.encode(listOf(row(""))))
    }

    @Test
    fun `one corrupt row does not take the others with it`() {
        val good = ChartScriptCodec.encode(listOf(row("plot(close)")))
        val mixed = "garbage$good"
        assertEquals(1, ChartScriptCodec.decode(mixed).size)
    }
}
