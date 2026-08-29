package com.coinepro.core.papertrade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fill rules, which are the part of this feature that can teach somebody a habit that costs
 * them money.
 *
 * Every test here pins a *behaviour* and states the arithmetic it expects in the comment, rather
 * than pinning a count or a shape. The numbers are chosen so they can be done by hand: last is 100,
 * the assumed spread is twenty basis points so each side is a tenth of a point, and the fees are a
 * tenth and a twentieth of a percent.
 */
class PaperFillRulesTest {

    private val rules = PaperRules(
        startingBalance = 10_000.0,
        leverage = 1.0,
        takerFeePercent = 0.1,
        makerFeePercent = 0.05,
        slippagePercent = 0.0,
        assumedSpreadPercent = 0.2,
        stopOutPercent = 50.0,
    )

    private fun book(rules: PaperRules = this.rules) = PaperBook(
        rules = rules,
        account = PaperAccount(rules.startingBalance, rules.startingBalance, AT, 1),
    )

    @Test
    fun `a market buy pays the offer, not the last price`() {
        // Last is 100 and there is no quoted book, so the assumed spread puts the ask at 100.1.
        // A fill at 100.0 would be the mid, which is the single most common lie a paper simulator
        // tells: it makes a round trip look free.
        val filled = PaperEngine.place(book(), buy(1.0), quotes(100.0), AT)
        val position = filled.positions.single()

        assertEquals(100.1, position.entry, 1e-9)
    }

    @Test
    fun `a market sell receives the bid`() {
        val filled = PaperEngine.place(book(), sell(1.0), quotes(100.0), AT)

        assertEquals(99.9, filled.positions.single().entry, 1e-9)
    }

    @Test
    fun `a round trip that goes nowhere still costs the spread twice and the fee twice`() {
        // In and straight back out at the same last price. Spread: 0.2 across the two fills. Fees:
        // 100.1 and 99.9 at ten basis points, so 0.1001 + 0.0999. Total 0.4 out of 10,000.
        val opened = PaperEngine.place(book(), buy(1.0), quotes(100.0), AT)
        val closed = PaperEngine.closePosition(opened, opened.positions.single().id, 1.0, quotes(100.0), AT)

        assertEquals(9_999.6, closed.account.balance, 1e-9)
        assertTrue("the round trip must be a loss, not a scratch", closed.closed.single().net < 0.0)
    }

    @Test
    fun `a quoted book is used exactly as the feed sent it`() {
        val quoted = mapOf(
            SYMBOL to PaperQuote(SYMBOL, last = 100.0, bid = 98.0, ask = 103.0, atEpochMillis = AT),
        )
        val filled = PaperEngine.place(book(), buy(1.0), quoted, AT)

        assertEquals(103.0, filled.positions.single().entry, 1e-9)
        assertFalse("a real book must not be reported as an assumption", filled.fills.single().assumedSpread)
    }

    @Test
    fun `a fill against no quoted book says the spread was assumed`() {
        val filled = PaperEngine.place(book(), buy(1.0), quotes(100.0), AT)

        assertTrue(filled.fills.single().assumedSpread)
    }

    @Test
    fun `slippage moves a taking fill further against the trader and never a resting one`() {
        val slipping = rules.copy(slippagePercent = 1.0)
        // Ask 100.1, then one percent worse: 101.101.
        val taken = PaperEngine.place(book(slipping), buy(1.0), quotes(100.0), AT)
        assertEquals(101.101, taken.positions.single().entry, 1e-9)

        // A limit that rested gets its own price. A limit that slipped would not be a limit.
        val resting = PaperEngine.place(book(slipping), limitBuy(95.0), quotes(100.0), AT)
        val filled = PaperEngine.observe(resting, quotes(94.0), AT)
        assertEquals(95.0, filled.positions.single().entry, 1e-9)
        assertEquals(0.0, filled.fills.single().slippage, 1e-9)
    }

