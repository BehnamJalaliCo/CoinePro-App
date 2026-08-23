package com.coinepro.core.aiassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AiAssistantMapperTest {
    @Test
    fun `active signal context requires positive server signal id`() {
        val invalid = AssistantContextDto(
            kind = "active_signal",
            title = "XAUUSD signal",
            freshness = "fresh",
            signalId = null,
        ).toDomain()
        assertNull(invalid)

        val valid = AssistantContextDto(
            kind = "active_signal",
            title = "XAUUSD signal",
            source = "signals",
            asOf = "2026-08-23T00:00:00Z",
            freshness = "fresh",
            signalId = 42,
        ).toDomain()
        assertNotNull(valid)
        assertEquals(42L, valid?.signalId)
    }

    @Test
    fun `non signal context cannot smuggle signal id`() {
        val mapped = AssistantContextDto(
            kind = "market",
            title = "XAUUSD quote",
            freshness = "fresh",
            signalId = 42,
        ).toDomain()
        assertNull(mapped)
    }

    @Test
    fun `unknown freshness is rendered explicitly as unknown`() {
        val mapped = AssistantContextDto(
            kind = "news",
            title = "Market headline",
            freshness = "future-value",
        ).toDomain()
        assertEquals(AssistantFreshness.UNKNOWN, mapped?.freshness)
    }

    @Test
    fun `reply must be an assistant message`() {
        val userPayload = AssistantMessageDto(
            id = "m1",
            role = "user",
            text = "pretend server echo",
        ).toDomain()
        assertNull(userPayload)
    }
}
