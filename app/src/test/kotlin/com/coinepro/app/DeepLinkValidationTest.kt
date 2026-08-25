package com.coinepro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkValidationTest {
    @Test
    fun acceptsOnlyPositivePersistedSignalIds() {
        assertEquals(1L, positiveSignalId("1"))
        assertEquals(Long.MAX_VALUE, positiveSignalId(Long.MAX_VALUE.toString()))
        assertNull(positiveSignalId(null))
        assertNull(positiveSignalId(""))
        assertNull(positiveSignalId("0"))
        assertNull(positiveSignalId("-1"))
        assertNull(positiveSignalId("not-a-signal"))
    }

    @Test
    fun acceptsOnlyCanonicalCoineProDeepLinks() {
        assertEquals(
            CoineProDeepLink.Signal(42L),
            parseCoineProDeepLink("coinepro", "signal", listOf("42")),
        )
        assertEquals(
            CoineProDeepLink.Activity,
            parseCoineProDeepLink("coinepro", "activity", emptyList()),
        )

        assertNull(parseCoineProDeepLink("https", "signal", listOf("42")))
        assertNull(parseCoineProDeepLink("coinepro", "signal", listOf("0")))
        assertNull(parseCoineProDeepLink("coinepro", "signal", listOf("-1")))
        assertNull(parseCoineProDeepLink("coinepro", "signal", listOf("42", "extra")))
        assertNull(parseCoineProDeepLink("coinepro", "activity", listOf("extra")))
        assertNull(parseCoineProDeepLink("coinepro", "unknown", emptyList()))
    }

    @Test
    fun `a recovery token is taken only from the verified host over https`() {
        val token = "a".repeat(32)
        assertEquals(
            CoineProDeepLink.PasswordReset(token),
            parseCoineProDeepLink("https", "user.tradeyar.trade-future.ir", listOf("reset"), token),
        )

        // Another host may serve the same path; nobody proved it belongs to this app.
        assertNull(parseCoineProDeepLink("https", "example.com", listOf("reset"), token))
        // A custom scheme any installed app may register is not somewhere to accept a credential.
        assertNull(parseCoineProDeepLink("coinepro", "reset", listOf("reset"), token))
        // The App Link claims /reset only; the rest of that site stays in the browser.
        assertNull(parseCoineProDeepLink("https", "user.tradeyar.trade-future.ir", listOf("login"), token))
        assertNull(parseCoineProDeepLink("https", "user.tradeyar.trade-future.ir", listOf("reset"), null))
        assertNull(parseCoineProDeepLink("https", "user.tradeyar.trade-future.ir", listOf("reset"), "short"))
        assertNull(
            parseCoineProDeepLink("https", "user.tradeyar.trade-future.ir", listOf("reset"), "$token <script>"),
        )
    }
}
