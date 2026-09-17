package com.coinepro.feature.chart

import com.coinepro.core.common.FeatureFlags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Which venues a build offers an account with** (run ΤΦΥ, F5 and F6).
 *
 * «خانه‌ی تحلیل کریپتو — با یک نگاه به طلا و دلار». With forex trading off, the introducing-broker
 * card and the second exchange are **gone from the sheet**, not dimmed in it — a greyed card is an
 * advertisement for an account the reader cannot open.
 *
 * LBank stays either way, and that is F6 rather than an oversight: it is where this app's crypto
 * prices come from and where its crypto orders go, so it is the venue rather than a partner listing
 * that happens to be switched on.
 *
 * Both states are driven, because the half that is off in a shipping build is the half that rots.
 */
class TradePartnersFlagTest {

    @After
    fun restore() = FeatureFlags.reset()

    @Test
    fun `a shipping build offers the crypto venue and nothing else`() {
        FeatureFlags.reset()
        assertEquals(listOf("lbank"), tradePartners().map { it.id })
    }

    @Test
    fun `with forex trading on, all three come back exactly as they were`() {
        FeatureFlags.forexTrading = true
        assertEquals(TRADE_PARTNERS, tradePartners())
        assertEquals(listOf("oneroyal", "lbank", "ourbit"), tradePartners().map { it.id })
        // And the links are untouched by the flag — it decides which cards exist, never what a
        // card does. A flag that quietly rewrote a referral link would cost the owner the month.
        assertEquals(TRADE_PARTNERS.map { it.url }, tradePartners().map { it.url })
    }

    @Test
    fun `the broker is the one that goes, and it is a broker`() {
        FeatureFlags.forexTrading = true
        val broker = tradePartners().single { it.kind == TradePartnerKind.BROKER }
        assertEquals("oneroyal", broker.id)
        FeatureFlags.forexTrading = false
        assertTrue("a broker survived a build with no forex account in it", tradePartners().none {
            it.kind == TradePartnerKind.BROKER
        })
    }
}
