package com.coinepro.core.marketdata

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ticker route's body, parsed by the same Gson the app builds.
 *
 * ### Why this test is not paranoia
 *
 * `NetworkFactory` sets `LOWER_CASE_WITH_UNDERSCORES`, which separates on **uppercase letters**. A
 * digit is not one, so a Kotlin property called `open24h` maps to the JSON name `open24h` — and
 * this route sends `open_24h`. Six of the most important fields on the row are spelled that way:
 * the day's open, high, low, change, volume and turnover.
 *
 * Had the naming policy been trusted, every one of them would have arrived null on a response that
 * was entirely correct, and the failure would have looked like an empty screen rather than an
 * error — the exact shape of bug the server's own team wrote a section about when they built this.
 *
 * So the body below is the server's own sample, pasted rather than paraphrased, and this test is
 * what holds the six names in place.
 */
class MarketTickerWireTest {

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    /** The server's documented response, verbatim. */
    private val body = """
        {
          "tickers": [
            {
              "symbol": "BTCUSDT",
              "last": 78102.4,
              "open_24h": 77360.9,
              "high_24h": 78308.2,
              "low_24h": 77217.9,
              "change_percent_24h": 0.9584,
              "volume_24h": 5624.6544,
              "turnover_24h": 437096837.3946788,
              "mark_price": 78102.4,
              "index_price": 78135.4,
              "funding_rate": 0.00009263,
              "funding_interval_s": 28800,
              "next_funding_at_ms": 1788048000000,
              "open_interest": 7966.5333,
              "ts": 1788036416000
            }
          ],
          "server_time_ms": 1788037124000,
          "cache_ttl_ms": 5000,
          "fetched_at_ms": 1788037121000,
          "source": "lbank",
          "price_feed": {
            "tier": "ws",
            "sockets_up": 5,
            "sockets_total": 5,
            "tick_age_ms": 3816
          }
        }
    """.trimIndent()

    @Test
    fun `every field on the server's own sample row lands`() {
        val row = gson.fromJson(body, MarketTickersDto::class.java).tickers.single().toDomain()
        assertNotNull("the sample row was dropped entirely", row)
        requireNotNull(row)
        assertEquals("BTCUSDT", row.symbol)
        assertEquals(78102.4, row.last, 1e-9)
        assertEquals(77360.9, row.open24h!!, 1e-9)
        assertEquals(78308.2, row.high24h!!, 1e-9)
        assertEquals(77217.9, row.low24h!!, 1e-9)
        assertEquals(0.9584, row.changePercent24h!!, 1e-9)
        assertEquals(5624.6544, row.volume24h!!, 1e-9)
        assertEquals(437096837.3946788, row.turnover24h!!, 1e-6)
        assertEquals(78102.4, row.markPrice!!, 1e-9)
        assertEquals(78135.4, row.indexPrice!!, 1e-9)
        assertEquals(0.00009263, row.fundingRate!!, 1e-12)
        assertEquals(28800L, row.fundingIntervalSeconds)
        assertEquals(1788048000000L, row.nextFundingAtEpochMillis)
        assertEquals(7966.5333, row.openInterest!!, 1e-9)
        assertEquals(1788036416000L, row.timestampEpochMillis)
    }

    @Test
    fun `the envelope's own fields land too`() {
        val table = gson.fromJson(body, MarketTickersDto::class.java)
        assertEquals(1788037124000L, table.serverTimeMs)
        assertEquals(5000L, table.cacheTtlMs)
        // Not asked for, and worth more than the TTL it accompanies: it says how old the figures
        // actually are rather than how old they are permitted to get.
        assertEquals(1788037121000L, table.fetchedAtMs)
        assertEquals("lbank", table.source)
        // The transport got its own key rather than overloading `source`, which names the exchange
        // and which this app has parsed since the route existed.
        val feed = table.priceFeed!!.toDomain()
        assertEquals(PriceFeedTier.WS, feed.tier)
        assertEquals(5, feed.socketsUp)
        assertEquals(5, feed.socketsTotal)
        assertEquals(3816L, feed.tickAgeMillis)
        assertTrue(!feed.degraded)
        // 3 816 ms on a healthy feed, and this is the trap: the relay rewrites its health record
        // every five seconds, so the age is a bound rather than a measurement. A threshold at the
        // write interval would put a «کهنه» badge on a feed whose true tick age was 2 ms.
        assertTrue(!feed.stale)
    }

