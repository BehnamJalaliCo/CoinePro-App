package com.coinepro.app.alerts

import com.coinepro.app.CoineProDeepLink
import com.coinepro.app.parseCoineProDeepLink
import com.coinepro.core.notifications.AlertChannel
import com.coinepro.core.notifications.AlertSound
import com.coinepro.app.notifications.NotificationChannels
import com.coinepro.app.notifications.priceAlertChannelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a fired alert's notification goes, and how loudly it arrives.
 *
 * Two things that had no test and no reachable behaviour. The link went to `coinepro://activity` —
 * the list of everything the app has ever said — so somebody woken at four in the morning by an
 * alert they set landed on a feed and still had to find the chart. And the loud notification
 * channel was posted to by nothing at all, because no control could push an alert's level past the
 * threshold that selects it.
 */
class AlertDeepLinkTest {

    @Test
    fun `the link goes to the chart for the symbol that fired`() {
        assertEquals("coinepro://market/BTCUSDT?tf=H1", AlertDeepLink.chart("BTCUSDT", "H1"))
    }

    @Test
    fun `it is not the activity list any more`() {
        // The whole product thesis: alert fires, the chart opens on the right symbol at the right
        // timeframe, twenty seconds, decide. The activity list is three steps from the decision.
        assertNotEquals("coinepro://activity", AlertDeepLink.chart("BTCUSDT", "H1"))
        assertTrue(AlertDeepLink.chart("BTCUSDT", "H1").startsWith("coinepro://market/"))
    }

    @Test
    fun `a pair's slash is encoded, because the parser takes exactly one path segment`() {
        // Raw, `XAU/USD` is two segments and `parseCoineProDeepLink` refuses it outright — that
        // refusal is deliberate, so nothing can smuggle a path through this host.
        assertEquals("coinepro://market/XAU%2FUSD?tf=D1", AlertDeepLink.chart("XAU/USD", "D1"))
    }

    @Test
    fun `the app's own parser reads back the symbol the notification was built with`() {
        // The link is only worth anything if the thing that receives it agrees. Decoded the way
        // Android decodes a path segment: one segment, percent-decoded.
        val decoded = "XAU%2FUSD".replace("%2F", "/")

        assertEquals(
            CoineProDeepLink.Market("XAU/USD"),
            parseCoineProDeepLink(scheme = "coinepro", host = "market", pathSegments = listOf(decoded)),
        )
    }

    @Test
    fun `a symbol is upper-cased on the way out, as every symbol in this app is`() {
        assertEquals("coinepro://market/BTCUSDT?tf=M15", AlertDeepLink.chart("btcusdt", "M15"))
    }

    @Test
    fun `no timeframe leaves the query off rather than writing an empty one`() {
        // An absent parameter and an empty one must not be two spellings of the same thing.
        assertEquals("coinepro://market/BTCUSDT", AlertDeepLink.chart("BTCUSDT", null))
        assertEquals("coinepro://market/BTCUSDT", AlertDeepLink.chart("BTCUSDT", "   "))
    }

    @Test
    fun `an alert the reader made loud is posted to the alarm channel`() {
        val channels = setOf(AlertChannel.PUSH, AlertChannel.SOUND)

        assertEquals(
            NotificationChannels.PRICE_ALERT_LOUD,
            priceAlertChannelId(channels, AlertSound.MAX_LEVEL),
        )
    }

    @Test
    fun `the default level is not loud, so an ordinary alert stays on the ordinary channel`() {
        // This is the assertion that would have caught a release in which every alert sat at the
        // default and the loud channel was never posted to: the two answers must differ.
        val channels = setOf(AlertChannel.PUSH, AlertChannel.SOUND)
        val ordinary = priceAlertChannelId(channels, AlertSound.DEFAULT_LEVEL)

        assertFalse(AlertSound.isLoud(AlertSound.DEFAULT_LEVEL))
        assertNotEquals(NotificationChannels.PRICE_ALERT_LOUD, ordinary)
    }

    @Test
    fun `loudness without sound does not reach the alarm channel`() {
        // Loud is a property of a sound. An alert with sound switched off and the level at maximum
        // has asked for a silent notification, and playing it on the alarm output would be the app
        // overruling the one thing the reader said plainly.
        assertEquals(
            NotificationChannels.PRICE_ALERT_VIBRATE,
            priceAlertChannelId(setOf(AlertChannel.PUSH, AlertChannel.VIBRATE), AlertSound.MAX_LEVEL),
        )
    }
}
