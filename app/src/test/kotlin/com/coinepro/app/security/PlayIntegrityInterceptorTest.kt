package com.coinepro.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayIntegrityInterceptorTest {

    @Test
    fun `only writes to the three money-or-credential routes are gated`() {
        assertTrue(PlayIntegrityInterceptor.isGated("POST", "/user/auth/login"))
        assertTrue(PlayIntegrityInterceptor.isGated("POST", "/api/mobile/v1/executions"))
        assertTrue(PlayIntegrityInterceptor.isGated("PUT", "/user/signals/execution/connections/"))
        assertFalse(
            "a read of the connection list is not an attestation",
            PlayIntegrityInterceptor.isGated("GET", "/user/signals/execution/connections"),
        )
        assertFalse(PlayIntegrityInterceptor.isGated("POST", "/api/mobile/v1/candles"))
    }

    @Test
    fun `the nonce is bound to the route and to the minute`() {
        val now = 1_700_000_000_000L
        val a = PlayIntegrityInterceptor.nonceFor("POST", "/user/auth/login", now)
        assertEquals(a, PlayIntegrityInterceptor.nonceFor("POST", "/user/auth/login", now + 30_000L))
        assertNotEquals(a, PlayIntegrityInterceptor.nonceFor("POST", "/user/auth/login", now + 60_000L))
        assertNotEquals(a, PlayIntegrityInterceptor.nonceFor("POST", "/api/mobile/v1/executions", now))
        // base64url, no padding: what a header and a backend both take without escaping.
        assertTrue(a.matches(Regex("[A-Za-z0-9_-]{43}")))
    }
}
