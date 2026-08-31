package com.coinepro.feature.guest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPullToRefresh
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.designsystem.rowMotion
import com.coinepro.core.guest.GuestController
import com.coinepro.core.guest.GuestHeadline
import com.coinepro.core.guest.GuestNewsState

/**
 * The headlines, on a page of their own, for a reader with no account.
 *
 * ### Why this exists rather than reusing the members' news screen
 *
 * The signed-in `NewsScreen` reads `MarketIntelController`, which is built only when there is a
 * session and answers 401 otherwise. Pointing a guest at it would give them an authentication
 * failure worded as an outage, on content the server publishes publicly. The two feeds are also
 * genuinely different shapes — the members' one carries relevance tags, impact and sentiment that
 * the public route does not send — so sharing one screen would mean a screen full of controls that
 * do nothing for half the people who see it.
 *
 * What is shared is everything that should be: the card, the empty state, the pull gesture, the
 * type. This is a list of headlines, not a second design.
 *
 * ### Why it exists at all
 *
 * The guest home printed all twelve of these in full, at the bottom, below a track record and a
 * membership card — about forty per cent of a page that took nearly five screens to scroll. And
 * there was nowhere to send them: `market/news` is passed `null` for a guest, so the one reader who
 * was being shown the most news was the one who could not open a news screen.
 */
@Composable
fun GuestNewsScreen(controller: GuestController, modifier: Modifier = Modifier) {
    val news by controller.news.collectAsStateWithLifecycle()

    DisposableEffect(controller) {
        controller.start()
        onDispose(controller::stop)
    }

    CoineProPullToRefresh(
        refreshing = news is GuestNewsState.Loading,
        onRefresh = controller::refreshNews,
        modifier = modifier.fillMaxSize().background(CoineProColors.Stage),
    ) {
        when (val current = news) {
            GuestNewsState.Loading -> CoineProThinkingDots()

            is GuestNewsState.Unavailable -> CoineProEmptyState(
                icon = CoineProIcons.News,
                // The server's own wording where it sent one: the client did not diagnose this
                // failure and has no business restating it.
                message = current.reason ?: stringResource(R.string.guest_news_unavailable),
                action = stringResource(R.string.guest_news_retry),
                onAction = controller::refreshNews,
            )

            is GuestNewsState.Ready -> if (current.headlines.isEmpty()) {
                CoineProEmptyState(
                    icon = CoineProIcons.News,
                    message = stringResource(R.string.guest_news_empty),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(CoineProSpacing.Two),
                    verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
                ) {
                    items(current.headlines, key = GuestHeadline::slug) { headline ->
                        Column(modifier = rowMotion().fillMaxWidth()) {
                            HeadlineCard(headline)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One headline.
 *
 * The source is shown and the time is not, because the public route sends `publishedAt` as an
 * unparsed ISO string and a date this app has not verified the timezone of is a date it should not
 * print beside a market headline.
 */
@Composable
private fun HeadlineCard(headline: GuestHeadline) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Text(
                text = headline.title,
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
            headline.summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            }
            headline.source?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}
