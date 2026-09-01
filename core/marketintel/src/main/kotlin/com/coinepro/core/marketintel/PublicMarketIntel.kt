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

    /**
     * The backend's own stories, with the pictures its route left out put back.
     *
     * ### Why this runs even when the backend answered perfectly well
     *
     * Because on this product "the backend answered" and "the reader got a news screen" have turned
     * out to be different things, and the difference is a `SELECT` list. See
     * [TradeYarPublicNews.media]: the members' route reads the same table as the public one and does
     * not select `source_image_url`, so a signed-in reader gets every story stripped of its
     * illustration while a signed-out one, reading the public route, gets all of them. The screen
     * was designed around the picture. That asymmetry is what «هنوز روی خبرها عکس نیست» describes.
     *
     * So this is *not* a fallback in the sense the rest of this class uses the word. The other
     * sources here answer a section the backend left **empty**; this one fills a field the backend
     * left empty on rows it otherwise answered completely. Hence the different rule: per story
     * rather than per section, and only ever into a null.
     *
     * ### What it costs, and what stops it costing that
     *
     * One extra GET, and only when there is something for it to do. A snapshot in which every story
     * already carries a picture — which is what the day after `docs/SERVER_ASK_NEWS_MEDIA.md` is
     * answered looks like — returns before the request is made, so this becomes dead weight on its
     * own and needs no removing. The same is true of a snapshot with no stories in it at all, where
     * `news()` above has already run and its result is what the reader is looking at.
     *
     * A story whose picture cannot be found is returned exactly as it arrived. Nothing here
     * substitutes a stand-in image, and `NewsHero` draws no placeholder for a null, so a story with
     * no illustration is laid out as a story with no illustration rather than as a broken one.
     */
    suspend fun illustrate(stories: List<MarketNewsItem>): List<MarketNewsItem> {
        if (platform != MarketPlatform.TRADEYAR) return stories
        if (stories.isEmpty() || stories.none { it.imageUrl == null }) return stories
        val base = platformBaseUrl?.takeIf { it.startsWith("https://") } ?: return stories
        val media = TradeYarPublicNews.media(client.get(TradeYarPublicNews.url(base)))
        if (media.isEmpty()) return stories
        // Two indexes rather than one pass per story: the feeds identify a row by slug on one route
        // and by source URL on the other, and thirty stories against thirty rows is nine hundred
        // string comparisons done the naive way for a screen that opens on a scroll.
        val bySlug = media.mapNotNull { row -> row.slug?.let { it to row.imageUrl } }.toMap()
        val byUrl = media.mapNotNull { row -> row.url?.let { it to row.imageUrl } }.toMap()
        return stories.map { story ->
            if (story.imageUrl != null) return@map story
            // The id first: on this backend it *is* the slug, and a slug is exact where a URL can
            // differ by a trailing slash or a tracking parameter the two routes disagree about.
            val found = bySlug[story.id] ?: story.url?.let(byUrl::get) ?: return@map story
            story.copy(imageUrl = found)
        }
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
