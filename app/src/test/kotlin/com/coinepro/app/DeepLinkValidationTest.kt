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
            parseCoineProDeepLink("https", "pro-chart.com", listOf("reset"), token),
        )

        // Another host may serve the same path; nobody proved it belongs to this app.
        assertNull(parseCoineProDeepLink("https", "example.com", listOf("reset"), token))
        // A custom scheme any installed app may register is not somewhere to accept a credential.
        assertNull(parseCoineProDeepLink("coinepro", "reset", listOf("reset"), token))
        // The App Link claims /reset only; the rest of that site stays in the browser.
        assertNull(parseCoineProDeepLink("https", "pro-chart.com", listOf("login"), token))
        assertNull(parseCoineProDeepLink("https", "pro-chart.com", listOf("reset"), null))
        assertNull(parseCoineProDeepLink("https", "pro-chart.com", listOf("reset"), "short"))
        assertNull(
            parseCoineProDeepLink("https", "pro-chart.com", listOf("reset"), "$token <script>"),
        )
    }

    // ── a shared script (4.82.0, run Σ item S3 C) ────────────────────────────────────────────

    @Test
    fun `a script link on the brand host is read`() {
        val id = "abcdefghjkm"
        assertEquals(
            CoineProDeepLink.Script(id),
            parseCoineProDeepLink("https", "pro-chart.com", listOf("s", id)),
        )
    }

    @Test
    fun `a script link from anywhere else is nothing`() {
        // A script id becomes a lookup and then a screen showing code. The link carries no source
        // and could not be made to — but the host check is still where an unverified link stops.
        val id = "abcdefghjkm"
        assertNull(parseCoineProDeepLink("https", "evil.example", listOf("s", id)))
        assertNull(parseCoineProDeepLink("https", "pro-chart.com.evil.example", listOf("s", id)))
        assertNull(parseCoineProDeepLink("http", "pro-chart.com", listOf("s", id)))
        assertNull(parseCoineProDeepLink("coinepro", "s", listOf(id)))
    }

    @Test
    fun `a script link with a malformed id is nothing`() {
        for (bad in listOf("", "ABCDEFGH", "abc defgh", "ab", "a".repeat(64), "../../etc/passwd")) {
            assertNull(bad, parseCoineProDeepLink("https", "pro-chart.com", listOf("s", bad)))
        }
        assertNull(parseCoineProDeepLink("https", "pro-chart.com", listOf("s")))
    }

    @Test
    fun `a script link does not collide with the recovery link`() {
        // Same host, different path, and the script branch is checked first — so a token on /reset
        // still gets through and an id on /s/ is never read as a credential.
        val token = "a".repeat(32)
        assertEquals(
            CoineProDeepLink.PasswordReset(token),
            parseCoineProDeepLink("https", "pro-chart.com", listOf("reset"), token),
        )
        assertNull(parseCoineProDeepLink("https", "pro-chart.com", listOf("s", "abcdefghjkm"), token).let {
            if (it is CoineProDeepLink.PasswordReset) it else null
        })
    }

    @Test
    fun `the web terminal's own chart address opens that chart`() {
        // `/terminal/BTCUSDT/4h`, the form index.html documents (5.18.1).
        assertEquals(CoineProDeepLink.Market("BTCUSDT", "4H"), webChartLinkOrNull(listOf("BTCUSDT", "4h")))
        assertEquals(CoineProDeepLink.Market("XAUUSD", "H1"), webChartLinkOrNull(listOf("XAUUSD"), "H1"))
        assertEquals(CoineProDeepLink.Market("ETHUSDT", null), webChartLinkOrNull(listOf("ETHUSDT")))
        // A screen's name is lower-case and is never read as a ticker.
        assertNull(webChartLinkOrNull(listOf("screener")))
        assertNull(webChartLinkOrNull(listOf("market", "BTCUSDT")))
        assertNull(webChartLinkOrNull(listOf("BTCUSDT", "4h", "extra")))
        assertNull(webChartLinkOrNull(listOf("BTC USDT")))
    }
}
