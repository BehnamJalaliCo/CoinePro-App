package com.coinepro.feature.dom

import com.coinepro.core.common.BidiText
import com.coinepro.core.orderbook.BookSide
import com.coinepro.core.orderbook.DepthLevel
import com.coinepro.core.orderbook.OrderBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /** A bare rung, for the assertions that are about one field and not about a whole ladder. */
    private fun rung(orders: Int?) = LadderRow(
        price = 1.0,
        quantity = 1.0,
        total = 1.0,
        side = BookSide.BID,
        barFraction = 0f,
        cumulativeBarFraction = 0f,
        curveFraction = 0f,
        orders = orders,
    )

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
        assertFalse("a book with no rungs has nothing to explain", ladder.hasOrders)
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
    fun `a venue that counts nothing produces no counts and no note explaining them`() {
        val ladder = ladderRows(book(bids = listOf(100.0 to 1.0), asks = listOf(101.0 to 1.0)))
        assertFalse(ladder.hasOrders)
        assertTrue(ladder.bids.all { it.orders == null })
        assertTrue(ladder.asks.all { it.orders == null })
    }

    @Test
    fun `one side counting is enough for the note, so a half-counted book is not silent`() {
        val half = OrderBook.of(
            symbol = "BTCUSDT",
            bids = listOf(DepthLevel(100.0, 1.0, orders = 2)),
            asks = listOf(DepthLevel(101.0, 1.0)),
            at = 1L,
        )
        assertTrue(ladderRows(half).hasOrders)
    }

    @Test
    fun `a stacked level is marked and an ordinary single-order one is not`() {
        // Roughly 88% of this venue's levels hold exactly one order, so a count drawn on every row
        // is a column of ones that buries the rare stacked one. Only the exceptions are marked.
        val counted = OrderBook.of(
            symbol = "BTCUSDT",
            bids = listOf(
                DepthLevel(100.0, 1.0, orders = 1),
                DepthLevel(99.0, 1.0, orders = STACKED_ORDERS_THRESHOLD),
                DepthLevel(98.0, 1.0, orders = 27),
            ),
            asks = listOf(DepthLevel(101.0, 1.0, orders = 1)),
            at = 1L,
        )
        val ladder = ladderRows(counted)

        assertFalse("a single order is the ordinary case and takes no mark", ladder.bids[0].stacked)
        assertTrue("the threshold itself is stacked, not one above it", ladder.bids[1].stacked)
        assertTrue(ladder.bids[2].stacked)
        assertFalse(ladder.asks[0].stacked)

        // And what is drawn follows the mark exactly: nothing on the ones, the real count on the
        // rest. A mark that read "many" rather than "27" would lose the difference between a wall
        // two participants are holding and one that twenty-seven are.
        assertNull(drawnOrders(ladder.bids[0]))
        assertEquals(STACKED_ORDERS_THRESHOLD, drawnOrders(ladder.bids[1]))
        assertEquals(27, drawnOrders(ladder.bids[2]))
    }

    @Test
    fun `every rung that has a count speaks it, including the ones that draw nothing`() {
        // The column went away because a figure reading 1 nine times out of ten crowds a sighted
        // ladder. None of that reasoning applies to a description read one row at a time, and a
        // reader who cannot see the ladder must not lose seven counts in eight to a layout choice.
        val counted = OrderBook.of(
            symbol = "BTCUSDT",
            bids = listOf(DepthLevel(100.0, 1.0, orders = 1), DepthLevel(99.0, 1.0, orders = 6)),
            asks = listOf(DepthLevel(101.0, 1.0, orders = 1)),
            at = 1L,
        )
        val ladder = ladderRows(counted)

        assertEquals(1, spokenOrders(ladder.bids[0]))
        assertEquals(6, spokenOrders(ladder.bids[1]))
        assertEquals(1, spokenOrders(ladder.asks[0]))
        // The two answers diverge on exactly the rows they are supposed to: drawn is quiet, spoken
        // is complete.
        assertNull(drawnOrders(ladder.bids[0]))
        assertEquals(6, drawnOrders(ladder.bids[1]))
    }

    @Test
    fun `an unknown count is never a mark and never spoken as a single order`() {
        // TradeYar omit the third element when they do not know the count rather than sending 0, so
        // null here means "not known". Absent and one are different facts about a price: one order
        // of forty is a single participant who can withdraw the wall in one message, and a level
        // nobody counted says nothing at all about that.
        val partly = OrderBook.of(
            symbol = "BTCUSDT",
            bids = listOf(DepthLevel(100.0, 1.0), DepthLevel(99.0, 1.0, orders = 5)),
            asks = listOf(DepthLevel(101.0, 1.0)),
            at = 1L,
        )
        val ladder = ladderRows(partly)

        assertFalse("an unknown count is not evidence of a single order", ladder.bids[0].stacked)
        assertNull(drawnOrders(ladder.bids[0]))
        assertNull("silence, not the number one", spokenOrders(ladder.bids[0]))
        assertNull(spokenOrders(ladder.asks[0]))
        // The counted rung beside it is unaffected: this is per level, not per side.
        assertEquals(5, spokenOrders(ladder.bids[1]))
        assertTrue(ladder.bids[1].stacked)
    }

    @Test
    fun `the threshold sits above the single-order case and inside the range this venue actually shows`() {
        // Pinned as behaviour rather than as the literal 2: what must hold is that one is ordinary
        // and that the stacked levels TradeYar measured — their 2-to-4 bucket and the 27 at the top
        // of it — all clear the bar. A threshold above their bucket would mark almost nothing.
        assertFalse(rung(orders = 1).stacked)
        assertTrue(rung(orders = 4).stacked)
        assertTrue(rung(orders = 27).stacked)
        assertFalse(rung(orders = null).stacked)
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
    @Test
    fun `the cumulative figure on a rung is the book's own running total`() {
        val ladder = ladderRows(
            book(
                bids = listOf(100.0 to 1.0, 99.0 to 2.0, 98.0 to 3.0),
                asks = listOf(101.0 to 4.0),
            ),
        )
        assertEquals(listOf(1.0, 3.0, 6.0), ladder.bids.map { it.total })
        assertEquals(listOf(4.0), ladder.asks.map { it.total })
        assertEquals(1.0, ladderFigure(ladder.bids[0], LadderFigure.AMOUNT), 1e-9)
        assertEquals(6.0, ladderFigure(ladder.bids[2], LadderFigure.CUMULATIVE), 1e-9)
    }

    @Test
    fun `the cumulative bar is scaled to the visible window so the deepest rung fills its cell`() {
        val ladder = ladderRows(
            book(
                bids = listOf(100.0 to 1.0, 99.0 to 2.0, 98.0 to 3.0),
                asks = listOf(101.0 to 1.0),
            ),
        )
        // Six on the buy side against one on the sell side, so six is the denominator both share.
        assertEquals(1f / 6f, ladder.bids[0].cumulativeBarFraction, 1e-6f)
        assertEquals(0.5f, ladder.bids[1].cumulativeBarFraction, 1e-6f)
        assertEquals(1f, ladder.bids[2].cumulativeBarFraction, 1e-6f)
        // Scaled across both sides, not per side: a one-lot sell side must not draw a full bar.
        assertEquals(1f / 6f, ladder.asks[0].cumulativeBarFraction, 1e-6f)
    }

    @Test
    fun `the cumulative bar and the curve wash are different numbers on the same rung`() {
        // Eight rows of a book that is much deeper. The bar fills its cell against the window; the
        // wash stays short against everything loaded. Collapsing them onto one denominator would
        // lose whichever answer it kept.
        val deep = book(
            bids = (0 until 40).map { (100.0 - it) to 1.0 },
            asks = (0 until 40).map { (101.0 + it) to 1.0 },
        )
        val ladder = ladderRows(deep, levels = 8)
        val deepest = ladder.bids.last()
        assertEquals(1f, deepest.cumulativeBarFraction, 1e-6f)
        assertEquals(8f / 40f, deepest.curveFraction, 1e-6f)
    }

    @Test
    fun `the figure, its bar and its decimals move together`() {
        val ladder = ladderRows(
            book(bids = listOf(100.0 to 1.0, 99.0 to 2.0), asks = listOf(101.0 to 1.0)),
        )
        val row = ladder.bids[1]
        assertEquals(row.quantity, ladderFigure(row, LadderFigure.AMOUNT), 1e-9)
        assertEquals(row.barFraction, ladderBarFraction(row, LadderFigure.AMOUNT))
        assertEquals(ladder.quantityDecimals, ladderFigureDecimals(ladder, LadderFigure.AMOUNT))

        assertEquals(row.total, ladderFigure(row, LadderFigure.CUMULATIVE), 1e-9)
        assertEquals(row.cumulativeBarFraction, ladderBarFraction(row, LadderFigure.CUMULATIVE))
        assertEquals(ladder.cumulativeDecimals, ladderFigureDecimals(ladder, LadderFigure.CUMULATIVE))
    }

    @Test
    fun `sums get fewer decimals than the levels they are sums of`() {
        // The largest level here is 40 and wants three decimals; the deepest total is 320 and wants
        // one. Printed at the levels' precision the total needs more width than the cell has and
        // truncates, which loses the leading digits — the ones that say how big the wall is.
        val ladder = ladderRows(
            book(
                bids = (0 until 8).map { (100.0 - it) to 0.004 },
                asks = (0 until 8).map { (101.0 + it) to 40.0 },
            ),
        )
        assertEquals(3, ladder.quantityDecimals)
        assertEquals(1, ladder.cumulativeDecimals)

        val small = ladderRows(book(bids = listOf(100.0 to 0.004), asks = listOf(101.0 to 0.004)))
        assertEquals(5, small.quantityDecimals)
        assertEquals(5, small.cumulativeDecimals)
    }

    @Test
    fun `an aggregated ladder takes its decimals from the step and not from the mid`() {
        // The visible half of the aggregation feature: a step of 1 makes every price a whole number,
        // so the two decimals the mid would have asked for are two printed zeroes.
        val folded = ladderRows(book(bids = listOf(77_588.0 to 1.0), asks = listOf(77_589.0 to 1.0)), step = 1.0)
        assertEquals(0, folded.priceDecimals)

        val tenth = ladderRows(book(bids = listOf(77_588.0 to 1.0), asks = listOf(77_588.5 to 1.0)), step = 0.5)
        assertEquals(1, tenth.priceDecimals)

        // No step at all and the mid decides, exactly as it always did.
        val raw = ladderRows(book(bids = listOf(0.5241 to 1.0), asks = listOf(0.5248 to 1.0)))
        assertEquals(4, raw.priceDecimals)
    }

    @Test
    fun `a step is printed to its own decimals and in Latin digits`() {
        assertEquals("0.1", BidiText.strip(stepLabel(0.1)))
        assertEquals("0.5", BidiText.strip(stepLabel(0.5)))
        assertEquals("1", BidiText.strip(stepLabel(1.0)))
        assertEquals("10", BidiText.strip(stepLabel(10.0)))
        assertEquals("0.0005", BidiText.strip(stepLabel(0.0005)))
    }
}
