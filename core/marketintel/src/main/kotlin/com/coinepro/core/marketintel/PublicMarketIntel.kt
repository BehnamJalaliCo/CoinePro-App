package com.coinepro.core.marketintel

import com.coinepro.core.model.MarketPlatform
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * News and the economic calendar, from public sources, for a section the backend left empty.
 *
 * ### Why this exists at all
 *
 * The reader has reported an empty news screen and an empty calendar **five times**. Each earlier
 * round found a real bug in this app and fixed it — a Gson default that `Unsafe` skipped, a
 * `published_at` the other backend spells `publishedAt` — and each time the screen stayed empty,
 * because the bugs were real and were not the reason.
 *
 * The reason, measured with `curl` against the live hosts:
 *
 *     GET https://coineprofx.com/api/academy/bn/news      → 200  {"items":[]}
 *     GET https://coineprofx.com/api/academy/bn/calendar  → 200  {"items":[]}
 *
 * Both are public, both are documented to be filled by a `news-worker`, and that worker has never
 * been deployed. There is no app-side fix for a source with nothing in it, and there was never
 * going to be one. Five rounds of looking for one is the cost of not having established that first.
 *
 * ### The rule
 *
 * **Per section, and only when empty.** The backend is asked first, always. If it returns news, its
 * news is shown and none of this runs; the same for the calendar, independently — a server that
 * serves stories and no events gets its stories kept and only the calendar filled in. When either
 * server starts publishing, its own Persian wins on the next refresh with nothing to undo here.
 *
 * That is also why this is not a "mode" or a setting. A switch a reader can throw is a switch that
 * is in the wrong position on the day the server comes back.
 *
 * ### What it costs
 *
 * Three HTTPS GETs to third parties — two newswires and one calendar file — carrying no
 * identifier, no token and nothing about the reader beyond the request itself. The privacy policy
 * names the hosts, because a reader is entitled to know which servers their phone talks to and this
 * app cannot claim it only talks to its own.
 */
class PublicMarketIntel(
    private val client: PublicFeedClient,
    private val platform: MarketPlatform,
    /**
     * This platform's own host, so its public routes can be asked before any third party's.
     *
     * Null leaves only the wires, which is what a build with no configured backend has. On TradeYar
     * this is where the good answer comes from — see [TradeYarPublicNews]: Persian headlines,
     * Persian summaries and pictures, published to anybody, on the same host that answers 401 to
     * the members' route. CoinePro-FX publishes no equivalent (`api/v1/news/list` is a 404 there),
     * so on the forex side this is skipped and the wires are the answer.
     */
    private val platformBaseUrl: String? = null,
    private val now: () -> Instant = Instant::now,
) {

    /**
     * Stories, best source first.
     *
     * The order is the order of authority, and it is the same argument as everywhere else in this
     * module: **this product's own Persian beats a third party's English**, always. So TradeYar's
     * public route is asked first and, when it answers, nothing else is. The wires exist for the
     * platform that has no such route and for the day that one is down.
     */
    suspend fun news(): List<MarketNewsItem> {
        ownRoute()?.takeIf { it.isNotEmpty() }?.let { return it }
        return wires()
    }

    private suspend fun ownRoute(): List<MarketNewsItem>? {
        if (platform != MarketPlatform.TRADEYAR) return null
        val base = platformBaseUrl?.takeIf { it.startsWith("https://") } ?: return null
        return TradeYarPublicNews.parse(client.get(TradeYarPublicNews.url(base)), now())
    }

    private suspend fun wires(): List<MarketNewsItem> = coroutineScope {
        val moment = now()
        PublicNewsFeed.feeds(platform)
            // Concurrently: they are independent hosts and the slowest of two in parallel is a
            // second, where one after the other is two. The news screen is the one the reader has
            // been staring at, and it should not feel like a queue.
            .map { feed -> async { PublicNewsFeed.parse(client.get(feed.url), feed, platform, moment) } }
            .flatMap { it.await() }
            .let(PublicNewsFeed::merge)
    }

    /**
     * This week's economic events, in Persian, ascending.
     *
     * The same list on both platforms, and that is correct rather than lazy: a Fed decision moves
     * the dollar, the dollar is one side of every metal quote *and* of every USDT pair, and a crypto
     * reader who is not shown CPI day is being kept from the single most consequential hour of their
     * week. The relevance tag still says metals — see [PublicCalendarFeed] — so any screen that
     * filters by instrument keeps its own judgement.
     */
    suspend fun calendar(): List<EconomicEvent> =
        PublicCalendarFeed.parse(client.get(PublicCalendarFeed.URL), now())
}
