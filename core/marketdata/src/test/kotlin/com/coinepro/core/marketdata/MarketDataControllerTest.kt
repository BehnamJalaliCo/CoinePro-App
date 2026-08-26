package com.coinepro.core.marketdata

import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.MarketType
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
    fun `naming symbols puts them on the socket URL`() {
        // TradeYar's crypto scope is 441 markets and a bare subscription takes all of them. Their
        // team declined to cap it server-side — a silent truncation would leave the app believing
        // it had the whole feed — so the cap is this parameter, and it has to actually be sent.
        val url = webSocketUrl(
            "https://crypto.example.com/api/".toHttpUrl(),
            listOf("btcusdt", " ethusdt "),
        )
        assertEquals("wss://crypto.example.com/api/ws/prices?symbols=BTCUSDT%2CETHUSDT", url)
    }

    @Test
    fun `an empty list still means the whole universe`() {
        // Not an oversight: a background sync and the diagnostics probe both genuinely want it, and
        // omitting the parameter is how both servers spell "everything".
        val url = webSocketUrl("https://crypto.example.com/api/".toHttpUrl(), emptyList())
        assertEquals("wss://crypto.example.com/api/ws/prices", url)
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
    fun a_forex_symbol_on_the_crypto_feed_is_typed_as_forex_not_guessed_as_crypto() {
        // It is not dropped here — the controller's platform filter is what keeps it off the crypto
        // screen — but it must never arrive labelled CRYPTO, which is how gold once reached a
        // crypto watchlist.
        val quote = WireQuoteDto(symbol = "EURUSD", price = 1.1, ts = 100L, source = "finnhub")
            .toDomain(nowMs = 100L, platform = MarketPlatform.TRADEYAR)
        assertEquals(MarketType.FOREX, quote?.instrument?.marketType)

        val coin = WireQuoteDto(symbol = "BTCUSDT", price = 60_000.0, ts = 100L, source = "lbank")
            .toDomain(nowMs = 100L, platform = MarketPlatform.TRADEYAR)
        assertEquals(MarketType.CRYPTO, coin?.instrument?.marketType)
    }

    @Test
    fun a_market_nobody_hand_listed_still_arrives() {
        // The rule used to be "XAUUSD, XAGUSD, or something ending in USDT" — so a market the
        // server started quoting was invisible until somebody edited a constant in the app.
        val listing = WireQuoteDto(symbol = "SUIUSDC", price = 3.2, ts = 100L, source = "lbank")
            .toDomain(nowMs = 100L, platform = MarketPlatform.TRADEYAR)
        assertEquals("SUI", listing?.instrument?.displayName)
        assertEquals(MarketType.CRYPTO, listing?.instrument?.marketType)

        val index = WireQuoteDto(symbol = "US500", price = 5_400.0, ts = 100L, source = "finnhub")
            .toDomain(nowMs = 100L, platform = MarketPlatform.COINEPRO_FX)
        assertEquals(MarketType.FOREX, index?.instrument?.marketType)
    }

    @Test
    fun a_symbol_the_app_cannot_draw_is_not_offered_as_a_market() {
        // This reverses an earlier rule, and the reversal is the owner's: the app used to accept
        // any symbol either feed sent and draw a lettered grey disc for the ones it had no mark
        // for. Beside forty real logos that does not read as a symbol, it reads as a broken image.
        //
        // The cost is real and is worth writing down: a genuinely new listing is now invisible
        // until `download-tv-logos.py` has been run and the app rebuilt. That is a slower path than
        // "the server added it, so it appears" — and it is the trade the owner asked for, because
        // what it removes is a long tail nobody opened the app to see.
        for (platform in MarketPlatform.entries) {
            assertNull(
                platform.name,
                WireQuoteDto(symbol = "SOMETHINGNEW", price = 1.0, ts = 100L, source = "lbank")
                    .toDomain(nowMs = 100L, platform = platform),
            )
        }
    }

    @Test
    fun a_symbol_the_app_can_draw_still_belongs_to_the_feed_it_arrived_on() {
        // The half of the old rule that survives: which market type a symbol gets is decided by the
        // feed it came in on, not by a hard-coded list. A server adding SOL to the crypto feed does
        // not need this app to be told that SOL is crypto.
        val onCrypto = WireQuoteDto(symbol = "SOLUSDT", price = 1.0, ts = 100L, source = "lbank")
            .toDomain(nowMs = 100L, platform = MarketPlatform.TRADEYAR)
        assertEquals(MarketType.CRYPTO, onCrypto?.instrument?.marketType)

        val onForex = WireQuoteDto(symbol = "EURUSD", price = 1.0, ts = 100L, source = "finnhub")
            .toDomain(nowMs = 100L, platform = MarketPlatform.COINEPRO_FX)
        assertEquals(MarketType.FOREX, onForex?.instrument?.marketType)
    }

    @Test
    fun feed_noise_is_not_a_market() {
        for (junk in listOf("1", "2Z", "0G", "1COIN")) {
            assertNull(
                junk,
                WireQuoteDto(symbol = junk, price = 1.0, ts = 100L, source = "lbank")
                    .toDomain(nowMs = 100L, platform = MarketPlatform.TRADEYAR),
            )
        }
    }

    @Test
    fun gold_is_shown_as_a_ticker_rather_than_an_English_word() {
        // It used to read "Gold" — an English word in an app that is Persian by default, and the
        // only row in the list that was not a ticker.
        val gold = WireQuoteDto(symbol = "XAUUSD", price = 2_400.0, ts = 100L, source = "finnhub")
            .toDomain(nowMs = 100L, platform = MarketPlatform.COINEPRO_FX)
        assertEquals("XAU/USD", gold?.instrument?.displayName)
    }
}
