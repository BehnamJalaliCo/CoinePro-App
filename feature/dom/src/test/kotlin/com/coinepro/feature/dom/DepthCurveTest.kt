package com.coinepro.feature.dom

import com.coinepro.core.orderbook.DepthLevel
import com.coinepro.core.orderbook.OrderBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape of the data the curve is drawn from.
 *
 * A depth curve is one of the few pictures on this screen with no figures printed on it, so nothing
 * about it is checkable by eye — a curve that falls away from the touch, or one whose two sides are
 * scaled differently, renders as a perfectly clean shape that says the wrong thing about where the
 * size is. Everything asserted here is a property the renderer cannot check for itself.
 */
class DepthCurveTest {

    private fun book(
        bids: List<Pair<Double, Double>>,
        asks: List<Pair<Double, Double>>,
    ) = OrderBook.of(
        symbol = "BTCUSDT",
        bids = bids.map { DepthLevel(it.first, it.second) },
        asks = asks.map { DepthLevel(it.first, it.second) },
        at = 1L,
    )

    private val balanced = book(
        bids = listOf(100.0 to 1.0, 99.0 to 2.0, 98.0 to 3.0),
        asks = listOf(102.0 to 1.0, 103.0 to 2.0, 104.0 to 3.0),
    )

    @Test
    fun `each side keeps the book's order, walking outward from the touch`() {
        val curve = depthCurve(balanced)!!
        assertEquals(listOf(100.0, 99.0, 98.0), curve.bids.map { it.price })
        assertEquals(listOf(102.0, 103.0, 104.0), curve.asks.map { it.price })
    }

    @Test
    fun `the running total only ever grows away from the spread`() {
        val curve = depthCurve(balanced)!!
        // The one shape a depth curve must never draw is a fall away from the touch. Asserted on
        // both the totals and the plotted heights, because the mapping is where an inversion would
        // be introduced.
        assertTrue(curve.bids.zipWithNext().all { (near, far) -> far.total >= near.total })
        assertTrue(curve.asks.zipWithNext().all { (near, far) -> far.total >= near.total })
        assertTrue(curve.bids.zipWithNext().all { (near, far) -> far.y >= near.y })
        assertTrue(curve.asks.zipWithNext().all { (near, far) -> far.y >= near.y })
    }

    @Test
    fun `the totals are the book's own and are not summed a second time here`() {
        val curve = depthCurve(balanced)!!
        assertEquals(listOf(1.0, 3.0, 6.0), curve.bids.map { it.total })
        assertEquals(listOf(1.0, 3.0, 6.0), curve.asks.map { it.total })
    }

    @Test
    fun `buys sit left of the middle and sells right of it, with the mid on the seam`() {
        val curve = depthCurve(balanced)!!
        assertEquals(101.0, curve.mid, 1e-9)
        assertTrue(curve.bids.all { it.x <= 0.5f })
        assertTrue(curve.asks.all { it.x >= 0.5f })
        // Outward from the touch is outward from the middle in both directions.
        assertTrue(curve.bids.zipWithNext().all { (near, far) -> far.x <= near.x })
        assertTrue(curve.asks.zipWithNext().all { (near, far) -> far.x >= near.x })
    }

    @Test
    fun `the price axis is symmetric about the mid`() {
        val curve = depthCurve(balanced)!!
        assertEquals(curve.mid - curve.lowPrice, curve.highPrice - curve.mid, 1e-9)
    }

    @Test
    fun `both sides share one half-width, so the shallower one stops short of its edge`() {
        // The sell side reaches three away from the mid and the buy side only one. Given its own
        // half-width the buy side would stretch to the left edge and the picture would say the two
        // sides reach equally far, which is the single thing the curve exists to disprove.
        val lopsided = book(
            bids = listOf(100.0 to 1.0, 99.5 to 1.0),
            asks = listOf(101.0 to 1.0, 104.0 to 1.0),
        )
        val curve = depthCurve(lopsided)!!
        assertEquals(100.5, curve.mid, 1e-9)
        assertEquals(3.5, curve.highPrice - curve.mid, 1e-9)
        assertEquals(97.0, curve.lowPrice, 1e-9)
        assertTrue("the shallow side must not reach its edge", curve.bids.last().x > 0f)
        assertEquals(1f, curve.asks.last().x, 1e-6f)
    }

    @Test
    fun `the heavier side reaches full height and the lighter one is a share of it`() {
        val lopsided = book(
            bids = listOf(100.0 to 3.0, 99.0 to 3.0),
            asks = listOf(101.0 to 1.0, 102.0 to 1.0),
        )
        val curve = depthCurve(lopsided)!!
        assertEquals(6.0, curve.peakTotal, 1e-9)
        assertEquals(1f, curve.bids.last().y, 1e-6f)
        // Two against six, not two against two: one denominator, or the two sides' heights say
        // nothing about which side is heavy.
        assertEquals(2f / 6f, curve.asks.last().y, 1e-6f)
    }

    @Test
    fun `every plotted coordinate stays inside the canvas`() {
        val curve = depthCurve(balanced)!!
        assertTrue((curve.bids + curve.asks).all { it.x in 0f..1f && it.y in 0f..1f })
    }

    @Test
    fun `a one-sided book has no curve at all`() {
        // Not an empty plot with axes on it: a curve of buyers against an empty half reads as an
        // absence of sellers rather than as an absence of data.
        assertNull(depthCurve(book(bids = listOf(100.0 to 1.0), asks = emptyList())))
        assertNull(depthCurve(book(bids = emptyList(), asks = listOf(101.0 to 1.0))))
        assertNull(depthCurve(OrderBook.empty("BTCUSDT")))
    }

    @Test
    fun `a crossed book has no curve either`() {
        // The axis collapses, and the ladder has already said in words that the book is momentarily
        // inconsistent. A picture of it would be a second, wordless claim.
        val crossed = OrderBook(
            symbol = "BTCUSDT",
            bids = listOf(DepthLevel(101.0, 1.0), DepthLevel(100.0, 1.0)),
            asks = listOf(DepthLevel(100.5, 1.0), DepthLevel(101.5, 1.0)),
            at = 1L,
            truncated = false,
        )
        assertNull(depthCurve(crossed))
    }

    @Test
    fun `a book of a single level a side still has a curve, because the spread gives it a width`() {
        val curve = depthCurve(book(bids = listOf(100.0 to 1.0), asks = listOf(101.0 to 2.0)))
        assertNotNull(curve)
        // One point a side is a shape the renderer will decline to stroke, and that is its decision
        // to make from the list it is handed. The data is well formed and says so rather than
        // vanishing here, where the reason would be invisible.
        assertEquals(1, curve!!.bids.size)
        assertEquals(2.0, curve.peakTotal, 1e-9)
    }
}
