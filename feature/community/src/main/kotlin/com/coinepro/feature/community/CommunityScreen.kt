package com.coinepro.feature.community

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.community.CommunityCategory
import com.coinepro.core.community.CommunityController
import com.coinepro.core.community.CommunityPost
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProErrorState
import com.coinepro.core.designsystem.CoineProHeaderAction
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProListHeader
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.designsystem.rememberCoineProHaptics

/**
 * The board.
 *
 * ### Where this screen is, and where it deliberately is not
 *
 * On CoinePro-FX only. TradeYar has no `/academy` surface and therefore no `/academy/community`
 * routes — not switched off, not empty: **absent**, in the same way it has no lessons. So the shell
 * puts `community` in its `absent` set on the crypto platform and this screen is never reached
 * there. That distinction is load-bearing in this codebase and `MenuCatalogue` states it: an absent
 * surface is not drawn, because there is nothing the reader can do about it and naming it would
 * advertise a feature that does not exist here; a locked one *is* drawn, because signing in is two
 * taps. Community is the first kind on one platform and the second kind on the other, and the two
 * must not be drawn the same way.
 *
 * ### The three tabs the reference has, and the six this one has
 *
 * «For you», «Editors' picks» and «Following» are three products, not three filters: a
 * recommendation engine, an editorial desk and a social graph. This backend has none of the three
 * and inventing any of them would be a tab that either shows the same list under a different name
 * or shows nothing. What it *does* have is a five-way category on every post, enforced server-side
 * on the way in and filtered server-side on the way out, so the strip is that — «همه» plus the
 * server's own five. It answers the same question the reference's tabs answer (which slice of this
 * board am I reading?) with the only slicing that is real here.
 *
 * ### The composer
 *
 * Inline and collapsed by default rather than a floating button over the list. A post here is five
 * to two thousand characters of Persian prose — nobody writes one by accident — and a permanent
 * button over a feed of other people's posts is an invitation to write before reading. The rules
 * that are the server's stay the server's: the length bounds are checked here so the send button
 * can be off rather than the reader discovering them from a refusal, and the moderation, the rate
 * limit and the link ban are not copied at all, because a client-side copy of somebody else's
 * moderation rules is a copy that goes stale.
 */
