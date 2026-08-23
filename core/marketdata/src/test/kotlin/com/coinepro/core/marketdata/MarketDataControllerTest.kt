package com.coinepro.core.marketdata

import com.coinepro.core.model.QuoteSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketDataControllerTest {
    @Test
    fun parser_reads_normalized_finnhub_and_lbank_quotes() {
        val raw = """
            {
              "type":"prices",
              "server_time_ms":1720075361238,
              "data":{
                "XAUUSD":{"symbol":"XAUUSD","price":3372.4,"ts":1720075361000,"source":"finnhub"},
                "BTCUSDT":{"symbol":"BTCUSDT","price":58971.6,"ts":1720075360000,"source":"lbank_futures_ws"}
              }
            }
        """.trimIndent()

        val parsed = requireNotNull(MarketWireParser().parse(raw))
        assertEquals(2, parsed.quotes.size)
        assertEquals(1720075361238, parsed.serverTimeMs)
        assertEquals("BTCUSDT", parsed.quotes.last().symbol)
    }

    @Test
    fun websocket_url_preserves_api_prefix_and_switches_to_wss() {
        val url = webSocketUrl("https://fx.example.com/api/".toHttpUrl())
        assertEquals("wss://fx.example.com/api/ws/prices", url.toString())
    }

    @Test
    fun freshness_is_source_specific_and_never_fakes_live_state() {
        val now = 200_000L
        assertFalse(isQuoteStale(QuoteSource.LBANK, now - 10_000L, now))
        assertTrue(isQuoteStale(QuoteSource.LBANK, now - 16_000L, now))
        assertFalse(isQuoteStale(QuoteSource.FINNHUB, now - 60_000L, now))
        assertTrue(isQuoteStale(QuoteSource.FINNHUB, now - 91_000L, now))
        assertTrue(isQuoteStale(QuoteSource.UNKNOWN, 0L, now))
    }

    @Test
    fun out_of_scope_market_symbol_is_rejected_instead_of_guessed_as_crypto() {
        assertNull(
            WireQuoteDto(symbol = "EURUSD", price = 1.1, ts = 100L, source = "finnhub")
                .toDomain(nowMs = 100L),
        )
        assertTrue(
            WireQuoteDto(symbol = "BTCUSDT", price = 60_000.0, ts = 100L, source = "lbank")
                .toDomain(nowMs = 100L) != null,
        )
    }
}
