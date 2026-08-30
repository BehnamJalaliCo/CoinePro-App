package com.coinepro.feature.news

import com.coinepro.core.marketintel.NewsFeedOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three ways a news feed can be empty, told apart.
 *
 * «اخبار آپدیت نمی‌شود» has now been reported four times, and the reason two rounds of work did not
 * settle it is that the app said the same sentence for causes with opposite fixes. A server that
 * published nothing is their problem; a body this build could not read is ours; and a body that was
 * read but arrived in the wrong order was the one that got fixed. Nothing on the screen separated
 * them, so nothing the owner could send separated them either.
 */
class NewsFeedShortfallTest {

    private fun probe(received: Int, kept: Int) = NewsFeedOutcome(
        route = "api/mobile/v1/market-intelligence",
        status = 200,
        received = received,
        kept = kept,
    )

    @Test
    fun `a body full of rows that produced no stories is unreadable, not empty`() {
        assertTrue(feedUnreadable(probe(received = 30, kept = 0)))
    }

    @Test
    fun `a server that published nothing is not the app's fault and does not claim to be`() {
        assertFalse(feedUnreadable(probe(received = 0, kept = 0)))
        assertNull(feedShortfall(probe(received = 0, kept = 0)))
    }

    @Test
    fun `a feed that mapped cleanly reports no shortfall`() {
        assertFalse(feedUnreadable(probe(received = 20, kept = 20)))
        assertNull(feedShortfall(probe(received = 20, kept = 20)))
    }

    @Test
    fun `rows lost out of a list that still has stories in it are still reported`() {
        // The case with no other symptom whatsoever: the list is full and two thirds of it is gone.
        val shortfall = requireNotNull(feedShortfall(probe(received = 30, kept = 10)))
        assertEquals(10, shortfall.kept)
        assertEquals(30, shortfall.received)
        assertEquals(20, shortfall.dropped)
        // And it is not the unreadable case, which draws an empty screen rather than a strip.
        assertFalse(feedUnreadable(probe(received = 30, kept = 10)))
    }

    @Test
    fun `a build talking to a server that never answered claims nothing either way`() {
        assertFalse(feedUnreadable(null))
        assertNull(feedShortfall(null))
    }
}
