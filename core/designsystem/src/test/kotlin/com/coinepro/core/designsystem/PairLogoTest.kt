package com.coinepro.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins which symbols are drawn as a pair of flags rather than as a single coin.
 *
 * The split is by shape, not by a market list, so the risk is a coin ticker that happens to be six
 * letters being torn in half and drawn as two unknown flags. That is what these assert against.
 */
class PairLogoTest {

    @Test
    fun `a forex symbol splits into base and quote`() {
        assertEquals("EUR" to "USD", pairOf("EURUSD"))
        assertEquals("USD" to "JPY", pairOf("USDJPY"))
        assertEquals("GBP" to "AUD", pairOf("GBPAUD"))
    }

    @Test
    fun `the metals are pairs too, quoted in a currency`() {
        assertEquals("XAU" to "USD", pairOf("XAUUSD"))
        assertEquals("XAG" to "EUR", pairOf("XAGEUR"))
    }

    @Test
    fun `a coin is never torn in half`() {
        // Seven characters, so the length test alone rejects it.
        assertNull(pairOf("BTCUSDT"))
        // Six characters and both halves unknown as currencies — the case the length test misses.
        assertNull(pairOf("MATICX"))
        assertNull(pairOf("PEPEUS"))
    }

    @Test
    fun `separators do not change the split`() {
        assertEquals("EUR" to "USD", pairOf("EUR/USD"))
        assertEquals("EUR" to "USD", pairOf("eur_usd"))
    }

    @Test
    fun `the offshore and onshore renminbi share one flag`() {
        // The MT5 feed quotes both; they are one country and must not render as one known flag and
        // one lettered stub.
        assertEquals("USD" to "CNH", pairOf("USDCNH"))
        assertEquals("USD" to "CNY", pairOf("USDCNY"))
    }

    @Test
    fun `a pair symbol routes away from the coin table`() {
        assertEquals(true, isPairSymbol("EURUSD"))
        assertEquals(true, isPairSymbol("XAUUSD"))
        assertEquals(false, isPairSymbol("BTCUSDT"))
        assertEquals(false, isPairSymbol("SOLUSDT"))
    }

    @Test
    fun `a currency with no artwork still lets the pair render`() {
        // Only one half has to be recognised. RUB has no flag here, and USDRUB must still draw as a
        // pair with a lettered stub rather than falling through to the coin table and finding
        // nothing at all.
        assertEquals("USD" to "RUB", pairOf("USDRUB"))
    }
}
