package com.coinepro.core.backtest

import com.coinepro.core.common.BidiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Rendering a metric that does not exist, and rendering one that does in the reader's own digits.
 *
 * The device locale in this app is Persian. Every assertion here would pass by accident on a
 * machine defaulting to English, so the locale is switched underneath them: that is the exact
 * condition the app ships in and the one under which an unqualified format call silently produces
 * «۱۲٫۵۰» in a column somebody is comparing against Binance.
 */
class BacktestFormatTest {

    private val original: Locale = Locale.getDefault()

    private fun <T> inPersianLocale(block: () -> T): T {
        Locale.setDefault(Locale.forLanguageTag("fa-IR"))
        return try {
            block()
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `an infinite profit factor is a dash, never a number`() {
        assertEquals(BacktestFormat.ABSENT, BacktestFormat.ratio(Double.POSITIVE_INFINITY))
        assertEquals(BacktestFormat.ABSENT, BacktestFormat.ratio(Double.NEGATIVE_INFINITY))
        assertEquals(BacktestFormat.ABSENT, BacktestFormat.ratio(Double.NaN))
    }

    @Test
    fun `every metric that can divide by zero renders as a dash rather than as a figure`() {
        assertEquals(BacktestFormat.ABSENT, BacktestFormat.money(Double.NaN))
        assertEquals(BacktestFormat.ABSENT, BacktestFormat.percent(Double.POSITIVE_INFINITY))
        assertEquals(BacktestFormat.ABSENT, BacktestFormat.signedPercent(Double.NaN))
        assertEquals(BacktestFormat.ABSENT, BacktestFormat.bars(Double.NaN))
    }

    @Test
    fun `a real ratio is rendered in Latin digits with a dot, whatever the device locale is`() {
        val text = inPersianLocale { BidiText.strip(BacktestFormat.ratio(1.874)) }
        assertEquals("1.87", text)
        assertFalse("a market figure must never carry Persian digits", text.any { it in '۰'..'۹' })
    }

    @Test
    fun `a percentage keeps its digits Latin and its decimal a dot`() {
        val plain = inPersianLocale { BidiText.strip(BacktestFormat.percent(12.5)) }
        assertEquals("12.50%", plain)

        val signed = inPersianLocale { BidiText.strip(BacktestFormat.signedPercent(12.5)) }
        assertEquals("+12.50%", signed)
    }

    @Test
    fun `a money figure carries a sign only where the sign was asked for`() {
        assertEquals("1,005.60", BidiText.strip(BacktestFormat.money(1005.6)))
        assertEquals("+1,005.60", BidiText.strip(BacktestFormat.money(1005.6, signed = true)))
        assertEquals("-42.00", BidiText.strip(BacktestFormat.money(-42.0)))
    }

    @Test
    fun `a count is a whole Latin figure`() {
        assertEquals("1,240", BidiText.strip(BacktestFormat.count(1240)))
        assertEquals("0", BidiText.strip(BacktestFormat.count(0)))
    }

    @Test
    fun `a raw percentage carries no invisible characters, because a spreadsheet cell cannot`() {
        val raw = BacktestFormat.rawPercent(12.5)
        assertEquals("12.50%", raw)
        assertTrue("a CSV cell must hold digits and nothing else", raw == BidiText.strip(raw))
    }

    @Test
    fun `an absent timestamp produces no date rather than a date in the wrong century`() {
        assertEquals("", BacktestFormat.dateRange(0L, 0L, java.time.ZoneId.of("Asia/Tehran")))
    }
}
