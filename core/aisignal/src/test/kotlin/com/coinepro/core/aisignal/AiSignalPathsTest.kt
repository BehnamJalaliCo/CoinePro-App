package com.coinepro.core.aisignal

import com.coinepro.core.model.MarketPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSignalPathsTest {
    private val forex = AiSignalPaths.of(MarketPlatform.COINEPRO_FX)
    private val crypto = AiSignalPaths.of(MarketPlatform.TRADEYAR)

    private fun AiSignalPaths.all() = setOf(quota, generate, result("j1"))

    @Test
    fun `no address is shared between the two backends`() {
        assertTrue(forex.all().intersect(crypto.all()).isEmpty())
    }

    @Test
    fun `each backend keeps its own prefix`() {
        assertEquals("user/ai-signal/quota", forex.quota)
        assertEquals("user/ai-signal/generate", forex.generate)
        assertEquals("user/ai-signal/result/j1", forex.result("j1"))

        assertEquals("api/mobile/v1/ai/quota", crypto.quota)
        assertEquals("api/mobile/v1/ai/generate", crypto.generate)
        assertEquals("api/mobile/v1/ai/result/j1", crypto.result("j1"))
    }

    @Test
    fun `the job id is placed in the path, never left as a template`() {
        assertTrue(forex.result("abc").endsWith("/abc"))
        assertTrue(crypto.result("abc").endsWith("/abc"))
    }
}
