package com.coinepro.feature.screener

import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolMeta
import com.coinepro.feature.screener.model.ScreenerField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The screener behaviour the TradingView parity pass changed (LISTS-02, 11, 24). */
class ScreenerParityTest {

    @Test
    fun `a phone table shows the reader's columns and nothing more`() {
        assertEquals(ScreenerField.DEFAULT_COLUMNS, displayColumns(ScreenerField.DEFAULT_COLUMNS, wide = false))
    }

    @Test
    fun `a wide table adds every quote column after the reader's own, and no derived one`() {
        val chosen = listOf(ScreenerField.RSI, ScreenerField.LAST_PRICE)
        val shown = displayColumns(chosen, wide = true)
        assertEquals(chosen, shown.take(2))
        assertTrue(ScreenerField.VOLUME in shown)
        assertTrue(ScreenerField.RANGE_PERCENT in shown)
        assertEquals("no column twice", shown.size, shown.toSet().size)
        assertFalse("an indicator costs a series per market", ScreenerField.ADX in shown)
        assertFalse("categorical fields stay in the sheet", ScreenerField.MARKET in shown)
    }

    @Test
    fun `every offered indicator has an English name and none of them is Persian`() {
        val arabic = Regex("[\\u0600-\\u06FF]")
        ScreenerIndicatorCatalog.offered(hasVolume = true).forEach { option ->
            assertFalse(option.id, arabic.containsMatchIn(option.labelEn))
            assertFalse(option.id, arabic.containsMatchIn(ScreenerIndicatorCatalog.labelOf(option.id, english = true)))
        }
        ScreenerIndicatorCatalog.Absence.entries.forEach { absence ->
            assertFalse(absence.name, arabic.containsMatchIn(absence.reasonIn(english = true)))
        }
    }

    @Test
    fun `the indicator search matches the English name too`() {
        val found = ScreenerIndicatorCatalog.matching("moving average", hasVolume = true).map { it.id }
        assertTrue("sma" in found)
        assertTrue("ema" in found)
        assertEquals("Simple Moving Average", ScreenerIndicatorCatalog.labelOf("sma", english = true))
        assertEquals("میانگین متحرک ساده", ScreenerIndicatorCatalog.labelOf("sma", english = false))
    }

    @Test
    fun `a row name that repeats the ticker is not drawn`() {
        val meta = SymbolMeta(
            symbol = "MEMESTOCKUSDT",
            canonical = "MEMESTOCKUSDT",
            category = SymbolCategory.CRYPTO,
            base = "MEMESTOCK",
            quote = "USDT",
            description = "MEMESTOCK",
            popular = false,
        )
        assertNull(screenerRowName(meta, "MEMESTOCK"))
        assertEquals("Bitcoin", screenerRowName(meta, "Bitcoin"))
    }
}
