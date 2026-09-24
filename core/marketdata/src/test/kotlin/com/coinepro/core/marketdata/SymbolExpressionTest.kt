package com.coinepro.core.marketdata

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spread and ratio symbols (5.14.0): the terminal's `symbolExpr.js`, rule for rule. */
class SymbolExpressionTest {

    @Test
    fun `the grammar has the terminal's precedence, parentheses, constants and unary minus`() {
        val e = SymbolExpression.parse("(btcusdt + ETHUSDT) / 2")!!
        assertEquals(listOf("BTCUSDT", "ETHUSDT"), e.symbols)
        assertEquals(15.0, e.evaluate(mapOf("BTCUSDT" to 20.0, "ETHUSDT" to 10.0))!!, 1e-9)
        assertEquals(7.0, SymbolExpression.parse("1 + 2 * 3")!!.evaluate(emptyMap())!!, 1e-9)
        assertEquals(-4.0, SymbolExpression.parse("-XAUUSD*2")!!.evaluate(mapOf("XAUUSD" to 2.0))!!, 1e-9)
        assertNull(SymbolExpression.parse("EURUSD/0")!!.evaluate(mapOf("EURUSD" to 1.1)))
        assertNull(SymbolExpression.parse("EURUSD/GBPUSD")!!.evaluate(mapOf("EURUSD" to 1.1)))
    }

    @Test
    fun `a bare symbol is not an expression, and junk does not parse`() {
        assertFalse(SymbolExpression.isExpression("EURUSD"))
        assertTrue(SymbolExpression.isExpression("EURUSD/GBPUSD"))
        assertNull(SymbolExpression.parse("EURUSD//GBPUSD"))
        assertNull(SymbolExpression.parse("(EURUSD"))
        assertNull(SymbolExpression.parse("EUR USD"))
        assertNull(SymbolExpression.parse("BTC$"))
        assertNull(SymbolExpression.parse(""))
    }

    @Test
    fun `bars meet on shared times and a ratio's high is never under its low`() {
        val a = listOf(OhlcBar(1, 2.0, 2.2, 1.8, 2.1, 5.0), OhlcBar(2, 2.1, 2.3, 2.0, 2.2, 5.0), OhlcBar(3, 2.2, 2.4, 2.1, 2.3, 5.0))
        val b = listOf(OhlcBar(2, 1.0, 1.1, 0.9, 1.05, 5.0), OhlcBar(3, 1.05, 1.2, 1.0, 1.1, 5.0), OhlcBar(4, 1.1, 1.2, 1.0, 1.1, 5.0))
        val bars = SymbolExpression.parse("A/B")!!.combine(mapOf("A" to a, "B" to b))
        assertEquals(listOf(2L, 3L), bars.map { it.t })
        for (bar in bars) {
            assertTrue(bar.h >= maxOf(bar.o, bar.c) && bar.l <= minOf(bar.o, bar.c))
            assertEquals(0.0, bar.v, 0.0)
        }
        assertEquals(2.1, bars[0].o, 1e-9)
        assertEquals(2.2 / 1.05, bars[0].c, 1e-9)
    }

    @Test
    fun `the gateway loads each symbol for an expression and passes a symbol straight through`() = runTest {
        val asked = mutableListOf<String>()
        val inner = object : CandleGateway {
            override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?): CandlePage {
                asked += symbol
                val price = if (symbol == "EURUSD") 1.10 else 1.25
                return CandlePage(symbol, timeframe, (0 until 5).map { OhlcBar(it * 3_600L, price, price, price, price, 1.0) })
            }
        }
        val gateway = SymbolExpressionGateway(inner)
        val page = gateway.load("EURUSD/GBPUSD", Timeframe.H1, 300, null)
        assertEquals(listOf("EURUSD", "GBPUSD"), asked)
        assertEquals(5, page.candles.size)
        assertEquals(1.10 / 1.25, page.candles.last().c, 1e-9)
        assertFalse(page.hasMore)
        asked.clear()
        gateway.load("XAUUSD", Timeframe.H1, 300, null)
        assertEquals(listOf("XAUUSD"), asked)
        // No history paging for a spread: the pages of its legs end in different places.
        assertTrue(gateway.load("EURUSD/GBPUSD", Timeframe.H1, 300, before = 7_200L).candles.isEmpty())
    }
}
