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
}
