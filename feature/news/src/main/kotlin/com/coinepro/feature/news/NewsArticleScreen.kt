package com.coinepro.feature.news

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.chartevents.ChartEventSymbols
import com.coinepro.core.common.BidiText
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.marketintel.MarketImpact
import com.coinepro.core.marketintel.NewsSentiment

/**
 * One story, on a page of its own, read here.
 *
 * ### What the owner said, twice
 *
 * «هنوز روی خبرها عکس نیست، و متن کامل باید داخل خود اپ باشد — نه اینکه من را به سایت منبع بفرستد.»
 * There is still no picture on the news, and the full text must be inside our own app rather than
 * sending the reader to the source site. Both halves of that sentence are structural and both are
 * answered here:
 *
 * * **The picture is the first thing on the page**, full-bleed, above the eyebrow and the headline.
 *   `MarketNewsItem.imageUrl` now carries it end to end — the gateway reads the field, the story
 *   type keeps it, this page draws it, and the saved copy remembers it — so the day either backend
 *   starts sending one, every picture in the feature appears at once with nothing to wire.
 * * **The page is the destination, not a lobby.** It was not, before. The whole of the text sat in
 *   one paragraph and under it was a gold button reading «خواندن متن کامل در منبع», which is a page
 *   whose loudest object is the exit — precisely the thing being complained about. The publisher's
 *   own copy is still reachable, because a reader who wants the original is entitled to it, but it
 *   is now a quiet line at the end of the text beside the publisher's name. It is a footnote. The
 *   story is the page.
 *
 * ### The full text, which now actually arrives
 *
 * [NewsStory.body] is the story's own text, and the claim that used to stand here — that neither
 * feed had one — was wrong. `news_posts` has a `body_fa` column holding a full Persian translation
 * and `api/v1/news/{slug}` has been serving it to anybody all along; the forex side's
 * `articles.content` is its own newsroom's article, rendered to plain text by
 * `user/mobile/news/{id}`. Neither is in the *list* route, deliberately — see [NewsBodySource] —
 * so `ReadingSurface` fetches it when a reader opens the story, and this page is handed a story
 * that has one.
 *
 * Two bad answers were available and this page takes neither. It does not render the source page in
 * a WebView (see [NewsHandoff] for why an arbitrary third-party host must not run in this process),
 * and it does not write something that reads like the article, which would be inventing a story and
 * attributing it to a named publisher.
 *
 * Where a body does not arrive — a backend that publishes none, a fetch that failed — the page is
 * still a page: the summary is set at eighteen points with reading leading, on a measure, under a
 * rule, with a line beneath it saying plainly that this is the summary the source published and not
 * the whole of it. A short article, honestly labelled, rather than a stub apologising for itself.
 * [ArticleText] holds both shapes, and the sentence about the summary disappears when it stops
 * being true.
 */
@Composable
fun NewsArticleScreen(
    story: NewsStory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Whether this story is in the reader's saved list. */
    saved: Boolean = false,
    /**
     * Adds or removes this story from the saved list.
     *
     * Null where there is no store — see [savedNewsStoreOrNull] — and then the control is not drawn
     * at all rather than drawn and inert.
     */
    onToggleSave: (() -> Unit)? = null,
    /**
     * Other headlines about the same market, newest first, this one excluded.
     *
     * The reason the page does not end. A reader who has just read that the Fed held rates is, at
     * that exact moment, more likely than at any other to want the next gold story — and the
     * alternative is a page whose only exit is back, which is how a news app becomes a thing people
     * check rather than a thing people read.
     */
    related: List<NewsStory> = emptyList(),
    onOpenRelated: (NewsStory) -> Unit = {},
    onOpenChart: ((symbol: String, atSeconds: Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val shareSubject = stringResource(R.string.news_share_subject)
    // One line, under the actions, for the two things that can fail from here. It is not a toast:
    // the toaster belongs to the app shell and this module cannot reach it, and a failure that
    // appears next to the button that caused it is easier to connect anyway.
    var problem by remember(story.id) { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CoineProColors.Stage),
    ) {
        // Outside the scroll on purpose. A reader four screens into a story should not have to
        // scroll back up to leave it, or to keep it.
        ArticleChrome(
            saved = saved,
            onBack = onBack,
            onToggleSave = onToggleSave,
            onShare = {
                problem = if (NewsHandoff.share(context, story.title, story.source, story.url, shareSubject)) {
                    null
                } else {
                    R.string.news_share_unavailable
                }
            },
        )
        problem?.let { message ->
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.Warning,
                textAlign = TextAlign.Right,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Half),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
        ) {
            // Full-bleed: no gutter, because a photograph inset by sixteen points on a phone is a
            // thumbnail. Everything below it is inset, which is what makes the picture read as the
            // top of the story rather than as one more block in a stack.
            NewsHero(
                url = story.imageUrl,
                contentDescription = stringResource(R.string.news_image_of, story.title),
                shape = RectangleShape,
            )

            Column(
                modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (story.impact != MarketImpact.UNKNOWN) ImpactPill(story.impact)
                    if (story.sentiment != NewsSentiment.UNKNOWN) SentimentPill(story.sentiment)
                    if (story.isStale) {
                        MetaPill(stringResource(R.string.news_stale), CoineProColors.Warning)
                    }
                }
                Text(
                    text = paragraphOf(story.title),
                    style = NewsTextStyles.Headline,
                    color = CoineProColors.TextPrimary,
                    textAlign = alignmentFor(story.title),
                    modifier = Modifier.fillMaxWidth(),
                )
                NewsByline(story)
                Rule()
                ArticleText(story)
                SourceLine(story, onProblem = { problem = it })
            }

            MarketContext(
                story = story,
                onOpenChart = onOpenChart,
                modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
            )

            if (related.isNotEmpty()) {
                RelatedStories(
                    related = related,
                    onOpen = onOpenRelated,
                    modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                )
            }

            Spacer(Modifier.height(CoineProSpacing.Four))
        }
    }
}

