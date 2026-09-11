package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rail's flyouts open on the tool the reader last armed in that group (run E): the
 * last-used tool leads its group, the groups keep their order, and a group nobody has used is
 * untouched.
 */
class ToolRailPromotionTest {

    private fun cells(rows: List<RailRow>) = rows.filterIsInstance<RailRow.Cell>().map { it.tool.id }

    @Test
    fun `the last-used tool leads its group and the groups keep their order`() {
        val plain = cells(railRows(DrawingTools.ALL, grouped = true))
        val promoted = cells(railRows(DrawingTools.ALL, grouped = true, lastUsed = mapOf(ToolGroup.LINES to "hline")))
        val lines = DrawingTools.ALL.filter { it.group == ToolGroup.LINES }.map { it.id }
        assertEquals("hline", promoted.first { it in lines })
        assertEquals(plain.toSet(), promoted.toSet())
        // Everything outside the promoted group is exactly where it was.
        assertEquals(plain.filter { it !in lines }, promoted.filter { it !in lines })
        // The group's heading still precedes its first cell.
        val rows = railRows(DrawingTools.ALL, grouped = true, lastUsed = mapOf(ToolGroup.LINES to "hline"))
        val heading = rows.indexOfFirst { it is RailRow.Heading && it.group == ToolGroup.LINES }
        assertTrue(heading >= 0)
        assertEquals("hline", (rows[heading + 1] as RailRow.Cell).tool.id)
    }

    @Test
    fun `with nothing last used the rail is the catalogue's own order`() {
        assertEquals(DrawingTools.ALL.map { it.id }, cells(railRows(DrawingTools.ALL, grouped = false)))
    }

    @Test
    fun `an unknown last-used id promotes nothing`() {
        val plain = cells(railRows(DrawingTools.ALL, grouped = true))
        val promoted = cells(railRows(DrawingTools.ALL, grouped = true, lastUsed = mapOf(ToolGroup.LINES to "no-such-tool")))
        assertEquals(plain, promoted)
    }
}
