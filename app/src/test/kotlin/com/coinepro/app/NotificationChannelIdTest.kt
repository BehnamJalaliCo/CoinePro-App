package com.coinepro.app

import com.coinepro.app.notifications.NotificationChannels
import com.coinepro.core.notifications.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The channel ids, which are the one part of the notification system that cannot be corrected
 * later.
 *
 * Android takes a channel's importance **only on the call that creates it** and ignores it on every
 * call after — deliberately, so an app cannot raise its own volume once a reader has set it. That
 * makes the id a one-way door: ship the wrong default under an id and every install that has seen
 * it keeps the wrong default for ever, whatever later builds pass.
 *
 * So an id is a released fact. These tests pin the ones this build ships, and they exist to make a
 * change to any of them deliberate — a diff here means "every reader's settings for that category
 * are being reset", which is a decision, not a refactor.
 */
class NotificationChannelIdTest {

    @Test
    fun `every category has a distinct, stable id`() {
        val ids = NotificationCategory.entries.map(NotificationChannels::channelId)
        assertEquals("Two categories share a channel", ids.size, ids.toSet().size)
        ids.forEach { id ->
            assertTrue("'$id' is not in the cat_<id>_v<n> shape", Regex("^cat_[a-z0-9_]+_v\\d+$").matches(id))
        }
    }

    @Test
    fun `the price alert channel is a generation ahead of the rest`() {
        // It shipped at the ordinary default importance, which on Android means it arrives in the
        // shade without a sound — and a price alert is the one notification in the whole product
        // the reader asked for by name. Correcting that needs a new id; this is that id.
        assertEquals("cat_price_alert_v3", NotificationChannels.channelId(NotificationCategory.PRICE_ALERT))
        NotificationCategory.entries
            .filter { it != NotificationCategory.PRICE_ALERT }
            .forEach { category ->
                assertEquals(
                    "${category.id} should still be on v2 — bumping it resets that category's settings",
                    "cat_${category.id}_v2",
                    NotificationChannels.channelId(category),
                )
            }
    }

    @Test
    fun `no channel id collides with the general channel or the legacy one`() {
        val ids = NotificationCategory.entries.map(NotificationChannels::channelId).toSet()
        assertTrue(NotificationChannels.GENERAL !in ids)
        assertNotEquals("market_events", NotificationChannels.GENERAL)
        assertTrue("market_events" !in ids)
    }
}
