package com.coinepro.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the symbol-to-artwork resolution, which is where this feature has failed before.
 *
 * The old table held eight entries and every one was a three-letter base with a clean quote suffix,
 * so none of the stripping rules were ever exercised. At seven hundred they are, and the failure
 * mode is silent: a wrongly stripped symbol finds no logo and falls back to a letter, which looks
 * like an archive gap rather than a bug.
 */
class AssetLogoTest {

    @Test
    fun `a quote suffix is stripped`() {
        assertEquals("BTC", baseOf("BTCUSDT"))
        assertEquals("BTC", baseOf("BTCUSDC"))
        assertEquals("BTC", baseOf("BTCUSD"))
        assertEquals("ETH", baseOf("ETHBTC"))
    }

    @Test
    fun `stripping never eats the instrument`() {
        // Wrapped Bitcoin ends in BTC, and taking that as the quote would leave "W".
        assertEquals("BTC", baseOf("WBTC"))
        // XBTUSD is Bitcoin under its ISO name; stripping USD leaves XBT, which aliases to BTC.
        assertEquals("BTC", baseOf("XBTUSD"))
        assertEquals("MIOTA", baseOf("IOTAUSDT"))
    }

    @Test
    fun `a symbol that is only a quote currency is left whole`() {
        // Two characters must survive a strip, so nothing here is cut down to a stub.
        assertEquals("USDT", baseOf("USDT"))
        assertEquals("USD", baseOf("USD"))
    }

    @Test
    fun `separators in a wire symbol do not change the instrument`() {
        assertEquals("BTC", baseOf("BTC/USDT"))
        assertEquals("BTC", baseOf("btc_usdt"))
        assertEquals("BTC", baseOf("BTC-USD"))
    }

    @Test
    fun `wrapped and staked tokens resolve to the asset they wrap`() {
        assertEquals("ETH", baseOf("STETHUSDT"))
        assertEquals("ETH", baseOf("WETH"))
        assertEquals("SOL", baseOf("WSOLUSDT"))
        assertEquals("MATIC", baseOf("POLUSDT"))
    }

    @Test
    fun `every market the app quotes today has artwork`() {
        val quoted = listOf(
            "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT",
            "XRPUSDT", "ADAUSDT", "DOGEUSDT", "TRXUSDT",
        )
        for (symbol in quoted) {
            assertTrue("$symbol has no logo", AssetLogoTable.forBase(baseOf(symbol)) != null)
        }
    }

    @Test
    fun `the recent listings the older archives miss are covered`() {
        // These are why a third archive was merged in; each one was a lettered token before.
        for (symbol in listOf("ARBUSDT", "SUIUSDT", "PEPEUSDT", "SEIUSDT", "WIFUSDT", "TONUSDT", "TIAUSDT")) {
            assertTrue("$symbol has no logo", AssetLogoTable.forBase(baseOf(symbol)) != null)
        }
    }

    @Test
    fun `the table is the whole archive, not a hand-picked handful`() {
        assertTrue(
            "Only ${AssetLogoTable.size} logos shipped — build-symbol-logos.py did not run",
            AssetLogoTable.size > 600,
        )
    }

    @Test
    fun `the metals keep their element symbols rather than a first letter`() {
        // Both are X-prefixed on the wire, so a first letter would label gold and silver alike.
        assertEquals("Au", initialFor("XAUUSD"))
        assertEquals("Ag", initialFor("XAGUSD"))
    }
}
