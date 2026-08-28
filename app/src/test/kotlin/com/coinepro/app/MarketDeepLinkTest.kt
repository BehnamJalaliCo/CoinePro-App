package com.coinepro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The widget's deep link, which arrives over an **unverified** scheme.
 *
 * Any installed app may register `coinepro://`, so what reaches `parseCoineProDeepLink` is an
 * arbitrary string from an untrusted sender — and it is about to become a navigation argument and
 * then a request path. The shape check is what stands between those two facts.
 */
class MarketDeepLinkTest {

    private fun parse(vararg segments: String) =
        parseCoineProDeepLink(scheme = "coinepro", host = "market", pathSegments = segments.toList())

    @Test
    fun `a ticker opens that market`() {
        assertEquals(CoineProDeepLink.Market("BTCUSDT"), parse("BTCUSDT"))
        assertEquals(CoineProDeepLink.Market("XAU/USD"), parse("XAU/USD"))
        assertEquals(CoineProDeepLink.Market("US500"), parse("US500"))
    }

    @Test
    fun `it is upper-cased, because every symbol in this app is`() {
        assertEquals(CoineProDeepLink.Market("BTCUSDT"), parse("btcusdt"))
    }

    @Test
    fun `anything that is not a ticker is refused rather than cleaned up`() {
        // Rejecting rather than sanitising: a ticker that needed cleaning was not a ticker, and
        // quietly opening a *different* market than the link named would be worse than opening
        // none. Traversal, injection and padding attempts all land here.
        listOf(
            "",
            " ",
            "..",
            "../../etc/passwd",
            "BTC USDT",
            "BTC;DROP",
            "<script>",
            "BTC%2FUSDT",
            "A",
            "TOOLONGASYMBOLNAME",
            "BTC/USDT/EXTRA",
        ).forEach { candidate ->
            assertNull("'$candidate' should not have parsed", parse(candidate))
        }
    }

    @Test
    fun `a market link with no ticker, or more than one, is refused`() {
        assertNull(parse())
        assertNull(parse("BTCUSDT", "ETHUSDT"))
    }

    @Test
    fun `only this app's scheme reaches the market route`() {
        assertNull(parseCoineProDeepLink("https", "market", listOf("BTCUSDT")))
        assertNull(parseCoineProDeepLink("coineproX", "market", listOf("BTCUSDT")))
    }
}
