package com.coinepro.core.symbols

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that nothing without a logo reaches a list.
 *
 * The lettered token — a grey disc with a letter in it — was the app's answer for anything it had
 * no mark for. Beside forty real logos it does not read as "this is that coin"; it reads as a
 * broken image, and six of them read as a broken app. So it is no longer an answer: the catalogue
 * filters on [SymbolArtwork.covers] and the token becomes a defensive fallback nothing reaches.
 *
 * These tests are the guard on that filter being *right*. A filter that is too eager is worse than
 * no filter, because it silently removes markets a reader is looking for.
 */
class SymbolArtworkTest {

    @Test
    fun `the majors every reader expects are all covered`() {
        // If any of these ever fall out, the filter has removed a market somebody was looking for
        // — which is the failure mode that matters here, and the one that looks like nothing.
        val majors = listOf(
            "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT", "ADAUSDT",
            "DOGEUSDT", "TRXUSDT", "LTCUSDT", "DOTUSDT", "AVAXUSDT", "LINKUSDT",
            "UNIUSDT", "ATOMUSDT", "XLMUSDT", "NEARUSDT", "APTUSDT", "ARBUSDT",
            "OPUSDT", "SUIUSDT", "PEPEUSDT", "TONUSDT", "INJUSDT", "AAVEUSDT",
        )
        val missing = majors.filterNot(SymbolArtwork::covers)
        assertTrue("these majors would be hidden from the list: $missing", missing.isEmpty())
    }

    @Test
    fun `the forex pairs and metals the broker quotes are covered`() {
        val pairs = listOf(
            "EURUSD", "GBPUSD", "USDJPY", "USDCHF", "AUDUSD", "NZDUSD", "USDCAD",
            "EURGBP", "EURJPY", "GBPJPY", "USDTRY", "USDZAR", "USDMXN", "USDSEK",
            "XAUUSD", "XAGUSD",
        )
        val missing = pairs.filterNot(SymbolArtwork::covers)
        assertTrue("these pairs would be hidden: $missing", missing.isEmpty())
    }

    @Test
    fun `a pair with one unknown leg is not listed`() {
        // TradingView publishes a grey placeholder rather than a flag for Hong Kong and Taiwan, so
        // those are absent by design. Half a pair drawn as a flag and half as a lettered disc is
        // the broken-image problem inside a single row.
        assertFalse(SymbolArtwork.covers("USDHKD"))
        assertFalse(SymbolArtwork.covers("USDTWD"))
        assertTrue("the leg that does exist must still work elsewhere", SymbolArtwork.covers("USDJPY"))
    }

    @Test
    fun `a coin with no artwork anywhere is not listed`() {
        assertFalse(SymbolArtwork.covers("NOSUCHCOINUSDT"))
        assertFalse(SymbolArtwork.covers("ZZZZUSDT"))
    }

    @Test
    fun `an equity or index mark counts`() {
        for (symbol in listOf("QQQUSDT", "NAS100", "TSLAUSDT", "SAMSUNGUSDT", "US500")) {
            assertTrue("$symbol has a company mark and should be listed", SymbolArtwork.covers(symbol))
        }
    }

    @Test
    fun `the same asset under every quote currency resolves alike`() {
        // Coverage is a property of the asset, not of what it is priced in. If USDT worked and USDC
        // did not, half a venue's listing would vanish for no reason a reader could see.
        for (quote in listOf("USDT", "USDC", "USD", "BTC", "ETH")) {
            assertTrue("BTC$quote", SymbolArtwork.covers("BTC$quote"))
        }
    }

    @Test
    fun `the wrapped forms resolve to what they wrap`() {
        assertTrue(SymbolArtwork.covers("WBTCUSDT"))
        assertTrue(SymbolArtwork.covers("WETHUSDT"))
    }

    @Test
    fun `an empty or nonsense symbol is not covered rather than throwing`() {
        assertFalse(SymbolArtwork.covers(""))
        assertFalse(SymbolArtwork.covers("   "))
        assertFalse(SymbolArtwork.covers("///"))
    }

    @Test
    fun `the coverage set is large enough to be the real one`() {
        // A guard against the generator writing an empty file and every market quietly vanishing —
        // which would pass every other test in this class, since they all assert on membership.
        assertTrue("only ${SymbolArtwork.BASES.size} bases", SymbolArtwork.BASES.size > 700)
        assertTrue(SymbolArtwork.CURRENCIES.size >= 24)
        assertTrue(SymbolArtwork.METALS.size >= 2)
    }

    @Test
    fun `an index is covered by its country's flag`() {
        listOf("US30", "US100", "US500", "UK100", "GER40", "FRA40", "JPN225", "AUS200", "EU50")
            .forEach { assertTrue(it, SymbolArtwork.covers(it)) }
    }

    @Test
    fun `an index with no flag stays out rather than showing a lettered disc`() {
        // Hong Kong's bauhinia is not in the flag set and is not something to approximate. The
        // index is left uncovered, which keeps it out of the catalogue — the honest outcome, and
        // the one the whole of SymbolArtwork exists to produce.
        assertFalse(SymbolArtwork.covers("HK50"))
    }

    @Test
    fun `every index in the country table has a name to show beside it`() {
        // A flag with no name would list as a disc and a ticker. The two tables are edited in
        // different files, so nothing but this stops them drifting apart.
        SymbolArtwork.INDEX_COUNTRY.keys.forEach {
            assertTrue("$it has a flag and no Persian name", it in SymbolNames.INDEX)
        }
    }
}
