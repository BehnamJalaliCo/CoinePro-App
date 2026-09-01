package com.coinepro.core.marketintel

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * The whole of a story's text, fetched when a reader actually opens it.
 *
 * ### What this closes
 *
 * «متن کامل خبر قرار بود ترجمه بشه و به زبان انسانی توی خبر گذاشته بشه» — the full story was
 * supposed to be translated and put inside the app in plain language, and the reading page has
 * been showing the two-line summary under a heading promising the article. Every round of work on
 * that assumed the text did not exist, and `MarketNewsItem.body` says so in as many words:
 * «TradeYar's `news_posts` stores `summary_fa` and no body».
 *
 * That is not true and has not been for some time. `news_posts` has a **`body_fa`** column, the
 * newsroom fills it with a full Persian translation running to a page or more, and the public
 * detail route serves it to anybody:
 *
 *     GET https://tradeyar.trade-future.ir/api/v1/news/{slug}
 *       → 200 {"data":{…,"summaryFa":"…","bodyFa":"بر اساس گزارش‌های تازه، سرمایه‌گذاران …"}}
 *
 * Plain text, paragraphs separated by blank lines, no markup — which is exactly the shape
 * [articleBody] accepts and `newsParagraphs` splits on. The only reason it was never on screen is
 * that nothing in this app had ever asked for it: the *list* route the feed is built from selects
 * `NULL AS body_fa` deliberately, because a list of thirty full articles is a payload nobody reads.
 *
 * ### Why it is fetched per story rather than with the feed
 *
 * Because that is what the list route is telling us by nulling the column. Thirty bodies at a page
 * each is on the order of fifty kilobytes of Persian prose to render three lines of card, on a feed
 * that refreshes on every resume. One story's body, fetched at the moment a reader opens that
 * story, is a few kilobytes on a screen they are about to spend a minute on — and it is fetched
 * once, because the page holds what it got for as long as it is open.
 *
 * ### What it refuses
 *
 * Everything [articleBody] refuses, through the same function rather than a second copy of the
 * rule: blank, a duplicate of the summary already on the page, and anything with markup in it. A
 * Compose `Text` renders `<p>` as four characters, and a story printed with its own tags through it
 * reads as a broken app rather than as a story — so the honest fallback is the summary the page was
 * already setting, well, under a line saying it is the summary.
 */
interface NewsBodySource {

    /**
     * The story's translated text, or null where there is none to be had.
     *
     * [id] is the story's own identifier as the feed sent it, which on this backend is the slug —
     * see `market_intelligence.py`, whose `id` is `str(row.get("slug") or row.get("id"))`. [summary]
     * is passed so a server that answers with a copy of it is refused here rather than printing the
     * same paragraph twice on one page.
     */
    suspend fun body(id: String, summary: String?): String?
}

/** No bodies, for a platform that publishes none and for every test that does not care. */
object NoNewsBodySource : NewsBodySource {
    override suspend fun body(id: String, summary: String?): String? = null
}

/**
 * CoinePro-FX's own article text, from the members' route beside the feed.
 *
 * ### Why it is a second request here too, and why it is a *different* route
 *
 * `articles.content` is a full editorial article — this backend's own newsroom, in Persian, running
 * to a page — and twenty of those in the snapshot every screen refresh is tens of kilobytes of prose
 * to draw twenty cards nobody has opened. So the same split the crypto side already had: the list
 * carries headlines, `user/mobile/news/{id}` carries one story's text when a reader opens it.
 *
 * The column is **HTML**, which is why this route exists at all rather than the app reading the
 * field directly: a Compose `Text` renders `<p>` as four characters, and [articleBody] refuses
 * markup for exactly that reason, so an app-side fix would have been an app-side refusal. The
 * server renders it to plain text — the same thing it already does for the site's markdown twin,
 * minus the markdown — and this reads the result.
 *
 * Unlike the crypto source this one carries a token: the route sits under `user/mobile` with the
 * rest of the members' surface, so it is read through the platform's authenticated client rather
 * than through [PublicFeedClient].
 */
