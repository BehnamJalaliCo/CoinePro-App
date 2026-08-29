package com.coinepro.feature.chart

import com.coinepro.core.chart.ChartPoint
import com.coinepro.core.chart.Drawing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What applying a saved style actually does to a drawing — and to the ones beside it.
 *
 * A template is two numbers, which makes it look like there is nothing to get wrong. There are
 * three things: a width that arrives as zero from an older record leaves a drawing on the chart,
 * saved and selectable, and invisible; a lock that is not honoured turns the whole point of the
 * lock into a suggestion; and stamping the width onto the wrong drawing restyles somebody's trend
 * line from last week instead of the one they just placed.
 */
class DrawingTemplateApplyTest {

    private val amber = 0xFFD8A848
    private val red = 0xFFF6465D

    private fun line(id: Long, colour: Long = amber, width: Float = 1.6f, locked: Boolean = false) =
        Drawing(
            id = id,
            toolId = "trend",
            points = listOf(ChartPoint(1_700_000_000L, 100.0), ChartPoint(1_700_003_600L, 110.0)),
            colour = colour,
            widthDp = width,
            locked = locked,
        )

    @Test
    fun `applying a template puts its colour and its width on the named drawing`() {
        val before = listOf(line(1L), line(2L))
        val after = applyStyle(before, id = 2L, colour = red, widthDp = 2.5f)
        val subject = after.first { it.id == 2L }
        assertEquals(red, subject.colour)
        assertEquals(2.5f, subject.widthDp, 1e-6f)
    }

    @Test
    fun `applying a template leaves every other drawing alone`() {
        val before = listOf(line(1L), line(2L))
        val after = applyStyle(before, id = 2L, colour = red, widthDp = 2.5f)
        val untouched = after.first { it.id == 1L }
        assertEquals(amber, untouched.colour)
        assertEquals(1.6f, untouched.widthDp, 1e-6f)
    }

    @Test
    fun `a locked drawing keeps its style, because a lock is what stops edits`() {
        val before = listOf(line(1L, locked = true))
        val after = applyStyle(before, id = 1L, colour = red, widthDp = 4f)
        assertEquals(amber, after.first().colour)
        assertEquals(1.6f, after.first().widthDp, 1e-6f)
        // And the list itself is the same object, so nothing downstream recomposes for nothing.
        assertSame(before, after)
    }

    @Test
    fun `applying to a drawing that is no longer there changes nothing`() {
        val before = listOf(line(1L))
        assertSame(before, applyStyle(before, id = 99L, colour = red, widthDp = 3f))
    }

    @Test
    fun `a width of zero from an old record becomes the default rather than an invisible line`() {
        assertEquals(DEFAULT_DRAWING_WIDTH_DP, usableWidth(0f), 1e-6f)
        val after = applyStyle(listOf(line(1L)), id = 1L, colour = red, widthDp = 0f)
        assertEquals(DEFAULT_DRAWING_WIDTH_DP, after.first().widthDp, 1e-6f)
    }

    @Test
    fun `a width outside the bounds is brought back inside them`() {
        assertEquals(MIN_DRAWING_WIDTH_DP, usableWidth(0.2f), 1e-6f)
        assertEquals(MAX_DRAWING_WIDTH_DP, usableWidth(40f), 1e-6f)
        assertEquals(DEFAULT_DRAWING_WIDTH_DP, usableWidth(Float.NaN), 1e-6f)
        assertEquals(DEFAULT_DRAWING_WIDTH_DP, usableWidth(Float.POSITIVE_INFINITY), 1e-6f)
    }

    @Test
    fun `the chosen width lands on the drawing that was just placed`() {
        val previous = listOf(line(1L))
        val next = previous + line(2L)
        val stamped = stampWidth(next, previous, widthDp = 2.5f)
        assertEquals(1.6f, stamped.first { it.id == 1L }.widthDp, 1e-6f)
        assertEquals(2.5f, stamped.first { it.id == 2L }.widthDp, 1e-6f)
    }