    @Test
    fun `four dead shards out of five is an outage the tier alone cannot see`() {
        // The relay calls itself `ws` while any shard lives, and `connected` while `sockets_up > 0`
        // — so this is precisely the state that hid for forty-five hours. Both halves of the rule
        // are needed, and this is the half a single flag misses.
        val body = """{"tickers":[],"price_feed":{"tier":"ws","sockets_up":1,"sockets_total":5}}"""
        val feed = gson.fromJson(body, MarketTickersDto::class.java).priceFeed!!.toDomain()
        assertTrue(feed.degraded)
        assertTrue(feed.partialOutage)
        assertTrue(!feed.fullOutage)
    }

    @Test
    fun `the fallback tier is the full outage, and is read as one`() {
        val body = """{"tickers":[],"price_feed":{"tier":"rest_fallback","sockets_up":0,"sockets_total":5}}"""
        val feed = gson.fromJson(body, MarketTickersDto::class.java).priceFeed!!.toDomain()
        assertTrue(feed.degraded)
        assertTrue(feed.fullOutage)
        assertTrue(!feed.partialOutage)
    }

    @Test
    fun `an unrecognised tier is unknown, never optimistically healthy`() {
        // Redis down, or a tier a later server grows. Either way the honest answer is that we do
        // not know, and «نمی‌دانم» must never be drawn as «سالم» — that is the whole reason the
        // server sends a string here instead of a count.
        val body = """{"tickers":[],"price_feed":{"tier":"quantum"}}"""
        val feed = gson.fromJson(body, MarketTickersDto::class.java).priceFeed!!.toDomain()
        assertEquals(PriceFeedTier.UNKNOWN, feed.tier)
        assertTrue(feed.degraded)
    }

    @Test
    fun `a server that does not send the field leaves it null rather than healthy`() {
        // A deployment older than the field. Null has to stay null all the way to the screen: a
        // badge reading «سالم» here would be the same silent lie the field was built to end.
        val body = """{"tickers":[{"symbol":"BTCUSDT","last":1.0}],"source":"lbank"}"""
        assertNull(gson.fromJson(body, MarketTickersDto::class.java).priceFeed)
    }

    @Test
    fun `a stale tick is only called stale past fifteen seconds`() {
        val fresh = """{"tickers":[],"price_feed":{"tier":"ws","sockets_up":5,"sockets_total":5,"tick_age_ms":4999}}"""
        val old = """{"tickers":[],"price_feed":{"tier":"ws","sockets_up":5,"sockets_total":5,"tick_age_ms":15001}}"""
        assertTrue(!gson.fromJson(fresh, MarketTickersDto::class.java).priceFeed!!.toDomain().stale)
        assertTrue(gson.fromJson(old, MarketTickersDto::class.java).priceFeed!!.toDomain().stale)
    }

    @Test
    fun `an absent key stays absent rather than becoming zero`() {
        // The contract the whole route rests on: the server omits what it does not know. A spot
        // market has no funding and no open interest, and `0.0` there would be a claim — a funding
        // rate of zero is a real reading a trader would act on, and "did not trade" is a fact
        // about the market rather than about our knowledge of it.
        val spot = """{"tickers":[{"symbol":"ADAUSDT","last":0.94}]}"""
        val row = gson.fromJson(spot, MarketTickersDto::class.java).tickers.single().toDomain()!!
        assertNull(row.fundingRate)
        assertNull(row.openInterest)
        assertNull(row.volume24h)
        assertNull(row.changePercent24h)
        assertEquals(0.94, row.last, 1e-9)
    }

