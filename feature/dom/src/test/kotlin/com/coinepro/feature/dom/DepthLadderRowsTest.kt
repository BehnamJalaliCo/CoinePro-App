package com.coinepro.feature.dom

import com.coinepro.core.common.BidiText
import com.coinepro.core.orderbook.BookSide
import com.coinepro.core.orderbook.DepthLevel
import com.coinepro.core.orderbook.OrderBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic the ladder draws from.
 *
 * Every assertion here is about something that is invisible when it is wrong: a side turned the
 * wrong way up, a bar scaled against the wrong denominator, a column of prices with the decimal
 * points out of line. The ladder renders identically in all of those cases and means something
 * different in each.
 */
class DepthLadderRowsTest {

    private fun book(
        bids: List<Pair<Double, Double>>,
        asks: List<Pair<Double, Double>>,
    ) = OrderBook.of(
        symbol = "BTCUSDT",
        bids = bids.map { DepthLevel(it.first, it.second) },
        asks = asks.map { DepthLevel(it.first, it.second) },
        at = 1L,
    )

    @Test
    fun `sells are turned over for the ladder so the highest price sits at the top`() {
        val ladder = ladderRows(
            book(
                bids = listOf(100.0 to 1.0),
                asks = listOf(101.0 to 1.0, 102.0 to 1.0, 103.0 to 1.0),
            ),
        )
        // Top of the screen down to the spread.
        assertEquals(listOf(103.0, 102.0, 101.0), ladder.asks.map { it.price })
        // Buys keep the book's own order: the spread is at the top of that block too.
        assertEquals(listOf(100.0), ladder.bids.map { it.price })
        assertTrue(ladder.asks.all { it.side == BookSide.ASK })
        assertTrue(ladder.bids.all { it.side == BookSide.BID })
    }

    @Test
    fun `the largest visible level fills its bar and everything else is a share of it`() {
        val ladder = ladderRows(
            book(
                bids = listOf(100.0 to 10.0, 99.0 to 5.0, 98.0 to 1.0),
                asks = listOf(101.0 to 2.0),
            ),
        )
        assertEquals(1.0f, ladder.bids[0].barFraction, 1e-6f)
        assertEquals(0.5f, ladder.bids[1].barFraction, 1e-6f)
        assertEquals(0.1f, ladder.bids[2].barFraction, 1e-6f)
        // Scaled across both sides, not per side: the two-lot ask must not draw as a full bar
        // beside the ten-lot bid.
        assertEquals(0.2f, ladder.asks[0].barFraction, 1e-6f)
    }

    @Test
    fun `the curve grows away from the spread and reaches full width at the heavier side's total`() {
        val ladder = ladderRows(
            book(
                bids = listOf(100.0 to 2.0, 99.0 to 3.0, 98.0 to 5.0),
                asks = listOf(101.0 to 1.0),
            ),
        )
        // Ten on the bid side, one on the ask, so the bid's deepest rung is the full-width point.
        assertEquals(0.2f, ladder.bids[0].curveFraction, 1e-6f)
        assertEquals(0.5f, ladder.bids[1].curveFraction, 1e-6f)
        assertEquals(1.0f, ladder.bids[2].curveFraction, 1e-6f)
        assertEquals(0.1f, ladder.asks[0].curveFraction, 1e-6f)
    }

    @Test
    fun `bars are scaled against the rows on screen, not against levels below the window`() {
        // A wall four levels down must not shrink every visible bar for a reason nothing on the
        // screen explains.
        val deep = book(
            bids = listOf(100.0 to 1.0, 99.0 to 1.0, 98.0 to 400.0),
            asks = listOf(101.0 to 1.0, 102.0 to 1.0, 103.0 to 400.0),
        )
        val narrow = ladderRows(deep, levels = 2)
        assertEquals(1.0f, narrow.bids[0].barFraction, 1e-6f)
        assertEquals(1.0f, narrow.asks[0].barFraction, 1e-6f)
        assertEquals(2, narrow.bids.size)
        assertEquals(2, narrow.asks.size)
        // And the ladder's own book knows it is a window, so the header can say so.
        assertTrue(narrow.book.truncated)
    }

    @Test
    fun `an empty book produces no rungs and no division by zero`() {
        val ladder = ladderRows(OrderBook.empty("BTCUSDT"))
        assertTrue(ladder.asks.isEmpty())
        assertTrue(ladder.bids.isEmpty())
        assertEquals(2, ladder.priceDecimals)
    }

    @Test
    fun `the whole price column takes one decimal choice, from the mid`() {
        // Levels straddling a magnitude step would otherwise print 0.5241 above 0.52 and the
        // decimal points would stop lining up in the one column that is read vertically.
        val cheap = ladderRows(book(bids = listOf(0.5241 to 1.0), asks = listOf(0.5248 to 1.0)))
        assertEquals(4, cheap.priceDecimals)

        val dear = ladderRows(book(bids = listOf(64_182.40 to 1.0), asks = listOf(64_182.90 to 1.0)))
        assertEquals(2, dear.priceDecimals)
    }

    @Test
    fun `an absent price gets two decimals rather than a precision claim about nothing`() {
        assertEquals(2, priceDecimalsFor(0.0))
    }

    @Test
    fun `size decimals follow the largest level, so small books keep their digits`() {
        assertEquals(5, quantityDecimalsFor(0.004))
        assertEquals(3, quantityDecimalsFor(1.5))
        assertEquals(1, quantityDecimalsFor(240.0))
        assertEquals(0, quantityDecimalsFor(48_000.0))
    }

    @Test
    fun `the share label is Latin-digit whatever the device locale is`() {
        // The device locale here is Persian, and a String.format without Locale.US emits Persian
        // digits silently. Stripped of the bidi isolate, this has to be plain ASCII.
        assertEquals("62%", BidiText.strip(percentLabel(0.62)))
        assertEquals("100%", BidiText.strip(percentLabel(1.0)))
        assertEquals("0%", BidiText.strip(percentLabel(0.0)))
    }
}