@Composable
fun CommunityScreen(
    controller: CommunityController,
    onOpenThread: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Offered only for [CommunityMode.SIGNED_OUT]. Null on a host with no sign-in route. */
    onSignIn: (() -> Unit)? = null,
    /** Offered only for [CommunityMode.LOCKED]. Null leaves the tier refusal stated without a sale. */
    onOpenMembership: (() -> Unit)? = null,
) {
    LaunchedEffect(controller) { controller.start() }
    val state by controller.state.collectAsStateWithLifecycle()
    var composing by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().background(CoineProColors.Stage),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        CoineProListHeader(
            title = stringResource(R.string.community_title),
            subtitle = stringResource(R.string.community_subtitle),
            actions = {
                CoineProHeaderAction(
                    icon = if (composing) CoineProIcons.Close else CoineProIcons.Add,
                    label = stringResource(
                        if (composing) R.string.community_compose_close else R.string.community_compose,
                    ),
                    onClick = { composing = !composing },
                )
                CoineProHeaderAction(
                    icon = CoineProIcons.Refresh,
                    label = stringResource(R.string.community_refresh),
                    onClick = controller::refresh,
                )
            },
        )

        // The composer sits above the strip rather than inside the list, so it does not scroll away
        // mid-sentence — and it is only offered where the board is actually readable. Asking a
        // reader who has been refused for a tier to write a post they cannot publish would be a
        // form that exists to be rejected.
        if (composing && communityMode(state) !in REFUSALS) {
            Composer(
                posting = state.posting,
                onSubmit = { text, category ->
                    controller.submit(text, category)
                    composing = false
                },
            )
        }

        state.notice?.let { notice ->
            // The server's own sentence — «منتشر شد.» or «برای بازبینیِ ادمین ارسال شد.» — shown
            // verbatim. Their wording changes with their moderation rules; ours would not.
            NoticeStrip(text = notice, onDismiss = controller::dismissNotice)
        }

        CategoryChips(selected = state.category, onSelect = controller::setCategory)

        AnimatedContent(
            targetState = communityMode(state),
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 8 }) togetherWith
                    (fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 12 })
            },
            label = "community-state",
        ) { mode ->
            when (mode) {
                CommunityMode.LOADING -> CentreState(stringResource(R.string.community_loading), busy = true)

                // Two refusals, two buttons. This is the whole reason `CommunityMode` exists.
                CommunityMode.SIGNED_OUT -> CoineProEmptyState(
                    icon = CoineProIcons.Locked,
                    message = state.serverText ?: stringResource(R.string.community_signed_out),
                    hint = stringResource(R.string.community_signed_out_hint),
                    action = onSignIn?.let { stringResource(R.string.community_sign_in) },
                    onAction = onSignIn,
                    // An invitation, not a retry: the board is behind this one press.
                    actionIsPrimary = true,
                    modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                )
                CommunityMode.LOCKED -> CoineProEmptyState(
                    icon = CoineProIcons.Locked,
                    message = state.serverText ?: stringResource(R.string.community_locked),
                    hint = stringResource(R.string.community_locked_hint),
                    action = onOpenMembership?.let { stringResource(R.string.community_membership) },
                    onAction = onOpenMembership,
                    actionIsPrimary = true,
                    modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                )

                // No retry. The next press produces the same twenty unreadable rows, and a control
                // whose every press repeats the same answer teaches the reader the app is broken.
                CommunityMode.UNREADABLE -> CoineProEmptyState(
                    icon = CoineProIcons.Warning,
                    message = stringResource(
                        R.string.community_unreadable,
                        state.unreadable.toPersianDigits(),
                    ),
                    hint = stringResource(R.string.community_unreadable_hint),
                    modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                )

                CommunityMode.ERROR -> CoineProErrorState(
                    message = stringResource(R.string.community_unavailable),
                    detail = state.serverText,
                    action = stringResource(R.string.community_retry),
                    onAction = controller::retry,
                    modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                )

                CommunityMode.NOTHING_POSTED -> CoineProEmptyState(
                    icon = CoineProIcons.Assistant,
                    message = stringResource(R.string.community_empty),
                    hint = stringResource(R.string.community_empty_hint),
                    action = stringResource(R.string.community_compose),
                    onAction = { composing = true },
                    // "Be the first" is the whole page; grey would bury it.
                    actionIsPrimary = true,
                    modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                )

                CommunityMode.POSTS -> PostList(
                    posts = state.posts,
                    canLoadMore = state.canLoadMore,
                    loadingMore = state.loadingMore,
                    onOpenThread = onOpenThread,
                    onLike = controller::toggleLike,
                    onLoadMore = controller::loadMore,
                )
            }
        }
    }
}

