package com.coinepro.feature.alerts

import com.coinepro.core.webhook.WebhookTarget
import com.coinepro.core.webhook.WebhookUrlRefusal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The webhook sheet's draft: what it will save, and what it refuses.
 *
 * Two things are worth asserting here and nothing else is. **The URL is judged before it is
 * stored**, because a webhook accepted now and refused six hours later fails at the moment a level
 * is finally hit, to somebody who has stopped watching. And **the secret survives an edit without
 * ever being shown**, which is a three-way distinction — untouched, cleared, replaced — that is
 * exactly the kind of thing a screen gets subtly wrong and nobody notices until signatures start
 * failing at the receiver.
 */
class WebhookDraftTest {

    private val existing = WebhookTarget(
        id = "hook-1",
        name = "ربات",
        url = "https://example.com/hook",
        secret = "s3cret",
        enabled = true,
        createdAt = 1_000L,
    )

    @Test
    fun `a name and an acceptable address are enough to save`() {
        val draft = WebhookDraft(name = "ربات", url = "https://example.com/hook")

        assertTrue(draft.valid)
        assertNull(draft.urlRefusal)
    }

    @Test
    fun `a target with no name is refused, because a log entry has to name something`() {
        assertFalse(WebhookDraft(name = "  ", url = "https://example.com/hook").valid)
    }

    @Test
    fun `the address is judged as it is typed, with the reason the module gives`() {
        // The sentence the reader sees comes from `WebhookUrl`, not from this sheet: the rule and
        // the words for it live together, or they drift and somebody is told the wrong reason.
        assertEquals(
            WebhookUrlRefusal.NOT_HTTPS,
            WebhookDraft(name = "a", url = "http://example.com/hook").urlRefusal,
        )
        assertEquals(
            WebhookUrlRefusal.NOT_A_DOMAIN,
            WebhookDraft(name = "a", url = "https://192.168.1.4/hook").urlRefusal,
        )
        assertEquals(
            WebhookUrlRefusal.PORT_NOT_ALLOWED,
            WebhookDraft(name = "a", url = "https://example.com:8080/hook").urlRefusal,
        )
        assertFalse(WebhookDraft(name = "a", url = "http://example.com/hook").valid)
    }

    @Test
    fun `editing a target does not load its secret, and saving without touching the field keeps it`() {
        // The field opens empty on purpose: loading the value would put a credential on a screen,
        // and into whatever a screenshot or a screen reader happens to capture, to save somebody
        // re-typing something they pasted once.
        val draft = WebhookDraft.of(existing)

        assertEquals("", draft.secret)
        assertFalse(draft.secretTouched)

        val saved = draft.toTarget(existing = existing, id = "ignored", nowEpochMillis = 9_000L)

        assertEquals("s3cret", saved?.secret)
    }

    @Test
    fun `clearing the secret field on purpose does remove it`() {
        // The difference between "left alone" and "deliberately emptied", which is what
        // `secretTouched` exists for. Without it, one of the two is impossible to express.
        val cleared = WebhookDraft.of(existing).copy(secret = "", secretTouched = true)

        assertNull(cleared.toTarget(existing = existing, id = "x", nowEpochMillis = 0L)?.secret)
    }

    @Test
    fun `a new secret replaces the old one`() {
        val changed = WebhookDraft.of(existing).copy(secret = " newkey ", secretTouched = true)

        assertEquals("newkey", changed.toTarget(existing = existing, id = "x", nowEpochMillis = 0L)?.secret)
    }

    @Test
    fun `an edit keeps the target's id and the day it was made`() {
        val renamed = WebhookDraft.of(existing).copy(name = "ربات تازه")

        val saved = renamed.toTarget(existing = existing, id = "a-new-id", nowEpochMillis = 9_000L)

        assertEquals("hook-1", saved?.id)
        assertEquals(1_000L, saved?.createdAt)
        assertEquals("ربات تازه", saved?.name)
    }

    @Test
    fun `a new target takes the id and the moment it is given`() {
        val draft = WebhookDraft(name = "شیت", url = "https://example.com/a ")

        val saved = draft.toTarget(existing = null, id = "fresh", nowEpochMillis = 5_000L)

        assertEquals("fresh", saved?.id)
        assertEquals(5_000L, saved?.createdAt)
        assertEquals("https://example.com/a", saved?.url)
        assertNull("a new target with an untouched secret field has none", saved?.secret)
    }

    @Test
    fun `a draft that would be refused builds nothing rather than a target nothing can post to`() {
        val bad = WebhookDraft(name = "a", url = "https://example.com:22/hook")

        assertNull(bad.toTarget(existing = null, id = "x", nowEpochMillis = 0L))
    }

    @Test
    fun `a switched-off target is still saved, so the pasted address is not lost`() {
        val off = WebhookDraft(name = "a", url = "https://example.com/hook", enabled = false)

        val saved = off.toTarget(existing = null, id = "x", nowEpochMillis = 0L)

        assertEquals(false, saved?.enabled)
        assertEquals(false, saved?.deliverable)
    }
}
