package com.coinepro.feature.dom

import com.coinepro.core.common.BidiText
import com.coinepro.core.orderbook.BookSide
import com.coinepro.core.orderbook.DepthLevel
import com.coinepro.core.orderbook.OrderBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `the curve is scaled against the loaded book, so a wall below the window still shows`() {
        // The change that made a hundred-level fetch worth making. Scaled to the window the
        // deepest visible rung would be full width whatever lies below it; scaled to the book it
        // says what it should — these two rows are a tenth of the resting size.
        val deep = book(
            bids = listOf(100.0 to 1.0, 99.0 to 1.0, 98.0 to 9.0, 97.0 to 9.0),
            asks = listOf(101.0 to 1.0, 102.0 to 1.0),
        )
        val narrow = ladderRows(deep, levels = 2)
        // Twenty on the bid side of the loaded book is the denominator, not the two on screen.
        assertEquals(0.05f, narrow.bids[0].curveFraction, 1e-6f)
        assertEquals(0.10f, narrow.bids[1].curveFraction, 1e-6f)
        // The bar keeps its own denominator — the visible window — so the two rungs on screen are
        // still compared against each other rather than vanishing under the wall below them.
        assertEquals(1.0f, narrow.bids[0].barFraction, 1e-6f)
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
        assertFalse("a book with no rungs has no counts to label", ladder.hasOrders)
    }

    @Test
    fun `an order count reaches the rung it belongs to, on the side it belongs to`() {
        // The ask side is turned over for display, which is exactly where a count can end up
        // beside the wrong price without anything looking wrong.
        val counted = OrderBook.of(
            symbol = "BTCUSDT",
            bids = listOf(DepthLevel(100.0, 1.0, orders = 3), DepthLevel(99.0, 1.0, orders = 4)),
            asks = listOf(DepthLevel(101.0, 1.0, orders = 5), DepthLevel(102.0, 1.0, orders = 6)),
            at = 1L,
        )
        val ladder = ladderRows(counted)
        assertTrue(ladder.hasOrders)
        assertEquals(listOf(3, 4), ladder.bids.map { it.orders })
        // Displayed top-down: 102 first, so its count comes first too.
        assertEquals(listOf(102.0, 101.0), ladder.asks.map { it.price })
        assertEquals(listOf(6, 5), ladder.asks.map { it.orders })
    }

    @Test
    fun `a venue that counts nothing produces no counts and no column to hold them`() {
        val ladder = ladderRows(book(bids = listOf(100.0 to 1.0), asks = listOf(101.0 to 1.0)))
        assertFalse(ladder.hasOrders)
        assertTrue(ladder.bids.all { it.orders == null })
        assertTrue(ladder.asks.all { it.orders == null })
    }

    @Test
    fun `one side counting is enough to label the column, so a half-counted book is not silent`() {
        val half = OrderBook.of(
            symbol = "BTCUSDT",
            bids = listOf(DepthLevel(100.0, 1.0, orders = 2)),
            asks = listOf(DepthLevel(101.0, 1.0)),
            at = 1L,
        )
        assertTrue(ladderRows(half).hasOrders)
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
    fun `the order count is Latin-digit, because it is a market figure and not a prose count`() {
        // `%d` through the device locale — Persian here — would emit ۱۲ silently, which is the one
        // number convention this app does not use for market figures.
        assertEquals("12", BidiText.strip(ordersLabel(12)))
        assertEquals("1", BidiText.strip(ordersLabel(1)))
        assertEquals("4096", BidiText.strip(ordersLabel(4_096)))
    }

    @Test
    fun `the staleness bound is printed in seconds with a decimal, because the bound is half a second`() {
        // Rounded to whole seconds the production bound prints as 0, which reads as "no age at
        // all" — the opposite of what an upper bound on staleness is for.
        assertEquals("0.5", BidiText.strip(maxAgeSecondsLabel(500L)))
        assertEquals("1.0", BidiText.strip(maxAgeSecondsLabel(1_000L)))
        assertEquals("2.5", BidiText.strip(maxAgeSecondsLabel(2_500L)))
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
