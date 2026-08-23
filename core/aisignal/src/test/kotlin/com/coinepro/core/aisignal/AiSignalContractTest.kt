package com.coinepro.core.aisignal

import com.coinepro.core.model.SignalDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSignalContractTest {
    private val request = AiSignalRequestDto(
        symbol = "XAUUSD",
        timeframe = "H1",
        risk = "medium",
    )

    @Test
    fun `validated structured result maps to persisted signal`() {
        val result = validResult().toDomain(requireNotNull(request.toDomain()))

        requireNotNull(result)
        assertEquals(42L, result.signalId)
        assertEquals("XAUUSD", result.symbol)
        assertEquals(SignalDirection.BUY, result.direction)
        assertEquals(2, result.targets.size)
    }

    @Test
    fun `unvalidated model output is never exposed as a generated signal`() {
        val result = validResult().copy(validated = false)
            .toDomain(requireNotNull(request.toDomain()))

        assertNull(result)
    }

    @Test
    fun `mismatched symbol is rejected instead of trusted`() {
        val result = validResult().copy(symbol = "BTCUSDT")
            .toDomain(requireNotNull(request.toDomain()))

        assertNull(result)
    }

    @Test
    fun `mismatched timeframe is rejected instead of trusted`() {
        val result = validResult().copy(timeframe = "H4")
            .toDomain(requireNotNull(request.toDomain()))

        assertNull(result)
    }

    @Test
    fun `invalid target invalidates the whole structured result`() {
        val result = validResult().copy(
            targets = listOf(AiSignalTargetDto(level = 1, price = Double.NaN)),
        ).toDomain(requireNotNull(request.toDomain()))

        assertNull(result)
    }

    @Test
    fun `job can only open a signal when done and validated`() {
        val domainRequest = requireNotNull(request.toDomain())
        val done = AiSignalJob(
            id = "job-1",
            status = AiSignalJobStatus.DONE,
            request = domainRequest,
            result = requireNotNull(validResult().toDomain(domainRequest)),
            errorCode = null,
            errorMessage = null,
            quota = null,
            createdAt = null,
            expiresAt = null,
        )
        val running = done.copy(status = AiSignalJobStatus.RUNNING)

        assertTrue(done.canOpenValidatedSignal)
        assertFalse(running.canOpenValidatedSignal)
    }

    @Test
    fun `product symbol normalization stays inside CoinePro scope`() {
        assertEquals("XAUUSD", AiSignalProductScope.normalizeSymbol("xau/usd"))
        assertEquals("BTCUSDT", AiSignalProductScope.normalizeSymbol("btc-usdt"))
        assertNull(AiSignalProductScope.normalizeSymbol("EURUSD"))
        assertNull(AiSignalProductScope.normalizeSymbol("BTCUSD"))
    }

    private fun validResult() = AiGeneratedSignalDto(
        validated = true,
        signalId = 42,
        symbol = "XAUUSD",
        direction = "BUY",
        timeframe = "H1",
        entry = 2500.0,
        entryZone = AiSignalEntryZoneDto(low = 2498.0, high = 2502.0),
        stopLoss = 2485.0,
        targets = listOf(
            AiSignalTargetDto(level = 1, price = 2520.0),
            AiSignalTargetDto(level = 2, price = 2540.0),
        ),
        confidence = 82,
        riskRewardTp1 = 1.5,
        rationale = "Structured server-validated rationale",
    )
}
