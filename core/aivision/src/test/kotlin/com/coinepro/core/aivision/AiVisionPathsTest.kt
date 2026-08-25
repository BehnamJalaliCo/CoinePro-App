package com.coinepro.core.aivision

import com.coinepro.core.model.MarketPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVisionPathsTest {
    private val forex = AiVisionPaths.of(MarketPlatform.COINEPRO_FX)
    private val crypto = AiVisionPaths.of(MarketPlatform.TRADEYAR)

    @Test
    fun `no address is shared between the two backends`() {
        assertTrue(setOf(forex.jobs, forex.job("j")).intersect(setOf(crypto.jobs, crypto.job("j"))).isEmpty())
    }

    @Test
    fun `CoinePro-FX hyphenates it and TradeYar nests it`() {
        // The app called `user/ai/vision/jobs` for a long time, which is neither of these.
        assertEquals("user/ai-vision/jobs", forex.jobs)
        assertEquals("user/ai-vision/jobs/j1", forex.job("j1"))

        assertEquals("api/mobile/v1/ai/vision/jobs", crypto.jobs)
        assertEquals("api/mobile/v1/ai/vision/jobs/j1", crypto.job("j1"))
    }
}
