package com.coinepro.core.symbols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SymbolPrecisionTest {
    @Test
    fun `a pair is quoted to the pipette`() {
        assertEquals(5, SymbolPrecision.of("EURUSD"))
        assertEquals(5, SymbolPrecision.of("AUDUSD"))
        assertEquals(3, SymbolPrecision.of("USDJPY"))
    }

    @Test
    fun `metals by their own convention`() {
        assertNull(SymbolPrecision.of("XAUUSD"))
        assertEquals(3, SymbolPrecision.of("XAGUSD"))
    }

    @Test
    fun `a coin keeps the magnitude rule`() {
        assertNull(SymbolPrecision.of("BTCUSDT"))
        assertNull(SymbolPrecision.of("XRPUSDT"))
    }
}
