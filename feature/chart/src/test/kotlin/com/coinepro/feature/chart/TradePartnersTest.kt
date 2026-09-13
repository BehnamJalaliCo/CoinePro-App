package com.coinepro.feature.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three venues, and the one thing about them that can silently stop being true: where the
 * button goes.
 *
 * A partner card is the only place in this app where a tap leaves for somebody else's site *and*
 * the owner is paid for it. Both halves fail quietly. A link that lost its tracking still opens a
 * working registration page, so nobody notices until a month's introductions are missing; a link
 * that gained a stray parameter still looks right in a diff. Neither is visible in a screenshot,
 * which is why it is asserted here rather than trusted to a frame.
 */
class TradePartnersTest {

    private fun partner(id: String) = TRADE_PARTNERS.single { it.id == id }

    @Test
    fun `the broker's button opens the owner's own introducing-broker link`() {
        // Handed over by the owner verbatim. OneRoyal's IB programme issues an address rather than
        // a code, so this is the whole link and not a page with something appended — see
        // `TradePartner.referralLink`.
        assertEquals("https://vc.cabinet.oneroyal.com/fa/links/go/16669", partner("oneroyal").url)
    }

    @Test
    fun `the tracking link is taken whole, with nothing appended to it`() {
        // The failure this is written against: somebody adds the referral *code* field later, and
        // the url builder helpfully appends `?ib=` to an address that already identifies the
        // introducer. The venue would read the first and ignore the second, or worse.
        val broker = partner("oneroyal")
        assertEquals(broker.referralLink, broker.url)
        assertTrue("the link carries an appended parameter", "?" !in broker.url && "&" !in broker.url)
    }

    @Test
    fun `a venue with no code still ships a link that opens`() {
        // The honest default the file describes: blank credits nobody and still sends the reader
        // where they were going. It must never be an empty string or a page with a dangling `=`.
        listOf("lbank", "ourbit").forEach { id ->
            val venue = partner(id)
            assertEquals("$id sends the reader somewhere other than its own page", venue.signUp, venue.url)
            assertTrue("$id does not open over https", venue.url.startsWith("https://"))
        }
    }

    @Test
    fun `every partner opens over https and names itself`() {
        TRADE_PARTNERS.forEach { venue ->
            assertTrue("${venue.id} does not open over https", venue.url.startsWith("https://"))
            assertTrue("${venue.id} has no name", venue.name.isNotBlank())
        }
    }
}
