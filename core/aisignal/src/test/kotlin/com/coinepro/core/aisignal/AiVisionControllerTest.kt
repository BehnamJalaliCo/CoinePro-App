package com.coinepro.core.aisignal

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiVisionControllerTest {
    @Test fun `terminal done clears selected image`() = runTest {
        val gateway = FakeVisionGateway(AiSignalJobStatus.DONE)
        val controller = AiVisionController(gateway, this, 1)
        controller.selectImage(AiVisionImage(byteArrayOf(1), "image/jpeg", AiVisionImageSource.GALLERY))
        controller.submit(AiVisionRequest("XAUUSD", AiSignalTimeframe.H1))
        advanceUntilIdle()
        assertNull(controller.state.value.selectedImage)
        assertTrue(controller.state.value.job?.canOpenValidatedSignal == true)
    }

    @Test fun `sign out clears image and job`() = runTest {
        val controller = AiVisionController(FakeVisionGateway(AiSignalJobStatus.DONE), this)
        controller.selectImage(AiVisionImage(byteArrayOf(1), "image/png", AiVisionImageSource.CAMERA))
        controller.onSignedOut()
        assertNull(controller.state.value.selectedImage)
        assertNull(controller.state.value.job)
    }
}

private class FakeVisionGateway(private val status: AiSignalJobStatus) : AiVisionGateway {
    override suspend fun quota() = AiSignalQuota(2, 3, null)
    override suspend fun createJob(image: AiVisionImage, request: AiVisionRequest): AiVisionJob = job(request)
    override suspend fun job(jobId: String): AiVisionJob = job(AiVisionRequest("XAUUSD", AiSignalTimeframe.H1))
    private fun job(request: AiVisionRequest) = AiVisionJob(
        id = "vision-1",
        status = status,
        request = request,
        result = if (status == AiSignalJobStatus.DONE) AiVisionAnalysis(
            42, request.symbol, request.timeframe.wireValue, "BULLISH", 2400.0, 2380.0,
            listOf(AiSignalTarget(1, 2430.0)), 80, "Validated structure",
        ) else null,
        errorMessage = null,
        quota = AiSignalQuota(1, 3, null),
    )
}
