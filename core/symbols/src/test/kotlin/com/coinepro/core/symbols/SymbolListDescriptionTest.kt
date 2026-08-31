package com.coinepro.core.symbols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a list row says under the ticker.
 *
 * The line has one job — telling two instruments apart — and it has failed at it in both directions
 * already: first by ellipsising every forex row, then by printing only the base, which made USDJPY,
 * USDCHF and USDCAD read identically. Both are pinned here.
 */
class SymbolListDescriptionTest {

    private fun forex(symbol: String) =
        SymbolClassifier.classify(symbol).listDescription

    @Test
    fun `three pairs sharing a base do not share a subtitle`() {
        val subtitles = listOf("USDJPY", "USDCHF", "USDCAD").map(::forex)
        assertEquals(subtitles.toString(), subtitles.size, subtitles.toSet().size)
    }

    @Test
    fun `a pair names both of its legs`() {
        assertEquals("دلار/ین", forex("USDJPY"))
        assertEquals("یورو/دلار", forex("EURUSD"))
        assertEquals("پوند/ین", forex("GBPJPY"))
    }

    @Test
    fun `a metal keeps its own name and its quote`() {
        assertEquals("طلا/دلار", forex("XAUUSD"))
        assertEquals("نقره/دلار", forex("XAGUSD"))
    }

    @Test
    fun `it stays short enough for a list row`() {
        // The failure the base-only rule was introduced to fix. Twelve characters is about what a
        // subtitle can hold beside a price on a 360dp phone.
        listOf("USDJPY", "EURUSD", "GBPUSD", "XAUUSD", "USDCHF", "AUDUSD").forEach { symbol ->
            val subtitle = forex(symbol)
            assertTrue("$symbol -> $subtitle", subtitle.length <= 14)
        }
    }

    @Test
    fun `the long names stay searchable`() {
        // A reader typing «فرانک سوئیس» must still find USDCHF. The long name was dropped from what
        // is *drawn*, not from what is matched.
        val chf = SymbolClassifier.classify("USDCHF")
        assertTrue(chf.description, "فرانک سوئیس" in chf.description)
    }
}
