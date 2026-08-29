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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.chartevents.ChartEventSymbols
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.marketintel.MarketImpact
import com.coinepro.core.marketintel.MarketNewsItem
import com.coinepro.core.marketintel.NewsSentiment

/**
 * One story, on a page of its own.
 *
 * ### What was wrong before
 *
 * There was no such page. A headline was a card in a list with its summary printed under it, and
 * that was the whole of the story — tapping it did nothing, because nothing was listening. So a
 * reader who wanted to know more about a rate decision had the same four lines whether they glanced
 * at the list or studied it, and the app's answer to "read this" was "you already have".
 *
 * ### What "read it in full" can honestly mean here, given what the feed sends
 *
 * Neither backend sends an article body. `MarketNewsDto` carries `title`, `summary`, `source`,
 * `url`, `published_at` and three classifications, and both contract documents are explicit that
 * this is a thin adapter over a cache of headlines — CoinePro-FX over its Redis key, TradeYar over
 * `news_posts`, which stores `summary_fa` and not a body. There is no text to render that the
 * server has not sent.
 *
 * Three answers were possible and this page takes the third:
 *
 * 1. **Render the source page in a WebView.** Rejected. The addresses are arbitrary third-party
 *    hosts chosen by a wire feed, and a WebView in this process carries this process's storage —
 *    see `NewsHandoff` for the longer version of that argument.
 * 2. **Reconstruct or summarise the article.** Rejected outright. This app does not have the text,
 *    and writing something that reads like the article would be inventing a story and attributing
 *    it to a named publisher.
 * 3. **Give everything the server did send its proper setting, and hand off honestly for the rest.**
 *    This one. The summary is set as a lede at reading size rather than as a caption; the source
 *    and the moment are stated plainly; the classifications become a short paragraph about what the
 *    story bears on rather than three coloured chips; and one gold button says, in words, that the
 *    full text lives with the publisher and opens it there.
 *
 * The day either server adds a body field, this page has the place for it already — between the
 * lede and the hand-off — and the hand-off becomes a footnote rather than the destination. That ask
 * is written out in the report.
 *
 * ### The picture
 *
 * [imageUrl] is at the very top, above everything, which is what the owner asked for and asked for
 * twice. It is null for every story today because no feed sends one; see [NewsHero], which draws
 * nothing at all in that case, and note that this page is laid out to be complete without it rather
 * than to have a hole where it goes.
 */
@Composable
fun NewsArticleScreen(
    item: MarketNewsItem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
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
    related: List<MarketNewsItem> = emptyList(),
    onOpenRelated: (MarketNewsItem) -> Unit = {},
    onOpenChart: ((symbol: String, atSeconds: Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val shareSubject = stringResource(R.string.news_share_subject)
    // One line, under the actions, for the two things that can fail from here. It is not a toast:
    // the toaster belongs to the app shell and this module cannot reach it, and a failure that
    // appears next to the button that caused it is easier to connect anyway.
    var problem by remember(item.id) { mutableStateOf<Int?>(null) }

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
                problem = if (NewsHandoff.share(context, item.title, item.source, item.url, shareSubject)) {
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
                url = imageUrl,
                contentDescription = stringResource(R.string.news_image_of, item.title),
                shape = RectangleShape,
            )

            Column(
                modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.impact != MarketImpact.UNKNOWN) ImpactPill(item.impact)
                    if (item.sentiment != NewsSentiment.UNKNOWN) SentimentPill(item.sentiment)
                    if (item.isStale) {
                        MetaPill(stringResource(R.string.news_stale), CoineProColors.Warning)
                    }
                }
                Text(
                    text = item.title,
                    style = NewsTextStyles.Headline,
                    color = CoineProColors.TextPrimary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
                NewsByline(item)
                Rule()
                // The summary, set as the article's opening paragraph rather than as a caption.
                // This is the whole of the text the server sends, so it gets the whole of the
                // reading treatment; printing it at `bodyMedium` under a headline, which is what
                // the card did, is what made the old screen feel like it had nothing in it.
                Text(
                    text = item.summary ?: stringResource(R.string.news_no_summary),
                    style = NewsTextStyles.Lede,
                    color = if (item.summary == null) CoineProColors.TextMuted else CoineProColors.TextSecondary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SourceHandoff(
                item = item,
                onProblem = { problem = it },
                modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
            )

            MarketContext(
                item = item,
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
                // The one thing on this row that changes state, and colour is how it says so. It is
                // the accent rather than a second gold object: the gold on this page belongs to the
                // button that opens the source, and two golds on a screen is a design bug here.
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
 * Where the rest of the story is, said in words.
 *
 * This is the honest half of the answer to "let the reader read it in full". The app does not have
 * the text and says so, names who does, and opens it there. A block that simply said «مشاهدهٔ منبع»
 * would be the same button with the truth left out — the reader would press it expecting more of
 * this page and get a browser.
 *
 * Where the feed sent no link the block is a single grey sentence and no button, because a
 * hand-off with nowhere to hand off to is not a lesser action, it is a missing one.
 */
@Composable
private fun SourceHandoff(
    item: MarketNewsItem,
    onProblem: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val link = remember(item.url) { NewsHandoff.safeUrl(item.url) }
    CoineProCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf)) {
            Text(
                text = stringResource(R.string.news_full_text_eyebrow),
                style = CoineProTextStyles.Eyebrow,
                color = CoineProColors.TextMuted,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(
                    if (link == null) R.string.news_no_link else R.string.news_full_text_note,
                    item.source,
                ),
                style = NewsTextStyles.Body,
                color = CoineProColors.TextSecondary,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
            if (link != null) {
                CoineProPrimaryButton(
                    text = stringResource(R.string.news_read_at_source),
                    onClick = {
                        onProblem(if (NewsHandoff.openSource(context, link)) null else R.string.news_open_failed)
                    },
                    icon = CoineProIcons.Link,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
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
 * sending the reader to an arbitrary chart would imply the story was about it.
 */
@Composable
private fun MarketContext(
    item: MarketNewsItem,
    onOpenChart: ((symbol: String, atSeconds: Long) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val symbol = remember(item.relevance) { ChartEventSymbols.symbolFor(item.relevance) }
    // Resolved before the join: stringResource is composable and cannot be called from inside
    // joinToString's non-composable transform.
    val markets = item.relevance.map { stringResource(it.labelRes()) }
    if (markets.isEmpty() && item.impact == MarketImpact.UNKNOWN && symbol == null) return
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
            if (onOpenChart != null && symbol != null) {
                CoineProSecondaryButton(
                    text = stringResource(R.string.news_open_chart),
                    icon = DesignR.drawable.nav_chart,
                    onClick = { onOpenChart(symbol, item.publishedAt.epochSecond) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The next thing to read, so the page has an exit that is not back. */
@Composable
private fun RelatedStories(
    related: List<MarketNewsItem>,
    onOpen: (MarketNewsItem) -> Unit,
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
