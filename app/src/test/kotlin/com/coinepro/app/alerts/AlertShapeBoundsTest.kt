package com.coinepro.app.alerts

import com.coinepro.core.notifications.AlertTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Alerts on shapes (5.17.0): a rectangle and a channel are their boundaries, not one line. */
class AlertShapeBoundsTest {

    @Test
    fun `a rectangle is its top and bottom, a channel its two rails`() {
        assertEquals(listOf(90.0, 110.0), AlertDrawingLevel.boundsAt("rect", listOf(0L to 110.0, 100L to 90.0), 50L))
        // Rising one a second from 100; the third anchor puts the upper rail ten above.
        val channel = listOf(0L to 100.0, 100L to 200.0, 50L to 160.0)
        assertEquals(listOf(150.0, 160.0), AlertDrawingLevel.boundsAt("channel", channel, 50L))
        assertEquals(listOf(100.0), AlertDrawingLevel.boundsAt("hline", listOf(0L to 100.0), 50L))
    }

    @Test
    fun `crossing either boundary of a shape is a touch, staying inside is not`() {
        val touch = AlertTrigger.DrawingTouch("7")
        val box = doubleArrayOf(90.0, 90.0, 110.0, 110.0)
        assertTrue("left through the top", touch.evaluate(previous = 105.0, current = 112.0, series = box))
        assertTrue("entered through the bottom", touch.evaluate(previous = 85.0, current = 95.0, series = box))
        assertFalse("moved inside", touch.evaluate(previous = 95.0, current = 105.0, series = box))
    }
}
