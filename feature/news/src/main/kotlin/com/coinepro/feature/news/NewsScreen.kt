package com.coinepro.feature.news

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.announcements.AnnouncementsController
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProHeaderAction
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProListHeader
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProPullToRefresh
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.marketintel.MarketImpact
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.marketintel.MarketNewsItem
import com.coinepro.core.marketintel.MarketRelevance
import com.coinepro.core.marketintel.NewsSentiment
import com.coinepro.core.model.MarketPlatform
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The market news feed, and the way into any one story.
 *
 * ### What this screen was, and what the owner said about it
 *
 * «اخبار بازار خیلی گرافیک بدی داره» — and it did. Every headline was one card of the same weight
 * as every other, opening with a row of three coloured pills and a timestamp before it said what
 * had happened; the headline itself was `titleSmall`, fifteen points, one step *below* the row title
 * on a market list; and the card did nothing when it was pressed, because there was nowhere to go.
 * A reader could scan that list. Nobody could read it.
 *
 * Three things changed and they are all the same change:
 *
 * * **The headline leads.** It is the first line of the card and it is set at eighteen points; the
 *   classifications moved above it as small pills and the source and the moment moved together onto
 *   one byline under it. What a card now answers, in order, is what happened, who says so, and when.
 * * **A story is a place.** Pressing a card opens [NewsArticleScreen] — the whole screen, not a
 *   sheet — with its own back, its own share, and its own save. See that file for what "read in
 *   full" can honestly mean given that no backend sends an article body.
 * * **The picture, where there is one, is above the headline.** The owner asked for that twice. See
 *   [NewsHero] and [imageUrlOf]: the layout is built around a picture and complete without one,
 *   which is what it has to be, because no feed sends one yet.
 *
 * ### Why the article is a state here rather than a route in the navigation graph
 *
 * Because a route in the graph is in `app/`, and a screen nobody can reach is not a feature. Held
 * as state, the story opens today, on the existing news route, with the system back gesture wired
 * to it through [BackHandler] and the open story surviving a rotation through [rememberSaveable].
 * [NewsArticleScreen] is a plain composable taking a plain item, so promoting it to `market/news/{id}`
 * later is a `composable(...)` block and a lambda — nothing here has to change shape for that, and
 * the exact code for it is in the report's wiring section.
 */
