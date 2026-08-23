package com.coinepro.core.aisignal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiVisionValidationTest {
    private val request = AiVisionRequest("XAUUSD", AiSignalTimeframe.H1)

    @Test fun `validated result maps to persisted signal`() {
        val result = valid().toDomain(request)
        assertEquals(42L, result?.signalId)
        assertEquals("BULLISH", result?.trend)
    }

    @Test fun `unvalidated result is blocked`() {
        assertNull(valid().copy(validated = false).toDomain(request))
    }

    @Test fun `mismatched symbol is blocked`() {
        assertNull(valid().copy(symbol = "XAGUSD").toDomain(request))
    }

    @Test fun `mismatched timeframe is blocked`() {
        assertNull(valid().copy(timeframe = "H4").toDomain(request))
    }

    @Test fun `invalid prices and empty explanation are blocked`() {
        assertNull(valid().copy(entry = Double.NaN).toDomain(request))
        assertNull(valid().copy(explanation = " ").toDomain(request))
    }

    private fun valid() = AiVisionAnalysisDto(
        validated = true,
        signalId = 42,
        symbol = "XAUUSD",
        timeframe = "H1",
        trend = "BULLISH",
        entry = 2400.0,
        stopLoss = 2380.0,
        targets = listOf(AiSignalTargetDto(1, 2430.0), AiSignalTargetDto(2, 2460.0)),
        confidence = 82,
        explanation = "Momentum and structure align.",
    )
}
