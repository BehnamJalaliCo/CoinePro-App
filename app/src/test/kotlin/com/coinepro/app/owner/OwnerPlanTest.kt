package com.coinepro.app.owner

import com.coinepro.core.notifications.NotificationCategory
import com.coinepro.core.notifications.NotificationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerPlanTest {

    private fun message(id: Long, kind: String = "announcement", notify: Boolean = true, high: Boolean = false, ends: Long? = null) =
        OwnerMessage(id, kind, high, "t$id", "b$id", "", "", "", notify, ends)

    @Test
    fun `an offer is marketing and everything else is an announcement`() {
        assertEquals(NotificationCategory.MARKETING, OwnerPlan.categoryOf("offer"))
        listOf("announcement", "update", "maintenance", "alert", "unknown").forEach {
            assertEquals(NotificationCategory.ANNOUNCEMENT, OwnerPlan.categoryOf(it))
        }
    }

    @Test
    fun `allowed campaigns are notified, the rest wait as cards`() {
        val offer = message(1, kind = "offer")
        val news = message(2)
        val quiet = message(3, notify = false)
        val (notify, cards) = OwnerPlan.plan(listOf(offer, news, quiet), emptySet(), 0L, { it.kind != "offer" }, quietNow = false)
        assertEquals(listOf(news), notify)
        assertEquals(listOf(offer, quiet), cards)
    }

    @Test
    fun `settled and ended campaigns appear nowhere`() {
        val (notify, cards) = OwnerPlan.plan(
            listOf(message(1), message(2, ends = 500L), message(3)),
            settled = setOf(3L),
            nowMillis = 1_000L,
            mayNotify = { true },
            quietNow = false,
        )
        assertEquals(listOf(1L), notify.map { it.id })
        assertTrue(cards.isEmpty())
    }

    @Test
    fun `at most two notifications a run, the rest the next run`() {
        val (notify, cards) = OwnerPlan.plan((1L..5L).map { message(it) }, emptySet(), 0L, { true }, quietNow = false)
        assertEquals(OwnerPlan.MAX_NOTIFICATIONS_PER_RUN, notify.size)
        assertEquals(listOf(3L, 4L, 5L), cards.map { it.id })
    }

    @Test
    fun `quiet hours delay a notification rather than turning it into a card`() {
        val (notify, cards) = OwnerPlan.plan(listOf(message(1), message(2, notify = false)), emptySet(), 0L, { false }, quietNow = true)
        assertTrue(notify.isEmpty())
        assertEquals(listOf(2L), cards.map { it.id })
    }

    @Test
    fun `the reader's switches decide, and only an urgent one passes the night`() {
        val settings = NotificationSettings()
        assertTrue(OwnerPlan.allowed(settings, message(1), 0L, 12 * 60, nightQuiet = false))
        // Offers are off until the reader turns them on.
        assertFalse(OwnerPlan.allowed(settings, message(2, kind = "offer"), 0L, 12 * 60, nightQuiet = false))
        assertFalse(OwnerPlan.allowed(settings, message(3), 0L, 2 * 60, nightQuiet = true))
        assertTrue(OwnerPlan.allowed(settings, message(4, high = true), 0L, 2 * 60, nightQuiet = true))
        assertFalse(OwnerPlan.allowed(settings.copy(enabled = false), message(5), 0L, 12 * 60, nightQuiet = false))
    }

    @Test
    fun `the update banner is for installs older than the recommended version`() {
        assertEquals("go", OwnerPlan.nudge(true, "go", 53_000_000, 52_900_000))
        assertNull(OwnerPlan.nudge(true, "go", 53_000_000, 53_000_000))
        assertNull(OwnerPlan.nudge(false, "go", 53_000_000, 52_900_000))
        assertNull(OwnerPlan.nudge(true, "go", 0, 52_900_000))
    }
}
