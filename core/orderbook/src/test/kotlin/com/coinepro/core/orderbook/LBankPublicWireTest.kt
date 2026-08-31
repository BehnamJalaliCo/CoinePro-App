package com.coinepro.core.orderbook

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exchange's own payload, pinned, because on this path the app is the thing that converts it.
 *
 * On the relayed path TradeYar hand the app numbers and their own tests pin that they do. Here the
 * app meets the venue's shape directly — prices, volumes and order counts all arrive as **strings**,
 * and the failures arrive with `HTTP 200` on them — so every conversion the relay would have done is
 * done in this module and has to be held in place here.
 *
 * The bodies below are verbatim from `lbkperp.lbank.com/cfd/openApi/v1/pub/marketOrder`, captured
 * 2026-08-31.
 */
class LBankPublicWireTest {

    private val gson = Gson()

    /** Three levels a side on BTCUSDT, exactly as the host sent them. */
    private val liveBody = """
        {
          "data": {
            "symbol": "BTCUSDT",
            "asks": [
              {"volume":"4.234","price":"77766.5","orders":"1"},
              {"volume":"0.5702","price":"77766.6","orders":"2"},
              {"volume":"0.6925","price":"77766.7","orders":"2"}
            ],
            "bids": [
              {"volume":"9.7759","price":"77766.4","orders":"1"},
              {"volume":"1.1689","price":"77766.3","orders":"2"},
              {"volume":"0.3888","price":"77766.2","orders":"1"}
            ]
          },
          "error_code": 0,
          "msg": "Success",
          "result": "true"
        }
    """.trimIndent()

    @Test
    fun `string prices and volumes become the numbers the ladder draws`() {
        val data = gson.fromJson(liveBody, LBankDepthDto::class.java).data!!

        val bids = data.bids.toDepthLevels()
        val asks = data.asks.toDepthLevels()

        // The relay's own test says it best: what matters is that the ladder never receives "100.5".
        assertEquals(77_766.4, bids.first().price, 1e-6)
        assertEquals(9.7759, bids.first().quantity, 1e-6)
        assertEquals(77_766.5, asks.first().price, 1e-6)
        assertEquals(4.234, asks.first().quantity, 1e-6)
        assertEquals(listOf(1, 2, 1), bids.map { it.orders })
        assertEquals(listOf(1, 2, 2), asks.map { it.orders })
    }

    @Test
    fun `the venue's book rebuilds into the same shape the relayed one does`() {
        val data = gson.fromJson(liveBody, LBankDepthDto::class.java).data!!

        val book = OrderBook.of(
            symbol = data.symbol.orEmpty(),
            bids = data.bids.toDepthLevels(),
            asks = data.asks.toDepthLevels(),
            at = 0L,
        )

        assertEquals("BTCUSDT", book.symbol)
        assertEquals(77_766.4, book.bestBid!!, 1e-6)
        assertEquals(77_766.5, book.bestAsk!!, 1e-6)
        // No venue clock and, unlike the relayed path, no cache in front of the call either — so
        // there is no bound on the age to state and none is invented.
        assertEquals(0L, book.at)
        assertNull(book.maxAgeMillis)
    }

    @Test
    fun `a delisted contract arrives as a two hundred and must not be read as an empty book`() {
        // This is the trap the envelope type exists for. The status line says the request succeeded;
        // only `error_code` says the market is gone. Read as a book it draws «there are no resting
        // orders right now» over a contract that has been retired — a claim about liquidity in a
        // market that no longer has any.
        val body = """
            {"error_code":20156,"msg":"This product has been delisted and is not available for trading.","result":"false","success":false}
        """.trimIndent()

        val envelope = gson.fromJson(body, LBankDepthDto::class.java)

        assertEquals(20156, envelope.errorCode)
        assertNull("a refusal carries no data, and none may be conjured", envelope.data)
    }

    @Test
    fun `a live market with nobody resting in it is a book, not a fault`() {
        // Both of TradeYar's genuinely empty symbols still answer `error_code 0` with empty arrays,
        // measured again on this endpoint. That is a successful call whose answer is an empty queue,
        // and the screen has its own sentence for it.
        val body = """{"data":{"symbol":"OBOLUSDT","asks":[],"bids":[]},"error_code":0,"result":"true"}"""

        val envelope = gson.fromJson(body, LBankDepthDto::class.java)
        val book = OrderBook.of("OBOLUSDT", emptyList(), emptyList(), at = 0L)

        assertEquals(0, envelope.errorCode)
        assertTrue(envelope.data!!.bids.isEmpty())
        assertTrue(book.bids.isEmpty() && book.asks.isEmpty())
    }

    @Test
    fun `a row missing either number is dropped rather than half-read as a zero`() {
        val rows = listOf(
            LBankDepthRowDto(price = "100.0", volume = "1.5", orders = "3"),
            LBankDepthRowDto(price = null, volume = "2.0", orders = "1"),
            LBankDepthRowDto(price = "101.0", volume = null, orders = "1"),
            LBankDepthRowDto(price = "not a number", volume = "1.0", orders = "1"),
        )

        val levels = rows.toDepthLevels()

        assertEquals(1, levels.size)
        assertEquals(100.0, levels.single().price, 1e-9)
        assertEquals(3, levels.single().orders)
    }

    @Test
    fun `an unknown order count stays unknown and never becomes one`() {
        // Absent and "one order" are different claims about a price, and the second is the dangerous
        // one: it says a single participant is holding that whole wall and can withdraw it in one
        // message. Zero is read the same way as absent, because a count of nought printed beside a
        // quantity that is plainly there is worse than no figure at all.
        val rows = listOf(
            LBankDepthRowDto(price = "100.0", volume = "1.0", orders = null),
            LBankDepthRowDto(price = "99.0", volume = "1.0", orders = "0"),
            LBankDepthRowDto(price = "98.0", volume = "1.0", orders = "-2"),
            LBankDepthRowDto(price = "97.0", volume = "1.0", orders = "hello"),
        )

        assertEquals(listOf(null, null, null, null), rows.toDepthLevels().map { it.orders })
    }

    @Test
    fun `the extra level is what makes truncated a measurement instead of a guess`() {
        // The gateway asks for one level more than it keeps, exactly as the relay does. More than
        // was asked for coming back means the book genuinely continues past the page; a page that
        // was not full means this is the whole book. Both directions are here because reading the
        // second as the first would mark every quiet market as truncated.
        val deeper = OrderBook.of(
            symbol = "BTCUSDT",
            bids = (1..4).map { DepthLevel(100.0 - it, 1.0) },
            asks = (1..4).map { DepthLevel(100.0 + it, 1.0) },
            at = 0L,
        ).top(3)
        assertTrue("four levels came back where three were kept", deeper.truncated)
        assertEquals(3, deeper.bids.size)
        // Cut from the touch outwards, so the rows kept are the ones nearest the spread.
        assertEquals(99.0, deeper.bestBid!!, 1e-9)

        val whole = OrderBook.of(
            symbol = "BTCUSDT",
            bids = (1..2).map { DepthLevel(100.0 - it, 1.0) },
            asks = (1..2).map { DepthLevel(100.0 + it, 1.0) },
            at = 0L,
        ).top(3)
        assertTrue("a page that was not full is the whole book", !whole.truncated)
    }
}
