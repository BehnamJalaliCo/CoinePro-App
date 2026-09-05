package com.coinepro.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pin list the build carries, read the way `NetworkFactory` reads it.
 *
 * What matters is the two ends: nothing configured means nothing pinned and no exception, and a
 * malformed entry is refused rather than skipped — a build that believes it is pinned and is not
 * is the worse of the two failures.
 */
class CertificatePinsTest {

    @Test
    fun `no property is no pinning`() {
        assertTrue(NetworkFactory.parsePins(null).isEmpty())
        assertTrue(NetworkFactory.parsePins("").isEmpty())
        assertTrue(NetworkFactory.parsePins("  ;  ").isEmpty())
    }

    @Test
    fun `a primary and a backup for one host are both kept, in order`() {
        val pins = NetworkFactory.parsePins(
            "coineprofx.com=sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=;" +
                "coineprofx.com=sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=;" +
                "tradeyar.trade-future.ir=sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=",
        )

        assertEquals(listOf("coineprofx.com", "tradeyar.trade-future.ir"), pins.keys.toList())
        assertEquals(2, pins.getValue("coineprofx.com").size)
        assertTrue(pins.getValue("coineprofx.com")[0].startsWith("sha256/AAAA"))
        assertTrue(pins.getValue("coineprofx.com")[1].startsWith("sha256/BBBB"))
    }

    @Test
    fun `a malformed entry is refused, not skipped`() {
        for (bad in listOf("coineprofx.com", "coineprofx.com=", "=sha256/AAAA", "coineprofx.com=md5/AAAA", "coineprofx.com=sha256/")) {
            val outcome = runCatching { NetworkFactory.parsePins(bad) }
            assertTrue("'$bad' should have been refused", outcome.isFailure)
        }
    }
}
