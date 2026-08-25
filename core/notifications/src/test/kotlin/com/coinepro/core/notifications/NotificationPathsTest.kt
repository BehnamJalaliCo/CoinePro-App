package com.coinepro.core.notifications

import com.coinepro.core.model.MarketPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Push, notifications and alerts, pinned per platform.
 *
 * This is the surface the mistake was found on first: the app called `user/signals/mobile/alerts`
 * for a long time and nothing there existed. It was invisible because a wrong address answers with
 * an ordinary HTTP error, inside one feature, worded like an outage.
 */
class NotificationPathsTest {
    private val forex = NotificationPaths.of(MarketPlatform.COINEPRO_FX)
    private val crypto = NotificationPaths.of(MarketPlatform.TRADEYAR)

    private fun NotificationPaths.all() = setOf(
        devices, preferences, notifications, markRead, alerts, alert("7"),
    )

    @Test
    fun `no address is shared between the two backends`() {
        assertEquals("Both must expose the same six", forex.all().size, crypto.all().size)
        assertTrue(forex.all().intersect(crypto.all()).isEmpty())
    }

    @Test
    fun `CoinePro-FX groups all three under user slash mobile`() {
        assertEquals("user/mobile/push/devices", forex.devices)
        assertEquals("user/mobile/notifications/read", forex.markRead)
        assertEquals("user/mobile/alerts/7", forex.alert("7"))
        assertTrue(forex.all().all { it.startsWith("user/mobile/") })
    }

    @Test
    fun `TradeYar mounts them as siblings inside its mobile prefix`() {
        assertEquals("api/mobile/v1/push/preferences", crypto.preferences)
        assertEquals("api/mobile/v1/notifications", crypto.notifications)
        assertEquals("api/mobile/v1/alerts/7", crypto.alert("7"))
        assertTrue(crypto.all().all { it.startsWith("api/mobile/v1/") })
    }

    @Test
    fun `an alert id is placed in the path, never left as a template`() {
        // A literal `{alertId}` reaching the wire is a 404 that looks like a missing alert.
        assertTrue(forex.alert("42").endsWith("/42"))
        assertTrue(crypto.alert("42").endsWith("/42"))
    }
}
