package com.coinepro.core.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The outputs Pro-Chart's NamaScript had and this one lacked until 5.15.0. */
class OutputsTest {

    private val series = ConformanceSuiteTest.fixture()

    private fun run(source: String): ScriptResult = NamaScript.run(source, series).also {
        assertTrue("refused: ${it.error?.code} ${it.error?.messageEn}", it.ok)
    }

    @Test
    fun `fill shades between two plots, over the price when they are prices`() {
        val result = run("p1 = plot(ta.sma(close, 20))\np2 = plot(ta.sma(close, 50))\nfill(p1, p2, color.new(color.blue, 80))")
        val fill = result.fills.single()
        assertEquals(false, fill.ownPane)
        assertEquals(result.plots[0].values[100], fill.upper[100])
        assertEquals(0x33, (fill.colour ushr 24).toInt())
        val pane = run("a = plot(ta.rsi(close, 14))\nb = plot(ta.sma(ta.rsi(close, 14), 9))\nfill(a, b)")
        assertTrue(pane.fills.single().ownPane)
    }

    @Test
    fun `barcolor names the bars the condition held on`() {
        val result = run("barcolor(close > open, color.orange)")
        val expected = series.bars.indices.filter { series.bars[it].c > series.bars[it].o }
        assertEquals(expected, result.barColours.single().bars)
    }

    @Test
    fun `plotcandle and plotbar draw a second set of bars`() {
        val result = run("ha = (open + high + low + close) / 4\nplotcandle(open, high, low, ha, title = \"HA\")\nplotbar(open, high, low, close)")
        assertEquals(2, result.candles.size)
        assertEquals("HA", result.candles[0].title)
        assertEquals(false, result.candles[0].bars)
        assertEquals(true, result.candles[1].bars)
        assertNull(result.candles[0].colour)
    }

    @Test
    fun `plotarrow points up where the series is positive and down where negative`() {
        val result = run("plotarrow(close - open)")
        val up = series.bars.indices.filter { series.bars[it].c > series.bars[it].o }
        val down = series.bars.indices.filter { series.bars[it].c < series.bars[it].o }
        assertEquals(up, result.markers[0].bars)
        assertEquals(ScriptMarkerStyle.ARROW_UP, result.markers[0].style)
        assertEquals(down, result.markers[1].bars)
    }

    @Test
    fun `a table is placed, filled cell by cell, and a second write replaces the first`() {
        val result = run(
            "t = table.new(position.top_right, 2, 2)\n" +
                "table.cell(t, 0, 0, \"RSI\")\n" +
                "table.cell(t, 1, 0, ta.rsi(close, 14))\n" +
                "table.cell(t, 0, 0, \"RSI 14\", text_color = color.white)\n" +
                "table.cell(t, 5, 5, \"off the grid\")",
        )
        val table = result.tables.single()
        assertEquals("top_right", table.position)
        assertEquals(2, table.cells.size)
        assertEquals("RSI 14", table.cells.first { it.column == 0 && it.row == 0 }.text)
        assertTrue(table.cells.first { it.column == 1 }.text.isNotBlank())
    }
}
