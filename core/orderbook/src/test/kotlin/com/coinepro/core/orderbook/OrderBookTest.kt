package com.coinepro.core.orderbook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The book's arithmetic, and the ordering rule underneath all of it.
 *
 * The ordering tests are not ceremony. Every other number here — spread, mid, the depth curve, the
 * bar the ladder scales — is read off the *first* element of a side, so a book sorted the wrong way
 * produces plausible values for all of them and a picture that says the opposite of the truth.
 */
class OrderBookTest {

    private fun book(
        bids: List<Pair<Double, Double>>,
        asks: List<Pair<Double, Double>>,
        truncated: Boolean = false,
    ) = OrderBook.of(
        symbol = "BTCUSDT",
        bids = bids.map { DepthLevel(it.first, it.second) },
        asks = asks.map { DepthLevel(it.first, it.second) },
        at = 1_756_400_000_000L,
        truncated = truncated,
    )

    /** The band the screen reads pressure over. Named here so the tests below say which one. */
    private val band = OrderBookGateway.IMBALANCE_LEVELS

    @Test
    fun `bids come back descending however they arrived`() {
        val sorted = book(
            bids = listOf(64_180.0 to 1.0, 64_182.0 to 2.0, 64_181.0 to 3.0),
            asks = listOf(64_183.0 to 1.0),
        )
        assertEquals(listOf(64_182.0, 64_181.0, 64_180.0), sorted.bids.map { it.price })
        assertEquals(64_182.0, sorted.bestBid!!, 1e-9)
    }

    @Test
    fun `asks come back ascending however they arrived`() {
        val sorted = book(
            bids = listOf(64_180.0 to 1.0),
            asks = listOf(64_185.0 to 1.0, 64_183.0 to 2.0, 64_184.0 to 3.0),
        )
        assertEquals(listOf(64_183.0, 64_184.0, 64_185.0), sorted.asks.map { it.price })
        assertEquals(64_183.0, sorted.bestAsk!!, 1e-9)
    }

    @Test
    fun `a book built with the sides sorted the wrong way is refused rather than drawn`() {
        // The guard that makes the two tests above worth having. A ladder cannot detect this by
        // eye, so the constructor has to.
        val ascendingBids = listOf(DepthLevel(64_180.0, 1.0), DepthLevel(64_182.0, 1.0))
        val ascendingAsks = listOf(DepthLevel(64_183.0, 1.0), DepthLevel(64_184.0, 1.0))
        var refused = false
        try {
            OrderBook("BTCUSDT", ascendingBids, ascendingAsks, at = 0L, truncated = false)
        } catch (error: IllegalArgumentException) {
            refused = true
        }
        assertTrue("an ascending bid side must not construct", refused)

        var asksRefused = false
        try {
            OrderBook("BTCUSDT", emptyList(), ascendingAsks.reversed(), at = 0L, truncated = false)
        } catch (error: IllegalArgumentException) {
            asksRefused = true
        }
        assertTrue("a descending ask side must not construct", asksRefused)
    }

    @Test
    fun `spread is the ask over the bid and the mid sits between them`() {
        val quoted = book(bids = listOf(64_182.40 to 1.0), asks = listOf(64_182.90 to 1.0))
        assertEquals(0.50, quoted.spread!!, 1e-9)
        assertEquals(64_182.65, quoted.midPrice!!, 1e-9)
        assertFalse(quoted.crossed)
    }

    @Test
    fun `a one-sided book has no spread and no mid rather than a zero`() {
        val bidsOnly = book(bids = listOf(64_182.0 to 1.0), asks = emptyList())
        assertNull(bidsOnly.spread)
        assertNull(bidsOnly.midPrice)
        assertEquals(64_182.0, bidsOnly.bestBid!!, 1e-9)
        assertNull(bidsOnly.bestAsk)
    }

    @Test
    fun `a book whose bid has reached its ask is reported as crossed`() {
        val stale = book(bids = listOf(64_183.0 to 1.0), asks = listOf(64_182.0 to 1.0))
        assertTrue(stale.crossed)
        // The spread is still computed, and it is negative. The screen reads `crossed` first;
        // hiding the number would leave nothing to diagnose the relay with.
        assertTrue(stale.spread!! < 0.0)
    }

    @Test
    fun `imbalance is one when only bids rest and zero when only asks do`() {
        val allBid = book(bids = listOf(100.0 to 5.0, 99.0 to 5.0), asks = emptyList())
        assertEquals(1.0, allBid.imbalance(band)!!, 1e-9)

        val allAsk = book(bids = emptyList(), asks = listOf(101.0 to 5.0, 102.0 to 5.0))
        assertEquals(0.0, allAsk.imbalance(band)!!, 1e-9)
    }

