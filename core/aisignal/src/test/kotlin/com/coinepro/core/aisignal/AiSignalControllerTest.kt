package com.coinepro.core.aisignal

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiSignalControllerTest {
    private val request = AiSignalRequest("XAUUSD", AiSignalTimeframe.H1, AiSignalRisk.MEDIUM)

    @Test
    fun `queued job becomes done only after server returns done`() = runTest {
        val queued = job(AiSignalJobStatus.QUEUED)
        val done = job(AiSignalJobStatus.DONE, result = validatedResult())
        val gateway = FakeGateway(create = queued, refresh = done)
        val controller = AiSignalController(gateway, this, pollIntervalMs = 1)

        controller.submit(request)
        advanceUntilIdle()

        assertEquals(AiSignalJobStatus.DONE, controller.state.value.job?.status)
        assertTrue(controller.state.value.job?.canOpenValidatedSignal == true)
        assertEquals(1, gateway.refreshCount)
    }

    @Test
    fun `done without validated result is blocked from opening`() = runTest {
        val gateway = FakeGateway(create = job(AiSignalJobStatus.DONE, result = null))
        val controller = AiSignalController(gateway, this, pollIntervalMs = 1)

        controller.submit(request)
        advanceUntilIdle()

        assertFalse(controller.state.value.job?.canOpenValidatedSignal ?: true)
        assertNotNull(controller.state.value.error)
    }

    @Test
    fun `quota exhaustion is a real server gate`() = runTest {
        val gateway = FakeGateway(quotaError = AiSignalQuotaExhaustedException())
        val controller = AiSignalController(gateway, this, pollIntervalMs = 1)

        controller.refreshQuota()
        advanceUntilIdle()

        assertTrue(controller.state.value.quotaExhausted)
        assertFalse(controller.state.value.refreshingQuota)
    }

    @Test
    fun `entitlement denial blocks submission`() = runTest {
        val gateway = FakeGateway(createError = AiSignalEntitlementRequiredException())
        val controller = AiSignalController(gateway, this, pollIntervalMs = 1)

        controller.submit(request)
        advanceUntilIdle()

        assertTrue(controller.state.value.entitlementRequired)
        assertFalse(controller.state.value.submitting)
    }

    @Test
    fun `server expiry exits pending state and remains recoverable`() = runTest {
        val gateway = FakeGateway(
            create = job(AiSignalJobStatus.RUNNING),
            refreshError = AiSignalJobExpiredException(),
        )
        val controller = AiSignalController(gateway, this, pollIntervalMs = 1)

        controller.submit(request)
        advanceUntilIdle()

        assertEquals(AiSignalJobStatus.EXPIRED, controller.state.value.job?.status)
        assertFalse(controller.state.value.job?.isPending ?: true)
        assertNotNull(controller.state.value.error)
    }

    private fun job(
        status: AiSignalJobStatus,
        result: AiGeneratedSignal? = null,
    ) = AiSignalJob(
        id = "job-1",
        status = status,
        request = request,
        result = result,
        errorCode = null,
        errorMessage = null,
        quota = AiSignalQuota(remaining = 4, limit = 5, resetAt = null),
        createdAt = null,
        expiresAt = null,
    )

    private fun validatedResult() = AiGeneratedSignal(
        signalId = 42,
        symbol = "XAUUSD",
        direction = com.coinepro.core.model.SignalDirection.BUY,
        timeframe = "H1",
        entry = 2500.0,
        entryZone = null,
        stopLoss = 2485.0,
        targets = listOf(AiSignalTarget(1, 2520.0)),
        confidence = 80,
        riskRewardTp1 = 1.3,
        rationale = null,
        validatedAt = null,
    )

    private class FakeGateway(
        private val create: AiSignalJob? = null,
        private val refresh: AiSignalJob? = null,
        private val quotaValue: AiSignalQuota = AiSignalQuota(remaining = 5, limit = 5, resetAt = null),
        private val createError: Exception? = null,
        private val refreshError: Exception? = null,
        private val quotaError: Exception? = null,
    ) : AiSignalGateway {
        var refreshCount: Int = 0
            private set

        override suspend fun quota(): AiSignalQuota {
            quotaError?.let { throw it }
            return quotaValue
        }

        override suspend fun createJob(request: AiSignalRequest): AiSignalJob {
            createError?.let { throw it }
            return requireNotNull(create)
        }

        override suspend fun job(jobId: String): AiSignalJob {
            refreshCount++
            refreshError?.let { throw it }
            return requireNotNull(refresh)
        }
    }
}
