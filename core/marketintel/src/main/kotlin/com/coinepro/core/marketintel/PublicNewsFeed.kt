package com.coinepro.core.marketintel

import com.coinepro.core.model.MarketPlatform
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Market news, read from public wires when the backend sends none.
 *
 * ### The honest position on language
 *
 * These headlines arrive in English. The backend was supposed to serve them in Persian —
 * TradeYar stores `summary_fa`, and CoinePro-FX's `academy/bn/news` is documented as
 * «ترجمه/خلاصه‌شده به فارسی توسطِ news-worker» — and both answer with nothing. Translating a live
 * newswire is not something an offline app can do, and inventing a Persian headline that does not
 * match the story it links to would be worse than showing the English one.
 *
 * So the rule here is: **the backend's Persian always wins.** This runs only when the backend sent
 * an empty list, the source is named on every row so nobody mistakes where it came from, and the
 * day either server starts publishing, this stops being reached. The alternative on offer is the
 * screen the reader has now reported five times, which says nothing in any language.
 *
 * The calendar is a different case and is fully Persian — see [CalendarPersian]. Its vocabulary is
 * closed; a newswire's is not.
 *
 * ### Why RSS, and why parsed with the JDK
 *
 * RSS because it is what these publishers serve without a key, a quota or a contract. Parsed with
 * `javax.xml.parsers` because it is in both the Android runtime and the JVM the unit tests run on,
 * so the fixtures under `src/test/resources` are parsed by exactly the code the device runs — an
 * `XmlPullParser` would have made this device-only and therefore untested.
 */
internal object PublicNewsFeed {

    /**
     * The wires, per backend.
     *
     * Two each, and they are not interchangeable. A reader on the crypto side gets crypto; a reader
     * on the forex side gets currencies and the commodities that trade against them. Merging the two
     * would put a token listing in front of somebody watching gold, which is the same wrong answer
     * the platform split exists to prevent.
     *
     * Ordered by how quickly each publishes, because [merge] keeps the newest and a tie is broken by
     * position: the wire that is usually first stays first.
     */
    fun feeds(platform: MarketPlatform): List<Feed> = when (platform) {
        MarketPlatform.TRADEYAR -> listOf(
            Feed("https://www.investing.com/rss/news_301.rss", "Investing.com"),
            Feed("https://www.cointelegraph.com/rss", "Cointelegraph"),
        )
        MarketPlatform.COINEPRO_FX -> listOf(
            Feed("https://www.investing.com/rss/news_1.rss", "Investing.com"),
            Feed("https://www.investing.com/rss/news_11.rss", "Investing.com"),
        )
    }

    data class Feed(val url: String, val source: String)

    /**
     * The stories in one feed body, newest first.
     *
     * [limit] is applied per feed rather than after merging, so one prolific wire cannot crowd the
     * other off the screen. Two feeds of ten is a fuller picture than twenty of one.
     */
    fun parse(body: String?, feed: Feed, platform: MarketPlatform, now: Instant, limit: Int = 15): List<MarketNewsItem> {
        if (body.isNullOrBlank()) return emptyList()
        val items = runCatching { elements(body) }.getOrNull().orEmpty()
        return items
            .mapNotNull { item -> story(item, feed, platform, now) }
            .sortedByDescending(MarketNewsItem::publishedAt)
            .take(limit)
    }

    /** Everything from every feed, newest first, with the same story from two wires kept once. */
    fun merge(stories: List<MarketNewsItem>, limit: Int = 30): List<MarketNewsItem> = stories
        .sortedByDescending(MarketNewsItem::publishedAt)
        // On the headline rather than the link: the same wire story is syndicated under different
        // tracking parameters, so the addresses differ and the words do not.
        .distinctBy { it.title.trim().lowercase() }
        .take(limit)