/**
 * The story itself: the lede, then the body where there is one.
 *
 * ### The lede
 *
 * The summary, set as the article's opening paragraph rather than as a caption. Where it is the
 * only text the server sent — which is every story on both feeds today — it *is* the article, and
 * it is set that way: eighteen points, reading leading, full measure. Printing it at `bodyMedium`
 * under a headline, which is what the card does deliberately and what this page used to do by
 * accident, is what made the old screen feel like it had nothing in it.
 *
 * ### The body, and the line that appears only when there is none
 *
 * A body arrives as plain text in paragraphs — see `articleBody`, which refuses markup and refuses
 * a body that is only the summary again — and is set at sixteen points under the lede, each
 * paragraph its own block. There is no drop cap, no pull quote and no first-line indent: this is
 * Persian, set right-aligned, and the paragraph break is the only structure the text carries.
 *
 * Where there is no body, one muted sentence says so. It is worth the line: without it a reader
 * cannot tell a story that is genuinely four lines long from an app that has lost the rest of it,
 * and the difference between those two is the difference between a short article and a bug.
 */
@Composable
private fun ArticleText(story: NewsStory, modifier: Modifier = Modifier) {
    val paragraphs = remember(story.body) { newsParagraphs(story.body) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        // The lede and the body follow the story's own script — see `BidiText.isLatinSentence` and
        // `alignmentFor`. Our own fallback sentence is Persian and lands on the right either way;
        // the wire's is not, and right-aligning it in an RTL paragraph puts its full stop at the
        // start of the line.
        val lede = story.summary ?: stringResource(R.string.news_no_summary)
        Text(
            text = paragraphOf(lede),
            style = NewsTextStyles.Lede,
            color = if (story.summary == null) CoineProColors.TextMuted else CoineProColors.TextSecondary,
            textAlign = alignmentFor(lede),
            modifier = Modifier.fillMaxWidth(),
        )
        paragraphs.forEach { paragraph ->
            Text(
                text = paragraphOf(paragraph),
                style = NewsTextStyles.Body,
                color = CoineProColors.TextSecondary,
                textAlign = alignmentFor(paragraph),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (paragraphs.isEmpty() && story.summary != null) {
            Text(
                text = stringResource(R.string.news_summary_is_all),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Who published this, and the one quiet way through to their own copy.
 *
 * This is what the gold «خواندن متن کامل در منبع» button became, and the demotion is the point. A
 * primary action is the thing a screen is asking the reader to do, and this screen is asking them
 * to read what is on it. The publisher's page is an entitlement, not an instruction — so it is a
 * neutral pill at the end of the text, sized to its label rather than to the column, beside the
 * name of whoever wrote the thing.
 *
 * Nothing is drawn where the feed sent neither a publisher nor an address, which is a real state on
 * the public feed rather than a defensive one. A row containing only a middle dot is furniture.
 */
@Composable
private fun SourceLine(story: NewsStory, onProblem: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val link = remember(story.url) { NewsHandoff.safeUrl(story.url) }
    if (story.source == null && link == null) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        story.source?.let { source ->
            Text(
                // Isolated, because this is the one place a publisher's name sits *inside* a Persian
                // sentence rather than alone in its own line. «منتشرشده در ForexLive» without the
                // isolate lets the bidi algorithm pull the Latin run to the wrong end of the line
                // the moment the name ends in anything but a letter — a full stop, a closing
                // bracket, the ".com" some feeds put in a source name.
                text = stringResource(R.string.news_published_by, BidiText.isolateLtr(source)),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.TextMuted,
                textAlign = TextAlign.Right,
                modifier = Modifier.weight(1f),
            )
        }
        if (link != null) {
            CoineProSecondaryButton(
                text = stringResource(R.string.news_open_source),
                icon = CoineProIcons.Link,
                onClick = {
                    onProblem(if (NewsHandoff.openSource(context, link)) null else R.string.news_open_failed)
                },
            )
        }
    }
}

/**
 * Back, save and share, in one row above the story.
 *
 * Back sits at the reading start — the right, in Persian — because that is where a reader's thumb
 * goes to leave, and the glyph is auto-mirrored so it points the way the reader is going back
 * towards. The two actions that act on the story sit at the far end, together, away from the exit.
 */
@Composable
private fun ArticleChrome(
    saved: Boolean,
    onBack: () -> Unit,
    onToggleSave: (() -> Unit)?,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NewsIconAction(
            icon = CoineProIcons.Back,
            label = stringResource(R.string.news_back),
            onClick = onBack,
        )
        Spacer(Modifier.weight(1f))
        onToggleSave?.let { toggle ->
            NewsIconAction(
                icon = DesignR.drawable.icon_bookmark_simple,
                label = stringResource(if (saved) R.string.news_unsave else R.string.news_save),
                onClick = toggle,
                // The one thing on this row that changes state, and colour is how it says so. With
                // the gold button gone from the body of the page this is now the only accent on the
                // screen, which is the right place for it: what the reader can change here is
                // whether they keep the story.
                tint = if (saved) CoineProColors.Accent else CoineProColors.TextPrimary,
            )
        }
        NewsIconAction(
            icon = CoineProIcons.Link,
            label = stringResource(R.string.news_share),
            onClick = onShare,
        )
    }
}

/**
 * What this story bears on, in a sentence, and the chart it bears on.
 *
 * The three classifications were three coloured chips and nothing else, which told a reader who
 * already knew the vocabulary something and everybody else nothing. A chip saying «تأثیر بالا» does
 * not say high impact on *what*. Here they are a sentence that names the market, and the chart
 * button underneath it stops being an orphan control at the bottom of a card.
 *
 * Only where the story names a market. A general-market headline has no instrument to open, and
 * sending the reader to an arbitrary chart would imply the story was about it. That also keeps the
 * card off the public feed entirely, which sends no classifications at all — see [NewsStory].
 */
@Composable
private fun MarketContext(
    story: NewsStory,
    onOpenChart: ((symbol: String, atSeconds: Long) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val symbol = remember(story.relevance) { ChartEventSymbols.symbolFor(story.relevance) }
    // Resolved before the join: stringResource is composable and cannot be called from inside
    // joinToString's non-composable transform.
    val markets = story.relevance.map { stringResource(it.labelRes()) }
    if (markets.isEmpty() && story.impact == MarketImpact.UNKNOWN && symbol == null) return
    CoineProCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf)) {
            Text(
                text = stringResource(R.string.news_context_eyebrow),
                style = CoineProTextStyles.Eyebrow,
                color = CoineProColors.TextMuted,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (markets.isEmpty()) {
                    stringResource(R.string.news_context_general)
                } else {
                    stringResource(
                        R.string.news_context_markets,
                        markets.joinToString(stringResource(R.string.news_market_join)),
                    )
                },
                style = NewsTextStyles.Body,
                color = CoineProColors.TextSecondary,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
            // The chart entry needs a moment to scroll to as well as an instrument to open. A story
            // the feed dated is every story a member sees; one the public feed left undated has no
            // second to point at, and a mark dropped on the axis at "now" would be a claim about
            // when the news broke that nobody made.
            val moment = story.publishedAt
            if (onOpenChart != null && symbol != null && moment != null) {
                CoineProSecondaryButton(
                    text = stringResource(R.string.news_open_chart),
                    icon = DesignR.drawable.nav_chart,
                    onClick = { onOpenChart(symbol, moment.epochSecond) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The next thing to read, so the page has an exit that is not back. */
@Composable
private fun RelatedStories(
    related: List<NewsStory>,
    onOpen: (NewsStory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Text(
            text = stringResource(R.string.news_related_title),
            style = MaterialTheme.typography.titleSmall,
            color = CoineProColors.TextPrimary,
            textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth(),
        )
        related.forEach { other ->
            CoineProCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpen(other) },
                contentPadding = PaddingValues(
                    horizontal = CoineProSpacing.CardHorizontal,
                    vertical = CoineProSpacing.OneHalf,
                ),
            ) {
                Text(
                    text = other.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                    maxLines = 2,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(CoineProSpacing.Half))
                NewsByline(other)
            }
        }
    }
}

/**
 * The hairline between the headline block and the text.
 *
 * A rule rather than a gap, and it is the only one on the page. It is where a newspaper puts one —
 * under the byline, at the point the furniture stops and the article starts — and it is what lets
 * the lede below it be quiet without looking like a caption that lost its picture.
 */
@Composable
private fun Rule() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(CoineProColors.BorderSubtle),
    )
}
