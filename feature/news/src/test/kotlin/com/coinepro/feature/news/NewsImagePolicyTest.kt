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
    fun `a publishers oversized hero is halved until it stops dwarfing the card`() {
        // A 2000px wide photograph on a 400px card: four steps of halving still covers it, five
        // would not. The result has to be a power of two, because BitmapFactory silently rounds
        // anything else down to one.
        assertEquals(4, NewsImagePolicy.sampleSize(sourceWidth = 2000, targetWidth = 400))
        assertEquals(2, NewsImagePolicy.sampleSize(sourceWidth = 900, targetWidth = 400))
    }

    @Test
    fun `a picture already at or under the card width is decoded whole`() {
        assertEquals(1, NewsImagePolicy.sampleSize(sourceWidth = 400, targetWidth = 400))
        assertEquals(1, NewsImagePolicy.sampleSize(sourceWidth = 120, targetWidth = 400))
    }

    @Test
    fun `bounds that could not be read do not divide by zero`() {
        assertEquals(1, NewsImagePolicy.sampleSize(sourceWidth = -1, targetWidth = 400))
        assertEquals(1, NewsImagePolicy.sampleSize(sourceWidth = 2000, targetWidth = 0))
    }
}
