package com.coinepro.core.marketintel

import com.coinepro.core.common.parseWireInstant
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant

/**
 * CoinePro-FX's own newsroom, read from the public academy route.
 *
 * ### Why this exists, measured on 2026-09-02
 *
 * ```
 * GET https://coineprofx.com/api/academy/bn/news → 200, twenty items, Persian, from today
 * ```
 *
 * The route this app was written against answered `{"items":[]}` for a month, so the forex news
 * screen was fed by two English wires — and those wires are on hosts an Iranian handset cannot
 * reach. The members' route behind auth answers 401 to a reader with no forex session, and the
 * screen showed that sentence: «برای این بخش باید وارد حساب خود شوید». Meanwhile the platform was
 * publishing twenty Persian stories a day on a route that needs no token at all, and nothing here
 * read it.
 *
 * ### The shape
 *
 *     {"items":[{"id":"6168","title":"…","summary":"…","url":"https://…",
 *                "image":"None","source":"CoinePro FX","published_at":"2026-09-02T11:25:25+00:00"}]}
 *
 * `image` arrives as the string `None` — Python's null, serialised as text — and is treated as no
 * picture. A story is kept only with a title and a time; everything else is optional.
 */
internal object AcademyPublicNews {

    const val PATH = "academy/bn/news"

    fun url(baseUrl: String): String = baseUrl.trimEnd('/') + "/" + PATH

    fun parse(body: String?, now: Instant): List<MarketNewsItem> {
        if (body.isNullOrBlank()) return emptyList()
        val root = runCatching { JsonParser.parseString(body) }.getOrNull() ?: return emptyList()
        val rows = when {
            root.isJsonArray -> root.asJsonArray.toList()
            root.isJsonObject -> listOf("items", "data", "results", "news")
                .firstNotNullOfOrNull { key -> root.asJsonObject.getAsJsonArray(key)?.toList() }
                .orEmpty()
            else -> emptyList()
        }
        return rows.mapNotNull { row -> story(row, now) }.sortedByDescending(MarketNewsItem::publishedAt)
    }

    private fun story(row: JsonElement, now: Instant): MarketNewsItem? {
        val obj = row.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return null
        val title = obj.text("title", "title_fa", "titleFa") ?: return null
        val published = obj.text("published_at", "publishedAt", "date")?.let(::parseWireInstant) ?: return null
        val summary = obj.text("summary", "summary_fa", "summaryFa")
        return MarketNewsItem(
            id = "fx:" + (obj.text("id", "slug") ?: title),
            title = title,
            summary = summary,
            source = obj.text("source") ?: "CoinePro FX",
            url = obj.text("url", "link"),
            publishedAt = published,
            // Not sent, and not guessed from a headline.
            sentiment = NewsSentiment.UNKNOWN,
            impact = MarketImpact.UNKNOWN,
            relevance = relevance(title, summary),
            isStale = published.isBefore(now.minusSeconds(STALE_AFTER_SECONDS)),
            imageUrl = obj.text("image", "image_url", "imageUrl")
                ?.takeIf { it.startsWith("https://") },
            body = null,
        )
    }

    /**
     * Which metal a story is about, from its own words.
     *
     * The route carries no tag. A silver analysis titled «تحلیل روزانه نقره» must not disappear
     * under the «طلا» chip and a gold one must not appear under «نقره», so the two words are read
     * off the title and summary; a story naming neither — a rate decision, the dollar — moves both
     * and is tagged with both.
     */
    private fun relevance(title: String, summary: String?): Set<MarketRelevance> {
        // The title decides. A silver analysis compares itself to gold in its summary as a
        // matter of course, and that mention must not file it under both metals.
        classify(title)?.let { return it }
        classify(summary.orEmpty())?.let { return it }
        return setOf(MarketRelevance.GOLD, MarketRelevance.SILVER)
    }

    private fun classify(text: String): Set<MarketRelevance>? {
        val silver = SILVER_WORDS.any { it in text }
        val gold = GOLD_WORDS.any { it in text }
        return when {
            silver && !gold -> setOf(MarketRelevance.SILVER)
            gold && !silver -> setOf(MarketRelevance.GOLD)
            else -> null
        }
    }

    private val SILVER_WORDS = listOf("نقره", "XAG", "Silver", "silver")
    private val GOLD_WORDS = listOf("طلا", "XAU", "Gold", "gold")

    private fun JsonObject.text(vararg names: String): String? = names.asSequence()
        .mapNotNull { name -> get(name)?.takeIf { it.isJsonPrimitive }?.asString?.trim() }
        // Python's `None` reaches the wire as text on this route; it is not a value.
        .firstOrNull { it.isNotBlank() && it != "None" && it != "null" }

    private const val STALE_AFTER_SECONDS = 24 * 60 * 60L
}
