package com.coinepro.core.aivision

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiVisionControllerTest {
    private val upload = AiVisionImageUpload(
        fileName = "chart.jpg",
        mimeType = "image/jpeg",
        bytes = byteArrayOf(1, 2, 3),
    )

    @Test
    fun `pending job becomes done only after server returns done`() = runTest {
        val gateway = FakeGateway(
            create = job(AiVisionJobStatus.QUEUED),
            refresh = job(AiVisionJobStatus.DONE, result = lowConfidenceResult()),
        )
        val controller = AiVisionController(gateway, this, pollIntervalMs = 1)

        controller.submit(upload)
        advanceUntilIdle()

        assertEquals(AiVisionJobStatus.DONE, controller.state.value.job?.status)
        assertEquals(1, gateway.refreshCount)
        assertNotNull(controller.state.value.job?.result)
    }

    @Test
    fun `server expiry leaves pending state cleanly`() = runTest {
        val gateway = FakeGateway(
            create = job(AiVisionJobStatus.RUNNING),
            refreshError = AiVisionJobExpiredException(),
        )
        val controller = AiVisionController(gateway, this, pollIntervalMs = 1)

        controller.submit(upload)
        advanceUntilIdle()

        assertEquals(AiVisionJobStatus.EXPIRED, controller.state.value.job?.status)
        assertFalse(controller.state.value.job?.isPending ?: true)
        assertNotNull(controller.state.value.error)
    }

    @Test
    fun `oversized local image is rejected before gateway`() = runTest {
        val gateway = FakeGateway(create = job(AiVisionJobStatus.QUEUED))
        val controller = AiVisionController(gateway, this, pollIntervalMs = 1)
        val bad = upload.copy(bytes = ByteArray(AI_VISION_MAX_UPLOAD_BYTES + 1))

        controller.submit(bad)
        advanceUntilIdle()

        assertEquals(0, gateway.createCount)
        assertNotNull(controller.state.value.error)
    }

    private fun job(
        status: AiVisionJobStatus,
        result: AiVisionResult? = null,
    ) = AiVisionJob(
        id = "vision-1",
        status = status,
        result = result,
        errorCode = null,
        errorMessage = null,
        createdAt = null,
        expiresAt = null,
    )

    private fun lowConfidenceResult() = AiVisionResult(
        assessment = AiVisionAssessment.LOW_CONFIDENCE,
        symbol = "XAUUSD",
        timeframe = "H1",
        confidence = 30,
        trendBias = "Mixed",
        marketStructure = null,
        setup = null,
        direction = null,
        entryZone = null,
        stopLoss = null,
        targets = emptyList(),
        risk = null,
        reasoning = "Image is not clear enough.",
        signalId = null,
        validatedAt = null,
    )

    private class FakeGateway(
        private val create: AiVisionJob? = null,
        private val refresh: AiVisionJob? = null,
        private val refreshError: Exception? = null,
    ) : AiVisionGateway {
        var createCount = 0
            private set
        var refreshCount = 0
            private set

        override suspend fun createJob(upload: AiVisionImageUpload): AiVisionJob {
            createCount++
            return requireNotNull(create)
        }

        override suspend fun job(jobId: String): AiVisionJob {
            refreshCount++
            refreshError?.let { throw it }
            return requireNotNull(refresh)
        }
    }
}
