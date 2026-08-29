package com.coinepro.core.orderbook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bucket arithmetic, and the asymmetry underneath it.
 *
 * Aggregation is the one transform in this module that can change what the ladder *says* while
 * leaving it looking entirely plausible. A book folded with both sides rounded the same way still
 * draws sixteen tidy rungs with a spread across the middle; the spread is just tighter than the
 * market's. So most of what is asserted here is about direction rather than about totals.
 */
class PriceAggregationTest {

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
        maxAgeMillis = 500L,
    )

    @Test
    fun `bids fold down onto their bucket and asks fold up onto theirs`() {
        val folded = book(
            bids = listOf(100.4 to 1.0, 100.1 to 2.0, 99.6 to 3.0),
            asks = listOf(100.6 to 1.0, 100.9 to 2.0, 101.4 to 3.0),
        ).aggregated(1.0)
        // 100.4 and 100.1 both floor to 100; 99.6 floors to 99.
        assertEquals(listOf(100.0, 99.0), folded.bids.map { it.price })
        assertEquals(listOf(3.0, 3.0), folded.bids.map { it.quantity })
        // 100.6 and 100.9 both ceil to 101; 101.4 ceils to 102.
        assertEquals(listOf(101.0, 102.0), folded.asks.map { it.price })
        assertEquals(listOf(3.0, 3.0), folded.asks.map { it.quantity })
    }

    @Test
    fun `folding can only widen the spread, never narrow it and never cross the book`() {
        // The dangerous shape: a bid and an ask a hair either side of the same boundary. Rounded to
        // nearest they would both land on 100 and the ladder would print a spread of zero.
        val raw = book(bids = listOf(99.99 to 1.0), asks = listOf(100.01 to 1.0))
        val folded = raw.aggregated(1.0)
        assertEquals(99.0, folded.bestBid!!, 1e-9)
        assertEquals(101.0, folded.bestAsk!!, 1e-9)
        assertTrue(folded.spread!! > raw.spread!!)
        assertFalse(folded.crossed)
    }

    @Test
    fun `a level already sitting on a bucket boundary keeps its own price on both sides`() {
        val folded = book(bids = listOf(100.0 to 1.0), asks = listOf(101.0 to 1.0)).aggregated(1.0)
        assertEquals(100.0, folded.bestBid!!, 1e-9)
        assertEquals(101.0, folded.bestAsk!!, 1e-9)
    }

    @Test
    fun `a decimal step buckets exactly rather than to within a rounding error`() {
        // The failure this covers: `floor(price / 0.1) * 0.1` in doubles puts these on two prices a
        // hair apart, and the ladder draws two rungs where the venue has one bucket.
        val folded = book(
            bids = listOf(77_588.14 to 1.0, 77_588.11 to 2.0),
            asks = listOf(77_588.21 to 1.0),
        ).aggregated(0.1)
        assertEquals(listOf(77_588.1), folded.bids.map { it.price })
        assertEquals(3.0, folded.bids.single().quantity, 1e-9)
        assertEquals(listOf(77_588.3), folded.asks.map { it.price })
    }

    @Test
    fun `order counts are summed into the bucket and an uncounted bucket stays uncounted`() {
        val folded = OrderBook.of(
            symbol = "BTCUSDT",
            bids = listOf(DepthLevel(100.4, 1.0, orders = 3), DepthLevel(100.1, 2.0, orders = 4)),
            asks = listOf(DepthLevel(100.6, 1.0), DepthLevel(100.9, 2.0)),
            at = 1L,
        ).aggregated(1.0)
        assertEquals(7, folded.bids.single().orders)
        // Neither ask level carried a count, so the bucket must not acquire a zero: a rung showing
        // no orders beside a bar that is plainly there is worse than a rung showing nothing.
        assertNull(folded.asks.single().orders)
    }

    @Test
    fun `an empty side folds to an empty side rather than to a level at zero`() {
        val folded = book(bids = emptyList(), asks = listOf(100.6 to 1.0)).aggregated(1.0)
        assertTrue(folded.bids.isEmpty())
        assertEquals(listOf(101.0), folded.asks.map { it.price })
        assertNull(folded.spread)
    }

    @Test
    fun `a step larger than the book's whole range leaves one bucket a side and does not throw`() {
        val folded = book(
            bids = listOf(100.4 to 1.0, 100.3 to 1.0, 100.2 to 1.0),
            asks = listOf(100.6 to 1.0, 100.7 to 1.0, 100.8 to 1.0),
        ).aggregated(50.0)
        assertEquals(listOf(100.0), folded.bids.map { it.price })
        assertEquals(listOf(150.0), folded.asks.map { it.price })
        assertEquals(3.0, folded.bids.single().quantity, 1e-9)
        assertEquals(3.0, folded.asks.single().quantity, 1e-9)
        assertFalse(folded.crossed)
    }

    @Test
    fun `a step coarser than the cheapest bid is refused rather than flooring that bid onto zero`() {
        // Floored at a step of 1 the 0.4 bid becomes a level at zero, which `OrderBook.of` drops —
        // so applying this step would draw a market with no buyers in it. Only reachable from a
        // preference stored against another instrument, and refused outright when it is.
        val raw = book(bids = listOf(0.9 to 1.0, 0.4 to 2.0), asks = listOf(1.1 to 1.0))
        assertSame(raw, raw.aggregated(1.0))
    }

    @Test
    fun `no step at all is the raw book itself and not a copy of it`() {
        val raw = book(bids = listOf(100.0 to 1.0), asks = listOf(101.0 to 1.0))
        assertSame(raw, raw.aggregated(null))
        assertSame(raw, raw.aggregated(0.0))
        assertSame(raw, raw.aggregated(Double.NaN))
    }

    @Test
    fun `truncation and the cache bound survive folding`() {
        val folded = book(
            bids = listOf(100.4 to 1.0),
            asks = listOf(100.6 to 1.0),
            truncated = true,
        ).aggregated(1.0)
        assertTrue(folded.truncated)
        assertEquals(500L, folded.maxAgeMillis)
    }

    @Test
    fun `folding preserves every unit of resting size on both sides`() {
        val raw = book(
            bids = (0 until 40).map { (100.0 - it * 0.1) to (it + 1).toDouble() },
            asks = (0 until 40).map { (100.5 + it * 0.1) to (it + 1).toDouble() },
        )
        val folded = raw.aggregated(1.0)
        assertEquals(raw.bidVolume, folded.bidVolume, 1e-6)
        assertEquals(raw.askVolume, folded.askVolume, 1e-6)
    }

    @Test
    fun `the tick is the finest gap the book actually shows`() {
        val measured = book(
            bids = listOf(100.0 to 1.0, 99.9 to 1.0, 99.5 to 1.0),
            asks = listOf(100.2 to 1.0, 100.5 to 1.0),
        )
        assertEquals(0.1, measured.inferredTick()!!, 1e-9)
    }

    @Test
    fun `a book with nothing to measure a gap across reports no tick`() {
        assertNull(book(bids = listOf(100.0 to 1.0), asks = listOf(101.0 to 1.0)).inferredTick())
        assertNull(OrderBook.empty("BTCUSDT").inferredTick())
    }

    @Test
    fun `the offered steps walk the round ladder above the tick and stop at the depth fetched`() {
        val btc = book(
            bids = (0 until 20).map { (77_588.0 - it * 0.1) to 1.0 },
            asks = (0 until 20).map { (77_588.1 + it * 0.1) to 1.0 },
        )
        // Tick 0.1, so the ladder is 0.5, 1, 5, 10 and stops there: a hundred levels of 0.1 span
        // ten, and a coarser step would fold the whole loaded book into one rung.
        assertEquals(listOf(0.5, 1.0, 5.0, 10.0), aggregationSteps(btc))
    }

    @Test
    fun `a sub-unit instrument gets a sub-unit ladder`() {
        val alt = book(
            bids = (0 until 20).map { (0.5241 - it * 0.0001) to 1.0 },
            asks = (0 until 20).map { (0.5242 + it * 0.0001) to 1.0 },
        )
        assertEquals(listOf(0.0005, 0.001, 0.005, 0.01), aggregationSteps(alt))
    }

    @Test
    fun `an off-ladder tick snaps down so the chips stay round`() {
        // A book whose finest gap is 0.3 must not offer 1.5 and 3; it offers the 0.1 ladder, which
        // is the largest round base that cannot claim a coarser granularity than the book showed.
        val sparse = book(
            bids = listOf(100.0 to 1.0, 99.7 to 1.0, 99.1 to 1.0),
            asks = listOf(100.4 to 1.0, 101.0 to 1.0),
        )
        assertEquals(0.3, sparse.inferredTick()!!, 1e-9)
        assertEquals(listOf(0.5, 1.0, 5.0, 10.0), aggregationSteps(sparse))
    }

    @Test
    fun `a book too thin to show a tick offers no steps`() {
        assertEquals(emptyList<Double>(), aggregationSteps(OrderBook.empty("BTCUSDT")))
    }

    @Test
    fun `a step the reader already chose is kept on the list this book would not have offered`() {
        val thin = OrderBook.empty("BTCUSDT")
        assertEquals(listOf(2.5), aggregationSteps(thin, keep = 2.5))

        val btc = book(
            bids = (0 until 20).map { (77_588.0 - it * 0.1) to 1.0 },
            asks = (0 until 20).map { (77_588.1 + it * 0.1) to 1.0 },
        )
        assertEquals(listOf(0.5, 1.0, 2.5, 5.0, 10.0), aggregationSteps(btc, keep = 2.5))
        // Already on the ladder: kept once, not twice.
        assertEquals(listOf(0.5, 1.0, 5.0, 10.0), aggregationSteps(btc, keep = 5.0))
    }

    @Test
    fun `every offered step is coarser than the tick it was derived from`() {
        val btc = book(
            bids = (0 until 20).map { (77_588.0 - it * 0.1) to 1.0 },
            asks = (0 until 20).map { (77_588.1 + it * 0.1) to 1.0 },
        )
        val tick = btc.inferredTick()!!
        assertTrue(aggregationSteps(btc).all { it > tick })
    }

    @Test
    fun `the price column takes exactly the decimals the step writes`() {
        assertEquals(1, aggregationDecimals(0.1))
        assertEquals(1, aggregationDecimals(0.5))
        assertEquals(0, aggregationDecimals(1.0))
        assertEquals(0, aggregationDecimals(10.0))
        assertEquals(0, aggregationDecimals(100.0))
        assertEquals(4, aggregationDecimals(0.0005))
        assertEquals(8, aggregationDecimals(0.00000001))
    }

    @Test
    fun `the ladder base is the round value at or below what it is given`() {
        assertEquals(0.1, ladderBase(0.1), 1e-12)
        assertEquals(0.1, ladderBase(0.3), 1e-12)
        assertEquals(0.5, ladderBase(0.5), 1e-12)
        assertEquals(0.5, ladderBase(0.9), 1e-12)
        assertEquals(1.0, ladderBase(1.0), 1e-12)
        assertEquals(0.0001, ladderBase(0.0001), 1e-15)
    }
}
