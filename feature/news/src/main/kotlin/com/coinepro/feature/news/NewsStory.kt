package com.coinepro.feature.news

import com.coinepro.core.common.parseWireInstant
import com.coinepro.core.guest.GuestHeadline
import com.coinepro.core.marketintel.MarketImpact
import com.coinepro.core.marketintel.MarketNewsItem
import com.coinepro.core.marketintel.MarketRelevance
import com.coinepro.core.marketintel.NewsSentiment
import java.time.Instant

/**
 * One story, as everything in this feature reads it.
 *
 * ### Why the reader stopped reading `MarketNewsItem` directly
 *
 * Because there are two feeds and only one of them is that type. A signed-in reader gets
 * `market-intelligence`, which is classified, timestamped and attributed; a guest gets the public
 * `news/list`, which is a headline, a Persian summary, a source that may be absent and a publication
 * time as an unparsed string. The screens were written against the first, so the second had its own
 * screen in `feature:guest` — a flat list of cards that could not be opened, with no picture and no
 * page behind them. That is half of what the owner is complaining about, and it was structural: the
 * guest feed could not be made to open a story without a type both feeds could become.
 *
 * ### Why two of its fields are nullable when `MarketNewsItem`'s are not
 *
 * [source] and [publishedAt] are the two the public route does not guarantee, and the alternative to
 * admitting that is worse than it looks. A required source means inventing a publisher's name for a
 * story that arrived without one; a required instant means dating an undated story, and the only
 * available lie — the epoch — prints as a day in 1970 next to a market headline. Absent is a state
 * the byline can draw. A wrong date is not.
 *
 * The signed-in feed fills both on every story, so nothing a member sees changes shape for this.
 */
data class NewsStory(
    val id: String,
    val title: String,
    /** The Persian summary. The whole of the text on both feeds today; see [body]. */
    val summary: String?,
    /**
     * The story's own text, where the server sent one.
     *
     * Null on both feeds as they stand — this is the field `docs/SERVER_ASK_NEWS_MEDIA.md` asks
     * for — and the reading page is written so that null is an ordinary shape for a story rather
     * than a gap in one. See `ArticleText`.
     */
    val body: String? = null,
    val source: String? = null,
    /** Where the publisher's own copy lives. An extra on the reading page, never the way into it. */
    val url: String? = null,
    val imageUrl: String? = null,
    val publishedAt: Instant? = null,
    val sentiment: NewsSentiment = NewsSentiment.UNKNOWN,
    val impact: MarketImpact = MarketImpact.UNKNOWN,
    val relevance: Set<MarketRelevance> = emptySet(),
    val isStale: Boolean = false,
)

/** A member's story. Every field the feed guarantees is present, so nothing is lost on the way in. */
internal fun MarketNewsItem.asStory(): NewsStory = NewsStory(
    id = id,
    title = title,
    summary = summary,
    body = body,
    source = source,
    url = url,
    imageUrl = imageUrl,
    publishedAt = publishedAt,
    sentiment = sentiment,
    impact = impact,
    relevance = relevance,
    isStale = isStale,
)

/**
 * A guest's headline, as a story the same page can open.
 *
 * Five fields arrive and five fields are set. Nothing here fabricates the rest:
 *
 * * **No picture and no body**, because the public route sends neither. The same ask covers both
 *   feeds and names this route explicitly.
 * * **No classification.** The public route sends `importance` and nothing else; impact, sentiment
 *   and market tags stay unknown rather than being guessed from a single integer, so the pills that
 *   would carry them are not drawn at all.
 *
 * The time is parsed with the same [parseWireInstant] the signed-in feed uses, and a string it
 * cannot read becomes null rather than a date. `feature:guest` declined to print the time at all for
 * that reason; parsing it is the same judgement reached one step further on — a verified instant is
 * printable, and an unverifiable one is now expressible as absent.
 *
 * ### The one field that arrives here with a weaker guarantee than on the signed-in path
 *
 * [NewsStory.url]. On the members' feed `safeHttpsUrl` has already refused everything but `https`
 * by the time the story leaves the gateway; the public route's `sourceUrl` is passed through as the
 * server sent it. So the scheme is checked where the address is *used* rather than where it is
 * mapped — `NewsHandoff.safeUrl`, which both the source pill and the share sheet go through. That
 * check has to exist regardless, because a URL can also reach a screen from a saved record written
 * by an older build, and it is the reason a cleartext guest link draws no pill instead of drawing
 * one that hands a plaintext request to the reader's browser.
 */
internal fun GuestHeadline.asStory(): NewsStory = NewsStory(
    id = slug,
    title = title,
    summary = summary,
    source = source,
    url = url,
    publishedAt = parseWireInstant(publishedAt),
)

/**
 * The story's text split into the paragraphs it was written in.
 *
 * A blank line is the break, which is what a plain-text body means by one everywhere it is written
 * by hand, and it is what `articleBody` in the gateway normalises runs of blank lines down to. A
 * single newline is *not* a break: wire copy is full of them at the ends of hard-wrapped lines, and
 * treating those as paragraphs would set a story as a column of orphans.
 *
 * Returns an empty list for text that is only whitespace, so the caller draws nothing rather than a
 * paragraph of nothing.
 */
internal fun newsParagraphs(body: String?): List<String> = body
    ?.split(PARAGRAPH_BREAK)
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    .orEmpty()

private val PARAGRAPH_BREAK = Regex("\n[ \\t]*\n")
