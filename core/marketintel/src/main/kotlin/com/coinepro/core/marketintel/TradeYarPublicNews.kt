package com.coinepro.core.marketintel

import com.coinepro.core.common.parseWireInstant
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant

/**
 * TradeYar's own public news route — Persian, with pictures, and live.
 *
 * ### Why this is first among the fallbacks
 *
 * `api/mobile/v1/market-intelligence` is behind auth and, for this reader, empty. But the same host
 * publishes `api/v1/news/list` to anybody, and a plain `curl` returns thirty rows of exactly what
 * the reader has been asking for:
 *
 *     {"data":[{"id":35377,"source":"decrypt",
 *               "sourceUrl":"https://decrypt.co/376473/…",
 *               "sourceImageUrl":"https://cdn.decrypt.co/…/money-bitcoin.jpg",
 *               "originalTitleEn":"Bitcoin Open Interest Collapses to 12%…",
 *               "titleFa":"ریزش ۱۲ درصدی بهره باز بیت‌کوین؛ …",
 *               "summaryFa":"…","tags":["bitcoin","futures"],
 *               "importance":6,"publishedAt":"2026-08-30T19:38:45+00:00"}], "meta":{…}}
 *
 * Persian headline, Persian summary, a picture, an importance score and a real timestamp. That is
 * this product's own newsroom, already translated, and it beats an English wire on every axis. So
 * for TradeYar the order is: the members' route, then **this**, then the wires — and the wires are
 * only ever reached if this host is unreachable altogether.
 *
 * The guest news screen has been reading this route all along, which is why a signed-out reader saw
 * stories and a signed-in one saw nothing. That asymmetry is the whole shape of the bug.
 *
 * ### camelCase, and it matters
 *
 * `publishedAt`, `titleFa`, `sourceUrl`, `sourceImageUrl`. `MarketIntelGateway`'s KDoc records what
 * happened last time this was assumed to be snake case: every row parsed with a null date, every row
 * was dropped, and the screen was empty behind a clean 200. Both spellings are accepted here for
 * that reason and not out of superstition.
 */
internal object TradeYarPublicNews {

    /**
     * Thirty, which is what the screen can show before a reader stops scrolling.
     *
     * The route caps at its own limit anyway; asking for more would be asking the server to do work
     * for rows nobody reads.
     */
    const val PATH = "api/v1/news/list?limit=30"

    /** Where to ask, given the host the app was built against. */
    fun url(baseUrl: String): String = baseUrl.trimEnd('/') + "/" + PATH

    /**
     * One row's picture, under both of the names the members' feed could match it by.
     *
     * See [media] for why this exists at all.
     */
    internal data class Illustration(val slug: String?, val url: String?, val imageUrl: String)

    /**
     * The pictures this route is publishing, addressed by slug and by source URL.
     *
     * ### Why the members' feed needs a second request to show a picture
     *
     * Because the two routes read the same table and select different columns. `news_posts` holds
     * `source_image_url` and the public `api/v1/news/list` returns it as `sourceImageUrl` — every
     * row, live, today. The members' `api/mobile/v1/market-intelligence` does not name that column
     * in its `SELECT` at all, so a signed-in reader is served the same stories with the pictures
     * removed, on a screen that was built around having one. A guest sees the illustrated feed and
     * a member does not, which is the exact shape the owner reported: «هنوز روی خبرها عکس نیست».
     *
     * The server side of that is one column in one query and is asked for in
     * `docs/SERVER_ASK_NEWS_MEDIA.md`. This is the half that does not need a deployment: the public
     * route is already open, already fast, and already fetched on this platform for the guest feed,
     * so one more read of it fills in what the members' route left out. The day the column is added
     * every story arrives with a picture and [illustrate] finds nothing left to do — it fills gaps
     * and never overwrites, so the server's own answer always wins and there is nothing here to
     * undo.
     *
     * ### Two keys, because the feeds disagree about identity
     *
     * The members' route sets `id` to the slug and `url` to `source_url`; the public route sends
     * both under their own names. Matching on the slug is exact where it is present, and the source
     * URL is the fallback for a row whose id arrived as the numeric primary key instead — which is
     * what `str(row.get("slug") or row.get("id"))` produces for any row with no slug.
     */
    fun media(body: String?): List<Illustration> {
        val rows = rowsOf(body)
        return rows.mapNotNull { row ->
            val obj = row.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
            val image = obj.text("sourceImageUrl", "source_image_url", "imageUrl", "image_url")
                ?.takeIf { it.startsWith("https://") }
                ?: return@mapNotNull null
            Illustration(
                slug = obj.text("slug", "id"),
                url = obj.text("sourceUrl", "source_url", "url"),
                imageUrl = image,
            )
        }
    }

