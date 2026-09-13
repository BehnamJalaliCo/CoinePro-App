package com.coinepro.core.chart

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which axis a two-finger gesture drives (run Σ, S1).
 *
 * ### The rule, and the rule it replaces
 *
 * **Where the gesture starts, and nothing else.** The code this tests replaced a pair of
 * orientation checks: the time axis only moved when the fingers were far enough apart
 * *horizontally*, and the price axis moved whenever they were far enough apart vertically —
 * anywhere on the canvas, gutter or not. A reader pinching at forty-five degrees got both; a reader
 * pinching vertically on the candles got the price scale when they had asked for more bars; and the
 * gesture the owner reported, a horizontal pinch on the plot, was the one that could not fire.
 *
 * The angle between two fingers is not a statement about intent. Where they landed is.
 */
class PinchZoneTest {

    private val canvas = 411f
    private val gutter = 64f
    private val timeAxisTop = 700f

    private fun frame(side: ScaleSide = ScaleSide.RIGHT) =
        plotFrame(canvas, gutter, side, axes = true)

    @Test
    fun `two fingers on the candles zoom time, at any angle`() {
        val frame = frame()
        // Level, upright, and diagonal — the same answer for all three, which is the whole fix.
        assertEquals(PinchZone.PLOT, pinchZoneOf(frame, timeAxisTop, Offset(40f, 300f)))
        assertEquals(PinchZone.PLOT, pinchZoneOf(frame, timeAxisTop, Offset(200f, 120f)))
        assertEquals(PinchZone.PLOT, pinchZoneOf(frame, timeAxisTop, Offset(300f, 650f)))
    }

    @Test
    fun `a gesture that starts in the price ladder scales the price`() {
        val frame = frame()
        assertEquals(PinchZone.PRICE, pinchZoneOf(frame, timeAxisTop, Offset(canvas - 10f, 300f)))
        // And on a chart whose ladder is on the other side, the other side.
        val left = frame(ScaleSide.LEFT)
        assertEquals(PinchZone.PRICE, pinchZoneOf(left, timeAxisTop, Offset(10f, 300f)))
        assertEquals(PinchZone.PLOT, pinchZoneOf(left, timeAxisTop, Offset(canvas - 10f, 300f)))
    }

    @Test
    fun `a gesture that starts on the date strip scales time`() {
        val frame = frame()
        assertEquals(PinchZone.TIME, pinchZoneOf(frame, timeAxisTop, Offset(200f, timeAxisTop)))
        assertEquals(PinchZone.TIME, pinchZoneOf(frame, timeAxisTop, Offset(200f, timeAxisTop + 20f)))
    }

    @Test
    fun `the corner where the two strips meet belongs to the date strip`() {
        // The time axis runs the full width, gutters included, so the bottom-right corner of a
        // right-hand-ladder chart is a date. Deciding it the other way would put a price gesture
        // under the one label that is not a price.
        val frame = frame()
        assertEquals(
            PinchZone.TIME,
            pinchZoneOf(frame, timeAxisTop, Offset(canvas - 5f, timeAxisTop + 5f)),
        )
    }

    @Test
    fun `a canvas with no axes is all plot`() {
        // A thumbnail, a list row, the share renderer. There is no ladder to scale and no strip to
        // land on, and a gesture there is about the bars or about nothing.
        val bare = plotFrame(canvas, gutter, ScaleSide.RIGHT, axes = false)
        assertEquals(PinchZone.PLOT, pinchZoneOf(bare, 0f, Offset(canvas - 2f, 300f)))
        assertEquals(PinchZone.PLOT, pinchZoneOf(bare, 0f, Offset(2f, 300f)))
    }

    @Test
    fun `every point on the canvas resolves to exactly one zone`() {
        // A sweep rather than three corners: a routing rule with a gap in it is a gesture that does
        // nothing, which is what the owner reported, and a gap is not visible from the three points
        // somebody thought to write down.
        val frame = frame()
        for (x in 0..canvas.toInt()) {
            for (y in 0..800 step 25) {
                val zone = pinchZoneOf(frame, timeAxisTop, Offset(x.toFloat(), y.toFloat()))
                val expected = when {
                    y >= timeAxisTop -> PinchZone.TIME
                    x >= frame.right -> PinchZone.PRICE
                    else -> PinchZone.PLOT
                }
                assertEquals("at ($x, $y)", expected, zone)
            }
        }
    }
}
