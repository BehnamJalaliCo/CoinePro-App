package com.coinepro.core.orderbook

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `orders` element, pinned against the payload TradeYar actually serve.
 *
 * Two facts are under test and they are not the same fact. The first is that a three-element row
 * carries its count all the way through — the field went live on 2026-08-29 and this is the shape it
 * arrives in. The second is that a **two-element** row leaves the count null: the relay omits the
 * element when it does not know the count rather than sending `0`, because `orders: 0` beside a
 * positive quantity would claim liquidity that nobody placed. Absent and none are different claims,
 * and the whole point of this contract is that the app never turns the first into the second.
 */
class DepthOrdersWireTest {

    private val gson = Gson()

    /** Their reply, verbatim: three levels a side at `depth=20` on BTCUSDT, every row counted. */
    private val liveBody = """
        {
          "symbol": "BTCUSDT",
          "depth": 20,
          "truncated": true,
          "bids": [[77621.90, 10.6449, 1], [77621.80, 0.5917, 1], [77621.70, 0.3884, 1]],
          "asks": [[77622.00, 14.5521, 1], [77622.10, 0.3896, 1], [77622.20, 0.3884, 1]],
          "server_time_ms": 1756465000000,
          "cache_ttl_ms": 500
        }
    """.trimIndent()

    @Test
    fun `a three-element row carries the venue's own order count into the level`() {
        val dto = gson.fromJson(liveBody, CryptoDepthDto::class.java)

        val bids = dto.bids.toDepthLevels()
        val asks = dto.asks.toDepthLevels()

        assertEquals(77_621.90, bids.first().price, 1e-6)
        assertEquals(10.6449, bids.first().quantity, 1e-6)
        assertEquals(1, bids.first().orders)
        assertEquals(listOf(1, 1, 1), bids.map { it.orders })
        assertEquals(listOf(1, 1, 1), asks.map { it.orders })
    }

    @Test
    fun `the whole payload survives the rebuild with its counts on the right prices`() {
        val dto = gson.fromJson(liveBody, CryptoDepthDto::class.java)

        val book = OrderBook.of(
            symbol = dto.symbol.orEmpty(),
            bids = dto.bids.toDepthLevels(),
            asks = dto.asks.toDepthLevels(),
            at = 0L,
            truncated = dto.truncated ?: false,
            maxAgeMillis = dto.cacheTtlMs,
        )

        assertEquals("BTCUSDT", book.symbol)
        assertEquals(77_621.90, book.bestBid!!, 1e-6)
        assertEquals(77_622.00, book.bestAsk!!, 1e-6)
        assertEquals(1, book.bids.first().orders)
        assertEquals(1, book.asks.first().orders)
        // The relay publishes no venue clock, so the only honest staleness figure is the cache bound.
        assertEquals(0L, book.at)
        assertEquals(500L, book.maxAgeMillis)
    }

    @Test
    fun `a two-element row leaves the count unknown rather than calling it zero`() {
        // Their rule: the element is omitted when the count is not known, never sent as 0. So a
        // short row is the relay saying "I do not know", and null is the only answer that repeats
        // that truthfully downstream.
        val body = """{"symbol":"BTCUSDT","bids":[[77621.90, 10.6449]],"asks":[[77622.00, 14.5521]]}"""

        val dto = gson.fromJson(body, CryptoDepthDto::class.java)

        assertNull(dto.bids.toDepthLevels().single().orders)
        assertNull(dto.asks.toDepthLevels().single().orders)
        // And the quantity is untouched by the missing third element: a short row is a level with
        // an unknown count, not a level that failed to parse.
        assertEquals(10.6449, dto.bids.toDepthLevels().single().quantity, 1e-6)
    }

    @Test
    fun `a mixed book keeps each level's own answer instead of levelling them`() {
        val body = """
            {
              "symbol": "BTCUSDT",
              "bids": [[77621.90, 10.6449, 4], [77621.80, 0.5917], [77621.70, 0.3884, 1]],
              "asks": []
            }
        """.trimIndent()

        val levels = gson.fromJson(body, CryptoDepthDto::class.java).bids.toDepthLevels()

        assertEquals(listOf(4, null, 1), levels.map { it.orders })
    }

    @Test
    fun `a count of zero is read as absent, because a level nobody placed cannot hold volume`() {
        // The relay says it never sends this. The failure path is written anyway: the day LBank
        // drops the field, they will drop it quietly, and a `0` printed beside a bar that is plainly
        // there is worse than no figure at all.
        val body = """{"symbol":"BTCUSDT","bids":[[77621.90, 10.6449, 0]],"asks":[[77622.00, 1.0, -3]]}"""

        val dto = gson.fromJson(body, CryptoDepthDto::class.java)

        assertNull(dto.bids.toDepthLevels().single().orders)
        assertNull(dto.asks.toDepthLevels().single().orders)
    }

    @Test
    fun `a row with fewer than two numbers is dropped, not read as a level at size zero`() {
        val body = """{"symbol":"BTCUSDT","bids":[[77621.90], [77621.80, 0.5917, 2]],"asks":[]}"""

        val levels = gson.fromJson(body, CryptoDepthDto::class.java).bids.toDepthLevels()

        assertEquals(1, levels.size)
        assertEquals(77_621.80, levels.single().price, 1e-6)
        assertEquals(2, levels.single().orders)
    }

    @Test
    fun `a server older than the field answers rows of two and the app draws a book anyway`() {
        // The shape this app shipped against before 2026-08-29, and the shape staging still serves.
        // Nothing about it is an error: the ladder simply has no counts to mark.
        val body = """
            {"symbol":"BTCUSDT","depth":20,"truncated":true,
             "bids":[[77594.90, 28.4705], [77594.80, 0.4102]],
             "asks":[[77595.00, 9.0969], [77595.10, 0.3873]],
             "cache_ttl_ms": 500}
        """.trimIndent()

        val dto = gson.fromJson(body, CryptoDepthDto::class.java)
        val book = OrderBook.of(
            symbol = dto.symbol.orEmpty(),
            bids = dto.bids.toDepthLevels(),
            asks = dto.asks.toDepthLevels(),
            at = 0L,
            truncated = dto.truncated ?: false,
        )

        assertEquals(2, book.bids.size)
        assertEquals(2, book.asks.size)
        assertTrue(book.bids.all { it.orders == null })
        assertTrue(book.asks.all { it.orders == null })
    }
}
