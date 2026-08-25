package com.coinepro.core.signals

import com.coinepro.core.model.MarketPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two platforms' addresses, pinned.
 *
 * A path built for the wrong backend does not throw. It 404s, and a 404 arrives worded like a
 * status line, so it reads as the server being down rather than as the app asking for something
 * that was never there. That mistake has been made three times in this codebase — the alerts
 * surface, chart analysis, and the whole crypto side — and each time it was found by a human
 * comparing two lists by hand. These tests are that comparison, run on every build.
 */
class SignalPathsTest {
    private val forex = SignalPaths(MarketPlatform.COINEPRO_FX)
    private val crypto = SignalPaths(MarketPlatform.TRADEYAR)

    @Test
    fun `no signal address is shared between the two backends`() {
        val forexPaths = SignalStatusFilter.entries.map { forex.list(it) }.toSet()
        val cryptoPaths = SignalStatusFilter.entries.map { crypto.list(it) }.toSet()

        assertTrue(
            "An address serving both platforms means one of them is being asked the wrong question",
            forexPaths.intersect(cryptoPaths).isEmpty(),
        )
    }

    @Test
    fun `CoinePro-FX splits the list by status into two public addresses`() {
        // Its signal list is not under `user` at all, and there is no status query — the status is
        // the address. Asking one of them for a status it does not understand returns everything,
        // silently, looking exactly like a filter that worked.
        assertEquals("public/signals/active", forex.list(SignalStatusFilter.ACTIVE))
        assertEquals("public/signals/recent", forex.list(SignalStatusFilter.RECENT))
        assertEquals("public/signals/recent", forex.list(SignalStatusFilter.CLOSED))
        SignalStatusFilter.entries.forEach { assertNull(forex.statusQuery(it)) }
    }

    @Test
    fun `TradeYar serves one list and filters it with a query`() {
        SignalStatusFilter.entries.forEach { status ->
            assertEquals("api/mobile/v1/signals", crypto.list(status))
            assertEquals(status.wireValue, crypto.statusQuery(status))
        }
    }

    @Test
    fun `both backends now answer for a single signal`() {
        assertEquals("api/mobile/v1/signals/42", crypto.detail(42L))
        // Namespaced under `detail` rather than `public/signals/42`, which would collide with the
        // two list addresses beside it.
        assertEquals("public/signals/detail/42", forex.detail(42L))
        assertNotEquals(forex.detail(42L), crypto.detail(42L))
    }
}
