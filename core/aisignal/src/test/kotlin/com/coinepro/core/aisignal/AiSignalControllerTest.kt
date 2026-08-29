package com.coinepro.core.aisignal

import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.symbols.SymbolClassifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun `a refused request reaches the screen as a reason, not as exception text`() = runTest {
        // The bug the whole change exists for. `AiSignalRequestRejectedException` used to carry the
        // authored English "AI Signal request was rejected by server validation", the controller
        // could not tell it from server copy, and the screen drew it to a Persian reader.
        val gateway = FakeGateway(
            createError = AiSignalRequestRejectedException(
                serverMessage = "تایم‌فریم انتخابی پشتیبانی نمی‌شود.",
                serverCode = "AI-422",
                field = "timeframe",
            ),
        )
        val controller = AiSignalController(gateway, this, pollIntervalMs = 1)

        controller.submit(request)
        advanceUntilIdle()

        val error = requireNotNull(controller.state.value.error)
        assertEquals(AiSignalFailure.REQUEST_REJECTED, error.reason)
        assertEquals("تایم‌فریم انتخابی پشتیبانی نمی‌شود.", error.serverText)
        assertEquals("AI-422", error.serverCode)
        assertEquals("timeframe", error.serverField)
        assertFalse(controller.state.value.submitting)
    }

    @Test
    fun `a refusal with no server copy leaves the sentence to the screen`() = runTest {
        val gateway = FakeGateway(createError = AiSignalRequestRejectedException())
        val controller = AiSignalController(gateway, this, pollIntervalMs = 1)

        controller.submit(request)
        advanceUntilIdle()

        val error = requireNotNull(controller.state.value.error)
        assertEquals(AiSignalFailure.REQUEST_REJECTED, error.reason)
        // Null, not an English fallback. The screen has Persian copy for exactly this case.
        assertNull(error.serverText)
    }

    @Test
    fun `an expired job carries no authored sentence of its own`() = runTest {
        val gateway = FakeGateway(
            create = job(AiSignalJobStatus.RUNNING),
            refreshError = AiSignalJobExpiredException(),
        )
        val controller = AiSignalController(gateway, this, pollIntervalMs = 1)

        controller.submit(request)
        advanceUntilIdle()

        assertNull(controller.state.value.job?.errorMessage)
        assertEquals(AiSignalFailure.JOB_EXPIRED, controller.state.value.error?.reason)
    }

    @Test
    fun `a spent allowance is stated rather than reported as a failure`() = runTest {
        // Zero left is a fact about tomorrow. Pushing it into the error slot made the screen say
        // something had gone wrong on a request the reader had not made yet.
        val gateway = FakeGateway(quotaValue = AiSignalQuota(remaining = 0, limit = 20, resetAt = null))
        val controller = AiSignalController(gateway, this, pollIntervalMs = 1)

        controller.refreshQuota()
        advanceUntilIdle()

        assertTrue(controller.state.value.quotaExhausted)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `a symbol that is not a ticker never reaches the network`() = runTest {
        val gateway = FakeGateway(create = job(AiSignalJobStatus.QUEUED))
        val controller = AiSignalController(gateway, this, pollIntervalMs = 1)

        controller.submit(request.copy(symbol = "   "))
        advanceUntilIdle()

        assertEquals(AiSignalFailure.SYMBOL_UNSUPPORTED, controller.state.value.error?.reason)
        assertNull(controller.state.value.job)
    }

    @Test
    fun `the picker offers what the server said it accepts`() = runTest {
        val gateway = FakeGateway(
            quotaValue = AiSignalQuota(
                remaining = 4,
                limit = 5,
                resetAt = null,
                symbols = listOf("XAUUSD", "EURUSD"),
                timeframes = listOf(AiSignalTimeframe.H1, AiSignalTimeframe.H4),
            ),
        )
        val controller = AiSignalController(
            gateway,
            this,
            pollIntervalMs = 1,
            catalog = { SymbolClassifier.classifyAll(listOf("BTCUSDT", "XAUUSD", "EURUSD")) },
            platform = MarketPlatform.COINEPRO_FX,
        )

        controller.refreshQuota()
        advanceUntilIdle()

        val universe = controller.state.value.universe
        assertEquals(AiSymbolOrigin.SERVER, universe.origin)
        assertTrue(universe.allows("EURUSD"))
        assertFalse(universe.allows("BTCUSDT"))
    }

    @Test
    fun `with no server list the picker reaches the whole catalogue`() = runTest {
        val gateway = FakeGateway()
        val controller = AiSignalController(
            gateway,
            this,
            pollIntervalMs = 1,
            catalog = { SymbolClassifier.classifyAll(listOf("BTCUSDT", "ETHUSDT", "SOLUSDT")) },
        )

        controller.refreshQuota()
        advanceUntilIdle()

        assertEquals(AiSymbolOrigin.CATALOGUE, controller.state.value.universe.origin)
    }

    @Test
    fun `a catalogue that never loads leaves the screen usable`() = runTest {
        val gateway = FakeGateway()
        val controller = AiSignalController(
            gateway,
            this,
            pollIntervalMs = 1,
            catalog = { error("no route to the snapshot endpoint") },
        )

        controller.refreshQuota()
        advanceUntilIdle()

        val universe = controller.state.value.universe
        assertEquals(AiSymbolOrigin.FALLBACK, universe.origin)
        assertTrue(universe.markets.isNotEmpty())
        assertFalse(universe.loading)
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

        override suspend fun job(jobId: String, request: AiSignalRequest): AiSignalJob {
            refreshCount++
            refreshError?.let { throw it }
            return requireNotNull(refresh)
        }
    }
}
