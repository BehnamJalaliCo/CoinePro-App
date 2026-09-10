package com.coinepro.core.marketintel

import com.coinepro.core.model.MarketPlatform

/**
 * The release build's third parties: none.
 *
 * The `debug/` twin names Investing.com, Cointelegraph and the ForexFactory file; this one
 * names nothing, so the release dex holds no such host and the app has no way to reach one —
 * a section our own routes answer empty stays empty and says so. `DIRECT_THIRD_PARTY_FEEDS`
 * remains the switch a debug build reads; here there is nothing for it to switch.
 */
internal object ThirdPartyWires {

    fun news(platform: MarketPlatform): List<PublicNewsFeed.Feed> = emptyList()

    /** No file, no host. */
    val CALENDAR_URL: String? = null

    const val PRESENT: Boolean = false
}
