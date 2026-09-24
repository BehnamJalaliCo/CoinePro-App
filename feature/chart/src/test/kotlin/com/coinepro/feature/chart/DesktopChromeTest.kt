package com.coinepro.feature.chart

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The desktop chrome's own formatting (5.16.0): the tab title, the clock and the range codes. */
class DesktopChromeTest {

    @Test
    fun `the tab title reads the way TradingView's does`() {
        assertEquals("XAUUSD 2,551.7 ▼ −1.74%", tabTitle("XAUUSD", 2551.7, -1.7412))
        assertEquals("BTCUSDT 63,418.5 ▲ +0.20%", tabTitle("BTCUSDT", 63418.5, 0.2))
        assertEquals("EURUSD 1.08450", tabTitle("EURUSD", 1.0845, null))
    }

    @Test
    fun `the clock names the zone by its offset, half hours included`() {
        // 2026-09-24T12:00:00Z
        val noon = 1_790_251_200_000L
        assertEquals("15:30:00 (UTC+3:30)", desktopClock(noon, ZoneId.of("Asia/Tehran")))
        assertEquals("12:00:00 (UTC+0)", desktopClock(noon, ZoneId.of("UTC")))
        assertEquals("08:00:00 (UTC-4)", desktopClock(noon, ZoneId.of("America/New_York")))
    }

    @Test
    fun `the ranges are TradingView's spellings and All keeps its word`() {
        assertEquals(listOf("1D", "5D", "1M", "3M", "6M", "YTD", "1Y", "5Y"), ChartRange.OFFERED.reversed().mapNotNull(::rangeCode))
        assertNull(rangeCode(ChartRange.ALL))
    }
}
