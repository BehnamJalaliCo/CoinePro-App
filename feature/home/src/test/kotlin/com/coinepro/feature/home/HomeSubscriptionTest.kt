package com.coinepro.feature.home

import com.coinepro.core.auth.EntitlementSnapshot
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSubscriptionTest {
    private val now: Instant = Instant.parse("2026-08-25T12:00:00Z")

    @Test
    fun `an account with no plan gets no card rather than an offer`() {
        assertNull(entitlement(isPaid = false, isVip = false).toHomeSubscription(now))
    }

    @Test
    fun `a paid plan reports its own name and what is left of it`() {
        val subscription = entitlement(
            isPaid = true,
            plan = "monthly",
            expiresAt = "2026-09-24T12:00:00Z",
        ).toHomeSubscription(now)!!

        assertEquals("monthly", subscription.planLabel)
        assertEquals(30, subscription.daysRemaining)
        assertFalse("A month out is not something to warn about", subscription.endingSoon)
    }

    @Test
    fun `a plan inside its last week is marked as ending soon`() {
        val subscription = entitlement(
            isPaid = true,
            expiresAt = "2026-08-29T12:00:00Z",
        ).toHomeSubscription(now)!!

        assertEquals(4, subscription.daysRemaining)
        assertTrue(subscription.endingSoon)
    }

    @Test
    fun `a vip account without a paid flag still has a subscription`() {
        assertTrue(entitlement(isPaid = false, isVip = true).toHomeSubscription(now) != null)
    }

    @Test
    fun `a plan the server still honours survives an expiry this device thinks has passed`() {
        val subscription = entitlement(
            isPaid = true,
            expiresAt = "2026-08-01T12:00:00Z",
        ).toHomeSubscription(now)

        assertTrue(
            "Only the server decides a plan is over; a fast device clock must not end one",
            subscription != null,
        )
        assertNull("A date already gone is not a countdown", subscription!!.daysRemaining)
    }

    @Test
    fun `an unreadable or absent expiry simply goes unmentioned`() {
        val garbled = entitlement(isPaid = true, expiresAt = "next tuesday").toHomeSubscription(now)!!
        assertNull(garbled.expiresLabel)
        assertNull(garbled.daysRemaining)

        val none = entitlement(isPaid = true, expiresAt = null).toHomeSubscription(now)!!
        assertNull(none.expiresLabel)
    }

    @Test
    fun `a plan the server did not name draws nothing, since the app has no name for it`() {
        assertNull(entitlement(isPaid = true, plan = "  ").toHomeSubscription(now))
    }

    private fun entitlement(
        isPaid: Boolean = false,
        isVip: Boolean = false,
        plan: String = "monthly",
        expiresAt: String? = null,
    ) = EntitlementSnapshot(
        isVip = isVip,
        isPaid = isPaid,
        panelAllowed = true,
        panelState = "buy",
        plan = plan,
        expiresAt = expiresAt,
    )
}
