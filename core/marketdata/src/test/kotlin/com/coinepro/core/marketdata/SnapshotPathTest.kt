package com.coinepro.core.marketdata

import com.coinepro.core.model.MarketPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SnapshotPathTest {
    @Test
    fun `the snapshot lives at a different address on each backend`() {
        // CoinePro-FX serves it from the root; TradeYar's nginx has a /ws location that its mobile
        // prefix deliberately sits inside. One hard-coded path leaves one platform's feed empty.
        assertEquals("ws/snapshot", MarketPlatform.COINEPRO_FX.snapshotPath())
        assertEquals("api/mobile/v1/ws/snapshot", MarketPlatform.TRADEYAR.snapshotPath())
        assertNotEquals(
            MarketPlatform.COINEPRO_FX.snapshotPath(),
            MarketPlatform.TRADEYAR.snapshotPath(),
        )
    }
}
