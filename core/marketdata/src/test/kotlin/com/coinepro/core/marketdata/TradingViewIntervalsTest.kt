package com.coinepro.core.marketdata

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** TradingView's 6M and 12M presets and its typed custom intervals (5.16.1). */
class TradingViewIntervalsTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `a half-year opens in January or July and a year in January`() {
        val autumn = Instant.parse("2026-09-24T10:00:00Z").epochSecond
        assertEquals(Instant.parse("2026-07-01T00:00:00Z").epochSecond, Timeframe.MN6.bucketStart(autumn, utc))
        assertEquals(Instant.parse("2026-01-01T00:00:00Z").epochSecond, Timeframe.MN12.bucketStart(autumn, utc))
        val spring = Instant.parse("2026-03-10T00:00:00Z").epochSecond
        assertEquals(Instant.parse("2026-01-01T00:00:00Z").epochSecond, Timeframe.MN6.bucketStart(spring, utc))
    }

    @Test
    fun `both are folded from daily bars and read back from their wire`() {
        assertEquals(Timeframe.D1, sourceTimeframeFor(ChartInterval.Preset(Timeframe.MN6)))
        assertEquals(Timeframe.D1, sourceTimeframeFor(ChartInterval.Preset(Timeframe.MN12)))
        assertEquals(Timeframe.MN6, Timeframe.of("MN6"))
        assertEquals(Timeframe.MN12, Timeframe.of("mn12"))
    }

    @Test
    fun `a typed interval takes its unit, the way TradingView's does`() {
        assertEquals(CustomInterval(205), customTypedOf("205"))
        assertEquals(CustomInterval(15), customTypedOf("15m"))
        assertEquals(CustomInterval(240), customTypedOf("4h"))
        assertEquals(CustomInterval(150), customTypedOf("2.5h"))
        assertEquals(CustomInterval(90), customTypedOf("۹۰ دقیقه"))
        assertEquals(CustomInterval(300), customTypedOf("۵ ساعت"))
        assertNull(customTypedOf("25h"))
        assertNull(customTypedOf("1.3m"))
        assertNull(customTypedOf("h"))
        // The stored wire stays digits only.
        assertNull(customOf("15m"))
    }
}
