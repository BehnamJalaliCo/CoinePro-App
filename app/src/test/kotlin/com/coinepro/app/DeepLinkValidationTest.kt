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
}
