package com.coinepro.core.webhook

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The body, the content type, and the delivery record.
 *
 * The content-type rule is the one a receiver notices immediately: a bot parsing JSON gets a
 * sentence, or a chat room gets a brace. It is asserted here over the shapes people actually type.
 */
class WebhookBodyTest {

    @Test
    fun `a json object is announced as json`() {
        val body = """{"side":"buy","symbol":"BTCUSDT","qty":0.5}"""
        assertTrue(WebhookBody.looksLikeJson(body))
        assertEquals(WebhookBody.JSON, WebhookBody.contentTypeOf(body))
    }

    @Test
    fun `a json array is json too`() {
        assertTrue(WebhookBody.looksLikeJson("""[1, 2, {"a": null}, true]"""))
    }

    @Test
    fun `a sentence is text even when it contains a brace`() {
        val body = "طلا به ۲۶۰۰ رسید {مهم}"
        assertFalse(WebhookBody.looksLikeJson(body))
        assertEquals(WebhookBody.TEXT, WebhookBody.contentTypeOf(body))
    }

    @Test
    fun `something that only starts like json is not announced as json`() {
        assertFalse(WebhookBody.looksLikeJson("""{"side":"buy" """))
        assertFalse(WebhookBody.looksLikeJson("""{"side" "buy"}"""))
        assertFalse(WebhookBody.looksLikeJson("""{"side":"buy"} and then some"""))
        assertFalse(WebhookBody.looksLikeJson("{}{}"))
    }

    @Test
    fun `a bare number or string is treated as the text it plainly is`() {
        assertFalse(WebhookBody.looksLikeJson("12"))
        assertFalse(WebhookBody.looksLikeJson("\"hello\""))
    }

    @Test
    fun `the default envelope is valid json and carries the time the alert fired`() {
        val event = WebhookEvent(
            alertId = "alert-1",
            symbol = "XAUUSD",
            firedAt = 1_700_000_000_000L,
            price = 2_614.25,
            timeframe = "1h",
        )
        val composed = event.body
        assertTrue(WebhookBody.looksLikeJson(composed))
        assertEquals(WebhookBody.JSON, event.contentType)
        assertTrue(composed.contains("\"firedAt\":1700000000000"))
        assertTrue(composed.contains("\"symbol\":\"XAUUSD\""))
    }

    @Test
    fun `a message the reader wrote is sent exactly as they wrote it`() {
        val event = WebhookEvent(
            alertId = "alert-1",
            symbol = "XAUUSD",
            firedAt = 1L,
            message = "  طلا به سقف رسید  ",
        )
        assertEquals("طلا به سقف رسید", event.body)
        assertEquals(WebhookBody.TEXT, event.contentType)
    }

    @Test
    fun `an envelope escapes a field that would otherwise break the json it claims to be`() {
        val event = WebhookEvent(alertId = "a\"b", symbol = "X\\Y", firedAt = 1L)
        assertTrue(WebhookBody.looksLikeJson(event.body))
    }
}

/**
 * The stored form of the webhooks and of the delivery log.
 *
 * Two properties matter and both are why the log exists. A **failure is recorded with its reason**,
 * so a reader whose webhook is silent has something to look at; and **decoding cannot throw**,
 * because a history a screen cannot open is worse than no history.
 */
class WebhookStoreCodecTest {

    private val target = WebhookTarget(
        id = "hook-1",
        name = "ربات تلگرام",
        url = "https://hooks.example.com/alerts/9f2",
        secret = "a-shared-key",
        enabled = true,
        createdAt = 1_700_000_000_000L,
    )

    @Test
    fun `a webhook survives a round trip unchanged`() {
        val decoded = WebhookStore.decodeTargets(WebhookStore.encodeTargets(listOf(target)))
        assertEquals(listOf(target), decoded)
    }

    @Test
    fun `a webhook switched off stays switched off`() {
        val off = target.copy(enabled = false)
        val decoded = WebhookStore.decodeTargets(WebhookStore.encodeTargets(listOf(off)))
        assertFalse(decoded.single().enabled)
    }

    @Test
    fun `a field carrying a separator is refused rather than silently rewritten`() {
        assertNull(WebhookStore.encodeTarget(target.copy(secret = "key" + UNIT + "more")))
    }

    @Test
    fun `a failed delivery is recorded with its status, its latency and its reason`() {
        val failure = WebhookAttempt(
            targetId = target.id,
            targetName = target.name,
            alertId = "alert-1",
            at = 1_700_000_000_000L,
            outcome = WebhookOutcome.REJECTED,
            status = 404,
            latencyMillis = 812,
            error = "گیرنده درخواست را نپذیرفت",
        )
        val row = WebhookStore.decodeAttempts(WebhookStore.encodeAttempts(listOf(failure))).single()
        assertEquals(WebhookOutcome.REJECTED, row.outcome)
        assertEquals(404, row.status)
        assertEquals(812L, row.latencyMillis)
        assertEquals("گیرنده درخواست را نپذیرفت", row.error)
        assertFalse(row.delivered)
    }

    @Test
    fun `a delivery record never carries the secret`() {
        val encoded = WebhookStore.encodeAttempts(
            listOf(
                WebhookAttempt(
                    targetId = target.id,
                    targetName = target.name,
                    alertId = "alert-1",
                    at = 2L,
                    outcome = WebhookOutcome.DELIVERED,
                    status = 200,
                    latencyMillis = 91,
                ),
            ),
        )
        assertFalse(encoded.contains(target.secret!!))
    }

    @Test
    fun `nothing a stored string can contain makes decoding throw`() {
        assertTrue(WebhookStore.decodeTargets(null).isEmpty())
        assertTrue(WebhookStore.decodeTargets("").isEmpty())
        assertTrue(WebhookStore.decodeTargets("nonsense").isEmpty())
        assertTrue(WebhookStore.decodeAttempts(listOf("half", "a", "row").joinToString(UNIT)).isEmpty())
        // An outcome from a later build is dropped rather than shown as some other outcome.
        val future = listOf("t", "n", "a", "3", "quantum", "200", "5", "").joinToString(UNIT)
        assertTrue(WebhookStore.decodeAttempts(future).isEmpty())
    }

    private companion object {
        /** The store's own field separator, so a test can build a row the way the store does. */
        const val UNIT = "\u001F"
    }
}

/**
 * What the poster does when it cannot post.
 *
 * No network is touched here, and that is the point: a URL the app will not post to must produce a
 * record saying so rather than a silent skip. The alternative — a target quietly not attempted — is
 * the exact failure this module exists to make visible.
 */
class WebhookPosterTest {

    @Test
    fun `a webhook whose url is no longer acceptable is recorded as blocked, with the reason`() = runTest {
        val poster = WebhookPoster(now = { 1_700_000_000_000L })
        val target = WebhookTarget(
            id = "hook-1",
            name = "ربات",
            url = "https://hooks.example.com:8443/alerts",
            createdAt = 1L,
        )
        val attempt = poster.deliver(target, WebhookEvent("alert-1", "BTCUSDT", firedAt = 5L))
        assertEquals(WebhookOutcome.BLOCKED, attempt.outcome)
        assertEquals(WebhookUrlRefusal.PORT_NOT_ALLOWED.reason, attempt.error)
        assertNull(attempt.status)
        assertEquals("alert-1", attempt.alertId)
        assertNotNull(attempt.targetName)
    }
}