    @Test
    fun `imbalance is a half at parity`() {
        val even = book(bids = listOf(100.0 to 3.0, 99.0 to 1.0), asks = listOf(101.0 to 2.0, 102.0 to 2.0))
        assertEquals(0.5, even.imbalance(band)!!, 1e-9)
    }

    @Test
    fun `imbalance leans toward whichever side is heavier`() {
        val heavyBid = book(bids = listOf(100.0 to 9.0), asks = listOf(101.0 to 1.0))
        assertEquals(0.9, heavyBid.imbalance(band)!!, 1e-9)
    }

    @Test
    fun `an empty book has no imbalance rather than a balanced one`() {
        // An empty book and a perfectly matched one are the same number and opposite facts.
        assertNull(book(bids = emptyList(), asks = emptyList()).imbalance(band))
    }

    @Test
    fun `cumulative totals grow away from the touch and end at the side's volume`() {
        val depth = book(
            bids = listOf(100.0 to 2.0, 99.0 to 3.0, 98.0 to 5.0),
            asks = listOf(101.0 to 1.0, 102.0 to 4.0),
        )
        val bidCurve = depth.cumulative(BookSide.BID)
        assertEquals(listOf(2.0, 5.0, 10.0), bidCurve.map { it.total })
        // The curve walks outwards: the first point is the best bid, not the deepest level.
        assertEquals(100.0, bidCurve.first().price, 1e-9)
        assertEquals(98.0, bidCurve.last().price, 1e-9)
        assertEquals(depth.bidVolume, bidCurve.last().total, 1e-9)

        val askCurve = depth.cumulative(BookSide.ASK)
        assertEquals(listOf(1.0, 5.0), askCurve.map { it.total })
        assertEquals(depth.askVolume, askCurve.last().total, 1e-9)
    }

    @Test
    fun `an empty side has an empty curve rather than a zero point`() {
        assertTrue(book(bids = emptyList(), asks = listOf(101.0 to 1.0)).cumulative(BookSide.BID).isEmpty())
    }

    @Test
    fun `the bar scale is the largest level across both sides, not per side`() {
        // Scaling each side to its own maximum would draw a two-lot bid and a forty-lot ask the
        // same length, which erases the only thing the ladder is for.
        val lopsided = book(bids = listOf(100.0 to 2.0), asks = listOf(101.0 to 40.0))
        assertEquals(40.0, lopsided.largestQuantity, 1e-9)
        assertEquals(40.0, lopsided.largestCumulative, 1e-9)
    }

    @Test
    fun `two rows at one price become one rung carrying both`() {
        val merged = book(bids = listOf(100.0 to 2.0, 100.0 to 3.0), asks = listOf(101.0 to 1.0))
        assertEquals(1, merged.bids.size)
        assertEquals(5.0, merged.bids.first().quantity, 1e-9)
    }

    @Test
    fun `a zero quantity is a removal instruction, not a rung`() {
        val cleaned = book(bids = listOf(100.0 to 2.0, 99.0 to 0.0), asks = listOf(101.0 to 1.0))
        assertEquals(listOf(100.0), cleaned.bids.map { it.price })
    }

    @Test
    fun `a row the relay could not fill is dropped rather than floored at zero`() {
        val cleaned = book(
            bids = listOf(100.0 to 2.0, Double.NaN to 1.0, 0.0 to 4.0),
            asks = listOf(101.0 to Double.NaN, 102.0 to 1.0),
        )
        assertEquals(listOf(100.0), cleaned.bids.map { it.price })
        assertEquals(listOf(102.0), cleaned.asks.map { it.price })
    }

    @Test
    fun `narrowing to the visible rows keeps the ones nearest the spread and says it cut`() {
        val wide = book(
            bids = listOf(100.0 to 1.0, 99.0 to 1.0, 98.0 to 1.0),
            asks = listOf(101.0 to 1.0, 102.0 to 1.0, 103.0 to 1.0),
        )
        val narrowed = wide.top(2)
        assertEquals(listOf(100.0, 99.0), narrowed.bids.map { it.price })
        assertEquals(listOf(101.0, 102.0), narrowed.asks.map { it.price })
        assertTrue(narrowed.truncated)
    }

    @Test
    fun `narrowing to more rows than the book holds changes nothing and claims nothing`() {
        val small = book(bids = listOf(100.0 to 1.0), asks = listOf(101.0 to 1.0))
        val narrowed = small.top(8)
        assertEquals(small, narrowed)
        assertFalse(narrowed.truncated)
    }