    @Test
    fun `a stop does not fill at the stop price`() {
        // The rule this whole feature exists for. A buy stop at 105 that triggers on a print of 106
        // fills where the market is — 106 plus the half spread — not at 105. A simulator that fills
        // stops at the stop price hides the cost of every stop the reader will ever be taken out on.
        val placed = PaperEngine.place(book(), stopBuy(105.0), quotes(100.0), AT)
        assertTrue("it must rest until the trigger is reached", placed.working.isNotEmpty())

        val filled = PaperEngine.observe(placed, quotes(106.0), AT)
        assertEquals(106.106, filled.positions.single().entry, 1e-9)
    }

    @Test
    fun `a stop loss is charged the same way, at the market rather than at its level`() {
        val opened = PaperEngine.place(book(), buy(1.0, stopLoss = 95.0), quotes(100.0), AT)
        val stopped = PaperEngine.observe(opened, quotes(90.0), AT)
        val trade = stopped.closed.single()

        assertEquals(PaperCloseReason.STOP_LOSS, trade.reason)
        // Bid at 90 is 89.91. Not 95, and not 90.
        assertEquals(89.91, trade.exit, 1e-9)
    }

    @Test
    fun `a take profit fills at its own price, because it is a limit`() {
        val opened = PaperEngine.place(book(), buy(1.0, takeProfit = 110.0), quotes(100.0), AT)
        val taken = PaperEngine.observe(opened, quotes(112.0), AT)
        val trade = taken.closed.single()

        assertEquals(PaperCloseReason.TAKE_PROFIT, trade.reason)
        // 110, not 111.888, and not 112. The gap's better price is not credited — see rule 5.
        assertEquals(110.0, trade.exit, 1e-9)
    }

    @Test
    fun `an overlapping stop and target resolve to the stop`() {
        // A stop above the market and a target below it can both be reached by one price. Nobody
        // can say which came first, so the loss-side rule wins.
        val opened = PaperEngine.place(book(), buy(1.0), quotes(100.0), AT)
        val protectedBook = PaperEngine.setProtection(
            opened,
            opened.positions.single().id,
            stopLoss = 105.0,
            takeProfit = 95.0,
        )
        val settled = PaperEngine.observe(protectedBook, quotes(100.0), AT)

        assertEquals(PaperCloseReason.STOP_LOSS, settled.closed.single().reason)
    }

    @Test
    fun `a limit that arrives marketable is a taker, and never pays more than its limit`() {
        // A buy limit at 105 with the ask at 100.1 is not a resting order, it is a market order
        // with a ceiling. It fills at the ask.
        val filled = PaperEngine.place(book(), limitBuy(105.0), quotes(100.0), AT)

        assertEquals(100.1, filled.positions.single().entry, 1e-9)
        assertEquals(PaperFillBasis.TAKEN, filled.fills.single().basis)
    }

    @Test
    fun `a resting limit fills at its own price and pays the maker fee`() {
        val placed = PaperEngine.place(book(), limitBuy(95.0), quotes(100.0), AT)
        val filled = PaperEngine.observe(placed, quotes(94.0), AT)
        val fill = filled.fills.single()

        assertEquals(95.0, fill.price, 1e-9)
        assertEquals(PaperFillBasis.RESTED, fill.basis)
        // Maker: five basis points of 95, not ten.
        assertEquals(0.0475, fill.fee, 1e-9)
    }

    @Test
    fun `a crossing the app watched is recorded as watched`() {
        val placed = PaperEngine.place(book(), limitBuy(95.0), quotes(100.0), AT)
        val nearer = PaperEngine.observe(placed, quotes(96.0), AT)
        val filled = PaperEngine.observe(nearer, quotes(94.0), AT)

        assertTrue(filled.fills.single().watched)
    }

