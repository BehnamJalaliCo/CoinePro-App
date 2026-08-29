package com.coinepro.feature.chart

import com.coinepro.core.chart.ChartPoint
import com.coinepro.core.chart.Drawing
import com.coinepro.core.chart.ObjectTree
import com.coinepro.core.chart.ToolGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The object tree's rows, and the two places a row's position has to map back to a drawing.
 *
 * Both are reversals waiting to happen. The tree shows each group topmost-first while the chart's
 * own list is oldest-first, so a display index used as a z index is wrong by exactly one on one
 * side and by the whole length on the other — and both mistakes produce a restack that *looks*
 * plausible. The selection mapping has the same shape: a row that selects the drawing beside the
 * one it names is worse than a tree that does nothing, because the reader then edits the wrong
 * object and blames themselves.
 */
class ObjectTreeSelectionTest {

    private fun trend(id: Long, from: Long, to: Long) = Drawing(
        id = id,
        toolId = "trend",
        points = listOf(ChartPoint(from, 100.0), ChartPoint(to, 110.0)),
    )

    private fun level(id: Long, price: Double) = Drawing(
        id = id,
        toolId = "hline",
        points = listOf(ChartPoint(1_700_000_000L, price)),
    )

    /** Three trend lines and two levels, all in the same group, oldest first. */
    private val lines = listOf(
        trend(1L, 1_700_000_000L, 1_700_003_600L),
        trend(2L, 1_700_007_200L, 1_700_010_800L),
        level(3L, 2_610.5),
        trend(4L, 1_700_014_400L, 1_700_018_000L),
        level(5L, 2_648.25),
    )

    @Test
    fun `a tap on the first row selects the drawing that is on top, not the oldest`() {
        val groups = ObjectTree.treeOf(lines)
        val group = groups.first { it.group == ToolGroup.LINES }
        // Everything here is a LINES tool, and the newest was placed last.
        assertEquals(5L, group.nodes.first().id)
        assertEquals(1L, group.nodes.last().id)
    }

    @Test
    fun `every row names a drawing that is actually on the chart`() {
        val groups = ObjectTree.treeOf(lines)
        val listed = groups.flatMap { it.nodes }.map { it.id }.toSet()
        assertEquals(lines.map(Drawing::id).toSet(), listed)
        for (id in listed) {
            assertNotNull("row $id must map to a drawing", lines.firstOrNull { it.id == id })
        }
    }

    @Test
    fun `a hidden drawing is still a row, and says so`() {
        val groups = ObjectTree.treeOf(lines, hiddenIds = setOf(3L))
        val nodes = groups.flatMap { it.nodes }
        assertTrue(nodes.first { it.id == 3L }.hidden)
        assertTrue(nodes.filter { it.id != 3L }.none { it.hidden })
    }

    @Test
    fun `dropping a row on another row takes that row's place in the z-order`() {
        val ids = ObjectTree.treeOf(lines).first { it.group == ToolGroup.LINES }.nodes.map { it.id }
        // Display order is 5, 4, 3, 2, 1. Dragging the topmost (5) down two rows should put it
        // where 3 is — which is z index 2 in the chart's own list.
        assertEquals(2, restackIndex(ids, lines, toRow = 2))
        val restacked = ObjectTree.reorder(lines, id = 5L, toIndex = 2)
        assertEquals(listOf(1L, 2L, 5L, 3L, 4L), restacked.map(Drawing::id))
        // And the tree now shows it in the row it was dropped on.
        val after = ObjectTree.treeOf(restacked).first { it.group == ToolGroup.LINES }
        assertEquals(5L, after.nodes[2].id)
    }

    @Test
    fun `dragging a row upwards puts it in front, which is the other direction of the same rule`() {
        val ids = ObjectTree.treeOf(lines).first { it.group == ToolGroup.LINES }.nodes.map { it.id }
        // The bottom row is the oldest drawing; dropping it on the top row makes it the newest.
        assertEquals(4, restackIndex(ids, lines, toRow = 0))
        val restacked = ObjectTree.reorder(lines, id = 1L, toIndex = 4)
        assertEquals(listOf(2L, 3L, 4L, 5L, 1L), restacked.map(Drawing::id))
        assertEquals(1L, ObjectTree.treeOf(restacked).first { it.group == ToolGroup.LINES }.nodes[0].id)
    }

    @Test
    fun `a drop on a row that is not there is refused rather than sent to the back`() {
        val ids = ObjectTree.treeOf(lines).first { it.group == ToolGroup.LINES }.nodes.map { it.id }
        assertEquals(-1, restackIndex(ids, lines, toRow = 9))
        assertEquals(-1, restackIndex(ids, lines, toRow = -1))
        // A drawing that has gone from the list since the drag began, too.
        assertEquals(-1, restackIndex(listOf(99L), lines, toRow = 0))
    }

    @Test
    fun `restacking keeps every drawing and duplicates none`() {
        for (id in lines.map(Drawing::id)) {
            for (target in lines.indices) {
                val restacked = ObjectTree.reorder(lines, id, target)
                assertEquals(lines.size, restacked.size)
                assertEquals(lines.map(Drawing::id).toSet(), restacked.map(Drawing::id).toSet())
            }
        }
    }

    @Test
    fun `a drag of more than half a row moves it, and less than half does not`() {
        assertEquals(1, rowAfterDrag(fromRow = 0, dragPx = 30f, rowPx = 44f, rowCount = 5))
        assertEquals(0, rowAfterDrag(fromRow = 0, dragPx = 14f, rowPx = 44f, rowCount = 5))
        assertEquals(2, rowAfterDrag(fromRow = 4, dragPx = -100f, rowPx = 44f, rowCount = 5))
    }

    @Test
    fun `a drag beyond the group stops at its ends`() {
        assertEquals(4, rowAfterDrag(fromRow = 0, dragPx = 4000f, rowPx = 44f, rowCount = 5))
        assertEquals(0, rowAfterDrag(fromRow = 4, dragPx = -4000f, rowPx = 44f, rowCount = 5))
    }

    @Test
    fun `an unmeasured row height leaves the drag where it started`() {
        assertEquals(3, rowAfterDrag(fromRow = 3, dragPx = 120f, rowPx = 0f, rowCount = 5))
        assertEquals(0, rowAfterDrag(fromRow = 3, dragPx = 120f, rowPx = 44f, rowCount = 0))
    }
}