@Composable
fun NewsScreen(
    controller: MarketIntelController,
    onOpenCalendar: () -> Unit,
    platform: MarketPlatform = MarketPlatform.TRADEYAR,
    /**
     * Open the chart at the instrument this story is about, scrolled to the second it broke.
     *
     * The other half of the marks on the chart's time axis, and the reason it is worth having: a
     * reader who found the headline here should be able to see what the candle did about it, and a
     * reader who tapped a mark on the axis is already able to read the headline. One direction
     * without the other is a feature that only works if you happened to start on the right screen.
     *
     * Null where the host has no chart to send them to — the guest shell — and then no story offers
     * the entry at all, rather than offering a button that does nothing. It now lives on the story's
     * own page rather than on its card: a button inside a card that is itself the tap target is two
     * destinations under one thumb.
     */
    onOpenChart: ((symbol: String, atSeconds: Long) -> Unit)? = null,
    /**
     * The address of the picture that belongs above a story, or null where there is none.
     *
     * **This is the whole image seam and it is deliberately one function.** Neither backend sends an
     * image field today — `MarketNewsDto` has no such member, and both contract documents describe a
     * thin adapter over a cache that has never held one — so the default answers null for every
     * story and every layout below is written to be correct in that case.
     *
     * It is a parameter rather than a field on [MarketNewsItem] because `core:marketintel` is not
     * this module's to change. The day the field lands there, the app passes `{ it.imageUrl }` here
     * and every picture in this feature appears at once: the hero on a card, the hero on the reading
     * page, and the copy kept with a saved story. The contract to ask for is in the report.
     */
    imageUrlOf: (MarketNewsItem) -> String? = { null },
    /**
     * The announcements channel, or null on a platform that has none.
     *
     * **Null is how "absent rather than broken" is expressed here, and it is why this is a
     * controller and not a callback.** TradeYar serves `api/mobile/v1/announcements`; CoinePro-FX
     * serves nothing at that address, and a shell that passed a controller pointing at the forex
     * host would give a reader a 404 worded as an outage on a feature that was never built for
     * them. With null the entry is not drawn at all, so there is no route into a screen that could
     * only fail.
     *
     * It hangs off the news screen for the same reason [NewsArticleScreen] does — see the note on
     * this file: a destination in the navigation graph is in `app/`, and a screen nobody can reach
     * is not a feature. [AnnouncementsScreen] is a plain composable over a plain controller, so
     * promoting it to `market/announcements` later is a `composable(...)` block and nothing here
     * changes shape for it. The exact code is in the report's wiring section.
     */
    announcements: AnnouncementsController? = null,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var relevance by remember { mutableStateOf<MarketRelevance?>(null) }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    // The open story is held twice, and both halves earn their place.
    //
    // The **id** is what survives a rotation and a process death, because it is the only part of a
    // story that can be written into a Bundle — and it is the right part to keep, since the feed
    // underneath is refetched every two hours and a stored copy of a replaced story would come back
    // out of date.
    //
    // The **item** is what keeps the page open while the reader is on it. Without it the page is
    // only ever as alive as the reader's own two lists, so unsaving a story that had already aged
    // out of the feed would make the page the reader was reading disappear under them — which is
    // the opposite of what pressing unsave asks for.
    var openArticleId by rememberSaveable { mutableStateOf<String?>(null) }
    var openArticle by remember { mutableStateOf<MarketNewsItem?>(null) }
    // Saveable, so a reader who rotates the phone while reading an outage notice is still reading
    // it afterwards. It is one boolean rather than a copy of the list because the list itself is
    // held by a singleton controller and survives anything short of process death; on the other
    // side of a process death the screen refetches, which on this route costs one small request.
    var showAnnouncements by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { savedNewsStoreOrNull(context) }
    val savedFlow = remember(store) { store?.saved() ?: flowOf(emptyList()) }
    val savedArticles by savedFlow.collectAsStateWithLifecycle(emptyList())

    LaunchedEffect(controller) { controller.refresh() }

    val filtered = remember(state.news, relevance, savedOnly, savedArticles) {
        when {
            // A saved story is drawn from the reader's own copy, not looked up in the feed. The feed
            // is a two-hour window; anything older than that is gone from it, and a saved list that
            // could only show what happened to still be in the window would be a saved list that
            // empties itself overnight.
            savedOnly -> savedArticles.map(SavedArticle::asNewsItem)
            relevance == null -> state.news
            else -> state.news.filter { relevance in it.relevance }
        }
    }

    // Only the markets this platform serves. A crypto session filtering by "Gold" would be asking
    // the feed for a market its own signals never mention.
    val relevances = remember(platform) {
        when (platform) {
            MarketPlatform.TRADEYAR -> listOf(MarketRelevance.CRYPTO)
            MarketPlatform.COINEPRO_FX -> listOf(MarketRelevance.GOLD, MarketRelevance.SILVER)
        }
    }
    LaunchedEffect(platform) { relevance = null }

    // The item first, then the two lists — the second path is what a reader who was killed mid-story
    // comes back through, and it is why saving is worth offering at all: it is the only one of the
    // two that still has the story an hour later.
    val reading = openArticleId?.let { id ->
        openArticle?.takeIf { it.id == id }
            ?: state.news.firstOrNull { it.id == id }
            ?: savedArticles.firstOrNull { it.id == id }?.asNewsItem()
    }

    // The system gesture and the button on the page do the same thing, which is the point: a reader
    // who swipes back out of a story should not leave the news screen entirely. Announcements are
    // checked first because the two surfaces cannot both be open — an announcement offers no way
    // into a story — so whichever is showing is the one the gesture closes.
    BackHandler(enabled = showAnnouncements || openArticleId != null) {
        if (showAnnouncements) {
            showAnnouncements = false
        } else {
            openArticleId = null
            openArticle = null
        }
    }

    // Before the article, and guarded on the controller rather than on the flag alone: a shell that
    // never passed one cannot be left holding a `true` restored from a Bundle written by a build
    // that did — which would otherwise be a blank screen with no way out.
    if (showAnnouncements && announcements != null) {
        AnnouncementsScreen(
            controller = announcements,
            onBack = { showAnnouncements = false },
        )
        return
    }

    if (openArticleId != null) {
        ReadingSurface(
            item = reading,
            saved = savedArticles.any { it.id == openArticleId },
            store = store,
            feed = state.news,
            imageUrlOf = imageUrlOf,
            onOpenChart = onOpenChart,
            onOpen = { next ->
                openArticle = next
                openArticleId = next.id
            },
            onClose = {
                openArticleId = null
                openArticle = null
            },
            onSave = { item, saved ->
                scope.launch {
                    val target = store ?: return@launch
                    if (saved) {
                        target.remove(item.id)
                    } else {
                        target.save(item.asSavedArticle(imageUrlOf(item), Instant.now()))
                    }
                }
            },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .padding(horizontal = CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        // The list voice: a compact header with its actions as icons, so the first headline is above
        // the fold rather than under a two-line heading and a button.
        CoineProListHeader(
            title = stringResource(if (savedOnly) R.string.news_saved_title else R.string.news_title),
            subtitle = if (savedOnly) {
                // A prose count, so Persian digits — the app's rule, and the opposite of what the
                // figures on a market row take.
                stringResource(R.string.news_saved_count, savedArticles.size.toPersianDigits())
            } else {
                stringResource(R.string.news_subtitle)
            },
            modifier = Modifier.padding(horizontal = 0.dp),
            actions = {
                // Only where there is a store to read. A filter that can only ever be empty is a
                // control that teaches the reader the feature is broken.
                if (store != null) {
                    NewsIconAction(
                        icon = DesignR.drawable.icon_bookmark_simple,
                        label = stringResource(R.string.news_saved_title),
                        onClick = { savedOnly = !savedOnly },
                        tint = if (savedOnly) CoineProColors.Accent else CoineProColors.TextPrimary,
                    )
                }
                // Only where there is a channel to open. On CoinePro-FX the route does not exist,
                // so the icon does not exist either — the alternative is a control that reports a
                // 404 as though the announcements service were down.
                if (announcements != null) {
                    CoineProHeaderAction(
                        icon = CoineProIcons.Info,
                        label = stringResource(R.string.announcements_open),
                        onClick = { showAnnouncements = true },
                    )
                }
                CoineProHeaderAction(
                    icon = DesignR.drawable.icon_calendar_dots,
                    label = stringResource(R.string.news_calendar),
                    onClick = onOpenCalendar,
                )
            },
        )

        // A one-market platform gets no filter at all: a control with a single alternative to "all"
        // is a switch that says nothing. Nor does the saved list, which is the reader's own sequence
        // and not a slice of today's feed.
        if (relevances.size > 1 && !savedOnly) {
            CoineProSegmentedControl(
                options = listOf<MarketRelevance?>(null).plus(relevances)
                    .map { it to (it?.let { r -> stringResource(r.labelRes()) } ?: stringResource(R.string.news_filter_all)) },
                selected = relevance,
                onSelect = { relevance = it },
            )
        }

        AnimatedContent(
            targetState = when {
                savedOnly && filtered.isEmpty() -> "saved-empty"
                savedOnly -> "content"
                state.loading -> "loading"
                state.error != null && state.news.isEmpty() -> "error"
                filtered.isEmpty() -> "empty"
                else -> "content"
            },
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 8 }) togetherWith
                    (fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 12 })
            },
            label = "news-state",
        ) { mode ->
            when (mode) {
                "loading" -> CenterState(stringResource(R.string.news_loading), showProgress = true)
                // Server wording when there is any: the client did not diagnose this.
                "error" -> CenterState(
                    message = state.error ?: stringResource(R.string.news_unavailable),
                    action = stringResource(R.string.news_retry),
                    onAction = controller::refresh,
                )
                "saved-empty" -> CoineProEmptyState(
                    icon = DesignR.drawable.icon_bookmark_simple,
                    message = stringResource(R.string.news_saved_empty),
                    hint = stringResource(R.string.news_saved_empty_hint),
                    action = stringResource(R.string.news_saved_back),
                    onAction = { savedOnly = false },
                )
                "empty" -> CoineProEmptyState(
                    icon = CoineProIcons.News,
                    message = stringResource(R.string.news_empty),
                    action = stringResource(R.string.news_refresh),
                    onAction = controller::refresh,
                )
                // The strip stays: it says how old the headlines are, which the gesture cannot.
                // What the gesture adds is the answer to a tug, which this list had none of.
                else -> CoineProPullToRefresh(
                    refreshing = state.refreshing,
                    onRefresh = controller::refresh,
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
                    ) {
                        if (!savedOnly) {
                            item {
                                FreshnessStrip(
                                    refreshing = state.refreshing,
                                    onRefresh = controller::refresh,
                                )
                            }
                        }
                        items(filtered, key = MarketNewsItem::id) { item ->
                            NewsCard(
                                item = item,
                                imageUrl = imageUrlOf(item),
                                onOpen = {
                                    openArticle = item
                                    openArticleId = item.id
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item { Spacer(Modifier.height(CoineProSpacing.Three)) }
                    }
                }
            }
        }
    }
}