    @Test
    fun `a limit crossed while the app was shut fills at its limit, not at the gap`() {
        // The reader's app is killed with a buy limit resting at 95. It reopens with the market at
        // 80. A real venue would very likely have filled them at 95 — or better — but nothing here
        // watched the path, so the fill takes the price that is worse for the trader and says the
        // crossing was unwatched. Filling at 80 would credit a windfall the simulator cannot prove.
        val placed = PaperEngine.place(book(), limitBuy(95.0), quotes(100.0), AT)
        val restarted = PaperBookCodec.decode(PaperBookCodec.encode(placed))
        val filled = PaperEngine.observe(restarted, quotes(80.0), AT)
        val fill = filled.fills.single()

        assertEquals(95.0, fill.price, 1e-9)
        assertFalse("nothing was watching, and the record has to say so", fill.watched)
    }

    @Test
    fun `a stop crossed while the app was shut fills at the market, however far past`() {
        // The mirror of the test above, and the reason the rule is stated as "the worse candidate"
        // rather than "the level": a stop that gapped is the case that empties accounts.
        val opened = PaperEngine.place(book(), buy(1.0, stopLoss = 95.0), quotes(100.0), AT)
        val restarted = PaperBookCodec.decode(PaperBookCodec.encode(opened))
        val stopped = PaperEngine.observe(restarted, quotes(70.0), AT)
        val trade = stopped.closed.single()

        assertEquals(69.93, trade.exit, 1e-9)
        assertFalse(stopped.fills.last().watched)
    }

    @Test
    fun `a stale observation fills nothing`() {
        val placed = PaperEngine.place(book(), limitBuy(95.0), quotes(100.0), AT)
        val stale = mapOf(SYMBOL to PaperQuote(SYMBOL, last = 80.0, stale = true, atEpochMillis = AT))
        val after = PaperEngine.observe(placed, stale, AT)

        assertTrue("a remembered price is not a price", after.working.isNotEmpty())
        assertTrue(after.positions.isEmpty())
    }

    @Test
    fun `a market order with no price is refused rather than parked`() {
        val refused = PaperEngine.place(book(), buy(1.0), emptyMap(), AT)

        assertEquals(PaperOrderState.REJECTED, refused.orders.single().state)
        assertEquals(PaperReject.NO_PRICE, refused.orders.single().rejectedBecause)
    }

    @Test
    fun `an order the account cannot margin is rejected, not opened`() {
        val refused = PaperEngine.place(book(), buy(200.0), quotes(100.0), AT)

        assertTrue(refused.positions.isEmpty())
        assertEquals(PaperReject.MARGIN, refused.orders.single().rejectedBecause)
    }

    @Test
    fun `adding to a position averages the entry rather than hiding behind the first fill`() {
        val first = PaperEngine.place(book(), buy(1.0), quotes(100.0), AT)
        val second = PaperEngine.place(first, buy(1.0), quotes(200.0), AT)
        val position = second.positions.single()

        assertEquals(2.0, position.size, 1e-9)
        // (100.1 + 200.2) / 2.
        assertEquals(150.15, position.entry, 1e-9)
    }

    @Test
    fun `a partial close realises its share and leaves the rest open`() {
        val opened = PaperEngine.place(book(), buy(2.0), quotes(100.0), AT)
        val half = PaperEngine.closePosition(opened, opened.positions.single().id, 0.5, quotes(120.0), AT)
        val position = half.positions.single()

        assertEquals(1.0, position.size, 1e-9)
        // Margin follows the size down: 200.2 held, half released.
        assertEquals(100.1, position.marginHeld, 1e-6)
        assertEquals(1, half.closed.size)
        assertEquals(1.0, half.closed.single().size, 1e-9)
    }

    @Test
    fun `an order larger than an opposing position closes it and opens the other way`() {
        val long = PaperEngine.place(book(), buy(1.0), quotes(100.0), AT)
        val reversed = PaperEngine.place(long, sell(3.0), quotes(100.0), AT)
        val position = reversed.positions.single()

        assertEquals(PaperSide.SELL, position.side)
        assertEquals(2.0, position.size, 1e-9)
        assertEquals(1, reversed.closed.size)
    }

