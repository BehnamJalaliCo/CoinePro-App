package com.coinepro.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteAssetsTest {

    @Test
    fun `the API prefix is not part of the static root`() {
        // The measurement this file exists for. `…/api/assets/logo/XAUUSD.webp` is a JSON 404 on
        // the live host; `…/assets/logo/XAUUSD.webp` is the picture.
        assertEquals(
            "https://coineprofx.com/assets/logo/XAUUSD.webp",
            SiteAssets.url("https://coineprofx.com/api/", "assets/logo/XAUUSD.webp"),
        )
    }

    @Test
    fun `a bare host is left alone`() {
        assertEquals(
            "https://tradeyar.trade-future.ir/assets/x.webp",
            SiteAssets.url("https://tradeyar.trade-future.ir/", "assets/x.webp"),
        )
    }

    @Test
    fun `a port and a deep prefix both resolve to the origin`() {
        assertEquals("https://example.test:8443", SiteAssets.originOf("https://example.test:8443/api/v2/"))
        assertEquals("http://10.0.2.2:8000", SiteAssets.originOf("http://10.0.2.2:8000/api"))
    }

    @Test
    fun `a leading slash on the path is not doubled`() {
        assertEquals(
            "https://coineprofx.com/assets/x.webp",
            SiteAssets.url("https://coineprofx.com/api/", "/assets/x.webp"),
        )
    }

    @Test
    fun `an address that is not absolute yields nothing to fetch`() {
        assertEquals("", SiteAssets.originOf(null))
        assertEquals("", SiteAssets.originOf("   "))
        assertEquals("", SiteAssets.originOf("coineprofx.com/api/"))
        assertEquals("", SiteAssets.originOf("https://"))
        assertNull(SiteAssets.url("", "assets/x.webp"))
        assertNull(SiteAssets.url("not-a-url", "assets/x.webp"))
    }
}
