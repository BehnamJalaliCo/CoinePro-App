package com.coinepro.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The status is the loudest word on a connection card, and the exchange writes it in English.
 */
class VenueStatusPersianTest {

    @Test
    fun `the word the exchange actually sends is Persian on screen`() {
        // What a Persian reader saw at the top of their own connection card, in English.
        assertEquals("در انتظار تأیید صرافی", VenueStatusPersian.label("awaiting_provider_confirmation"))
    }

    @Test
    fun `spelling differences between the two backends land on one word`() {
        assertEquals("متصل", VenueStatusPersian.label("connected"))
        assertEquals("متصل", VenueStatusPersian.label("ACTIVE"))
        assertEquals("حساب ناهم‌خوان", VenueStatusPersian.label("account-mismatch"))
    }

    @Test
    fun `a status this table has never seen still reaches the reader`() {
        // Only the venue knows why it is not connected. Flattening an unknown word into «خطا» would
        // take away the one thing that could tell somebody what to fix.
        assertEquals("margin call pending", VenueStatusPersian.label("margin_call_pending"))
    }

    @Test
    fun `nothing is said where the server said nothing`() {
        assertEquals("", VenueStatusPersian.label(null))
        assertEquals("", VenueStatusPersian.label("   "))
    }
}
