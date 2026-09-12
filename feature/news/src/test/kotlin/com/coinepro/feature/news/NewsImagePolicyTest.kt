package com.coinepro.feature.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the hero will and will not fetch, and how far it downsamples what it does.
 *
 * Both are decisions made before a byte is read, which is exactly why they are worth a test: a
 * scheme check that quietly stops working is a cleartext request nobody notices, and a sample size
 * that quietly returns one is a 24MB bitmap per card that nobody notices until a mid-range phone
 * starts killing the app in the list.
 */
class NewsImagePolicyTest {

    @Test
    fun `an https address is accepted exactly as it was sent`() {
        val url = "https://cdn.example.com/2026/08/gold.jpg?w=1200"
        assertEquals(url, NewsImagePolicy.accept(url))
    }

    @Test
    fun `cleartext and other schemes are refused`() {
        assertNull(NewsImagePolicy.accept("http://cdn.example.com/gold.jpg"))
        assertNull(NewsImagePolicy.accept("intent://cdn.example.com/gold.jpg#Intent;end"))
        assertNull(NewsImagePolicy.accept("file:///data/data/com.coinepro/gold.jpg"))
    }

    @Test
    fun `an address with no host is refused`() {
        assertNull(NewsImagePolicy.accept("https:///gold.jpg"))
    }

    @Test
    fun `nothing at all is refused rather than fetched`() {
        assertNull(NewsImagePolicy.accept(null))
        assertNull(NewsImagePolicy.accept("   "))
        assertNull(NewsImagePolicy.accept("not a url"))
    }

    @Test
    fun `an address longer than the cap is refused`() {
        val long = "https://cdn.example.com/" + "a".repeat(NewsImagePolicy.MAX_URL_LENGTH)
        assertNull(NewsImagePolicy.accept(long))
    }

    @Test
    fun `with no backend picture route the publishers own address is fetched unchanged`() {
        // Which is what ships today, and the reason this is a test rather than a comment: the seam
        // must be inert until somebody sets it, or turning it on becomes a change to the loader.
        val url = "https://cdn.example.com/gold.jpg"
        assertEquals(url, NewsImagePolicy.through(base = null, url = url))
        assertEquals(url, NewsImagePolicy.through(base = "   ", url = url))
    }

    @Test
    fun `a configured backend route carries the publishers address as a query`() {
        val proxied = NewsImagePolicy.through(
            base = "https://api.example.com/news/image/",
            url = "https://cdn.example.com/gold.jpg?w=2000",
        )
        // The trailing slash of the base is not doubled, and the publisher's own query survives
        // being encoded — an un-encoded `?w=2000` would arrive at the backend as *its* parameter.
        assertEquals(
            "https://api.example.com/news/image?url=https%3A%2F%2Fcdn.example.com%2Fgold.jpg%3Fw%3D2000",
            proxied,
        )
    }

    @Test
    fun `an address the policy refuses is never handed to the backend either`() {
        val hostile = "http://cdn.example.com/gold.jpg"
        assertEquals(hostile, NewsImagePolicy.through(base = "https://api.example.com/news/image", url = hostile))
    }
}
