package com.coinepro.app

import com.coinepro.app.widget.WidgetLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the widget decides what fits.
 *
 * The only part of a widget that can be tested without a launcher, and the part most likely to be
 * wrong invisibly: a row too many is a row clipped in half on somebody's home screen, on one
 * launcher, at one font size, and it will never show up in a screenshot taken here.
 */
class WidgetLayoutTest {

    /** Roughly what a launcher reports for a grid of this many cells on a typical phone. */
    private fun cells(wide: Int, tall: Int) = WidgetLayout.of(widthDp = wide * 70, heightDp = tall * 70)

    @Test
    fun `the smallest widget still shows a market`() {
        // A widget that computes zero rows is an empty rectangle, and the reader's conclusion is
        // that the app is broken rather than that their widget is small.
        val tiny = WidgetLayout.of(widthDp = 40, heightDp = 40)
        assertTrue(tiny.rows >= 1)
        assertFalse(tiny.header)
        assertFalse(tiny.names)
    }

    @Test
    fun `a two-cell-high widget spends its height on prices`() {
        // The header would cost a third of the glass, and somebody who put a *price* widget on
        // their home screen did not ask for a title bar. They still get the clock.
        listOf(cells(2, 2), cells(4, 2)).forEach { small ->
            assertFalse("A header would cost a third of this widget", small.header)
            assertTrue("It still has to say when", small.footer)
            assertTrue(small.rows >= 2)
        }
    }

    @Test
    fun `three cells high earns the header`() {
        val medium = cells(4, 3)
        assertTrue(medium.header)
        assertFalse("Never both strips", medium.footer)
        assertTrue(medium.names)
        assertTrue(medium.rows >= WidgetLayout.HEADER_MIN_ROWS)
    }

    @Test
    fun `the two strips are never drawn together`() {
        for (height in 40..600 step 10) {
            val layout = WidgetLayout.of(widthDp = 300, heightDp = height)
            assertFalse("${height}dp drew both", layout.header && layout.footer)
        }
    }

    @Test
    fun `bigger is never fewer rows`() {
        // Monotonic in height. A resize that *lost* a row would look like data disappearing.
        var previous = 0
        listOf(60, 100, 140, 180, 220, 300, 400, 600).forEach { height ->
            val rows = WidgetLayout.of(widthDp = 300, heightDp = height).rows
            assertTrue("$height dp lost a row", rows >= previous)
            previous = rows
        }
    }

    @Test
    fun `it stops at eight rows however tall the widget is`() {
        // Past eight it is a list, not a widget — and `WidgetSnapshotStore` only stores twelve.
        assertEquals(WidgetLayout.MAX_ROWS, WidgetLayout.of(widthDp = 400, heightDp = 4_000).rows)
    }

    @Test
    fun `rows plus header always fit the height given`() {
        // The property the whole file exists for. Every size a launcher can report must produce a
        // layout that fits inside it, or something is clipped.
        for (width in 40..500 step 20) {
            for (height in 40..500 step 20) {
                val layout = WidgetLayout.of(width, height)
                val strip = when {
                    layout.header -> WidgetLayout.HEADER_HEIGHT_DP
                    layout.footer -> WidgetLayout.FOOTER_HEIGHT_DP
                    else -> 0
                }
                val used = layout.rows * WidgetLayout.ROW_HEIGHT_DP + strip +
                    WidgetLayout.VERTICAL_PADDING_DP
                // One row is allowed to overflow a widget too small to hold even one, because the
                // alternative is drawing nothing at all.
                if (layout.rows > 1) {
                    assertTrue("${width}x$height used $used", used <= height)
                }
            }
        }
    }

    @Test
    fun `names need width, not height`() {
        // A tall narrow widget has room for rows and not for a name beside a price.
        assertFalse(WidgetLayout.of(widthDp = 150, heightDp = 400).names)
        assertTrue(WidgetLayout.of(widthDp = 300, heightDp = 100).names)
    }
}
