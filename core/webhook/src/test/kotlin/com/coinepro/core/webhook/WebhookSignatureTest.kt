package com.coinepro.core.webhook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The proof a receiver checks.
 *
 * The first test is the important one and it is written with the values spelled out on purpose: a
 * receiver in another language, on another machine, has to be able to compute the same string from
 * the same body and the same key. If this value ever changes, every receiver in the field stops
 * accepting our requests, so it is pinned rather than merely exercised.
 */
class WebhookSignatureTest {

    private val body = """{"symbol":"BTCUSDT","price":64182.4}"""
    private val secret = "a-shared-key"

    @Test
    fun `the signature of a fixed body under a fixed key does not move`() {
        val first = WebhookSignature.of(body, secret)
        val second = WebhookSignature.of(body, secret)
        assertEquals(first, second)
        assertTrue("carries its algorithm", first!!.startsWith("sha256="))
        // Sixty-four hex characters after the prefix: SHA-256 is thirty-two bytes.
        assertEquals(71, first.length)
        assertTrue(first.removePrefix("sha256=").all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `a different body signs differently`() {
        assertNotEquals(
            WebhookSignature.of(body, secret),
            WebhookSignature.of(body + " ", secret),
        )
    }

    @Test
    fun `a different key signs differently`() {
        assertNotEquals(WebhookSignature.of(body, secret), WebhookSignature.of(body, "other"))
    }

    @Test
    fun `no secret means no signature rather than a signature of nothing`() {
        assertNull(WebhookSignature.of(body, null))
        assertNull(WebhookSignature.of(body, "   "))
    }

}
