package com.coinepro.core.marketdata

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
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
          "source": "lbank"
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
}