    @Test
    fun `imbalance reads only the band it is given, so a deep wall cannot swing the meter`() {
        // The reason the fetch got wider and the reading did not. Nine lots of resting bid sit
        // three levels out; over two levels a side the market is balanced, and it is balanced,
        // because nothing at 97 is going to trade in the next second.
        val deepWall = book(
            bids = listOf(100.0 to 1.0, 99.0 to 1.0, 98.0 to 9.0, 97.0 to 9.0),
            asks = listOf(101.0 to 1.0, 102.0 to 1.0, 103.0 to 1.0, 104.0 to 1.0),
        )
        assertEquals(0.5, deepWall.imbalance(2)!!, 1e-9)
        // Widened to everything loaded, the same book reads as heavily bid — a different claim,
        // which is exactly why the band is a parameter the caller has to name.
        assertEquals(20.0 / 24.0, deepWall.imbalance(8)!!, 1e-9)
    }

    @Test
    fun `a band wider than the book is the whole book rather than an error`() {
        val small = book(bids = listOf(100.0 to 3.0), asks = listOf(101.0 to 1.0))
        assertEquals(0.75, small.imbalance(500)!!, 1e-9)
    }

    @Test
    fun `a band of zero or fewer is refused, because a share of nothing is not a share`() {
        var refused = false
        try {
            book(bids = listOf(100.0 to 1.0), asks = listOf(101.0 to 1.0)).imbalance(0)
        } catch (error: IllegalArgumentException) {
            refused = true
        }
        assertTrue("a non-positive band must not silently answer null", refused)
    }

    @Test
    fun `an order count rides through with its level and is not invented where absent`() {
        val counted = OrderBook.of(
            symbol = "BTCUSDT",
            bids = listOf(DepthLevel(100.0, 2.0, orders = 7)),
            asks = listOf(DepthLevel(101.0, 1.0)),
            at = 0L,
        )
        assertEquals(7, counted.bids.first().orders)
        // The venue said nothing about this side, and nothing is what comes back — not zero, which
        // would draw as a level with no orders behind a quantity that is plainly there.
        assertNull(counted.asks.first().orders)
    }

    @Test
    fun `two rows at one price merge their order counts along with their sizes`() {
        val merged = OrderBook.of(
            symbol = "BTCUSDT",
            bids = listOf(DepthLevel(100.0, 2.0, orders = 3), DepthLevel(100.0, 3.0, orders = 4)),
            asks = emptyList(),
            at = 0L,
        )
        assertEquals(1, merged.bids.size)
        assertEquals(5.0, merged.bids.first().quantity, 1e-9)
        assertEquals(7, merged.bids.first().orders)
    }

    @Test
    fun `merging a counted row with an uncounted one keeps the count it actually has`() {
        // Summing with a zero for the silent row would understate nothing here, but reading the
        // absence as zero on a book where nobody counts is how a column of zeroes appears.
        val mixed = OrderBook.of(
            symbol = "BTCUSDT",
            bids = listOf(DepthLevel(100.0, 2.0, orders = 3), DepthLevel(100.0, 1.0)),
            asks = emptyList(),
            at = 0L,
        )
        assertEquals(3, mixed.bids.first().orders)
    }

    @Test
    fun `the staleness bound is carried separately from the venue time and neither fills the other`() {
        val bounded = OrderBook.of(
            symbol = "BTCUSDT",
            bids = listOf(DepthLevel(100.0, 1.0)),
            asks = listOf(DepthLevel(101.0, 1.0)),
            at = 0L,
            maxAgeMillis = 500L,
        )
        // No venue timestamp, which is the crypto case: LBank's futures book publishes none.
        assertEquals(0L, bounded.at)
        assertEquals(500L, bounded.maxAgeMillis)

        // And a book with no declared bound says so rather than claiming a small one.
        assertNull(book(bids = listOf(100.0 to 1.0), asks = listOf(101.0 to 1.0)).maxAgeMillis)
    }

    @Test
    fun `narrowing to the visible rows keeps the staleness bound, which belongs to the fetch`() {
        val wide = OrderBook.of(
            symbol = "BTCUSDT",
            bids = listOf(DepthLevel(100.0, 1.0), DepthLevel(99.0, 1.0)),
            asks = listOf(DepthLevel(101.0, 1.0), DepthLevel(102.0, 1.0)),
            at = 0L,
            maxAgeMillis = 500L,
        )
        assertEquals(500L, wide.top(1).maxAgeMillis)
    }
}