class ForexNewsBodySource private constructor(private val api: NewsArticleApi) : NewsBodySource {

    override suspend fun body(id: String, summary: String?): String? {
        // `articles.id`, which is what `_news_item` puts in the feed's `id`. Anything else is a
        // story from the other backend or from a saved record written by an older build, and the
        // route answers 404 to it — so it is not asked, rather than asked and refused once per
        // opened article.
        val numeric = id.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        val response = runCatching { api.article(numeric) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null
        return articleBody(bodyOf(response.body()), summary)
    }

    companion object {
        fun create(retrofit: Retrofit): NewsBodySource =
            ForexNewsBodySource(retrofit.create(NewsArticleApi::class.java))
    }
}

/**
 * The forex article route, read as JSON.
 *
 * [JsonElement] rather than a typed body for the reason `MarketIntelGateway` gives at length: Gson
 * deserialises it without consulting the field-naming policy, so nothing about what this reads
 * depends on how the converter happens to be configured — which is the mistake that emptied this
 * module's screens twice.
 */
internal interface NewsArticleApi {
    @GET("user/mobile/news/{id}")
    suspend fun article(@Path("id") id: String): Response<JsonElement>
}

/**
 * TradeYar's public detail route, read for `bodyFa`.
 *
 * Public, so it needs no token and works for a signed-out reader on exactly the same terms as a
 * member — which matters, because the guest news screen opens the same reading page and has had the
 * same empty article on it.
 */
class TradeYarNewsBodySource(
    private val client: PublicFeedClient,
    /** This platform's own host. Anything but `https` is refused rather than fetched. */
    private val baseUrl: String,
) : NewsBodySource {

    override suspend fun body(id: String, summary: String?): String? {
        val slug = slugOf(id) ?: return null
        if (!baseUrl.startsWith("https://")) return null
        val response = client.get(baseUrl.trimEnd('/') + "/api/v1/news/" + slug)
        return articleBody(readArticleBody(response), summary)
    }

    private companion object {

        /**
         * The slug, from whatever the feed put in `id`.
         *
         * Two shapes reach here and only one of them is addressable. The members' route sends the
         * slug itself; `TradeYarPublicNews` prefixes its own ids with `tyr:` so they cannot collide
         * with the other backend's, and that prefix is this module's, not the server's — so it comes
         * off before the address is built. A numeric id is refused outright: the route is
         * `/{slug}` and a number would be a request for a story that does not exist, answered 404,
         * once per opened article.
         */
        fun slugOf(id: String): String? {
            val bare = id.removePrefix("tyr:").trim()
            if (bare.isEmpty()) return null
            if (bare.all(Char::isDigit)) return null
            // A slug is a URL path segment and this app builds a URL out of it. Anything that is
            // not what this route's slugs are made of — letters, digits, hyphens — is refused
            // rather than escaped, because a server's identifier that needs escaping is not one.
            if (!bare.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return null
            return bare
        }

    }
}

/**
 * The story's text out of a detail response, under any of the names a backend might send it by.
 *
 * File-level rather than private to one reader because **both** readers use it and the two routes
 * genuinely disagree: TradeYar wraps its row in `{"data": {...}}` and calls the field `bodyFa`;
 * CoinePro-FX answers the bare object and calls it `body`. Naming every spelling costs a line each,
 * and the alternative is the failure this module has already had twice — a screen that is empty
 * behind a clean 200 because the field arrived under a key nobody listed.
 */
internal fun readArticleBody(response: String?): String? {
    if (response.isNullOrBlank()) return null
    val root = runCatching { JsonParser.parseString(response) }.getOrNull() ?: return null
    return bodyOf(root)
}

/** The same read, for a caller that already holds the parsed body. */
internal fun bodyOf(root: JsonElement?): String? {
    val obj = root?.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return null
    val row = obj.getAsJsonObject("data") ?: obj
    return listOf("bodyFa", "body_fa", "body", "contentFa", "content_fa", "content")
        .asSequence()
        .mapNotNull { name -> row.bodyText(name) }
        .firstOrNull()
}

private fun JsonObject.bodyText(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank)