/**
 * The reading page, plus the one state the list cannot show: a story that is no longer anywhere.
 *
 * It happens for one reason and it is worth telling the reader about rather than silently bouncing
 * them back: the process was killed while they were reading, the feed was refetched on the way back
 * in, and their story had aged out of a two-hour window. Saving is the answer to that, so the
 * message says so.
 */
@Composable
private fun ReadingSurface(
    item: MarketNewsItem?,
    saved: Boolean,
    store: SavedNewsStore?,
    feed: List<MarketNewsItem>,
    imageUrlOf: (MarketNewsItem) -> String?,
    onOpenChart: ((symbol: String, atSeconds: Long) -> Unit)?,
    onOpen: (MarketNewsItem) -> Unit,
    onClose: () -> Unit,
    onSave: (MarketNewsItem, Boolean) -> Unit,
) {
    if (item == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CoineProColors.Stage)
                .padding(horizontal = CoineProSpacing.Gutter),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CoineProEmptyState(
                icon = CoineProIcons.News,
                message = stringResource(R.string.news_article_gone),
                hint = stringResource(R.string.news_article_gone_hint),
                action = stringResource(R.string.news_back_to_list),
                onAction = onClose,
            )
        }
        return
    }
    NewsArticleScreen(
        item = item,
        onBack = onClose,
        imageUrl = imageUrlOf(item),
        saved = saved,
        onToggleSave = store?.let { { onSave(item, saved) } },
        related = remember(feed, item) { relatedTo(item, feed) },
        onOpenRelated = onOpen,
        onOpenChart = onOpenChart,
    )
}

