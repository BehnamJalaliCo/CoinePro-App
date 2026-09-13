package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scrub chip is **on the axis**, not on the plot (run Ω-FIX item 7).
 *
 * The chip used to be a fixed 84 or 108 points wide, right-aligned to the canvas with two more
 * points of deliberate bleed. This chart's price gutter is about sixty-four, so between twenty and
 * forty-four points of it sat over the candles — during a scrub, which is the one moment the reader
 * is looking at exactly those candles. «پیل «77,124.2 ⇄» روی پلات هنگام scrub».
 *
 * It is now [PlotFrame.tagGutterWidth] wide and laid out from [alertChipLeft], and those two numbers
 * are the whole of the claim: the chip starts where the plot stops and ends where the canvas does.
 * Both are pure arithmetic, so this is arithmetic rather than a screenshot — a picture would show
 * the chip in the right place on one gutter width and prove nothing about the others.
 */
class AlertChipGutterTest {

    private val canvas = 411f
    private val gutter = 64f

    @Test
    fun `on a right-hand axis the chip starts where the plot stops`() {
        val frame = plotFrame(canvas, gutter, ScaleSide.RIGHT, axes = true)
        val left = alertChipLeft(frame)
        assertEquals("the chip overhangs the plot", frame.right, left, 0f)
        assertEquals("and it stops at the canvas edge", canvas, left + frame.tagGutterWidth, 0.001f)
    }

    @Test
    fun `on a left-hand axis it is the mirror of that, and still off the plot`() {
        val frame = plotFrame(canvas, gutter, ScaleSide.LEFT, axes = true)
        val left = alertChipLeft(frame)
        assertEquals("the chip left the canvas", 0f, left, 0f)
        assertEquals("the chip overhangs the plot", frame.left, left + frame.tagGutterWidth, 0.001f)
    }

    @Test
    fun `with two axes the chip follows the tags to the live edge`() {
        val frame = plotFrame(canvas, gutter, ScaleSide.BOTH, axes = true)
        assertTrue("the tags are on the right with two gutters", frame.tagsOnRight)
        assertEquals(frame.right, alertChipLeft(frame), 0f)
    }

    @Test
    fun `no pixel of the chip is ever over the plot, at any gutter width`() {
        // The property, over every gutter this chart can be drawn with — a phone's sixty-four, a
        // tablet's wider ladder, and the narrow one a compact pane gets.
        for (width in 30..120 step 5) {
            for (side in listOf(ScaleSide.RIGHT, ScaleSide.LEFT, ScaleSide.BOTH, ScaleSide.MERGED)) {
                val frame = plotFrame(canvas, width.toFloat(), side, axes = true)
                val left = alertChipLeft(frame)
                val right = left + frame.tagGutterWidth
                val overlapsPlot = right > frame.left && left < frame.right
                assertTrue("a $width pt $side chip runs onto the plot", !overlapsPlot)
                assertTrue("a $width pt $side chip runs off the canvas", left >= -0.001f && right <= canvas + 0.001f)
            }
        }
    }

    @Test
    fun `a canvas with no axis has no gutter to put it in`() {
        // And then the affordance is not drawn at all rather than drawn on the candles. A thumbnail
        // and a share render both take this path.
        val frame = plotFrame(canvas, gutter, ScaleSide.RIGHT, axes = false)
        assertEquals(0f, frame.tagGutterWidth, 0f)
    }
}
