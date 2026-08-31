package com.coinepro.feature.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ring the chart's own command band steps through.
 *
 * The switcher this replaces had a defect no test could have caught — it was composed only on a
 * build with no per-symbol controller holder, and the shipping app has one, so in production it was
 * unreachable code. What *can* be caught is the arithmetic, and every case below is one a reader
 * meets on an ordinary watchlist: the first entry, the last entry, a list of two, and a chart
 * opened on something they never starred.
 */
class SymbolWheelTest {

    /** Four instruments this app has artwork for, so the covering filter is not what is under test. */
    private val list = listOf("XAUUSD", "XAGUSD", "EURUSD", "BTCUSDT")

    @Test
    fun `the neighbours are the entries either side`() {
        val ring = symbolNeighbours(list, "XAGUSD")

        assertEquals("XAUUSD", ring.previous)
        assertEquals("EURUSD", ring.next)
        assertEquals(2, ring.position)
        assertEquals(4, ring.total)
    }

    @Test
    fun `the ring wraps, so the last entry is not a dead control`() {
        // A wheel that stops at both ends is dead on the first and last symbol of the list, and a
        // reader cannot tell a dead control from a broken one.
        val last = symbolNeighbours(list, "BTCUSDT")
        assertEquals("EURUSD", last.previous)
        assertEquals("XAUUSD", last.next)

        val first = symbolNeighbours(list, "XAUUSD")
        assertEquals("BTCUSDT", first.previous)
        assertEquals("XAGUSD", first.next)
    }

    @Test
    fun `two symbols draw one neighbour rather than the same one twice`() {
        val ring = symbolNeighbours(listOf("XAUUSD", "BTCUSDT"), "XAUUSD")

        assertNull(ring.previous)
        assertEquals("BTCUSDT", ring.next)
        assertFalse(ring.isEmpty)
    }

    @Test
    fun `a list of one has nowhere to go and draws nothing at all`() {
        val ring = symbolNeighbours(listOf("XAUUSD"), "XAUUSD")

        assertTrue(ring.isEmpty)
        assertEquals(1, ring.position)
    }

    @Test
    fun `an empty watchlist draws nothing`() {
        assertTrue(symbolNeighbours(emptyList(), "XAUUSD").isEmpty)
    }

    @Test
    fun `a chart opened on something unstarred still steps into the reader's list`() {
        // Doing nothing here would be a control that is present and inert on exactly the charts a
        // reader reaches from search. The counter is zero, so no «۱ از ۴» is claimed.
        val ring = symbolNeighbours(list, "GBPUSD")

        assertEquals("BTCUSDT", ring.previous)
        assertEquals("XAUUSD", ring.next)
        assertEquals(0, ring.position)
        assertEquals(4, ring.total)
    }

    @Test
    fun `the current symbol is matched however it is cased`() {
        val ring = symbolNeighbours(list, "xagusd")

        assertEquals("XAUUSD", ring.previous)
        assertEquals("EURUSD", ring.next)
        assertEquals(2, ring.position)
    }

    @Test
    fun `one instrument listed twice is one place on the ring`() {
        val ring = symbolNeighbours(listOf("XAUUSD", "xauusd", "BTCUSDT"), "XAUUSD")

        assertEquals(2, ring.total)
        assertEquals("BTCUSDT", ring.next)
        assertNull(ring.previous)
    }

    @Test
    fun `a symbol this app has no artwork for never reaches the band`() {
        // The house rule, applied here as everywhere: no blank squares and no lettered discs.
        val ring = symbolNeighbours(listOf("XAUUSD", "ZZZQQQ", "BTCUSDT"), "XAUUSD")

        assertEquals(2, ring.total)
        assertEquals("BTCUSDT", ring.next)
    }

    // ── the flick, which is item 7 ────────────────────────────────────────────────────────────

    @Test
    fun `a flick forward is the next entry and a flick back is the previous one`() {
        assertEquals("EURUSD", symbolStep(list, "XAGUSD", 1))
        assertEquals("XAUUSD", symbolStep(list, "XAGUSD", -1))
    }

    @Test
    fun `the flick wraps at both ends, exactly as the taps do`() {
        // The wheel is a ring. A drag that stops dead on the last entry is a control the reader
        // reports as broken, and they are right to.
        assertEquals("XAUUSD", symbolStep(list, "BTCUSDT", 1))
        assertEquals("BTCUSDT", symbolStep(list, "XAUUSD", -1))
    }

    @Test
    fun `a hard flick lands the right number of places along`() {
        assertEquals("BTCUSDT", symbolStep(list, "XAUUSD", 3))
        // And past the end it keeps going round rather than clamping at the last entry.
        assertEquals("XAGUSD", symbolStep(list, "XAUUSD", 5))
        assertEquals("EURUSD", symbolStep(list, "XAUUSD", -2))
    }

    @Test
    fun `a flick from a symbol the reader never starred steps into their list`() {
        // Same rule as the neighbours: one gesture should take somebody *into* their watchlist
        // rather than do nothing at all.
        assertEquals("XAUUSD", symbolStep(list, "SOLUSDT", 1))
        assertEquals("BTCUSDT", symbolStep(list, "SOLUSDT", -1))
    }

    @Test
    fun `there is nowhere to flick to on a list of one, and nothing is emitted`() {
        // Null rather than the symbol already on screen. Emitting it would swap the controller for
        // the one it already holds and reload the chart the reader is looking at.
        assertNull(symbolStep(listOf("XAUUSD"), "XAUUSD", 1))
        assertNull(symbolStep(emptyList(), "XAUUSD", 1))
        assertNull(symbolStep(list, "XAUUSD", 0))
        // A whole turn of the ring is where you started, and that is not a switch either.
        assertNull(symbolStep(list, "XAUUSD", 4))
    }

    @Test
    fun `a symbol with no artwork is not a place the flick can land`() {
        assertEquals("BTCUSDT", symbolStep(listOf("XAUUSD", "ZZZQQQ", "BTCUSDT"), "XAUUSD", 1))
    }
}
