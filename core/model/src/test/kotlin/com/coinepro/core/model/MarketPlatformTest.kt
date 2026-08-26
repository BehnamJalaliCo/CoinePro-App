package com.coinepro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The platform identifiers, which are load-bearing in a way an enum usually is not.
 *
 * [MarketPlatform.id] is not a label. It selects a preference key in the encrypted token store, a
 * BuildConfig base URL and a Hilt binding, all of which are looked up by string. Changing one is
 * not a rename — it signs every existing install out of that platform and points its requests at
 * nothing. These tests exist to make that consequence visible at the moment somebody edits the
 * value, rather than at the moment a release ships.
 */
class MarketPlatformTest {

    @Test
    fun `the identifiers are pinned, because storage and configuration are keyed on them`() {
        // If this fails, read the class comment before changing it: every install that already has
        // a token filed under the old id will be signed out, and every BuildConfig lookup for the
        // old name will resolve to nothing.
        assertEquals("coinepro_fx", MarketPlatform.COINEPRO_FX.id)
        assertEquals("tradeyar", MarketPlatform.TRADEYAR.id)
    }

    @Test
    fun `identifiers are unique and contain nothing that needs escaping`() {
        val ids = MarketPlatform.entries.map { it.id }
        assertEquals("two platforms cannot share one id", ids.size, ids.toSet().size)
        for (id in ids) {
            assertTrue("`$id` must be a safe key", id.matches(Regex("^[a-z0-9_]+$")))
        }
    }

    @Test
    fun `every market type has exactly one platform serving it`() {
        // forMarket uses `first`, so a second platform for one market type would silently pick
        // whichever was declared earlier — and half the app would talk to the wrong backend.
        for (type in MarketType.entries) {
            val serving = MarketPlatform.entries.filter { it.marketType == type }
            assertEquals("$type is served by $serving", 1, serving.size)
            assertEquals(serving.single(), MarketPlatform.forMarket(type))
        }
    }

    @Test
    fun `an id round-trips, and an unknown one is null rather than a default`() {
        // Null matters: this reads persisted preferences, and an unrecognised value means the
        // stored platform is gone. Defaulting to the first one would silently move a reader's
        // session to the other backend.
        for (platform in MarketPlatform.entries) {
            assertEquals(platform, MarketPlatform.fromId(platform.id))
        }
        assertNull(MarketPlatform.fromId("binance"))
        assertNull(MarketPlatform.fromId(""))
        assertNull(MarketPlatform.fromId("COINEPRO_FX"))
    }
}
