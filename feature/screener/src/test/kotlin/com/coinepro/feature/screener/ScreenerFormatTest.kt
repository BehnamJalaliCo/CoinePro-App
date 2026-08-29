package com.coinepro.feature.screener

import com.coinepro.core.common.BidiText
import com.coinepro.feature.screener.model.ScreenerUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a cell is written, with the bidirectional isolates stripped so the assertions read.
 *
 * The isolates are not incidental — without them a Persian paragraph reorders `1.24B` to `B1.24`
 * and `+3.20%` to `%3.20+` — but they are invisible in a diff and would make every expected value
 * in this file unreadable. [plain] removes them and one test checks they were there.
 */
class ScreenerFormatTest {

    /** The isolate pair `BidiText` wraps a Latin run in, removed so an expected value reads. */
    private fun plain(value: String) = BidiText.strip(value)

    @Test
    fun `a figure is written in Latin digits, whatever the device locale says`() {
        // The rule the whole app turns on: a market number is compared against another terminal,
        // and Persian digits make that a conversion in the reader's head.
        val text = plain(ScreenerFormat.cell(91_248.3, ScreenerUnit.PRICE))
        assertEquals("91,248.30", text)
        assertTrue("no Persian digit may reach a market figure", text.none { it in '۰'..'۹' })
    }

    @Test
    fun `a small price keeps the decimals its own magnitude needs`() {
        assertEquals("0.00002418", plain(ScreenerFormat.cell(0.00002418, ScreenerUnit.PRICE)))
    }

    @Test
    fun `a percentage carries its sign and its per-cent mark`() {
        assertEquals("+3.20%", plain(ScreenerFormat.cell(3.2, ScreenerUnit.PERCENT)))
        assertEquals("-1.05%", plain(ScreenerFormat.cell(-1.05, ScreenerUnit.PERCENT)))
    }

    @Test
    fun `turnover is abbreviated so a column of it stays scannable`() {
        assertEquals("1.24B", plain(ScreenerFormat.volume(1_240_000_000.0)))
        assertEquals("12.4M", plain(ScreenerFormat.volume(12_400_000.0)))
        assertEquals("124K", plain(ScreenerFormat.volume(124_000.0)))
        assertEquals("1.24T", plain(ScreenerFormat.volume(1_240_000_000_000.0)))
    }

    @Test
    fun `a figure under a thousand is not abbreviated at all`() {
        assertEquals("48.20", plain(ScreenerFormat.volume(48.2)))
        assertEquals("482", plain(ScreenerFormat.volume(482.0)))
    }

    @Test
    fun `an abbreviated figure is isolated, so a Persian paragraph cannot reorder it`() {
        val raw = ScreenerFormat.volume(1_240_000_000.0)
        assertTrue("the run is wrapped in an isolate pair", raw.startsWith("⁦") && raw.endsWith("⁩"))
    }

    @Test
    fun `a value the screener has not read is a dash rather than a zero`() {
        assertEquals(ScreenerFormat.ABSENT, ScreenerFormat.cell(null, ScreenerUnit.VOLUME))
        assertEquals(ScreenerFormat.ABSENT, ScreenerFormat.cell(Double.NaN, ScreenerUnit.PRICE))
        assertEquals(ScreenerFormat.ABSENT, ScreenerFormat.cell(Double.POSITIVE_INFINITY, ScreenerUnit.PERCENT))
    }

    @Test
    fun `a bare reading is written to two decimals with no unit at all`() {
        assertEquals("64.30", plain(ScreenerFormat.cell(64.3, ScreenerUnit.PLAIN)))
    }

    @Test
    fun `a threshold echoed into a sentence loses its grouping and its trailing zero`() {
        assertEquals("30", plain(ScreenerFormat.threshold(30.0)))
        assertEquals("2.5", plain(ScreenerFormat.threshold(2.5)))
        assertEquals("12000", plain(ScreenerFormat.threshold(12_000.0)))
    }
}
