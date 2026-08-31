package com.coinepro.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.community.CommunityPost
import com.coinepro.core.community.CommunityReactions
import com.coinepro.core.designsystem.CoineProAvatar
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.model.AvatarBase
import com.coinepro.core.model.AvatarRing
import com.coinepro.core.model.AvatarSpec

/**
 * One post, as a card.
 *
 * ### Why there is no cover image and no headline
 *
 * Because there is no cover image and no headline. `AcademyPost` has a body, an author, a category
 * and two counters, and nothing else — the reference screenshot's title, thumbnail and verified
 * badge have no column behind them here. Cutting the first line off the body to use as a title
 * would be this app writing a headline the author did not write; picking a stock chart for a cover
 * would be illustrating somebody's opinion with a picture they did not choose. Both are the same
 * fault as a lettered disc standing in for a market's logo, in prose instead of in artwork.
 *
 * So the card is honest at any length: a name, a moment, a topic, and the words. A short post looks
 * like a short post rather than like a card that failed to load.
 *
 * ### Two counters, no glyphs
 *
 * The design system has no social icon set — no heart, no speech bubble, no rocket — and the shapes
 * it does have are already spoken for: the star is the watchlist and the callout is a drawing tool.
 * Borrowing either would hand a reader a shape they have already learned means something else on a
 * screen where it means a third thing. So the counts are labelled in words, which is unambiguous,
 * translates, and reads correctly in a right-to-left line.
 *
 * The numbers are **prose counts**, so they are in Persian digits — «۱۹۲ پسند» — unlike every
 * market figure in this app, which stays Latin so a reader can check it against another terminal.
 * Nothing on this screen is checkable against another terminal.
 */
@Composable
internal fun CommunityPostCard(
    post: CommunityPost,
    onOpen: (() -> Unit)?,
    onLike: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** Every line of the body, for the thread page. The feed cuts it; see [FEED_LINES]. */
    expanded: Boolean = false,
    /** The reaction row, on the thread page only. Null keeps the card to its two counters. */
    onReact: ((String) -> Unit)? = null,
) {
    CoineProCard(
        modifier = modifier.fillMaxWidth(),
        // The card's own click rather than a `clickable` on its modifier: `CoineProCard` moves its
        // *fill* on press as well as its scale, which is what a finger on a 360dp block actually
        // reads as contact. Null leaves the card inert, which is the honest shape for a render.
        onClick = onOpen,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            ) {
                // The initial, ringless. This is the "comment row that does not exist yet" the
                // avatar's own KDoc names: the server sends no picture for a community author and
                // never has, and a reader's own chosen avatar belongs to their profile rather than
                // to somebody else's post. A gold ring here would put a second gold object on a
                // screen whose one accent belongs to the composer's send button.
                CoineProAvatar(
                    spec = AvatarSpec(base = AvatarBase.Initial, ring = AvatarRing.NONE),
                    initial = post.author.trim().take(1),
                    size = 30.dp,
                    contentDescription = post.author,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.author,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = CoineProColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    post.createdAt?.let { moment ->
                        Text(
                            text = PersianDateTime.moment(moment),
                            style = MaterialTheme.typography.labelSmall,
                            color = CoineProColors.TextMuted,
                            maxLines = 1,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                // The server's own word for the topic, kept even when this build does not recognise
                // it — see `CommunityPost.categoryLabel`. A chip is not drawn at all where the post
                // carried no category, rather than being filled with «عمومی» this app guessed.
                post.categoryLabel?.let { label -> TopicChip(label) }
            }

            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else FEED_LINES,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )

            if (post.pending) {
                // Only ever true for a post the reader has just written: the feed filters on
                // `status == "published"`. Saying so is the whole point — a post that vanished with
                // no explanation is the thing a writer reads as a lost post.
                Text(
                    text = stringResource(R.string.community_post_pending),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A zero is not a count, it is an absence, and «۰ پسند» reads as a tally of
                // nothing where the pill is really an invitation to be the first. Both pills stay
                // — they are the post's two actions and a card missing one would be a card with
                // nothing to press — but at zero they say only what pressing them does.
                CountPill(
                    label = if (post.likes > 0) {
                        stringResource(R.string.community_likes, post.likes.toPersianDigits())
                    } else {
                        stringResource(R.string.community_like)
                    },
                    active = post.liked,
                    onClick = onLike,
                )
                CountPill(
                    label = if (post.replyCount > 0) {
                        stringResource(R.string.community_replies, post.replyCount.toPersianDigits())
                    } else {
                        stringResource(R.string.community_reply)
                    },
                    active = false,
                    onClick = onOpen,
                )
            }

            if (post.reactions.isNotEmpty() || onReact != null) {
                ReactionRow(post = post, onReact = onReact)
            }
        }
    }
}

/**
 * A topic chip — the server's category, drawn as a label rather than as a filter.
 *
 * Tinted rather than filled: `CoineProTint.fill` at the system's own 8% keeps it a surface with a
 * word on it instead of a second coloured object competing with the composer's gold button.
 */
@Composable
internal fun TopicChip(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextSecondary,
        maxLines = 1,
        modifier = Modifier
            .background(
                CoineProTint.fill(CoineProColors.TextSecondary, CoineProColors.Surface),
                CoineProPillShape,
            )
            .padding(horizontal = CoineProSpacing.One, vertical = 3.dp),
    )
}

