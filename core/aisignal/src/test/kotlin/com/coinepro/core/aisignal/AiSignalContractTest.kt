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
    fun `a result with no first target is unusable, not partial`() {
        // The whole call is built around it. Second and third are genuinely optional on both
        // servers, but an analysis with nowhere to take profit is not a partial answer.
        val result = validResult().copy(tp1 = Double.NaN).toDomain(requireNotNull(request.toDomain()))

        assertNull(result)
    }

    @Test
    fun `a confidence written as a fraction is read as percent, not as one percent`() {
        // TradeYar's prompt asks the model for a value between zero and one; CoinePro-FX writes a
        // whole number. Read raw, a strong TradeYar call would render as worthless.
        val fraction = validResult().copy(confidence = 0.82).toDomain(requireNotNull(request.toDomain()))
        assertEquals(82, requireNotNull(fraction).confidence)

        val percent = validResult().copy(confidence = 82.0).toDomain(requireNotNull(request.toDomain()))
        assertEquals(82, requireNotNull(percent).confidence)
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
    fun `symbol normalization cleans a ticker and refuses what is not one`() {
        // This test used to assert that `EURUSD` and `BTCUSD` normalize to null, which pinned the
        // bug rather than the behaviour: the client refused most of both platforms' universes
        // before the request left the phone. The server owns its product scope and refuses what it
        // does not serve, with a reason; this only has to reject text that is not a ticker at all.
        assertEquals("XAUUSD", AiSignalProductScope.normalizeSymbol("xau/usd"))
        assertEquals("BTCUSDT", AiSignalProductScope.normalizeSymbol("btc-usdt"))
        assertEquals("EURUSD", AiSignalProductScope.normalizeSymbol("EURUSD"))
        assertEquals("BTCUSD", AiSignalProductScope.normalizeSymbol("BTCUSD"))
        assertNull(AiSignalProductScope.normalizeSymbol(""))
        assertNull(AiSignalProductScope.normalizeSymbol("1234"))
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
        tp1 = 2520.0,
        tp2 = 2540.0,
        confidence = 82.0,
        riskRewardTp1 = 1.5,
        rationale = "Structured server-validated rationale",
    )
}
