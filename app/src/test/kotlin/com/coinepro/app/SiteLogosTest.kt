package com.coinepro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The 5.24.2 check's 404s: a coin's mark asked of the forex site, which publishes none. */
class SiteLogosTest {

    private val logos = siteLogos("https://coineprofx.com/api/")

    @Test
    fun `a coin asks nobody`() {
        assertNull(logos.url("NILUSDT"))
        assertNull(logos.url("BTCUSDT"))
    }

    @Test
    fun `a forex mark is the site's, not the api's`() {
        assertEquals("https://coineprofx.com/assets/logo/XAUUSD.webp", logos.url("xauusd"))
    }
}