    @Test
    fun `a reduce-only order never opens anything`() {
        val long = PaperEngine.place(book(), buy(1.0), quotes(100.0), AT)
        val request = PaperOrderRequest(SYMBOL, PaperSide.SELL, PaperOrderType.MARKET, 5.0, reduceOnly = true)
        val flat = PaperEngine.place(long, request, quotes(100.0), AT)

        assertTrue(flat.positions.isEmpty())
        assertEquals(1, flat.closed.size)
    }

    @Test
    fun `reduce-only with nothing to reduce is refused with its own reason`() {
        val request = PaperOrderRequest(SYMBOL, PaperSide.SELL, PaperOrderType.MARKET, 1.0, reduceOnly = true)
        val refused = PaperEngine.place(book(), request, quotes(100.0), AT)

        assertEquals(PaperReject.NOTHING_TO_REDUCE, refused.orders.single().rejectedBecause)
    }

    @Test
    fun `a stop-limit rests as a limit once its stop has gone`() {
        val request = PaperOrderRequest(
            SYMBOL, PaperSide.SELL, PaperOrderType.STOP_LIMIT, 1.0,
            limitPrice = 90.0, stopPrice = 95.0,
        )
        // The stop goes at 95 and the market is already at 89, below the limit, so the order rests
        // rather than selling into a price its owner said they would not accept.
        val placed = PaperEngine.place(book(), request, quotes(100.0), AT)
        val triggered = PaperEngine.observe(placed, quotes(89.0), AT)

        assertTrue("the stop went; the limit has not been reached", triggered.working.single().triggered)
        assertTrue(triggered.positions.isEmpty())

        // Back up through 90: a resting sell limit is lifted at its own price, not at the better
        // one the market has moved to.
        val filled = PaperEngine.observe(triggered, quotes(91.0), AT)
        assertEquals(90.0, filled.positions.single().entry, 1e-9)
        assertEquals(PaperFillBasis.RESTED, filled.fills.single().basis)
    }

    @Test
    fun `a stop-limit whose limit is already through fills as a taker`() {
        val request = PaperOrderRequest(
            SYMBOL, PaperSide.BUY, PaperOrderType.STOP_LIMIT, 1.0,
            limitPrice = 110.0, stopPrice = 105.0,
        )
        val placed = PaperEngine.place(book(), request, quotes(100.0), AT)
        val filled = PaperEngine.observe(placed, quotes(106.0), AT)

        assertEquals(106.106, filled.positions.single().entry, 1e-9)
        assertEquals(PaperFillBasis.TAKEN, filled.fills.single().basis)
    }

    @Test
    fun `an account through its stop-out level is closed out`() {
        // Two hundred of balance at ten times. Fifteen units at ~100 is 1,501.5 of notional and
        // 150.15 of margin. At 90 the position is 151.5 down, equity is about 47, and the margin
        // level is under a third — well through fifty.
        val levered = rules.copy(startingBalance = 200.0, leverage = 10.0)
        val opened = PaperEngine.place(book(levered), buy(15.0), quotes(100.0), AT)
        assertEquals(1, opened.positions.size)

        val wiped = PaperEngine.observe(opened, quotes(90.0), AT)

        assertTrue(wiped.positions.isEmpty())
        assertEquals(PaperCloseReason.LIQUIDATION, wiped.closed.single().reason)
    }

