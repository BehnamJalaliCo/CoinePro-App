package com.coinepro.feature.news

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProListHeader
import com.coinepro.core.designsystem.CoineProPullToRefresh
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.guest.GuestController
import com.coinepro.core.guest.GuestHeadline
import com.coinepro.core.guest.GuestNewsState

/**
 * The headlines, read by somebody with no account.
 *
 * ### Why this is here rather than in `feature:guest`, where the guest's news screen used to be
 *
 * Because the owner's complaint is about the *reading*, and reading is this module. The guest screen
 * in `feature:guest` was a list of cards that could not be pressed: no picture, no page behind them,
 * nowhere to go. It was written that way for a defensible reason at the time — the members' screen
 * takes a `MarketIntelController` a guest has no session for, and the two feeds carry genuinely
 * different fields — but the conclusion drawn from it was that the guest needed a second design, and
 * that is what left half the app's readers with a feed they could only look at.
 *
 * [NewsStory] is what makes one design serve both. The two feeds map into it, the fields the public
 * route does not send arrive as null, and every surface below already draws a story missing any of
 * them. So a guest now gets the same card, the same picture, the same reading page and the same
 * suggestions at the end of it as a member, minus exactly the things the public route does not
 * carry — and nothing on the screen refers to something that is not there.
 *
 * ### What a guest does not get, and why none of it is drawn as broken
 *
 * * **No classification.** The public route sends `importance` and no market tags, so the pills are
 *   absent rather than grey. Which also removes the market card at the foot of the story: there is
 *   no instrument to open a chart at, and a guest shell has no chart to open anyway.
 * * **No saving.** The store would work — it is a local file, and it does not care who the reader
 *   is — but the saved list is reached through a filter on the members' header, so a save here
 *   would put stories somewhere with no way back to them. A control that files things into a
 *   drawer the reader cannot open is worse than no control.
 *
 * The publisher's own page is *not* on that list any more. `GuestHeadline` used to read the public
 * route's `sourceUrl` and drop it, which left a guest's story with no way through to the source at
 * all; `core:guest` now carries it, so the same quiet pill a member gets appears here on any story
 * whose address survives `NewsHandoff.safeUrl`. It is still the extra rather than the way to read —
 * the story is on this page, which is the whole point of the page existing.
 */
@Composable
fun PublicNewsScreen(
    controller: GuestController,
    modifier: Modifier = Modifier,
    /**
     * The whole of a story's text, for the reading page. See `ReadingSurface`.
     *
     * A guest gets it on exactly the same terms a member does, because the route it comes from is
     * public — and that symmetry is the point. The guest feed was the *illustrated* one all along
     * while the members' feed had no pictures; shipping the body to one and not the other would be
     * the same asymmetry pointing the other way.
     */
    fetchBody: (suspend (NewsStory) -> String?)? = null,
) {
    val news by controller.news.collectAsStateWithLifecycle()

    // The news alone, rather than [GuestController.start], which also begins the ten-second price
    // poll and whose matching `stop` would cancel it for whichever other screen started it. There
    // are no prices on this screen; asking for a quote every ten seconds so nothing can draw it is
    // somebody else's rate limit spent on nothing.
    LaunchedEffect(controller) { controller.refreshNews() }

    val stories = remember(news) {
        (news as? GuestNewsState.Ready)?.headlines?.map(GuestHeadline::asStory).orEmpty()
    }

    // Held exactly as the members' screen holds it, and for the same two reasons: the id survives a
    // rotation because it is the only part of a story a Bundle can carry, and the story itself keeps
    // the page alive while a refresh lands underneath it.
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<NewsStory?>(null) }

    BackHandler(enabled = openId != null) {
        openId = null
        open = null
    }

    if (openId != null) {
        ReadingSurface(
            story = openId?.let { id -> open?.takeIf { it.id == id } ?: stories.firstOrNull { it.id == id } },
            saved = false,
            // No store, so no save control is drawn. See the note on this file.
            store = null,
            feed = stories,
            onOpenChart = null,
            onOpen = { next ->
                open = next
                openId = next.id
            },
            onClose = {
                openId = null
                open = null
            },
            onSave = { _, _ -> },
            fetchBody = fetchBody,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .padding(horizontal = CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        CoineProListHeader(
            title = stringResource(R.string.news_title),
            // Not the members' subtitle. That one promises structured news with named sources, and
            // this route sends neither impact nor relevance and may send no source at all — a
            // heading has to be true of the list under it.
            subtitle = stringResource(R.string.news_public_subtitle),
            modifier = Modifier.padding(horizontal = 0.dp),
        )

        when (val current = news) {
            GuestNewsState.Loading -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                CoineProThinkingDots()
            }

            is GuestNewsState.Unavailable -> CoineProEmptyState(
                icon = CoineProIcons.News,
                // The server's own wording where it sent one: the client did not diagnose this
                // failure and has no business restating it.
                message = current.reason ?: stringResource(R.string.news_unavailable),
                action = stringResource(R.string.news_retry),
                onAction = controller::refreshNews,
            )

            is GuestNewsState.Ready -> if (stories.isEmpty()) {
                CoineProEmptyState(
                    icon = CoineProIcons.News,
                    message = stringResource(R.string.news_empty),
                    action = stringResource(R.string.news_refresh),
                    onAction = controller::refreshNews,
                )
            } else {
                CoineProPullToRefresh(
                    // The public route answers in one request and the state goes back to Loading
                    // while it is in flight, so there is no separate refreshing flag to report and
                    // no moment where the gesture spins over a list that is already fresh.
                    refreshing = false,
                    onRefresh = controller::refreshNews,
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
                    ) {
                        items(stories, key = NewsStory::id) { story ->
                            NewsCard(
                                story = story,
                                onOpen = {
                                    open = story
                                    openId = story.id
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
