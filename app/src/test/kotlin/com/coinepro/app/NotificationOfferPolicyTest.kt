package com.coinepro.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 5.27.0: offered on opening, once a day, until notifications are on — as TradingView does. */
class NotificationOfferPolicyTest {

    @Test
    fun `offered once a day until enabled, never after «do not ask again»`() {
        assertTrue(NotificationOfferPolicy.shouldOffer(enabled = false, never = false, lastOfferDay = null, today = 5))
        assertTrue(NotificationOfferPolicy.shouldOffer(enabled = false, never = false, lastOfferDay = 4, today = 5))
        assertFalse("twice in one day", NotificationOfferPolicy.shouldOffer(enabled = false, never = false, lastOfferDay = 5, today = 5))
        assertFalse("already on", NotificationOfferPolicy.shouldOffer(enabled = true, never = false, lastOfferDay = null, today = 5))
        assertFalse("declined for good", NotificationOfferPolicy.shouldOffer(enabled = false, never = true, lastOfferDay = null, today = 5))
    }
}