    private fun elements(body: String): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // A feed is a document fetched from a third party. Nothing in RSS needs an external
            // entity, and a parser that will resolve one is a parser that can be pointed at a file
            // on this device by whoever controls the feed.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
            isNamespaceAware = false
        }
        val document = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(body.toByteArray(StandardCharsets.UTF_8)))
        val nodes = document.getElementsByTagName("item")
        // Atom, for the feeds that serve it instead. Same three fields under different names.
        val entries = if (nodes.length > 0) nodes else document.getElementsByTagName("entry")
        return (0 until entries.length).mapNotNull { index -> entries.item(index) as? Element }
    }

    private fun story(item: Element, feed: Feed, platform: MarketPlatform, now: Instant): MarketNewsItem? {
        val title = item.text("title")?.trim()?.takeIf(String::isNotBlank) ?: return null
        val published = item.text("pubDate", "published", "updated", "dc:date")?.let(::instantOrNull)
            ?: return null
        val link = item.text("link")?.trim()?.takeIf(String::isNotBlank)
            ?: item.attributeOf("link", "href")
        val description = item.text("description", "summary", "content:encoded")
        return MarketNewsItem(
            // The link, not the title: a publisher revises a headline after filing and the story is
            // still the same story. `guid` would be better still, but not every feed here sends one.
            id = "rss:${link ?: title}",
            title = title,
            summary = description?.let(::plainText)?.takeIf(String::isNotBlank),
            source = feed.source,
            url = link,
            publishedAt = published,
            // Never guessed. A sentiment inferred from a headline by keyword is the most dangerous
            // number this app could print, because a reader would act on it — the screens render
            // UNKNOWN correctly and say nothing rather than something made up.
            sentiment = NewsSentiment.UNKNOWN,
            impact = MarketImpact.UNKNOWN,
            relevance = relevance(platform),
            isStale = published.isBefore(now.minusSeconds(STALE_AFTER_SECONDS)),
            imageUrl = item.image(),
            // The feed carries a summary, not the article. `body` is what decides whether the reader
            // is offered a full story to read in the app, and a truncated RSS blurb is not one.
            body = null,
        )
    }

    private fun relevance(platform: MarketPlatform): Set<MarketRelevance> = when (platform) {
        MarketPlatform.TRADEYAR -> setOf(MarketRelevance.CRYPTO)
        MarketPlatform.COINEPRO_FX -> setOf(MarketRelevance.GOLD, MarketRelevance.SILVER)
    }

    /**
     * The picture above the story.
     *
     * Three places a feed may put it, tried in the order they are trustworthy: `media:content` is
     * the one built for this, `enclosure` is the older convention, and an `<img>` inside the
     * description is what is left when a publisher sends neither. The last is a real case here —
     * Cointelegraph floats its cover image inside the description and sends `media:content` too,
     * while some Investing.com rows send only the enclosure.
     */
    private fun Element.image(): String? {
        attributeOf("media:content", "url")?.let { return it }
        attributeOf("enclosure", "url")?.let { return it }
        val description = text("description", "content:encoded") ?: return null
        return IMG_SRC.find(description)?.groupValues?.getOrNull(1)?.takeIf { it.startsWith("https://") }
    }

    private fun Element.attributeOf(tag: String, attribute: String): String? {
        val nodes = getElementsByTagName(tag)
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            val value = element.getAttribute(attribute)
            if (value.isNotBlank()) return value
        }
        return null
    }

    private fun Element.text(vararg tags: String): String? {
        for (tag in tags) {
            val nodes = getElementsByTagName(tag)
            for (index in 0 until nodes.length) {
                val value = nodes.item(index)?.textContent?.trim()
                if (!value.isNullOrBlank()) return value
            }
        }
        return null
    }

    /** An RSS description is HTML. The screen wants a sentence. */
    private fun plainText(html: String): String = html
        .replace(TAG, " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(WHITESPACE, " ")
        .trim()

    private fun instantOrNull(raw: String): Instant? {
        val text = raw.trim()
        for (format in FORMATS) {
            try {
                return ZonedDateTime.parse(text, format).toInstant()
            } catch (error: DateTimeParseException) {
                continue
            }
        }
        runCatching { Instant.parse(text) }.getOrNull()?.let { return it }
        // The zoneless form, last. See [ZONELESS] — an assumed zone is a worse answer than a parsed
        // one, so it is only reached when nothing that carries its own offset matched.
        return try {
            LocalDateTime.parse(text, ZONELESS).toInstant(ZoneOffset.UTC)
        } catch (error: DateTimeParseException) {
            null
        }
    }

    /** RFC 1123 is what RSS specifies; ISO is what the Atom feeds send. Both appear here. */
    private val FORMATS = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ISO_ZONED_DATE_TIME,
    )

    /**
     * `2026-08-30 21:58:13` — a moment with no zone on it at all.
     *
     * Investing.com's feeds send this, and it is out of spec: RSS requires RFC 822 dates, which
     * carry an offset. Both of the forex wires are that publisher, so refusing it would have meant
     * an empty news screen on the forex side — which is the bug this whole file exists to end,
     * reintroduced one layer down.
     *
     * Read as UTC, which is what that publisher's timestamps are. If the assumption is ever wrong it
     * is wrong by an hour or two on a «۳ ساعت پیش» label, and the ordering within the feed — the
     * thing a reader actually uses — is unaffected because every row in it shares the assumption.
     */
    private val ZONELESS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private val TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")
    private val IMG_SRC = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    /** A day old, a market story has been overtaken. The screens grey a stale row rather than hide it. */
    private const val STALE_AFTER_SECONDS = 24 * 60 * 60L
}
