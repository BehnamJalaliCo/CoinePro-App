package com.coinepro.core.execution

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionRateLimitTest {
    @Test
    fun `rate limited execute is surfaced and never retried automatically`() {
        val gateway = RateLimitedExecutionGateway()
        val controller = ExecutionController(gateway, CoroutineScope(Dispatchers.Unconfined))

        controller.executeSignal(
            signalId = 42L,
            venue = ExecutionVenue.MT5,
            quantity = 0.1,
            clientRequestId = "req-42",
        )

        assertEquals(1, gateway.executeCalls)
        assertFalse(controller.execution.value.loading)
        // A flag rather than a message, so the screen can say it in the reader's language and say
        // the part that matters: nothing was sent.
        assertTrue(controller.execution.value.rateLimited)
        assertEquals(null, controller.execution.value.error)
    }
}

private class RateLimitedExecutionGateway : ExecutionGateway {
    var executeCalls = 0

    override suspend fun connections(): Pair<VenueConnection?, VenueConnection?> = null to null

    override suspend fun connectMt5(broker: String, server: String, login: String, password: String) = Unit

    override suspend fun disconnectMt5() = Unit

    override suspend fun connectLbank(apiKey: String, apiSecret: String, permission: LbankPermission) = Unit

    override suspend fun disconnectLbank() = Unit

    override suspend fun executeSignal(
        signalId: Long,
        venue: ExecutionVenue,
        quantity: Double,
        clientRequestId: String,
    ): SignalExecution {
        executeCalls += 1
        throw ExecutionRateLimitedException()
    }

    override suspend fun executions(limit: Int): List<SignalExecution> = emptyList()

    override suspend fun execution(executionId: String): SignalExecution = error("unused")

    override suspend fun requestClose(executionId: String): SignalExecution = error("unused")
}