    @Test
    fun `a zero the server actually sent is kept`() {
        // The other half of the rule above. Absent means unknown; present-and-zero means zero, and
        // a funding rate that has genuinely settled at zero must not be erased by a filter written
        // to catch the unknown case.
        val flat = """{"tickers":[{"symbol":"ADAUSDT","last":0.94,"funding_rate":0.0}]}"""
        val row = gson.fromJson(flat, MarketTickersDto::class.java).tickers.single().toDomain()!!
        assertEquals(0.0, row.fundingRate!!, 1e-12)
    }

    @Test
    fun `a row with no symbol or no price is dropped, not repaired`() {
        val broken = """
            {"tickers":[
              {"last": 12.0},
              {"symbol": "BTCUSDT"},
              {"symbol": "ETHUSDT", "last": 0.0},
              {"symbol": "  ", "last": 5.0},
              {"symbol": "SOLUSDT", "last": 190.5}
            ]}
        """.trimIndent()
        val rows = gson.fromJson(broken, MarketTickersDto::class.java).tickers.mapNotNull { it.toDomain() }
        assertEquals(listOf("SOLUSDT"), rows.map { it.symbol })
    }

    @Test
    fun `the symbol is normalised the way every other route spells it`() {
        val lower = """{"tickers":[{"symbol":" btcusdt ","last":1.0}]}"""
        val row = gson.fromJson(lower, MarketTickersDto::class.java).tickers.single().toDomain()!!
        assertEquals("BTCUSDT", row.symbol)
    }

    @Test
    fun `the route sits under the mobile prefix`() {
        // TradeYar's nginx has a `/ws` location that the mobile prefix deliberately sits inside, so
        // a path written without it reaches a different server and answers plausibly.
        assertTrue(NetworkMarketTickerGateway.TRADEYAR_PATH.startsWith("api/mobile/v1/"))
        assertEquals("api/mobile/v1/market/tickers", NetworkMarketTickerGateway.TRADEYAR_PATH)
    }

    @Test
    fun `the forex route sits under that platform's own prefix`() {
        // The two backends do not share a prefix and nothing here tries to make them: CoinePro-FX
        // mounts the app's whole surface under `user/`, and a path borrowed from the crypto side
        // would 404 on it. This platform had no such route at all until 2026-09-01 — see the note
        // on `create`, and the three screens that were half empty because of it.
        assertTrue(NetworkMarketTickerGateway.FOREX_PATH.startsWith("user/mobile/"))
        assertEquals("user/mobile/market/tickers", NetworkMarketTickerGateway.FOREX_PATH)
        assertNotEquals(NetworkMarketTickerGateway.TRADEYAR_PATH, NetworkMarketTickerGateway.FOREX_PATH)
    }

    @Test
    fun `a row with no day behind it still carries its price`() {
        // What the forex route sends for a symbol whose hourly candles have not been written for
        // the last day — a fresh listing, a gap in the resampler. The contract is that only
        // `symbol` and `last` are guaranteed, and this is the case that exercises it: the market
        // row draws the price and leaves the change column empty, rather than the row vanishing or
        // the change reading a confident zero per cent.
        val body = """{"tickers":[{"symbol":"XAUUSD","last":2643.18,"open_24h":null,
            "high_24h":null,"low_24h":null,"change_percent_24h":null,"volume_24h":null,
            "ts":1788261604779}]}"""
        val row = gson.fromJson(body, MarketTickersDto::class.java).tickers.single().toDomain()!!
        assertEquals("XAUUSD", row.symbol)
        assertEquals(2643.18, row.last, 0.0)
        assertNull(row.changePercent24h)
        assertNull(row.open24h)
    }
}
