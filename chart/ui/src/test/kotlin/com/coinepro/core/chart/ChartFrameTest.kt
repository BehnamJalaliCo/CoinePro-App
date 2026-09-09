package com.coinepro.core.chart

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the plot sits once the price gutter has been taken off the canvas.
 *
 * `ScaleSide` was stored, saved per symbol and restored on reopen while the renderer measured every
 * gutter against the right edge — so three of its four values were a setting the reader could pick
 * and nothing would move. These are the assertions that say the geometry now answers to it.
 */
class ChartFrameTest {

    private val canvas = 400f
    private val gutter = 60f

    @Test
    fun `a right-hand gutter leaves the plot at the canvas origin`() {
        val frame = plotFrame(canvas, gutter, ScaleSide.RIGHT, axes = true)
        assertEquals(0f, frame.left, 0f)
        assertEquals(canvas - gutter, frame.width, 0f)
        assertEquals(gutter, frame.rightGutter, 0f)
        assertEquals(0f, frame.leftGutter, 0f)
        assertTrue(frame.tagsOnRight)
    }

    @Test
    fun `a left-hand gutter moves the plot right by exactly its width`() {
        val frame = plotFrame(canvas, gutter, ScaleSide.LEFT, axes = true)
        assertEquals(gutter, frame.left, 0f)
        assertEquals(canvas - gutter, frame.width, 0f)
        assertFalse(frame.tagsOnRight)
        // And the gutter is painted at a negative x, because the draw pass has translated into the
        // plot's own space by then.
        assertEquals(-gutter, frame.tagGutterX, 0f)
        assertEquals(gutter, frame.tagGutterWidth, 0f)
    }

    @Test
    fun `two gutters take width from the plot at both ends`() {
        val frame = plotFrame(canvas, gutter, ScaleSide.BOTH, axes = true)
        assertEquals(gutter, frame.left, 0f)
        assertEquals(canvas - gutter * 2, frame.width, 0f)
        assertEquals(gutter, frame.leftGutter, 0f)
        assertEquals(gutter, frame.rightGutter, 0f)
        // The tags go on the live-edge side when there are two ladders to choose between.
        assertTrue(frame.tagsOnRight)
    }

    @Test
    fun `merged draws the same gutter as right does`() {
        assertEquals(
            plotFrame(canvas, gutter, ScaleSide.RIGHT, axes = true),
            plotFrame(canvas, gutter, ScaleSide.MERGED, axes = true),
        )
    }

    @Test
    fun `with the axis switched off the plot is the whole canvas`() {
        ScaleSide.entries.forEach { side ->
            val frame = plotFrame(canvas, gutter, side, axes = false)
            assertEquals("$side keeps the whole canvas", canvas, frame.width, 0f)
            assertEquals(0f, frame.left, 0f)
            assertEquals(0f, frame.tagGutterWidth, 0f)
        }
    }

    @Test
    fun `a gutter never eats more than half the canvas`() {
        val frame = plotFrame(80f, 60f, ScaleSide.BOTH, axes = true)
        assertTrue("the plot must survive a narrow canvas", frame.width >= 0f)
        assertEquals(40f, frame.left, 0f)
    }

    @Test
    fun `a touch is in the gutter on whichever side the gutter is`() {
        val right = plotFrame(canvas, gutter, ScaleSide.RIGHT, axes = true)
        assertTrue(right.inGutter(canvas - 10f, reachPx = 0f))
        assertFalse(right.inGutter(10f, reachPx = 0f))

        val left = plotFrame(canvas, gutter, ScaleSide.LEFT, axes = true)
        assertTrue(left.inGutter(10f, reachPx = 0f))
        assertFalse(left.inGutter(canvas - 10f, reachPx = 0f))
    }

    @Test
    fun `the reach widens the strip into the plot and not off the canvas`() {
        val frame = plotFrame(canvas, gutter, ScaleSide.RIGHT, axes = true)
        val edge = canvas - gutter
        assertFalse("just inside the plot is not the gutter", frame.inGutter(edge - 20f, reachPx = 12f))
        assertTrue("within reach of the labels is", frame.inGutter(edge - 6f, reachPx = 12f))
    }

    @Test
    fun `a canvas position becomes the plot position the viewport expects`() {
        val frame = plotFrame(canvas, gutter, ScaleSide.LEFT, axes = true)
        val plot = frame.toPlot(Offset(gutter + 25f, 40f))
        assertEquals(25f, plot.x, 0f)
        assertEquals("the vertical is untouched", 40f, plot.y, 0f)
        assertTrue(frame.onPlot(gutter + 25f))
        assertFalse("the gutter is not the plot", frame.onPlot(5f))
    }
}
