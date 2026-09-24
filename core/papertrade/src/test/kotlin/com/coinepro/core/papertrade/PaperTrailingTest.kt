package com.coinepro.core.papertrade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The old ticket's trailing stop and time in force (5.15.0). */
class PaperTrailingTest {

    private val rules = PaperRules(
        startingBalance = 10_000.0,
        leverage = 1.0,
        takerFeePercent = 0.1,
        makerFeePercent = 0.05,
        slippagePercent = 0.0,
        assumedSpreadPercent = 0.2,
        stopOutPercent = 50.0,
    )

    private fun book() = PaperBook(rules = rules, account = PaperAccount(rules.startingBalance, rules.startingBalance, AT, 1))

    private fun quotes(last: Double) = mapOf(SYMBOL to PaperQuote(SYMBOL, last = last, atEpochMillis = AT))

    @Test
    fun `a trailing sell follows the price up, never down, and fires when the price comes back`() {
        val long = PaperEngine.place(book(), PaperOrderRequest(SYMBOL, PaperSide.BUY, PaperOrderType.MARKET, 1.0), quotes(100.0), AT)
        var next = PaperEngine.place(
            long,
            PaperOrderRequest(SYMBOL, PaperSide.SELL, PaperOrderType.TRAILING, 1.0, trailPercent = 5.0, reduceOnly = true),
            quotes(100.0),
            AT,
        )
        assertEquals(95.0, next.working.single().stopPrice!!, 1e-9)
        next = PaperEngine.observe(next, quotes(120.0), AT + 1)
        assertEquals(114.0, next.working.single().stopPrice!!, 1e-9)
        next = PaperEngine.observe(next, quotes(116.0), AT + 2)
        assertEquals("a dip does not loosen it", 114.0, next.working.single().stopPrice!!, 1e-9)
        next = PaperEngine.observe(next, quotes(113.0), AT + 3)
        assertTrue("it fired", next.working.isEmpty())
        assertTrue("and closed the long", next.positions.isEmpty())
    }

    @Test
    fun `an immediate-or-cancel limit away from the market is cancelled, not rested`() {
        val request = PaperOrderRequest(SYMBOL, PaperSide.BUY, PaperOrderType.LIMIT, 1.0, limitPrice = 90.0, timeInForce = PaperTimeInForce.IOC)
        val next = PaperEngine.place(book(), request, quotes(100.0), AT)
        assertTrue(next.working.isEmpty())
        assertEquals(PaperReject.NOT_IMMEDIATE, next.orders.single().rejectedBecause)
        // At the market it fills like any limit.
        val filled = PaperEngine.place(book(), request.copy(limitPrice = 101.0, timeInForce = PaperTimeInForce.FOK), quotes(100.0), AT)
        assertEquals(1, filled.positions.size)
    }

    @Test
    fun `the trailing distance and the time in force survive a restart`() {
        val placed = PaperEngine.place(
            book(),
            PaperOrderRequest(SYMBOL, PaperSide.BUY, PaperOrderType.TRAILING, 1.0, trailPercent = 2.5),
            quotes(100.0),
            AT,
        )
        val decoded = PaperBookCodec.decode(PaperBookCodec.encode(placed))
        val order = decoded.working.single()
        assertEquals(PaperOrderType.TRAILING, order.type)
        assertEquals(2.5, order.trailPercent!!, 1e-9)
        assertEquals(102.5, order.stopPrice!!, 1e-9)
    }

    private companion object {
        const val SYMBOL = "BTCUSDT"
        const val AT = 1_756_000_000_000L
    }
}
