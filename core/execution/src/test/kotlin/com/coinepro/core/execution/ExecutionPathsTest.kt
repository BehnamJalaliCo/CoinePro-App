package com.coinepro.core.execution

import com.coinepro.core.model.MarketPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionPathsTest {
    @Test
    fun `order execution exists on TradeYar and nowhere else`() {
        assertNotNull(ExecutionPaths.of(MarketPlatform.TRADEYAR))
        assertNull(
            "CoinePro-FX never built this surface; its model is copy trading, somewhere else",
            ExecutionPaths.of(MarketPlatform.COINEPRO_FX),
        )
    }

    @Test
    fun `TradeYar's addresses carry its mobile prefix`() {
        val paths = requireNotNull(ExecutionPaths.of(MarketPlatform.TRADEYAR))

        assertEquals("api/mobile/v1/venues/lbank", paths.venue)
        assertEquals("api/mobile/v1/executions", paths.executions)
        assertEquals("api/mobile/v1/executions/9", paths.execution("9"))
        assertEquals("api/mobile/v1/executions/9/close", paths.close("9"))
        assertTrue(
            listOf(paths.venue, paths.executions, paths.execution("9"), paths.close("9"))
                .all { it.startsWith("api/mobile/v1/") },
        )
    }

    @Test
    fun `the signal id is not in the execute path`() {
        val paths = requireNotNull(ExecutionPaths.of(MarketPlatform.TRADEYAR))

        // It travels in the body. It used to be a path segment, which is the kind of difference
        // that answers 404 rather than failing in a way anyone reads.
        assertEquals("api/mobile/v1/executions", paths.executions)
        assertTrue(!paths.executions.contains("signals"))
    }
}
