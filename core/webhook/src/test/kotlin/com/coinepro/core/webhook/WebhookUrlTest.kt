package com.coinepro.core.webhook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a webhook URL is allowed to be.
 *
 * These are the rules that decide whether a reader finds out at the moment they paste a URL or at
 * three in the morning when an alert fires into nothing. Each assertion below is one of them, and
 * each names the case rather than the rule, because the cases are what actually get pasted.
 */
class WebhookUrlTest {

    @Test
    fun `an ordinary https endpoint is accepted`() {
        assertNull(WebhookUrl.validate("https://hooks.example.com/alerts/9f2"))
    }

    @Test
    fun `the standard https port may be written out`() {
        assertNull(WebhookUrl.validate("https://hooks.example.com:443/alerts"))
    }

    @Test
    fun `any other port is refused, and says so`() {
        assertEquals(
            WebhookUrlRefusal.PORT_NOT_ALLOWED,
            WebhookUrl.validate("https://hooks.example.com:8443/alerts"),
        )
    }

    @Test
    fun `an address is refused where a domain name is required`() {
        assertEquals(
            WebhookUrlRefusal.NOT_A_DOMAIN,
            WebhookUrl.validate("https://192.168.1.40/alerts"),
        )
    }

    @Test
    fun `a host with no domain in it is the same claim as an address`() {
        assertEquals(WebhookUrlRefusal.NOT_A_DOMAIN, WebhookUrl.validate("https://localhost/alerts"))
    }

    @Test
    fun `plain http is refused`() {
        assertEquals(WebhookUrlRefusal.NOT_HTTPS, WebhookUrl.validate("http://hooks.example.com/a"))
    }

    @Test
    fun `something that is not a url at all is refused rather than parsed hopefully`() {
        assertEquals(WebhookUrlRefusal.MALFORMED, WebhookUrl.validate("hooks.example.com/alerts"))
        assertEquals(WebhookUrlRefusal.EMPTY, WebhookUrl.validate("   "))
    }

    @Test
    fun `a refused url can never be delivered to`() {
        val target = WebhookTarget(
            id = "t1",
            name = "ربات",
            url = "https://hooks.example.com:8443/alerts",
            createdAt = 1L,
        )
        assertFalse(target.deliverable)
        assertTrue(target.copy(url = "https://hooks.example.com/alerts").deliverable)
    }

    @Test
    fun `every refusal carries a sentence the reader can act on`() {
        WebhookUrlRefusal.entries.forEach { refusal ->
            assertTrue(refusal.name, refusal.reason.isNotBlank())
        }
    }
}
