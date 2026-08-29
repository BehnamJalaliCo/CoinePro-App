package com.coinepro.core.papertrade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stored form of the book.
 *
 * The tests that matter here are the ones about damage: a row from an older build, a row from a
 * newer one, a truncated file. A paper account that throws on its own stored value is an account
 * the reader cannot reach without deleting the app's data, which loses them everything to fix one
 * row.
 */
class PaperBookCodecTest {

    @Test
    fun `a book survives the round trip`() {
        val restored = PaperBookCodec.decode(PaperBookCodec.encode(traded()))
        val original = traded()

        assertEquals(original.account, restored.account)
        assertEquals(original.rules, restored.rules)
        assertEquals(original.positions.size, restored.positions.size)
        assertEquals(original.closed, restored.closed)
        assertEquals(original.fills, restored.fills)
        assertEquals(original.nextId, restored.nextId)
    }

    @Test
    fun `nothing stored is not an empty book`() {
        // The difference decides whether a first-run reader gets their starting balance or a zero.
        val fallback = PaperBook(account = PaperAccount(balance = 500.0, startingBalance = 500.0))

        assertEquals(500.0, PaperBookCodec.decode(null, fallback).account.balance, 1e-9)
        assertEquals(500.0, PaperBookCodec.decode("", fallback).account.balance, 1e-9)
    }

    @Test
    fun `the watched price is deliberately not stored`() {
        // Load-bearing, not an omission. A restarted app has watched nothing, and the first
        // observation after it comes back has to be treated as a crossing nobody saw.
        val placed = PaperEngine.place(
            PaperBook(),
            PaperOrderRequest("BTCUSDT", PaperSide.BUY, PaperOrderType.LIMIT, 1.0, limitPrice = 95.0),
            mapOf("BTCUSDT" to PaperQuote("BTCUSDT", 100.0)),
            AT,
        )
        assertNotNull(placed.working.single().lastSeenPrice)

        val restored = PaperBookCodec.decode(PaperBookCodec.encode(placed))
        assertNull(restored.working.single().lastSeenPrice)
    }

    @Test
    fun `a row a later build wrote is skipped rather than treated as corruption`() {
        val text = PaperBookCodec.encode(traded()) + ";Z|something|new"
        val restored = PaperBookCodec.decode(text)

        assertEquals(traded().closed, restored.closed)
    }

    @Test
    fun `a truncated row decodes with defaults for everything after the break`() {
        // What an older build's row looks like: the fields that existed then, and nothing after.
        val text = "N|9;A|1000.0|1000.0|1|1;P|4|BTCUSDT|b|2.0|100.0"
        val position = PaperBookCodec.decode(text).positions.single()

        assertEquals(2.0, position.size, 1e-9)
        assertEquals(1.0, position.leverage, 1e-9)
        assertNull(position.stopLoss)
    }

    @Test
    fun `a row that cannot be a position is dropped and the rest is kept`() {
        val text = "N|9;A|1000.0|1000.0|1|1;P|4|BTCUSDT|b|0.0|100.0;P|5|ETHUSDT|s|1.0|2000.0"
        val positions = PaperBookCodec.decode(text).positions

        assertEquals(1, positions.size)
        assertEquals("ETHUSDT", positions.single().symbol)
    }

    @Test
    fun `a number that is not a number never reaches the book`() {
        val text = "N|9;A|1000.0|1000.0|1|1;P|4|BTCUSDT|b|NaN|100.0;C|6|BTCUSDT|b|1.0|100.0|Infinity"
        val book = PaperBookCodec.decode(text)

        assertTrue(book.positions.isEmpty())
        assertTrue(book.closed.isEmpty())
    }

    @Test
    fun `the next id can never collide with one already in the book`() {
        // A truncated `N` row used to hand out an id a position already held, and two positions
        // with one id is a book that closes the wrong one.
        val text = "N|1;P|40|BTCUSDT|b|1.0|100.0"

        assertEquals(41L, PaperBookCodec.decode(text).nextId)
    }

    @Test
    fun `stored rules are forced back into a range the arithmetic survives`() {
        val text = "R|10000.0|-5.0|900.0|0.02|0.02|0.04|50.0"
        val rules = PaperBookCodec.decode(text).rules

        assertEquals(1.0, rules.leverage, 1e-9)
        assertEquals(PaperRules.MAX_FEE_PERCENT, rules.takerFeePercent, 1e-9)
    }

    @Test
    fun `a settled order keeps its reason`() {
        val rejected = PaperEngine.place(
            PaperBook(),
            PaperOrderRequest("BTCUSDT", PaperSide.BUY, PaperOrderType.MARKET, 1.0),
            emptyMap(),
            AT,
        )
        val restored = PaperBookCodec.decode(PaperBookCodec.encode(rejected)).orders.single()

        assertEquals(PaperOrderState.REJECTED, restored.state)
        assertEquals(PaperReject.NO_PRICE, restored.rejectedBecause)
        assertFalse(restored.working)
    }

    private fun traded(): PaperBook {
        val quotes = mapOf("BTCUSDT" to PaperQuote("BTCUSDT", 100.0, atEpochMillis = AT))
        val opened = PaperEngine.place(
            PaperBook(),
            PaperOrderRequest("BTCUSDT", PaperSide.BUY, PaperOrderType.MARKET, 1.0, stopLoss = 90.0),
            quotes,
            AT,
        )
        val closedOut = PaperEngine.closePosition(opened, opened.positions.single().id, 1.0, quotes, AT)
        return PaperEngine.place(
            closedOut,
            PaperOrderRequest("ETHUSDT", PaperSide.SELL, PaperOrderType.MARKET, 2.0),
            mapOf("ETHUSDT" to PaperQuote("ETHUSDT", 2_000.0, atEpochMillis = AT)),
            AT,
        )
    }

    private companion object {
        const val AT = 1_756_000_000_000L
    }
}
