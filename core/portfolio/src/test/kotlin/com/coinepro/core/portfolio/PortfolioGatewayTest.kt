package com.coinepro.core.portfolio

import com.coinepro.core.model.MarketPlatform
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import java.util.concurrent.atomic.AtomicReference
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The two histories, read against the JSON their teams actually returned.
 *
 * Both payloads below are copied from the response documents rather than invented, including the
 * awkward parts: CoinePro-FX's ISO-8601 strings on a server whose candle routes use unix seconds,
 * and TradeYar's nulls on trades whose opening leg fell outside the window.
 *
 * The stub is an interceptor rather than a local web server, so nothing binds a port.
 */
class PortfolioGatewayTest {

    private val captured = AtomicReference<Request?>()

    private fun retrofit(body: String): Retrofit {
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    captured.set(chain.request())
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            )
            .build()
        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        return Retrofit.Builder()
            .baseUrl("https://example.invalid/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    // Their live sample, unchanged.
    private val forexBody = """
        {"items":[{
          "id":7207, "deal_id":28208475, "signal_id":953,
          "symbol":"XAGUSD", "direction":"buy", "volume":0.08,
          "entry_price":68.967, "exit_price":68.293,
          "open_time":"2026-08-26T07:06:15.190342+00:00",
          "close_time":"2026-08-26T13:00:01+00:00",
          "duration_sec":21225,
          "gross_profit":-256.8, "commission":-0.56, "swap":0.0, "spread_cost":0.0,
          "net_profit":-257.36, "pips":-67.4,
          "close_reason":"sl", "balance_after":46485.18
        }],
         "total":64, "page":1, "per_page":50, "total_pages":2}
    """.trimIndent()

    private val cryptoBody = """
        {"trades":[
          {"id":"1007998664102044","symbol":"ETHUSDT","side":"sell",
           "opened_at":1787743555,"closed_at":1787750716,
           "entry":2465.75,"exit":2438.28,"quantity":0.011,
           "pnl":0.30217,"fee":0.01609265,"currency":"USDT","liquidated":false},
          {"id":"1007998664102045","symbol":"WLDUSDT","side":"sell",
           "opened_at":null,"closed_at":1787750800,
           "entry":null,"exit":0.3941,"quantity":62.8,
           "pnl":0.2198,"fee":0.004,"currency":"USDT","liquidated":true}
        ],
        "page":1,"per_page":100,"total":52,
        "window":{"from":1787146659,"to":1787751459},
        "window_max_days":31,
        "as_of":1787751459,"fresh_until":1787751519,
        "truncated":false}
    """.trimIndent()

    @Test
    fun `the forex ledger reads, ISO timestamps and all`() = runTest {
        val gateway = CoineProFxPortfolioGateway(retrofit(forexBody))
        val page = gateway.history()

        assertEquals(1, page.trades.size)
        val trade = page.trades.first()
        assertEquals("7207", trade.id)
        assertEquals("XAGUSD", trade.symbol)
        assertEquals(TradeDirection.BUY, trade.direction)
        assertEquals(-257.36, trade.netProfit!!, 1e-9)
        assertEquals(-256.8, trade.grossProfit!!, 1e-9)
        assertEquals("sl", trade.closeReason)
        assertEquals(46_485.18, trade.balanceAfter!!, 1e-9)
        // 2026-08-26T13:00:01+00:00
        assertEquals(1_787_749_201L, trade.closedAt)
        assertEquals(1_787_727_975L, trade.openedAt)
        // Their own duration_sec, arrived at independently.
        assertEquals(21_226L, trade.durationSeconds)
    }

    @Test
    fun `the forex route is asked for its own path and page`() = runTest {
        CoineProFxPortfolioGateway(retrofit(forexBody)).history(page = 2, perPage = 25)
        val url = captured.get()!!.url
        assertEquals("/user/trade-history", url.encodedPath)
        assertEquals("2", url.queryParameter("page"))
        assertEquals("25", url.queryParameter("per_page"))
    }

    @Test
    fun `more pages are offered while the server says there are more`() = runTest {
        val page = CoineProFxPortfolioGateway(retrofit(forexBody)).history()
        assertTrue("page 1 of 2", page.hasMore)
        assertEquals(64, page.total)
    }

    @Test
    fun `an sl close with a positive result is still a win`() = runTest {
        // Their live data has exactly this: a stop that trailed above entry. Reading close_reason
        // as the outcome would file a profitable trade as a loss.
        val body = forexBody.replace("\"net_profit\":-257.36", "\"net_profit\":19.56")
        val trade = CoineProFxPortfolioGateway(retrofit(body)).history().trades.first()
        assertEquals("sl", trade.closeReason)
        assertTrue(trade.isWin)
        assertFalse(trade.isLoss)
    }

    @Test
    fun `the crypto history reads, including the trades whose open is unknown`() = runTest {
        val gateway = TradeYarPortfolioGateway(retrofit(cryptoBody))
        val page = gateway.history(from = 1_787_146_659L, to = 1_787_751_459L)

        assertEquals(2, page.trades.size)
        val eth = page.trades.first()
        assertEquals("ETHUSDT", eth.symbol)
        assertEquals(TradeDirection.SELL, eth.direction)
        assertEquals(0.30217, eth.netProfit!!, 1e-9)
        // The fee arrives positive and is stored as a cost, matching the forex side's convention
        // of negative commissions — so `costs` sums the same way on both platforms.
        assertEquals(-0.01609265, eth.commission!!, 1e-9)
        assertEquals("USDT", eth.currency)

        val wld = page.trades[1]
        assertNull("the opening leg fell before the window", wld.openedAt)
        assertNull(wld.entry)
        assertNull("no duration without an open", wld.durationSeconds)
        assertTrue(wld.liquidated)
    }

    @Test
    fun `the crypto side has no gross and no swap, and does not invent them`() = runTest {
        val trade = TradeYarPortfolioGateway(retrofit(cryptoBody)).history().trades.first()
        assertNull(trade.grossProfit)
        assertNull(trade.swap)
        assertNull(trade.balanceAfter)
    }

    @Test
    fun `the window the server served is reported back`() = runTest {
        val page = TradeYarPortfolioGateway(retrofit(cryptoBody)).history()
        assertEquals(1_787_146_659L, page.windowFrom)
        assertEquals(1_787_751_459L, page.windowTo)
        assertFalse(page.truncated)
    }

    @Test
    fun `the crypto route carries the window as query parameters`() = runTest {
        TradeYarPortfolioGateway(retrofit(cryptoBody)).history(from = 100L, to = 200L, page = 3)
        val url = captured.get()!!.url
        assertEquals("/api/mobile/v1/portfolio/history", url.encodedPath)
        assertEquals("100", url.queryParameter("from"))
        assertEquals("200", url.queryParameter("to"))
        assertEquals("3", url.queryParameter("page"))
    }

    @Test
    fun `paging stops at the total rather than asking for a page that costs seventeen seconds`() = runTest {
        // 1 × 100 of 52 is already past the end. The server answers no "hasMore", so it is derived
        // — and getting it wrong here means a cold request against LBank for nothing.
        val page = TradeYarPortfolioGateway(retrofit(cryptoBody)).history()
        assertFalse(page.hasMore)
    }

    @Test
    fun `a truncated window is carried through rather than swallowed`() = runTest {
        val body = cryptoBody.replace("\"truncated\":false", "\"truncated\":true")
        assertTrue(TradeYarPortfolioGateway(retrofit(body)).history().truncated)
    }

    @Test
    fun `a row missing its symbol or its close is dropped rather than drawn`() = runTest {
        val body = """
            {"trades":[
              {"id":"1","symbol":null,"side":"sell","closed_at":1787750716,"pnl":1.0},
              {"id":"2","symbol":"BTCUSDT","side":"sell","closed_at":null,"pnl":1.0},
              {"id":"3","symbol":"BTCUSDT","side":"nonsense","closed_at":1787750716,"pnl":1.0},
              {"id":"4","symbol":"BTCUSDT","side":"buy","closed_at":1787750716,"pnl":1.0}
            ],"page":1,"per_page":100,"total":4}
        """.trimIndent()
        val page = TradeYarPortfolioGateway(retrofit(body)).history()
        assertEquals(listOf("4"), page.trades.map { it.id })
    }

    @Test
    fun `each platform gets its own gateway`() {
        val fx = PortfolioGatewayFactory.create(MarketPlatform.COINEPRO_FX, retrofit("{}"))
        val crypto = PortfolioGatewayFactory.create(MarketPlatform.TRADEYAR, retrofit("{}"))
        assertTrue(fx is CoineProFxPortfolioGateway)
        assertTrue(crypto is TradeYarPortfolioGateway)
    }
}