    @Test
    fun `nothing is closed out while a position has no fresh mark`() {
        // A margin level computed with one position missing is a number that can cross the line
        // because a socket dropped. Two positions, one price.
        val levered = rules.copy(startingBalance = 400.0, leverage = 10.0)
        val one = PaperEngine.place(book(levered), buy(15.0), quotes(100.0), AT)
        val other = PaperEngine.place(one, PaperOrderRequest("ETHUSDT", PaperSide.BUY, PaperOrderType.MARKET, 1.0), quotes(100.0, "ETHUSDT"), AT)
        val partial = PaperEngine.observe(other, quotes(50.0), AT)

        assertEquals(2, partial.positions.size)
    }

    @Test
    fun `equity is the balance plus what is open, and the margin level follows it`() {
        val opened = PaperEngine.place(book(), buy(1.0), quotes(100.0), AT)
        val marks = mapOf(SYMBOL to 120.0)

        // Balance is 10,000 less the 0.1001 entry fee; the position is 19.9 up on an entry of 100.1.
        assertEquals(10_019.7999, opened.equity(marks), 1e-6)
        assertNotNull(opened.marginLevelPercent(marks))
        assertNull("nothing open is not an infinite margin level", book().marginLevelPercent(marks))
    }

    @Test
    fun `a reset keeps the record and starts the balance again`() {
        val opened = PaperEngine.place(book(), buy(1.0), quotes(100.0), AT)
        val closedOut = PaperEngine.closePosition(opened, opened.positions.single().id, 1.0, quotes(120.0), AT)
        val again = PaperEngine.reset(closedOut, closedOut.rules, AT)

        assertEquals(1, again.closed.size)
        assertEquals(10_000.0, again.account.balance, 1e-9)
        assertEquals(2, again.account.generation)
        assertTrue(again.positions.isEmpty())
    }

    @Test
    fun `a wipe takes the record with it`() {
        val opened = PaperEngine.place(book(), buy(1.0), quotes(100.0), AT)
        val closedOut = PaperEngine.closePosition(opened, opened.positions.single().id, 1.0, quotes(120.0), AT)

        assertTrue(PaperEngine.wipe(closedOut.rules, AT).closed.isEmpty())
    }

    @Test
    fun `a cancelled order stops being watched`() {
        val placed = PaperEngine.place(book(), limitBuy(95.0), quotes(100.0), AT)
        val cancelled = PaperEngine.cancel(placed, placed.orders.single().id, AT)
        val after = PaperEngine.observe(cancelled, quotes(90.0), AT)

        assertTrue(after.positions.isEmpty())
        assertEquals(PaperOrderState.CANCELLED, after.orders.single().state)
    }

    @Test
    fun `amending a level forgets the crossing the old level had watched`() {
        val placed = PaperEngine.place(book(), limitBuy(95.0), quotes(100.0), AT)
        val moved = PaperEngine.amend(placed, placed.orders.single().id, limitPrice = 90.0)

        assertNull(moved.working.single().lastSeenPrice)
        assertEquals(90.0, moved.working.single().limitPrice!!, 1e-9)
    }

    /* ------------------------------------------------------------------------------- the fixtures */

    private fun buy(size: Double, stopLoss: Double? = null, takeProfit: Double? = null) =
        PaperOrderRequest(SYMBOL, PaperSide.BUY, PaperOrderType.MARKET, size, stopLoss = stopLoss, takeProfit = takeProfit)

    private fun sell(size: Double) = PaperOrderRequest(SYMBOL, PaperSide.SELL, PaperOrderType.MARKET, size)

    private fun limitBuy(price: Double) =
        PaperOrderRequest(SYMBOL, PaperSide.BUY, PaperOrderType.LIMIT, 1.0, limitPrice = price)

    private fun stopBuy(price: Double) =
        PaperOrderRequest(SYMBOL, PaperSide.BUY, PaperOrderType.STOP, 1.0, stopPrice = price)

    private fun quotes(last: Double, symbol: String = SYMBOL) =
        mapOf(symbol to PaperQuote(symbol, last = last, atEpochMillis = AT))

    private companion object {
        const val SYMBOL = "BTCUSDT"
        const val AT = 1_756_000_000_000L
    }
}
