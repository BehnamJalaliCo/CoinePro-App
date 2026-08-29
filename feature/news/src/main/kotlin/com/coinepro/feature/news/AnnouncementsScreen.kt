package com.coinepro.feature.news

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.announcements.Announcement
import com.coinepro.core.announcements.AnnouncementImportance
import com.coinepro.core.announcements.AnnouncementsController
import com.coinepro.core.announcements.AnnouncementsState
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.ErrorKind
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProErrorState
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProListHeader
import com.coinepro.core.designsystem.CoineProPullToRefresh
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProThinkingDots

/**
 * Everything the service has said, kept.
 *
 * ### The two gaps this fills, and why a push was not enough for either
 *
 * Nobody learns when a market is added to the catalogue, and when we announce something ourselves —
 * an outage, a new version, a change to how membership works — the only channel is a push. For an
 * audience on unstable connections a push is the worst possible carrier for exactly the messages
 * that matter most: it arrives once, at a moment the reader may be off the network, and after that
 * it is gone. This list is the durable half. It is still there tomorrow, and it is still there for
 * somebody who reinstalled.
 *
 * ### Why this is a separate surface from the news feed rather than a filter on it
 *
 * Because the server split the routes, and it split them for a reason the app has to honour rather
 * than paper over. News is transient and carries a `stale` flag; an announcement is durable and
 * deliberately carries none — "the exchange connection is down" is true until it is not, and fading
 * it after twenty-four hours would tell a reader the outage had passed when nobody had said so. Two
 * behaviours on one surface means one of them always has to ignore the other's rules, and the one
 * that would have been ignored here is the one about the outage.
 *
 * ### The empty state is the design problem, and it is the whole of it
 *
 * On the day this ships the list is empty and that is correct, not broken. The news pipeline tags
 * whatever it ingests as `news`; an announcement is something a person decides to say, so until
 * somebody says one there is nothing here. Every reflex answer for an empty list is wrong here:
 *
 * * «چیزی پیدا نشد» is what a failed search says, and it reads as a fault in the app.
 * * A retry button says the reader can fix this by pressing something. They cannot, and a control
 *   that does nothing twice teaches them the screen is broken — so this state has **no action at
 *   all**, and the pull gesture stays for anybody who wants to check anyway.
 * * A spinner that never resolves is what an empty list looks like if the screen cannot tell "we
 *   have not asked" from "we asked and there is nothing". That distinction is
 *   [AnnouncementsState.loaded] and it exists for this sentence.
 *
 * What is left is to say the true thing plainly: nothing has been announced, this list is not a
 * feed and does not fill itself, and here is what will appear in it when it does. A reader who
 * finishes that sentence knows the app is working.
 */
