package com.coinepro.core.symbols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The classifier, pinned on the cases that were bugs before it was one.
 *
 * Every test here names a symbol that a simpler rule gets wrong. The easy cases — `EURUSD` is
 * forex, `BTCUSDT` is crypto — are covered incidentally; they were never the risk.
 */
class SymbolClassifierTest {

    @Test
    fun `a metal is a metal, not a six-letter currency pair`() {
        val gold = SymbolClassifier.classify("XAUUSD")
        assertEquals(SymbolCategory.METAL, gold.category)
        assertEquals("XAU", gold.base)
        assertEquals("USD", gold.quote)
        assertEquals("طلا / دلار آمریکا", gold.description)
    }

    @Test
    fun `an index beats the currency code it starts with`() {
        // US500 begins with USD's first two letters and is five characters of mostly digits; a
        // forex-first order classifies it as nothing and it falls through to OTHER.
        assertEquals(SymbolCategory.INDEX, SymbolClassifier.classify("US500").category)
        assertEquals(SymbolCategory.ENERGY, SymbolClassifier.classify("USOIL").category)
    }

    @Test
    fun `NATGAS is energy`() {
        // The original classified this as OTHER, because its energy test was a prefix regex that
        // NATGAS did not start with. It then sorted to the bottom of every list.
        val gas = SymbolClassifier.classify("NATGAS")
        assertEquals(SymbolCategory.ENERGY, gas.category)
        assertEquals("گاز طبیعی", gas.description)
    }

    @Test
    fun `a broker's spelling of an index resolves to the one this app knows`() {
        for (spelling in listOf("NAS100", "NDX", "USTEC", "US100")) {
            val meta = SymbolClassifier.classify(spelling)
            assertEquals(spelling, SymbolCategory.INDEX, meta.category)
            assertEquals(spelling, "US100", meta.canonical)
            assertEquals(spelling, "نزدک ۱۰۰", meta.description)
        }
    }

    @Test
    fun `the feed's spelling stays the identity even when an alias renames it`() {
        val gold = SymbolClassifier.classify("GOLD")
        // What goes back on the wire must be what the broker called it.
        assertEquals("GOLD", gold.symbol)
        assertEquals("XAUUSD", gold.canonical)
        assertEquals(SymbolCategory.METAL, gold.category)
    }

    @Test
    fun `a wrapped coin keeps its own ticker and reads as the asset it wraps`() {
        val wrapped = SymbolClassifier.classify("WBTCUSDT")
        assertEquals(SymbolCategory.CRYPTO, wrapped.category)
        // Not "BTC": a list showing both must not print the same row twice.
        assertEquals("WBTC", wrapped.base)
        assertEquals("BTCUSDT", wrapped.canonical)
        assertTrue(wrapped.description.startsWith("بیت‌کوین"))
    }

    @Test
    fun `stripping a quote never eats the base`() {
        // Two characters must survive the strip. Without that rule these become W and XB.
        assertEquals("WBTC", SymbolClassifier.classify("WBTCUSDT").base)
        assertEquals("XBT", SymbolClassifier.classify("XBTUSD").base)
    }

    @Test
    fun `an unknown coin is a coin, with its ticker as its name`() {
        val meta = SymbolClassifier.classify("QUACKUSDT")
        assertEquals(SymbolCategory.CRYPTO, meta.category)
        assertEquals("QUACK", meta.base)
        assertEquals("QUACK", meta.description)
    }

    @Test
    fun `an unrecognised instrument is shown, not hidden`() {
        val meta = SymbolClassifier.classify("SOMEFUTURE")
        assertEquals(SymbolCategory.OTHER, meta.category)
        assertEquals("SOMEFUTURE", meta.description)
        assertNull(meta.base)
        assertFalse(SymbolClassifier.isNoise("SOMEFUTURE"))
    }

    @Test
    fun `feed noise is dropped and real instruments are not`() {
        for (junk in listOf("1", "4", "2Z", "0G", "1COIN", "67COIN")) {
            assertTrue(junk, SymbolClassifier.isNoise(junk))
        }
        for (real in listOf("BTCUSDT", "EURUSD", "XAUUSD", "US500", "1INCHUSDT", "NATGAS")) {
            assertFalse(real, SymbolClassifier.isNoise(real))
        }
    }

    @Test
    fun `both spellings of the renminbi name the same currency`() {
        assertEquals("یوآن چین / دلار آمریکا", SymbolClassifier.classify("CNHUSD").description)
        assertEquals("یوآن چین / دلار آمریکا", SymbolClassifier.classify("CNYUSD").description)
    }

    @Test
    fun `punctuation and case in the feed's spelling do not change the answer`() {
        val slashed = SymbolClassifier.classify("btc/usdt")
        assertEquals("BTCUSDT", slashed.symbol)
        assertEquals(SymbolCategory.CRYPTO, slashed.category)
    }

    @Test
    fun `a six-letter coin ticker is not mistaken for a currency pair`() {
        // Both halves have to be real currency codes, so this stays a coin.
        assertEquals(SymbolCategory.CRYPTO, SymbolClassifier.classify("PEPEUSDT").category)
        assertEquals(SymbolCategory.OTHER, SymbolClassifier.classify("ABCDEF").category)
    }

    @Test
    fun `display forms say what a terminal says`() {
        assertEquals("EUR/USD", SymbolClassifier.classify("EURUSD").pretty)
        assertEquals("BTC", SymbolClassifier.classify("BTCUSDT").short)
        assertEquals("XAU/USD", SymbolClassifier.classify("XAUUSD").short)
        assertEquals("US500", SymbolClassifier.classify("US500").pretty)
    }
}
