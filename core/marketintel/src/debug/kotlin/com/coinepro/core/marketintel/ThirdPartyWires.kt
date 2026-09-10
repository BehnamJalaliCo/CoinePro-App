package com.coinepro.core.marketintel

import com.coinepro.core.model.MarketPlatform

/**
 * The third-party hosts a **debug** build may read when our own routes answer empty.
 *
 * A source set rather than a flag, so the release build carries no such host at all: the
 * `release/` twin of this file names nothing, and a grep of the release dex for
 * `investing.com`, `cointelegraph.com` or `faireconomy.media` finds nothing — the audit in
 * `scripts/quality/check-release-surface.py` fails the build if it ever does. Item 6 of the
 * 4.52 run, finished in 4.65.0; `docs/backend/FEEDS.md` is the contract the backend meets so
 * that the release has nothing to miss.
 */
internal object ThirdPartyWires {

    fun news(platform: MarketPlatform): List<PublicNewsFeed.Feed> = when (platform) {
        MarketPlatform.TRADEYAR -> listOf(
            PublicNewsFeed.Feed("https://www.investing.com/rss/news_301.rss", "Investing.com"),
            PublicNewsFeed.Feed("https://www.cointelegraph.com/rss", "Cointelegraph"),
        )
        MarketPlatform.COINEPRO_FX -> listOf(
            PublicNewsFeed.Feed("https://www.investing.com/rss/news_1.rss", "Investing.com"),
            PublicNewsFeed.Feed("https://www.investing.com/rss/news_11.rss", "Investing.com"),
        )
    }

    /** This week's ForexFactory file, from its own host. */
    const val CALENDAR_URL: String = "https://nfs.faireconomy.media/ff_calendar_thisweek.json"

    /** Whether this build can reach a third party at all. */
    const val PRESENT: Boolean = true
}
