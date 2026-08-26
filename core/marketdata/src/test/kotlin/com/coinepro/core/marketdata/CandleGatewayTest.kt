package com.coinepro.core.marketdata

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The candle gateways, against the payloads the two teams actually documented.
 *
 * The JSON in these tests is copied from `RESPONSE_TRADEYAR.md` and `RESPONSE_COINEPROFX.md`, not
 * invented. That matters more here than usual: both teams warned about the same class of failure —
 * an integration that returns an empty list and looks entirely plausible — and both found real
 * instances of it in their own code while answering. A fixture written from the prose rather than
 * from the payload reproduces the prose, not the wire.
 */
class CandleGatewayTest {

    /**
     * A canned response, and the request that asked for it.
     *
     * An interceptor rather than a local HTTP server: it binds no port, needs no dependency the
     * project does not already have, and still exercises everything that can actually be wrong
     * here — the URL Retrofit builds, the headers it sends, and the JSON the converter parses.
     */
    private class Stub(private val body: String) : Interceptor {
        lateinit var request: Request
            private set

        override fun intercept(chain: Interceptor.Chain): Response {
            request = chain.request()
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private lateinit var stub: Stub

    private fun retrofit(body: String): Retrofit {
        stub = Stub(body)
        return Retrofit.Builder()
            .baseUrl("https://example.test/api/")
            .client(OkHttpClient.Builder().addInterceptor(stub).build())
            .addConverterFactory(
                GsonConverterFactory.create(
                    GsonBuilder()
                        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                        .create(),
                ),
            )
            .build()
    }

    // ── TradeYar ──────────────────────────────────────────────────────────────────────

    private val tradeYarBody = """
        {
          "symbol": "BTCUSDT",
          "tf": "H1",
          "candles": [
            {"t": 1787742000, "o": 78100.0, "h": 78300.0, "l": 78000.0, "c": 78250.0,
             "v": 90.1, "closed": true},
            {"t": 1787745600, "o": 78250.0, "h": 78420.0, "l": 78180.0, "c": 78400.0,
             "v": 75.5, "closed": true},
            {"t": 1787749200, "o": 78280.5, "h": 78416.0, "l": 77943.6, "c": 78006.0,
             "v": 109.5562, "closed": false}
          ],
          "oldest": 1787572800,
          "has_more": true,
          "limit_max": 1000,
          "source": "lbank",
          "server_time_ms": 1787750220123
        }
    """.trimIndent()

    @Test
    fun `a crypto page parses, in order, with the forming bar marked`() = runTest {
        val page = TradeYarCandleGateway(retrofit(tradeYarBody)).load("btcusdt", Timeframe.H1)

        assertEquals("BTCUSDT", page.symbol)
        assertEquals(Timeframe.H1, page.timeframe)
        assertEquals(3, page.candles.size)
        assertEquals(1787742000L, page.candles.first().t)
        assertEquals(78006.0, page.candles.last().c, 1e-9)
        assertTrue("the live edge is still forming and must say so", !page.candles.last().closed)
        assertTrue(page.candles.dropLast(1).all { it.closed })
        assertEquals(1787572800L, page.oldest)
        assertTrue(page.hasMore)
        assertEquals(1000, page.limitMax)
    }

    @Test
    fun `the symbol is uppercased and the timeframe is sent in the canonical spelling`() = runTest {
        TradeYarCandleGateway(retrofit(tradeYarBody)).load("btcusdt", Timeframe.M15, limit = 120, before = 999L)

        val url = stub.request.url
        assertEquals("BTCUSDT", url.queryParameter("symbol"))
        assertEquals("M15", url.queryParameter("tf"))
        assertEquals("120", url.queryParameter("limit"))
        assertEquals("999", url.queryParameter("before"))
    }

    @Test
    fun `the server's own spelling of the timeframe wins`() = runTest {
        // Their team documented this: send `15m` and the answer says `M15`. A saved layout can
        // carry the other spelling, and the server's answer is the one to believe.
        val body = """{"symbol":"BTCUSDT","tf":"M15","candles":[],"limit_max":1000}"""
        val page = TradeYarCandleGateway(retrofit(body)).load("BTCUSDT", Timeframe.H1)
        assertEquals(Timeframe.M15, page.timeframe)
    }

    // ── CoinePro-FX ───────────────────────────────────────────────────────────────────

    private val academyBody = """
        {"symbol":"XAUUSD","timeframe":"H1",
         "candles":[
           {"t":1787540400,"o":4630.0,"h":4640.0,"l":4625.0,"c":4638.0,"v":18000.0},
           {"t":1787547600,"o":4639.15,"h":4646.64,"l":4629.87,"c":4640.72,"v":21946.0}
         ],
         "price":4640.72}
    """.trimIndent()

    private class FixedToken(private val value: String = "academy-token") : AcademyTokenStore {
        var minted = 0
        override suspend fun token(): String {
            minted++
            return value
        }
        override fun clear() = Unit
    }

    @Test
    fun `a forex page parses and carries the academy token, not the mobile one`() = runTest {
        val tokens = FixedToken()
        val page = CoineProFxCandleGateway(retrofit(academyBody), tokens, nowSeconds = { 1_787_551_000 })
            .load("xauusd", Timeframe.H1)

        assertEquals("Bearer academy-token", stub.request.header("Authorization"))
        assertEquals(1, tokens.minted)
        assertEquals("XAUUSD", page.symbol)
        assertEquals(2, page.candles.size)
        assertEquals(4640.72, page.candles.last().c, 1e-9)
    }

    @Test
    fun `a forex bar whose period has not elapsed is marked as still forming`() = runTest {
        // CoinePro-FX sends no `closed` flag, so it is derived from the clock. Assuming every bar
        // is closed would draw a half-formed hour as a real one at the right edge of every chart.
        val page = CoineProFxCandleGateway(
            retrofit(academyBody),
            FixedToken(),
            // Two minutes into the second bar's hour.
            nowSeconds = { 1_787_547_600 + 120 },
        ).load("XAUUSD", Timeframe.H1)

        assertTrue("the earlier bar is complete", page.candles.first().closed)
        assertFalse("the live bar is not", page.candles.last().closed)
    }

    @Test
    fun `the forex limit is clamped to the server's stated ceiling`() = runTest {
        CoineProFxCandleGateway(retrofit(academyBody), FixedToken())
            .load("XAUUSD", Timeframe.D1, limit = 9_000)
        assertEquals("3000", stub.request.url.queryParameter("limit"))
    }

    // ── shared mapping ────────────────────────────────────────────────────────────────

    @Test
    fun `a descending page is sorted rather than drawn as a mirror image`() = runTest {
        // Only TradeYar promised ascending order and enforces it server-side. A chart drawn from a
        // descending page looks like a real chart of a market that did the opposite, which is what
        // makes this worth forcing rather than trusting.
        val body = """{"symbol":"XAUUSD","timeframe":"H1","candles":[
                 {"t":300,"o":3.0,"h":3.0,"l":3.0,"c":3.0,"v":1.0},
                 {"t":200,"o":2.0,"h":2.0,"l":2.0,"c":2.0,"v":1.0},
                 {"t":100,"o":1.0,"h":1.0,"l":1.0,"c":1.0,"v":1.0}]}"""
        val page = CoineProFxCandleGateway(retrofit(body), FixedToken()).load("XAUUSD", Timeframe.H1)
        assertEquals(listOf(100L, 200L, 300L), page.candles.map { it.t })
    }

    @Test
    fun `a duplicated open time is kept once`() = runTest {
        val body = """{"symbol":"XAUUSD","timeframe":"H1","candles":[
                 {"t":100,"o":1.0,"h":1.0,"l":1.0,"c":1.0,"v":1.0},
                 {"t":100,"o":1.0,"h":1.0,"l":1.0,"c":9.0,"v":1.0}]}"""
        val page = CoineProFxCandleGateway(retrofit(body), FixedToken()).load("X", Timeframe.H1)
        assertEquals(1, page.candles.size)
    }

    @Test
    fun `a bar missing a price is dropped, not zeroed`() = runTest {
        // A zero close puts a candle on the floor of the chart and rescales the price axis around
        // it — one bad row and the whole picture is wrong rather than one bar being absent.
        val body = """{"symbol":"XAUUSD","timeframe":"H1","candles":[
                 {"t":100,"o":1.0,"h":1.0,"l":1.0,"c":1.0,"v":1.0},
                 {"t":200,"o":2.0,"h":2.0,"l":2.0,"v":1.0}]}"""
        val page = CoineProFxCandleGateway(retrofit(body), FixedToken()).load("X", Timeframe.H1)
        assertEquals(listOf(100L), page.candles.map { it.t })
    }

    @Test
    fun `an empty page is empty rather than throwing`() = runTest {
        val body = """{"symbol":"BTCUSDT","tf":"H1","candles":[],"limit_max":1000}"""
        assertTrue(TradeYarCandleGateway(retrofit(body)).load("BTCUSDT", Timeframe.H1).isEmpty)
    }

    // ── timeframes ────────────────────────────────────────────────────────────────────

    @Test
    fun `both spellings of a timeframe resolve to the same one`() {
        assertEquals(Timeframe.M15, Timeframe.of("M15"))
        assertEquals(Timeframe.M15, Timeframe.of("15m"))
        assertEquals(Timeframe.H1, Timeframe.of("1h"))
        assertEquals(Timeframe.D1, Timeframe.of("1d"))
        assertEquals(Timeframe.W1, Timeframe.of("1w"))
    }

    @Test
    fun `an unknown timeframe is null rather than a silent default`() {
        // A default would draw an hourly chart for somebody who asked for something else and say
        // nothing about it. The caller decides what to do with the null.
        for (junk in listOf("", "  ", "H7", "banana", "0m")) {
            assertEquals(junk, null, Timeframe.of(junk))
        }
        assertEquals(null, Timeframe.of(null))
    }

    @Test
    fun `every timeframe's period matches its name`() {
        assertEquals(60L, Timeframe.M1.seconds)
        assertEquals(900L, Timeframe.M15.seconds)
        assertEquals(3_600L, Timeframe.H1.seconds)
        assertEquals(14_400L, Timeframe.H4.seconds)
        assertEquals(86_400L, Timeframe.D1.seconds)
        assertEquals(604_800L, Timeframe.W1.seconds)
    }
}