/**
 * A count in a pressable pill.
 *
 * [onClick] null leaves it a label. That is the honest shape for the reply count on a card whose
 * thread cannot be opened — a guest's read-only view, or a render — and it is drawn identically
 * rather than greyed out, because a disabled control on a card is an advertisement for something
 * the reader cannot have.
 */
@Composable
internal fun CountPill(label: String, active: Boolean, onClick: (() -> Unit)?) {
    val haptics = rememberCoineProHaptics()
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        // Gold as ink on a tinted fill, never gold text on the stage: `Accent` is a dark brown in
        // the light theme and would be unreadable as a bare label there.
        color = if (active) CoineProColors.Accent else CoineProColors.TextSecondary,
        maxLines = 1,
        modifier = Modifier
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable {
                        haptics.select()
                        onClick()
                    }
                },
            )
            .background(
                color = if (active) {
                    CoineProTint.fill(CoineProColors.Accent, CoineProColors.Surface)
                } else {
                    Color.Transparent
                },
                shape = CoineProPillShape,
            )
            .border(1.dp, CoineProColors.Border, CoineProPillShape)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.Half),
    )
}

/**
 * The five reactions the route accepts, and no others.
 *
 * Built from [CommunityReactions.ALLOWED] rather than from an emoji keyboard, because
 * `community_react` refuses anything outside that tuple with `400 {"detail":"ایموجی مجاز نیست."}`
 * — a keyboard would offer several hundred taps of which five work.
 *
 * On a card with no [onReact] the row is read-only and shows only the reactions somebody has
 * actually left, which is what the feed wants: five inert emoji under every post would be five rows
 * of decoration down a scrolling list.
 */
@Composable
private fun ReactionRow(post: CommunityPost, onReact: ((String) -> Unit)?) {
    val haptics = rememberCoineProHaptics()
    val emojis = if (onReact == null) post.reactions.keys.toList() else CommunityReactions.ALLOWED
    if (emojis.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        contentPadding = PaddingValues(0.dp),
    ) {
        items(emojis.size) { index ->
            val emoji = emojis[index]
            val count = post.reactions[emoji] ?: 0
            Text(
                // The count is a prose count, so Persian digits — and it is omitted entirely at
                // zero rather than drawn as «۰», which reads as a reaction nobody left rather than
                // as one nobody has left yet.
                text = if (count > 0) emoji + " " + count.toPersianDigits() else emoji,
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextSecondary,
                modifier = Modifier
                    .then(
                        if (onReact == null) {
                            Modifier
                        } else {
                            Modifier.clickable {
                                haptics.select()
                                onReact(emoji)
                            }
                        },
                    )
                    .background(CoineProColors.SurfaceElevated, CoineProPillShape)
                    .padding(horizontal = CoineProSpacing.One, vertical = 3.dp),
            )
        }
    }
}

/**
 * How much of a post the feed shows.
 *
 * Four lines. The route caps a post at two thousand characters, which is about thirty lines on a
 * phone, and a feed of those is one post per screen — a list nobody scrolls. Four is enough to know
 * whether the post is for you and short enough that six of them fit.
 */
private const val FEED_LINES = 4