/**
 * The next few stories about the same market, newest first.
 *
 * Same market rather than "everything else", because the point is to be the story this reader was
 * already going to want. A story tagged with nothing has no market to match on, so it gets the rest
 * of the feed instead — which is still the right answer for a general-market headline, where the
 * reader's interest is the feed itself.
 */
internal fun relatedTo(item: MarketNewsItem, feed: List<MarketNewsItem>): List<MarketNewsItem> {
    val others = feed.filterNot { it.id == item.id }
    val matched = if (item.relevance.isEmpty()) {
        others
    } else {
        others.filter { candidate -> candidate.relevance.any { it in item.relevance } }
    }
    return matched.sortedByDescending(MarketNewsItem::publishedAt).take(MAX_RELATED)
}

/** Four. Enough that the page continues, few enough that it is a suggestion and not a second feed. */
internal const val MAX_RELATED = 4

@Composable
private fun FreshnessStrip(refreshing: Boolean, onRefresh: () -> Unit) {
    CoineProCard(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.OneHalf),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (refreshing) R.string.news_refreshing else R.string.news_timestamp_note,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (!refreshing) {
                CoineProSecondaryButton(
                    text = stringResource(R.string.news_refresh),
                    onClick = onRefresh,
                )
            }
        }
    }
}

/**
 * One story in the list.
 *
 * ### The order of the card, which is the whole of what changed
 *
 * Picture, classification, headline, summary, byline. The old card ran pills-and-timestamp,
 * headline, summary, source-and-markets, chart button — five bands of roughly equal weight, so the
 * eye had nowhere to land and every card cost the same to read whether or not it was interesting.
 *
 * The picture goes above everything, which is what the owner asked for and is also what makes a
 * feed look like a feed rather than like a settings list. The pills stay above the headline, small
 * and quiet, because a reader filtering for high-impact stories is scanning that band — but they
 * are now the only thing between the picture and the headline. The byline moved to the bottom and
 * merged with the timestamp: who and when are one question.
 *
 * The card is the tap target and it has nothing else tappable inside it. The chart entry that used
 * to sit here moved to the story's own page: two destinations under one thumb is a card a reader
 * cannot press confidently, and it was the reason the old card had a button at all.
 */