    fun parse(body: String?, now: Instant): List<MarketNewsItem> {
        val rows = rowsOf(body)
        return rows.mapNotNull { row -> story(row, now) }
            .sortedByDescending(MarketNewsItem::publishedAt)
    }

    /** The array, whichever envelope it arrived in, or empty for a body that is not one. */
    private fun rowsOf(body: String?): List<JsonElement> {
        if (body.isNullOrBlank()) return emptyList()
        val root = runCatching { JsonParser.parseString(body) }.getOrNull() ?: return emptyList()
        return when {
            root.isJsonArray -> root.asJsonArray.toList()
            root.isJsonObject -> root.asJsonObject.rows()
            else -> emptyList()
        }
    }

    /** `data` is what this route uses; the other two are what its siblings on the same host use. */
    private fun JsonObject.rows(): List<JsonElement> =
        listOf("data", "items", "results")
            .firstNotNullOfOrNull { key -> getAsJsonArray(key)?.toList() }
            .orEmpty()

    private fun story(row: JsonElement, now: Instant): MarketNewsItem? {
        val obj = row.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return null
        // Persian first, and the English original only as a fallback: a reader who opened a Persian
        // app is owed the Persian headline where the server took the trouble to write one.
        val title = obj.text("titleFa", "title_fa", "title", "originalTitleEn", "original_title_en")
            ?: return null
        val published = obj.text("publishedAt", "published_at", "sourcePublishedAt", "source_published_at")
            ?.let(::parseWireInstant)
            ?: return null
        return MarketNewsItem(
            id = "tyr:" + (obj.text("id", "slug") ?: title),
            title = title,
            summary = obj.text("summaryFa", "summary_fa", "summary"),
            // The upstream publisher, not «TradeYar» — the row names `decrypt`, `theblock` and the
            // rest, and passing that on is both accurate and the more useful thing to read.
            source = obj.text("source") ?: "TradeYar",
            url = obj.text("sourceUrl", "source_url", "url"),
            publishedAt = published,
            // Not sent, and not inferred. See `PublicNewsFeed` — a sentiment guessed from a headline
            // is the most dangerous number this app could print.
            sentiment = NewsSentiment.UNKNOWN,
            impact = importance(obj.number("importance")),
            relevance = setOf(MarketRelevance.CRYPTO),
            isStale = published.isBefore(now.minusSeconds(STALE_AFTER_SECONDS)),
            imageUrl = obj.text("sourceImageUrl", "source_image_url", "imageUrl", "image_url")
                ?.takeIf { it.startsWith("https://") },
            // A summary, not an article. The route serves no body and inventing one from the summary
            // would put the same words on the card and on the page behind it.
            body = null,
        )
    }

    /**
     * The server's own zero-to-ten score, as the app's three levels.
     *
     * Thresholds rather than a scale, because the app has three words for this and the server has
     * eleven numbers. Seven and up is the story a reader would want woken for; four and up is worth
     * reading; below that is the wire's ordinary traffic.
     */
    private fun importance(score: Double?): MarketImpact = when {
        score == null -> MarketImpact.UNKNOWN
        score >= 7 -> MarketImpact.HIGH
        score >= 4 -> MarketImpact.MEDIUM
        else -> MarketImpact.LOW
    }

    private fun JsonObject.text(vararg names: String): String? = names.asSequence()
        .mapNotNull { name -> get(name)?.takeIf { it.isJsonPrimitive }?.asString }
        .firstOrNull { it.isNotBlank() }

    private fun JsonObject.number(name: String): Double? =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble }.getOrNull() }

    private const val STALE_AFTER_SECONDS = 24 * 60 * 60L
}
