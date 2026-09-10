package com.coinepro.app

import com.coinepro.core.network.NetworkFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pins the build ships with — item 6 of the 4.52 run.
 *
 * Two hosts, each with a primary and at least one backup, and an expiry in the future: the
 * three things `docs/security/PINNING.md` says a pin set must have before it may ship. Read
 * from `BuildConfig`, so this is the artefact's own pin list and not a copy of it.
 */
class CertificatePinDefaultsTest {

    @Test
    fun `both API hosts carry a primary and a backup pin`() {
        val pins = NetworkFactory.parsePins(BuildConfig.CERTIFICATE_PINS)
        assertEquals(setOf("tradeyar.trade-future.ir", "coineprofx.com"), pins.keys)
        for ((host, digests) in pins) {
            assertTrue("$host has ${digests.size} pin(s); a primary and a backup are the floor", digests.size >= 2)
            assertTrue(digests.all { it.startsWith("sha256/") && it.length == "sha256/".length + 44 })
        }
        // TradeYar's primary is the leaf the server team measured; CoinePro-FX's is Cloudflare's
        // issuing intermediate (GTS WE1) — see PINNING.md.
        assertTrue(pins.getValue("tradeyar.trade-future.ir").contains("sha256/RO8XwxTQmKWLxQ7Ij7dkTd5vWTS4aC2pROWNg3Sh25c="))
        assertTrue(pins.getValue("coineprofx.com").contains("sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="))
    }

    @Test
    fun `the pins expire, and not yet`() {
        val until = BuildConfig.CERTIFICATE_PINS_UNTIL
        assertTrue("pins must carry an expiry", until > 0L)
        assertTrue("the expiry has passed; re-measure the hosts and move the date", until > System.currentTimeMillis())
    }
}