@Composable
private fun NewsCard(
    item: MarketNewsItem,
    imageUrl: String?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A live high-impact story is the one card that gets an edge. Everything else is separated by
    // the gap, so the edge means "read this one" rather than "this is a card".
    val urgent = item.impact == MarketImpact.HIGH && !item.isStale
    CoineProCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .then(
                if (urgent) {
                    Modifier.border(1.dp, CoineProColors.Warning.copy(alpha = 0.55f), MaterialTheme.shapes.large)
                } else {
                    Modifier
                },
            ),
        onClick = onOpen,
        // Zero, so the picture can reach the card's own edges. Everything under it carries the
        // padding the card would have applied — see the column below.
        contentPadding = PaddingValues(0.dp),
    ) {
        NewsHero(
            url = imageUrl,
            contentDescription = stringResource(R.string.news_image_of, item.title),
            // The card's own radius on the two corners the picture touches, so no fill shows through
            // behind it. `MaterialTheme.shapes.large` is 16dp; naming it here rather than reading it
            // keeps the two corners identical to the card's even if this card is ever given another.
            shape = RoundedCornerShape(topStart = CARD_RADIUS, topEnd = CARD_RADIUS),
        )
        Column(
            modifier = Modifier.padding(
                horizontal = CoineProSpacing.CardHorizontal,
                vertical = CoineProSpacing.CardVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.impact != MarketImpact.UNKNOWN) ImpactPill(item.impact)
                if (item.sentiment != NewsSentiment.UNKNOWN) SentimentPill(item.sentiment)
                // Staleness is said, not implied by a dimmer grey nobody reads as a claim.
                if (item.isStale) MetaPill(stringResource(R.string.news_stale), CoineProColors.Warning)
            }
            Text(
                text = item.title,
                style = NewsTextStyles.CardHeadline,
                color = CoineProColors.TextPrimary,
                maxLines = 3,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
            item.summary?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                    // Two lines. The whole summary is on the story's own page, and a card that
                    // prints all of it is a card that has already been read — which is precisely
                    // what made the old list feel like it had nowhere to go.
                    maxLines = 2,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            NewsByline(item)
        }
    }
}

/** The card's corner, repeated here so the picture inside it can match. See [NewsCard]. */
private val CARD_RADIUS = 16.dp

@Composable
private fun CenterState(
    message: String,
    showProgress: Boolean = false,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showProgress) CoineProThinkingDots()
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(16.dp),
        )
        if (action != null && onAction != null) {
            CoineProPrimaryButton(text = action, onClick = onAction)
        }
    }
}