@Composable
private fun PostList(
    posts: List<CommunityPost>,
    canLoadMore: Boolean,
    loadingMore: Boolean,
    onOpenThread: (Long) -> Unit,
    onLike: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CoineProSpacing.Gutter,
            end = CoineProSpacing.Gutter,
            bottom = CoineProSpacing.Three,
        ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        items(posts, key = CommunityPost::id) { post ->
            CommunityPostCard(
                post = post,
                onOpen = { onOpenThread(post.id) },
                onLike = { onLike(post.id) },
            )
        }
        // An explicit control rather than a scroll trigger. The route pages at twenty with no total
        // and each page is a round trip on a connection this product is built for; a list that
        // fetched on its own would spend a reader's data because they scrolled past the end.
        if (canLoadMore || loadingMore) {
            item("more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.One),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loadingMore) {
                        CoineProThinkingDots()
                    } else {
                        CoineProSecondaryButton(
                            text = stringResource(R.string.community_more),
                            onClick = onLoadMore,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The strip of topics.
 *
 * Pills in a scrolling row rather than a filled tray, for the reason the markets tab gives about
 * its own second strip: six Persian labels are far wider than a 360dp phone, and a tray that
 * squeezed them all in would give «تجربه» the same weight as the whole board.
 */
@Composable
private fun CategoryChips(selected: CommunityCategory?, onSelect: (CommunityCategory?) -> Unit) {
    val haptics = rememberCoineProHaptics()
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        contentPadding = PaddingValues(horizontal = CoineProSpacing.Gutter),
    ) {
        items(COMMUNITY_CHIPS, key = { it?.name ?: "all" }) { category ->
            val active = category == selected
            Text(
                text = stringResource(category.chipLabelRes()),
                style = MaterialTheme.typography.labelSmall,
                color = if (active) CoineProColors.OnAccent else CoineProColors.TextSecondary,
                maxLines = 1,
                modifier = Modifier
                    .clickable {
                        // Only a change is worth a tick.
                        if (!active) haptics.select()
                        onSelect(category)
                    }
                    .background(
                        color = if (active) CoineProColors.Accent else Color.Transparent,
                        shape = CoineProPillShape,
                    )
                    .border(1.dp, CoineProColors.Border, CoineProPillShape)
                    .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.Half),
            )
        }
    }
}

/**
 * Writing a post.
 *
 * The counter is shown from the moment there is any text, not only once the limit is close: a
 * reader who has written nineteen hundred characters and finds out at two thousand has lost the
 * end of their post. It is a **prose count**, so Persian digits.
 */
@Composable
private fun Composer(
    posting: Boolean,
    onSubmit: (String, CommunityCategory) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(CommunityCategory.DEFAULT) }
    val trimmed = text.trim()
    val sendable = trimmed.length in MIN_POST..MAX_POST && !posting

    CoineProCard(modifier = Modifier.fillMaxWidth().padding(horizontal = CoineProSpacing.Gutter)) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= MAX_POST) text = it },
                label = { Text(stringResource(R.string.community_compose_label)) },
                // Room for a paragraph without becoming a page. A single line would make a
                // two-thousand-character limit look like a joke.
                minLines = 3,
                maxLines = 8,
                enabled = !posting,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                items(CommunityCategory.entries.toList(), key = CommunityCategory::name) { option ->
                    val active = option == category
                    Text(
                        text = stringResource(option.chipLabelRes()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) CoineProColors.OnAccent else CoineProColors.TextSecondary,
                        maxLines = 1,
                        modifier = Modifier
                            .clickable { category = option }
                            .background(
                                color = if (active) CoineProColors.Accent else Color.Transparent,
                                shape = CoineProPillShape,
                            )
                            .border(1.dp, CoineProColors.Border, CoineProPillShape)
                            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.Half),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            ) {
                Text(
                    text = if (trimmed.isEmpty()) {
                        stringResource(R.string.community_compose_hint, MIN_POST.toPersianDigits())
                    } else {
                        stringResource(
                            R.string.community_compose_count,
                            trimmed.length.toPersianDigits(),
                            MAX_POST.toPersianDigits(),
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Right,
                )
                CoineProPrimaryButton(
                    text = stringResource(R.string.community_send),
                    onClick = {
                        onSubmit(trimmed, category)
                        text = ""
                    },
                    enabled = sendable,
                )
            }
        }
    }
}

/** The server's own word about something the reader just did, until they dismiss it. */
@Composable
private fun NoticeStrip(text: String, onDismiss: () -> Unit) {
    CoineProCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter),
        accent = CoineProColors.Accent,
        onClick = onDismiss,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextPrimary,
            textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CentreState(message: String, busy: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().padding(CoineProSpacing.Three),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            if (busy) CoineProThinkingDots()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The two states where offering a composer would be offering a form that exists to be rejected. */
private val REFUSALS = setOf(CommunityMode.SIGNED_OUT, CommunityMode.LOCKED)

/** `if len(text) < 5` in `community_post`. Mirrored so the send button can be off rather than lying. */
private const val MIN_POST = 5

/** `if len(text) > 2000`, on both write routes. */
private const val MAX_POST = 2_000
