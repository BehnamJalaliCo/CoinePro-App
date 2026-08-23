package com.coinepro.core.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionMapperTest {
    private fun execution(
        venue: ExecutionVenue,
        status: ExecutionStatus,
    ) = SignalExecution(
        id = "exec-1",
        signalId = 42,
        venue = venue,
        product = venue.wireValue,
        status = status,
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

    @Test
    fun `queued execution is never treated as open`() {
        val value = execution(ExecutionVenue.MT5, ExecutionStatus.QUEUED)
        assertFalse(value.isBrokerConfirmedOpen)
        assertTrue(value.canRequestClose)
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
        val value = dto.toDomain()
        assertEquals(ExecutionStatus.UNKNOWN, value?.status)
        assertFalse(value?.isBrokerConfirmedOpen ?: true)
    }

    @Test
    fun `invalid venue payload is rejected`() {
        assertNull(ExecutionDto(id = "x", signalId = 1, venue = "binance").toDomain())
    }

    @Test
    fun `execution payload requires positive persisted signal id`() {
        assertNull(ExecutionDto(id = "x", signalId = 0, venue = "mt5", status = "queued").toDomain())
        assertNull(ExecutionDto(id = "x", signalId = -1, venue = "mt5", status = "queued").toDomain())
    }

    @Test
    fun `lbank close is hidden after provider submission`() {
        assertTrue(execution(ExecutionVenue.LBANK, ExecutionStatus.QUEUED).canRequestClose)
        assertFalse(execution(ExecutionVenue.LBANK, ExecutionStatus.SUBMITTED).canRequestClose)
        assertFalse(execution(ExecutionVenue.LBANK, ExecutionStatus.OPEN).canRequestClose)
    }

    @Test
    fun `mt5 close is available only before a close request is already in flight`() {
        assertTrue(execution(ExecutionVenue.MT5, ExecutionStatus.SUBMITTED).canRequestClose)
        assertTrue(execution(ExecutionVenue.MT5, ExecutionStatus.OPEN).canRequestClose)
        assertFalse(execution(ExecutionVenue.MT5, ExecutionStatus.CLOSE_REQUESTED).canRequestClose)
        assertFalse(execution(ExecutionVenue.MT5, ExecutionStatus.CLOSED).canRequestClose)
    }

    @Test
    fun `quantity validation enforces venue bounds`() {
        assertEquals(
            "MT5 quantity must be at least 0.01 lot",
            ExecutionController.quantityValidationError(ExecutionVenue.MT5, 0.009),
        )
        assertNull(ExecutionController.quantityValidationError(ExecutionVenue.MT5, 0.01))
        assertEquals(
            "MT5 quantity cannot exceed 100 lots",
            ExecutionController.quantityValidationError(ExecutionVenue.MT5, 100.01),
        )
        assertEquals(
            "LBank amount must be greater than zero",
            ExecutionController.quantityValidationError(ExecutionVenue.LBANK, 0.0),
        )
        assertNull(ExecutionController.quantityValidationError(ExecutionVenue.LBANK, 1.0))
        assertEquals(
            "Quantity must be a finite number",
            ExecutionController.quantityValidationError(ExecutionVenue.LBANK, Double.NaN),
        )
    }
}