    @Test
    fun `a placement made in the same frame as a deletion restyles only the new one`() {
        // Undo then place: drawing 2 is gone and drawing 3 has arrived, in one state.
        val previous = listOf(line(1L), line(2L))
        val next = listOf(line(1L), line(3L))
        val stamped = stampWidth(next, previous, widthDp = 4f)
        assertEquals(1.6f, stamped.first { it.id == 1L }.widthDp, 1e-6f)
        assertEquals(4f, stamped.first { it.id == 3L }.widthDp, 1e-6f)
    }

    @Test
    fun `nothing is stamped when the reader is on the default width`() {
        val previous = listOf(line(1L))
        val next = previous + line(2L)
        assertSame(next, stampWidth(next, previous, widthDp = DEFAULT_DRAWING_WIDTH_DP))
    }

    @Test
    fun `a hidden drawing survives the canvas handing its own list straight back`() {
        val all = listOf(line(1L), line(2L), line(3L))
        val hidden = setOf(2L)
        // What the canvas is given, and what it hands back untouched.
        val shown = all.filterNot { it.id in hidden }
        val merged = mergeHidden(shown, all, hidden)
        assertEquals(listOf(1L, 2L, 3L), merged.map(Drawing::id))
    }

    @Test
    fun `a hidden drawing goes back where it was in the z-order`() {
        val all = listOf(line(1L), line(2L), line(3L), line(4L))
        val hidden = setOf(1L, 3L)
        val shown = all.filterNot { it.id in hidden }
        assertEquals(listOf(1L, 2L, 3L, 4L), mergeHidden(shown, all, hidden).map(Drawing::id))
    }

    @Test
    fun `deleting a visible drawing does not take the hidden ones with it`() {
        val all = listOf(line(1L), line(2L), line(3L))
        val hidden = setOf(3L)
        // The reader deleted drawing 2 on the canvas; 1 is all that comes back.
        val merged = mergeHidden(listOf(line(1L)), all, hidden)
        assertEquals(listOf(1L, 3L), merged.map(Drawing::id))
    }

    @Test
    fun `a drawing placed while another is hidden is kept alongside it`() {
        val all = listOf(line(1L), line(2L))
        val hidden = setOf(2L)
        val merged = mergeHidden(listOf(line(1L), line(9L)), all, hidden)
        assertTrue(merged.map(Drawing::id).containsAll(listOf(1L, 2L, 9L)))
        assertEquals(3, merged.size)
    }

    @Test
    fun `nothing hidden is the same list back, so an ordinary chart pays nothing`() {
        val all = listOf(line(1L), line(2L))
        assertSame(all, mergeHidden(all, all, emptySet()))
    }

    @Test
    fun `saving a template records the tool, the style and a name without its spaces`() {
        val template = templateOf(
            toolId = "trend",
            name = "  قرمز ضخیم  ",
            colour = red,
            widthDp = 2.5f,
            now = 1_700_000_000_000L,
        )
        assertEquals("trend", template.toolId)
        assertEquals("قرمز ضخیم", template.name)
        assertEquals(red, template.colour)
        assertEquals(2.5f, template.widthDp, 1e-6f)
        assertEquals(1_700_000_000_000L, template.createdAt)
    }

    @Test
    fun `two templates saved at different moments do not share an id`() {
        val first = templateOf("trend", "الف", amber, 1.6f, now = 1_700_000_000_000L)
        val second = templateOf("trend", "الف", amber, 1.6f, now = 1_700_000_000_001L)
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `a template cannot be saved at a width that would make the drawing invisible`() {
        assertEquals(
            DEFAULT_DRAWING_WIDTH_DP,
            templateOf("trend", "الف", amber, 0f, now = 1L).widthDp,
            1e-6f,
        )
    }
}
