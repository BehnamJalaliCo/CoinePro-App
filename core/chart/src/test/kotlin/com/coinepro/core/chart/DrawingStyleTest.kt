package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The three style fields a selection toolbar sets, and the one rule that makes them safe to add.
 *
 * Null on [Drawing.textColour] and [Drawing.fillColour] is not a colour and not a placeholder for
 * one: it is «the reader never said», which is what every mark drawn before the toolbar existed
 * means and what keeps them looking exactly as they did. These tests are about that distinction
 * surviving the actions, because a setter that quietly turned «unset» into «gold» would make the
 * toolbar's default button do nothing the reader could see.
 */
class DrawingStyleTest {

    private fun trend(id: Long = 1L) = Drawing(
        id = id,
        toolId = "trend",
        points = listOf(ChartPoint(1_700_000_000L, 2_643.18), ChartPoint(1_700_003_600L, 2_651.40)),
    )

    private fun stateWith(vararg drawings: Drawing) = DrawingState(drawings = drawings.toList())

    @Test
    fun `a drawing placed today follows its line colour for text and fill`() {
        val placed = trend()
        assertNull(placed.textColour)
        assertNull(placed.fillColour)
        assertEquals(LineStyleKind.SOLID, placed.lineStyle)
    }

    @Test
    fun `setting a text colour leaves the line and the fill alone`() {
        val after = DrawingActions.setTextColour(stateWith(trend()), 1L, 0xFFFFFFFF)

        val styled = after.drawings.single()
        assertEquals(0xFFFFFFFF, styled.textColour)
        assertEquals(Drawing.DEFAULT_DRAWING_COLOUR, styled.colour)
        assertNull(styled.fillColour)
    }

    @Test
    fun `clearing a text colour puts the drawing back to following its line`() {
        val chosen = DrawingActions.setTextColour(stateWith(trend()), 1L, 0xFF00B15C)
        assertNotNull(chosen.drawings.single().textColour)

        // Null is a real argument on this setter, not «no change»: it is the only way out of a
        // colour the reader regrets.
        val cleared = DrawingActions.setTextColour(chosen, 1L, null)
        assertNull(cleared.drawings.single().textColour)
    }

    @Test
    fun `a fill colour is set on the drawing that was asked for and on no other`() {
        val after = DrawingActions.setFillColour(stateWith(trend(1L), trend(2L)), 2L, 0x330E8A4CL)

        assertNull(after.drawings.first { it.id == 1L }.fillColour)
        assertEquals(0x330E8A4CL, after.drawings.first { it.id == 2L }.fillColour)
    }

    @Test
    fun `a line style is set and read back as the enum the pixels layer already owns`() {
        val after = DrawingActions.setLineStyle(stateWith(trend()), 1L, LineStyleKind.LARGE_DASHED)

        assertEquals(LineStyleKind.LARGE_DASHED, after.drawings.single().lineStyle)
        // The same enum the renderer turns into a dash pattern, rather than a second one beside it.
        assertEquals(2, dashIntervals(after.drawings.single().lineStyle, 2f).size)
    }

    @Test
    fun `a locked drawing keeps the style it was locked with`() {
        val locked = stateWith(trend().copy(locked = true))

        // Restyling is an edit, and the lock exists so an edit cannot happen by accident — the same
        // reading `DrawingActions.restyle` takes of the colour and the width.
        assertNull(DrawingActions.setTextColour(locked, 1L, 0xFFFFFFFF).drawings.single().textColour)
        assertNull(DrawingActions.setFillColour(locked, 1L, 0xFFFFFFFF).drawings.single().fillColour)
        assertEquals(
            LineStyleKind.SOLID,
            DrawingActions.setLineStyle(locked, 1L, LineStyleKind.DOTTED).drawings.single().lineStyle,
        )
    }

    @Test
    fun `a solid line style still lets a dashed tool draw itself dashed`() {
        // SOLID means «whatever the tool draws by default», which for a price label and a forecast
        // is a broken line. The renderer reads that off an empty dash pattern.
        assertEquals(0, dashIntervals(LineStyleKind.SOLID, 2f).size)
    }
}
