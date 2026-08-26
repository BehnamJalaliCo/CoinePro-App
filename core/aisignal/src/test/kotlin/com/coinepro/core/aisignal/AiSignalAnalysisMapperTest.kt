package com.coinepro.core.aisignal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSignalAnalysisMapperTest {

    private fun validResult(
        recentCandles: List<AiCandleDto> = emptyList(),
        block: AiGeneratedSignalDto.() -> AiGeneratedSignalDto = { this },
    ): AiGeneratedSignal? = AiGeneratedSignalDto(
        validated = true,
        signalId = 55L,
        symbol = "XAUUSD",
        direction = "BUY",
        timeframe = "H1",
        entry = 2400.0,
        stopLoss = 2380.0,
        tp1 = 2440.0,
        confidence = 70.0,
        recentCandles = recentCandles,
    ).block().toDomain(AiSignalRequest("XAUUSD", AiSignalTimeframe.H1, AiSignalRisk.MEDIUM))

    @Test
    fun `indicator readings survive the mapper`() {
        val result = validResult { copy(ema20 = 2401.5, rsi14 = 63.2, atr14 = 8.4, priceNow = 2405.0) }
        val snapshot = requireNotNull(result?.snapshot)

        assertEquals(2401.5, snapshot.ema20!!, 0.0001)
        assertEquals(63.2, snapshot.rsi14!!, 0.0001)
        assertEquals(8.4, snapshot.atr14!!, 0.0001)
    }

    @Test
    fun `an indicator the server could not compute stays missing rather than zero`() {
        val snapshot = requireNotNull(validResult { copy(ema20 = 2401.5) }?.snapshot)

        assertEquals(2401.5, snapshot.ema20!!, 0.0001)
        assertNull(snapshot.rsi14)
        assertNull(snapshot.macd)
    }

    @Test
    fun `a result with no indicators at all reports no snapshot`() {
        assertNull(validResult()?.snapshot)
    }

    @Test
    fun `non-finite indicator readings are dropped`() {
        val snapshot = requireNotNull(
            validResult { copy(ema20 = Double.NaN, rsi14 = Double.POSITIVE_INFINITY, macd = 0.4) }?.snapshot,
        )

        assertNull(snapshot.ema20)
        assertNull(snapshot.rsi14)
        assertEquals(0.4, snapshot.macd!!, 0.0001)
    }

    @Test
    fun `swing position places price within the twenty-bar range`() {
        val snapshot = requireNotNull(
            validResult { copy(swingLow20 = 2380.0, swingHigh20 = 2420.0, priceNow = 2400.0) }?.snapshot,
        )

        assertEquals(0.5, snapshot.swingPosition!!, 0.0001)
    }

    @Test
    fun `a collapsed swing range yields no position rather than dividing by zero`() {
        val snapshot = requireNotNull(
            validResult { copy(swingLow20 = 2400.0, swingHigh20 = 2400.0, priceNow = 2400.0) }?.snapshot,
        )

        assertNull(snapshot.swingPosition)
    }

    @Test
    fun `well formed candles are kept and malformed ones dropped`() {
        val result = validResult(
            recentCandles = listOf(
                AiCandleDto(o = 100.0, h = 110.0, l = 95.0, c = 105.0),
                // high below low — impossible, and would render as an inverted wick
                AiCandleDto(o = 100.0, h = 90.0, l = 95.0, c = 92.0),
                // missing close
                AiCandleDto(o = 100.0, h = 110.0, l = 95.0, c = null),
                AiCandleDto(o = 105.0, h = 112.0, l = 104.0, c = 111.0),
            ),
        )

        assertEquals(2, result?.recentCandles?.size)
        assertEquals(AiCandle(100.0, 110.0, 95.0, 105.0), result?.recentCandles?.first())
    }

    @Test
    fun `the two servers time their evidence differently and both land in seconds`() {
        // TradeYar sends `ts_ms` straight through, so milliseconds; CoinePro-FX's evidence block
        // carries no time at all. Reading the first as seconds would place every bar in the year
        // 55000, where the chart draws the whole series one pixel wide.
        val result = validResult(
            recentCandles = listOf(
                AiCandleDto(t = 1_735_689_600_000L, o = 100.0, h = 110.0, l = 95.0, c = 105.0),
                AiCandleDto(t = 1_735_693_200L, o = 105.0, h = 112.0, l = 104.0, c = 111.0),
                AiCandleDto(o = 111.0, h = 115.0, l = 110.0, c = 113.0),
                // Zero is not a timestamp, it is a field the server left at its default.
                AiCandleDto(t = 0L, o = 113.0, h = 116.0, l = 112.0, c = 114.0),
            ),
        )

        assertEquals(
            listOf(1_735_689_600L, 1_735_693_200L, null, null),
            result?.recentCandles?.map { it.time },
        )
    }

    @Test
    fun `blank server warnings are discarded and real ones kept verbatim`() {
        val result = validResult { copy(warnings = listOf("  ", "Spread is wide", "")) }

        assertEquals(listOf("Spread is wide"), result?.warnings)
    }

    @Test
    fun `a non-positive lot is not presented as a position size`() {
        assertNull(validResult { copy(lot = 0.0) }?.lot)
        assertEquals(0.2, validResult { copy(lot = 0.2) }?.lot!!, 0.0001)
    }

    @Test
    fun `an unvalidated result is still refused regardless of the new fields`() {
        assertNull(validResult { copy(validated = false, ema20 = 2401.0) })
    }

    @Test
    fun `every optional request input reaches the wire only when set`() {
        val bare = AiSignalRequest("XAUUSD", AiSignalTimeframe.H1, AiSignalRisk.MEDIUM)
        assertNull(bare.tradeStyle)
        assertNull(bare.minRiskReward)

        val shaped = bare.copy(
            tradeStyle = AiTradeStyle.SWING,
            riskAppetite = AiRiskAppetite.CONSERVATIVE,
            directionBias = AiDirectionBias.LONG,
            minRiskReward = 2.5,
        )
        assertEquals("swing", shaped.tradeStyle?.wireValue)
        assertEquals("conservative", shaped.riskAppetite?.wireValue)
        assertEquals("long", shaped.directionBias?.wireValue)
        assertTrue(shaped.minRiskReward!! > 0.0)
    }
}
