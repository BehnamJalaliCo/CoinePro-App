package com.coinepro.core.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionMapperTest {
    @Test
    fun `queued execution is never treated as open`() {
        val execution = SignalExecution(
            id = "exec-1",
            signalId = 42,
            venue = ExecutionVenue.MT5,
            product = "mt5",
            status = ExecutionStatus.QUEUED,
            side = "buy",
            quantity = "0.1",
            providerOrderId = null,
            errorCode = null,
            errorMessage = null,
            signal = null,
            createdAt = null,
            updatedAt = null,
            closedAt = null,
        )
        assertFalse(execution.isBrokerConfirmedOpen)
        assertTrue(execution.canRequestClose)
    }

    @Test
    fun `unknown provider status remains unknown instead of open`() {
        val dto = ExecutionDto(
            id = "exec-2",
            signalId = 8,
            venue = "lbank",
            status = "mystery",
            quantity = "1",
        )
        val execution = dto.toDomain()
        assertEquals(ExecutionStatus.UNKNOWN, execution?.status)
        assertFalse(execution?.isBrokerConfirmedOpen ?: true)
    }

    @Test
    fun `invalid venue payload is rejected`() {
        assertNull(ExecutionDto(id = "x", signalId = 1, venue = "binance").toDomain())
    }
}