@Composable
fun AnnouncementsScreen(
    controller: AnnouncementsController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // One line under the header for the one thing that can fail from here. The toaster belongs to
    // the app shell and this module cannot reach it; a failure shown where the button was pressed
    // is easier to connect to the press anyway.
    var problem by remember { mutableStateOf<Int?>(null) }

    // Read on arrival rather than at app start. Nothing on this route is needed until somebody
    // opens it, and an audience paying for every kilobyte should not be charged for a list they
    // never asked to see.
    LaunchedEffect(controller) { controller.refresh() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .padding(horizontal = CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        CoineProListHeader(
            title = stringResource(R.string.announcements_title),
            // The count only once there is one. «۰ اطلاعیه» under a heading is a number where a
            // sentence belongs, and the sentence is in the empty state below.
            subtitle = if (state.announcements.isEmpty()) {
                stringResource(R.string.announcements_subtitle)
            } else {
                // A prose count, so Persian digits — the app's rule, and the opposite of what a
                // price on a market row takes.
                stringResource(R.string.announcements_count, state.announcements.size.toPersianDigits())
            },
            modifier = Modifier.padding(horizontal = 0.dp),
            actions = {
                NewsIconAction(
                    icon = CoineProIcons.Back,
                    label = stringResource(R.string.announcements_back),
                    onClick = onBack,
                )
            },
        )

        problem?.let { message ->
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.Sell,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when (announcementsMode(state)) {
            AnnouncementsMode.WAITING -> WaitingForFirstRead()

            AnnouncementsMode.FAILED -> CoineProErrorState(
                message = stringResource(
                    if (state.failure == ErrorKind.NETWORK) {
                        R.string.announcements_offline
                    } else {
                        R.string.announcements_unavailable
                    },
                ),
                // The server's own words underneath ours, never instead of them. A refusal it wrote
                // for a reader is better than anything this screen could compose; a refusal it did
                // not write is absent, and then the line above is the whole answer.
                detail = state.failureText,
                action = stringResource(R.string.announcements_retry),
                onAction = controller::refresh,
            )

            AnnouncementsMode.EMPTY -> CoineProEmptyState(
                icon = CoineProIcons.Info,
                message = stringResource(R.string.announcements_empty),
                hint = stringResource(R.string.announcements_empty_hint),
                // No action, deliberately. See the note on this file: there is nothing wrong and
                // therefore nothing to retry, and a button here would be the screen apologising for
                // a state that is correct.
            )

            AnnouncementsMode.CONTENT -> CoineProPullToRefresh(
                refreshing = state.refreshing,
                onRefresh = controller::refresh,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
                ) {
                    // A refresh that failed over a list that did not. The announcements stay —
                    // they are still the last thing the service said — and the strip says only
                    // that they may no longer be the latest, which is the exact claim a failed
                    // request supports.
                    if (state.failure != null) {
                        item { RefreshFailedStrip(onRetry = controller::refresh) }
                    }
                    items(state.announcements, key = Announcement::id) { announcement ->
                        AnnouncementCard(
                            announcement = announcement,
                            onOpenLink = {
                                problem = if (NewsHandoff.openSource(context, announcement.url)) {
                                    null
                                } else {
                                    R.string.announcements_open_failed
                                }
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

/**
 * Which of the four things this screen can be.
 *
 * A function rather than a `when` inside the composable so the decision can be tested without a
 * renderer — and it is the decision worth testing, because three of the four branches are the same
 * empty list and telling them apart wrongly is how this feature would report itself as broken on
 * the day it is working perfectly.
 */
internal enum class AnnouncementsMode { WAITING, FAILED, EMPTY, CONTENT }

internal fun announcementsMode(state: AnnouncementsState): AnnouncementsMode = when {
    // Content first, and ahead of the failure: a failed refresh over a list that loaded earlier is
    // still a screen with announcements on it, and hiding them behind an error would take a live
    // outage notice off the reader's screen at the moment the connection it describes is failing.
    state.announcements.isNotEmpty() -> AnnouncementsMode.CONTENT
    // Ahead of `loaded`, so a channel that was empty and has now failed to reload reports the
    // failure rather than repeating «چیزی اعلام نشده» — which would be this screen asserting
    // something about the server on the strength of a request that never reached it.
    state.failure != null -> AnnouncementsMode.FAILED
    state.loaded -> AnnouncementsMode.EMPTY
    else -> AnnouncementsMode.WAITING
}

/**
 * The gap between opening the screen and knowing anything.
 *
 * Deliberately wordless apart from one line. Any sentence here would be a guess about an answer
 * that has not arrived, and the two guesses available — "nothing has been announced" and "this is
 * not working" — are the two sentences this screen exists to avoid saying by accident.
 */
@Composable
private fun WaitingForFirstRead() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CoineProThinkingDots()
        Text(
            text = stringResource(R.string.announcements_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(CoineProSpacing.Two),
        )
    }
}

@Composable
private fun RefreshFailedStrip(onRetry: () -> Unit) {
    CoineProCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(
            horizontal = CoineProSpacing.Two,
            vertical = CoineProSpacing.OneHalf,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.announcements_refresh_failed),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
                textAlign = TextAlign.Right,
                modifier = Modifier.weight(1f),
            )
            CoineProSecondaryButton(
                text = stringResource(R.string.announcements_retry),
                onClick = onRetry,
            )
        }
    }
}

/**
 * One announcement, printed in full.
 *
 * ### Why nothing here is truncated and there is no page behind it
 *
 * A news card clips its summary to two lines because the story continues on its own page and a card
 * that printed all of it would be a card nobody needed to open. An announcement has no page behind
 * it and no more text anywhere: `news_posts` stores `summary_fa`, not a body, and the sentence the
 * service wrote is the whole of what it said. Clipping it would hide part of an outage notice
 * behind a tap that leads nowhere.
 *
 * ### Only the important ones are marked
 *
 * A pill on every card marks nothing. The label appears for [AnnouncementImportance.HIGH] alone, so
 * that when a reader sees one it means this announcement is not like the others — and the hairline
 * around the card says the same thing a second time, for a reader scrolling too fast to read a
 * pill. Medium, low and ungraded announcements are drawn identically, because the difference
 * between them is not one a reader needs.
 */
@Composable
private fun AnnouncementCard(
    announcement: Announcement,
    onOpenLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val important = announcement.importance == AnnouncementImportance.HIGH
    CoineProCard(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (important) {
                    Modifier.border(
                        1.dp,
                        CoineProColors.Warning.copy(alpha = 0.55f),
                        MaterialTheme.shapes.large,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            if (important) {
                MetaPill(
                    text = stringResource(R.string.announcements_important),
                    color = CoineProColors.Warning,
                )
            }
            Text(
                text = announcement.title,
                style = NewsTextStyles.CardHeadline,
                color = CoineProColors.TextPrimary,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
            announcement.body?.let { body ->
                Text(
                    text = body,
                    style = NewsTextStyles.Body,
                    color = CoineProColors.TextSecondary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnnouncementByline(announcement)
            announcement.url?.let {
                CoineProSecondaryButton(
                    text = stringResource(R.string.announcements_read_more),
                    onClick = onOpenLink,
                    icon = CoineProIcons.Link,
                )
            }
        }
    }
}

/**
 * When it was said, and who said it when that is not us.
 *
 * ### The year is printed here and is not printed on a news card
 *
 * [PersianDateTime.moment] omits the year, correctly, for a feed whose every row is within a
 * two-hour window. This list is the opposite: it is durable by design and an announcement from last
 * spring is expected to still be in it, so «۵ شهریور» alone would leave a reader unable to tell an
 * outage notice from this morning from one from a year ago — which is precisely the confusion a
 * durable list of service statements cannot afford.
 *
 * The clock stays Latin and isolated, as everywhere in this app: a time is a figure a reader checks
 * against something else, and the day beside it is prose.
 */
@Composable
private fun AnnouncementByline(announcement: Announcement, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        announcement.source?.let { source ->
            Text(
                // Isolated because a publisher's name is very often Latin inside a Persian line,
                // and without an isolate the middle dot after it lands on the wrong side of it.
                text = BidiText.isolateLtr(source),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.TextSecondary,
                maxLines = 1,
                textAlign = TextAlign.Right,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = ANNOUNCEMENT_DOT,
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
        }
        Text(
            text = PersianDateTime.dayWithYear(announcement.publishedAt) +
                " $ANNOUNCEMENT_DOT " +
                PersianDateTime.clock(announcement.publishedAt),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            maxLines = 1,
        )
    }
}

/** The same separator the news byline uses, so the two lists read as one voice. */
private const val ANNOUNCEMENT_DOT = "·"
