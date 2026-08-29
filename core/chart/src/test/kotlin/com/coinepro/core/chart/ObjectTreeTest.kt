package com.coinepro.core.chart

import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ObjectTreeTest {

    private val defaultLocale = Locale.getDefault()
    private val defaultZone = TimeZone.getDefault()

    /**
     * The app's own default, because that is the condition the formatting bug appears under: a
     * price rendered against a Persian locale comes back in Persian digits.
     */
    @Before
    fun useThePersianDeviceLocale() {
        Locale.setDefault(Locale.forLanguageTag("fa-IR"))
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTheLocale() {
        Locale.setDefault(defaultLocale)
        TimeZone.setDefault(defaultZone)
    }

    private fun drawing(
        id: Long,
        toolId: String,
        points: List<ChartPoint> = listOf(ChartPoint(1_700_000_000L, 2_643.18)),
        text: String? = null,
        locked: Boolean = false,
    ) = Drawing(id = id, toolId = toolId, points = points, text = text, locked = locked)

    @Test
    fun `groups come in the rail's order and the topmost drawing leads its group`() {
        val drawings = listOf(
            drawing(1, "rect", listOf(ChartPoint(10, 1.0), ChartPoint(20, 2.0))),
            drawing(2, "trend", listOf(ChartPoint(10, 1.0), ChartPoint(20, 2.0))),
            drawing(3, "hline"),
        )

        val tree = ObjectTree.treeOf(drawings)

        assertEquals(listOf(ToolGroup.LINES, ToolGroup.SHAPES), tree.map(ObjectGroup::group))
        // Newest last in the state's list is topmost on the canvas, so it is first in the tree —
        // the row a tap on an overlap would have selected.
        assertEquals(listOf(3L, 2L), tree.first().nodes.map(ObjectNode::id))
        assertEquals(listOf(1L), tree.last().nodes.map(ObjectNode::id))
    }

    @Test
    fun `a horizontal line is named by its price in Latin digits under a Persian locale`() {
        val node = ObjectTree.treeOf(listOf(drawing(1, "hline"))).single().nodes.single()

        assertEquals("خط افقی 2643.2", node.label)
        assertTrue(node.label.none { it in '۰'..'۹' })
    }

    @Test
    fun `a text tool is named by what it says, cut to one line`() {
        val note = drawing(1, "text", text = "مقاومت هفتگی\nخط دوم")
        assertEquals("متن مقاومت هفتگی", ObjectTree.labelOf(note))
    }

    @Test
    fun `a trend line is named by its two ends`() {
        val line = drawing(
            id = 1,
            toolId = "trend",
            points = listOf(ChartPoint(1_700_000_000L, 1.0), ChartPoint(1_701_000_000L, 2.0)),
        )

        // Eleven days apart, so both ends are dated rather than clocked.
        assertEquals("خط روند 14 Nov – 26 Nov", ObjectTree.labelOf(line))
    }

    @Test
    fun `a drawing whose tool this build does not know is left out of the tree`() {
        val tree = ObjectTree.treeOf(listOf(drawing(1, "not_a_tool"), drawing(2, "hline")))

        assertEquals(1, tree.size)
        assertEquals(listOf(2L), tree.single().nodes.map(ObjectNode::id))
    }

    @Test
    fun `a hidden id marks its own row and leaves the others alone`() {
        val drawings = listOf(drawing(1, "hline"), drawing(2, "hline", locked = true))

        val nodes = ObjectTree.treeOf(drawings, hiddenIds = setOf(1L)).single().nodes

        assertTrue(nodes.single { it.id == 1L }.hidden)
        assertFalse(nodes.single { it.id == 2L }.hidden)
        assertTrue(nodes.single { it.id == 2L }.locked)
    }

    @Test
    fun `reorder moves one drawing and keeps every other one`() {
        val drawings = (1L..4L).map { drawing(it, "hline") }

        val moved = ObjectTree.reorder(drawings, id = 2L, toIndex = 3)

        assertEquals(listOf(1L, 3L, 4L, 2L), moved.map(Drawing::id))
        assertEquals(drawings.size, moved.size)
        assertEquals(drawings.map(Drawing::id).toSet(), moved.map(Drawing::id).toSet())
    }

    @Test
    fun `an index past the end lands at the end rather than doing nothing`() {
        val drawings = (1L..3L).map { drawing(it, "hline") }

        assertEquals(listOf(2L, 3L, 1L), ObjectTree.reorder(drawings, 1L, 99).map(Drawing::id))
        assertEquals(listOf(3L, 1L, 2L), ObjectTree.reorder(drawings, 3L, -5).map(Drawing::id))
    }

    @Test
    fun `an id that is not in the list leaves the list exactly as it was`() {
        val drawings = (1L..3L).map { drawing(it, "hline") }

        assertEquals(drawings, ObjectTree.reorder(drawings, 99L, 0))
    }

    @Test
    fun `bringToFront and sendToBack are the two ends of reorder`() {
        val drawings = (1L..3L).map { drawing(it, "hline") }

        assertEquals(listOf(2L, 3L, 1L), ObjectTree.bringToFront(drawings, 1L).map(Drawing::id))
        assertEquals(listOf(3L, 1L, 2L), ObjectTree.sendToBack(drawings, 3L).map(Drawing::id))
    }
}
