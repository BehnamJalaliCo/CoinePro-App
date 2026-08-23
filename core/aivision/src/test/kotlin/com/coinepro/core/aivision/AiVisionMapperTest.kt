package com.coinepro.core.aivision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVisionMapperTest {
    @Test
    fun `actionable result requires server validation`() {
        val result = actionable(validated = false).toDomain()
        assertNull(result)
    }

    @Test
    fun `unknown result cannot carry executable signal id`() {
        val result = AiVisionResultDto(
            validated = true,
            assessment = "unknown",
            confidence = 30,
            signalId = 42,
        ).toDomain()
        assertNull(result)
    }

    @Test
    fun `low confidence result remains non executable`() {
        val result = AiVisionResultDto(
            validated = true,
            assessment = "low_confidence",
            symbol = "XAUUSD",
            timeframe = "H1",
            confidence = 35,
            trendBias = "Mixed",
            reasoning = "Chart quality is insufficient for a reliable setup.",
        ).toDomain()

        assertNotNull(result)
        assertEquals(AiVisionAssessment.LOW_CONFIDENCE, result?.assessment)
        assertFalse(result?.canOpenValidatedSignal ?: true)
        assertNull(result?.signalId)
    }

    @Test
    fun `buy setup rejects stop loss inside entry zone`() {
        val result = actionable(stopLoss = 2505.0).toDomain()
        assertNull(result)
    }

    @Test
    fun `validated actionable setup maps to persisted signal`() {
        val result = actionable().toDomain()

        assertNotNull(result)
        assertEquals(AiVisionAssessment.ACTIONABLE, result?.assessment)
        assertEquals(42L, result?.signalId)
        assertTrue(result?.canOpenValidatedSignal == true)
        assertEquals(3, result?.targets?.size)
    }

    private fun actionable(
        validated: Boolean = true,
        stopLoss: Double = 2480.0,
    ) = AiVisionResultDto(
        validated = validated,
        assessment = "actionable",
        symbol = "XAUUSD",
        timeframe = "H1",
        confidence = 82,
        trendBias = "Bullish",
        marketStructure = "Higher highs and higher lows",
        setup = "Pullback continuation",
        direction = "BUY",
        entryZone = AiVisionEntryZoneDto(low = 2500.0, high = 2510.0),
        stopLoss = stopLoss,
        targets = listOf(
            AiVisionTargetDto(1, 2530.0),
            AiVisionTargetDto(2, 2550.0),
            AiVisionTargetDto(3, 2580.0),
        ),
        risk = "medium",
        reasoning = "Structured continuation setup after a pullback.",
        signalId = 42,
        validatedAt = "2026-08-23T00:00:00Z",
    )
}
