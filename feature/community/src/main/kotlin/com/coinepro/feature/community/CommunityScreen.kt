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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.Image
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.community.CommunityCategory
import com.coinepro.core.community.CommunityController
import com.coinepro.core.community.CommunityError
import com.coinepro.core.community.CommunityPost
import com.coinepro.core.community.CommunityUiState
import com.coinepro.core.community.NetworkCommunityGateway
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
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSheetSearch
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.pageAccentInk
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.designsystem.rowMotion

/**
 * The board.
 *
 * ### Whose board it is
 *
 * The app's. Not CoinePro-FX's academy board, which is what this screen used to show and which
 * sits behind a forex sign-in and a VIP tier — so that a crypto reader was told the community was
 * absent and a forex reader without a subscription was told to buy one. It is served from
 * TradeYar's host and tied to neither platform's account; a guest reads it, and anyone who has
 * chosen a name writes on it. That is the owner's instruction and it is also why the screen has no
 * «ورود» and no «اشتراک» button: there is nothing to sign into.
 *
 * ### The name
 *
 * Reading needs nothing. The first time a reader tries to write — a post, a like, a reply — the
 * screen asks for a display name, once, in a card that explains what it is: the only thing anybody
 * else ever sees. The controller keeps the name in the app's preferences and mirrors it into
 * [CommunityUiState.displayName], so the composer knows whether to ask before the first request
 * rather than after the server refuses it.
 *
 * ### The tabs the reference has, and the six this one has
 *
 * «For you», «Editors' picks» and «Following» are three products, not three filters: a
 * recommendation engine, an editorial desk and a social graph. This board has none of the three
 * and inventing any of them would be a tab that either shows the same list under a different name
 * or shows nothing. What it *does* have is a five-way category on every post, enforced server-side
 * on the way in and filtered server-side on the way out, so the strip is that — «همه» plus the
 * server's own five.
 *
 * ### The composer
 *
 * Inline and collapsed by default rather than a floating button over the list. A post here is five
 * to two thousand characters of Persian prose — nobody writes one by accident — and a permanent
 * button over a feed of other people's posts is an invitation to write before reading. The rules
 * that are the server's stay the server's: the length bounds are checked here so the send button
 * can be off rather than the reader discovering them from a refusal; the link ban and the rate
 * limit are not copied, because a client-side copy of somebody else's moderation rules is a copy
 * that goes stale. A refusal comes back in the server's own sentence and the text stays in the box.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    controller: CommunityController,
    onOpenThread: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether this screen is drawn inside another that has already named the page.
     *
     * True under the Ideas switch, where a heading here is a second name for a page that has one —
     * «سیگنال‌ها» under a key that already says «سیگنال‌ها», and the reader pays a headline and its
     * padding for it on every switch. The header row stays, because the actions in it are this
     * screen's own and belong nowhere else; only the words go.
     *
     * False on the route of its own, which is still registered and still reachable — a saved back
     * stack or a deep link opens exactly this screen, and there it does need its name.
     */
    embedded: Boolean = false,
) {
    LaunchedEffect(controller) { controller.start() }
    val state by controller.state.collectAsStateWithLifecycle()
    var composing by rememberSaveable { mutableStateOf(false) }
    // Whether the name card is open, and what the reader was trying to do when it opened. The
    // action runs once the name is confirmed so a like that asked for a name is still a like.
    var naming by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var leaderboard by rememberSaveable { mutableStateOf(false) }
    val mode = communityMode(state)
    val clipboard = LocalClipboardManager.current
    val reported = stringResource(R.string.community_reported)
    val copied = stringResource(R.string.community_copied)

    LaunchedEffect(state.named) {
        if (state.named && naming) {
            naming = false
            pending?.invoke()
            pending = null
        }
    }

    /** Runs [action] now, or after the reader has chosen a name. */
    fun withName(action: () -> Unit) {
        if (state.named) {
            action()
        } else {
            pending = action
            naming = true
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(CoineProColors.Stage),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        CoineProListHeader(
            title = if (embedded) null else stringResource(R.string.community_title),
            subtitle = if (embedded) {
                null
            } else {
                state.displayName?.let { stringResource(R.string.community_name_as, it) }
                    ?: stringResource(R.string.community_subtitle)
            },
            actions = {
                CoineProHeaderAction(
                    icon = if (composing) CoineProIcons.Close else CoineProIcons.Add,
                    label = stringResource(
                        if (composing) R.string.community_compose_close else R.string.community_compose,
                    ),
                    onClick = {
                        if (composing) composing = false else withName { composing = true }
                    },
                )
                CoineProHeaderAction(
                    icon = DesignR.drawable.icon_filled_star,
                    label = stringResource(R.string.community_leaderboard),
                    onClick = {
                        controller.loadLeaderboard()
                        leaderboard = true
                    },
                )
                CoineProHeaderAction(
                    icon = CoineProIcons.Refresh,
                    label = stringResource(R.string.community_refresh),
                    onClick = controller::refresh,
                )
            },
        )

        // The name card sits where the composer would, and only while it is needed: the reader
        // asked to write, or the server said it holds no name for this key.
        if ((naming || mode == CommunityMode.UNREGISTERED) && mode != CommunityMode.LOCKED) {
            NameCard(
                state = state,
                onConfirm = controller::register,
                onDismiss = if (mode == CommunityMode.UNREGISTERED) null else {
                    {
                        naming = false
                        pending = null
                    }
                },
            )
        }

        // The composer sits above the strip rather than inside the list, so it does not scroll away
        // mid-sentence — and only for a reader who can publish.
        if (composing && state.named && mode != CommunityMode.LOCKED) {
            Composer(
                posting = state.posting,
                refusal = state.serverText?.takeIf { state.error == CommunityError.REFUSED },
                onChangeName = { naming = true },
                onSubmit = { text, category, image ->
                    controller.dismissError()
                    controller.submit(text, category, image)
                },
                onDismissRefusal = controller::dismissError,
            )
        }

        state.notice?.let { notice ->
            // The server's own sentence — «منتشر شد.» — shown verbatim. Their wording is theirs.
            NoticeStrip(text = notice, accent = CoineProColors.Accent, onDismiss = {
                controller.dismissNotice()
                composing = false
            })
        }

        // A refused write while the board is on screen: said beside the posts, not instead of them.
        if (!composing && state.posts.isNotEmpty() && state.error != null && state.error != CommunityError.UNREGISTERED) {
            NoticeStrip(
                text = state.serverText ?: stringResource(
                    if (state.error == CommunityError.REFUSED) R.string.community_refused else R.string.community_unavailable,
                ),
                accent = CoineProColors.Sell,
                onDismiss = controller::dismissError,
            )
        }

        // The board's own search, above the topics: it searches every published post server-side,
        // so it is not a filter over the twenty rows on screen and must not look like one. A blank
        // field puts the board back — see `CommunityController.search`.
        CoineProSheetSearch(
            value = state.query,
            onValueChange = controller::search,
            placeholder = stringResource(R.string.community_search),
            modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
        )

        // Hidden while searching: the chips filter the *board*, and a topic chip over a result list
        // would be a control that appears to narrow a search and does nothing.
        if (!state.isSearch) {
            CategoryChips(selected = state.category, onSelect = controller::setCategory)
        }

        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 8 }) togetherWith
                    (fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 12 })
            },
            label = "community-state",
        ) { shown ->
            when (shown) {
                CommunityMode.LOADING -> CentreState(stringResource(R.string.community_loading), busy = true)

                // The name card above is the control; down here the board is simply empty.
                CommunityMode.UNREGISTERED -> CoineProEmptyState(
                    icon = CoineProIcons.Assistant,
                    message = state.serverText ?: stringResource(R.string.community_unregistered),
                    modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                )
                CommunityMode.LOCKED -> CoineProEmptyState(
                    icon = CoineProIcons.Locked,
                    message = state.serverText ?: stringResource(R.string.community_locked),
                    hint = stringResource(R.string.community_locked_hint),
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
                    message = if (state.isSearch) {
                        stringResource(R.string.community_search_empty)
                    } else {
                        stringResource(R.string.community_empty)
                    },
                    hint = stringResource(R.string.community_empty_hint),
                    action = stringResource(R.string.community_compose),
                    onAction = { withName { composing = true } },
                    // "Be the first" is the whole page; grey would bury it.
                    actionIsPrimary = true,
                    modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                )

                CommunityMode.POSTS -> PostList(
                    // Handed the controller, not a picture: a card fetches its own when it scrolls
                    // into view, because a feed that loaded twenty photographs to show three would
                    // spend a reader's data on cards they never reach. See `rememberPostImage`.
                    controller = controller,
                    posts = state.posts,
                    canLoadMore = state.canLoadMore,
                    loadingMore = state.loadingMore,
                    onOpenThread = onOpenThread,
                    onLike = { id -> withName { controller.toggleLike(id) } },
                    onLoadMore = controller::loadMore,
                    // A report needs a name, like every other write on this board.
                    onReport = { id -> withName { controller.report(id, reported) } },
                    onCopy = { post ->
                        clipboard.setText(AnnotatedString(post.content))
                        controller.notice(copied)
                    },
                )
            }
        }
    }

    if (leaderboard) {
        CoineProSheet(
            title = stringResource(R.string.community_leaderboard),
            subtitle = state.leaderboard?.let { table ->
                table.myRank?.let { rank ->
                    stringResource(
                        R.string.community_leaderboard_rank,
                        rank.toPersianDigits(),
                        table.totalStudents.toPersianDigits(),
                    )
                }
            },
            onDismiss = { leaderboard = false },
        ) {
            LeaderboardBody(state = state)
        }
    }
}

/**
 * The board's scoreboard.
 *
 * Points, posts and a rank — the three numbers the route serves — and the reader's own row marked
 * rather than pulled to the top: a table that moves your row to the front is not a table you can
 * read your position off. Prose counts, so Persian digits, which is the same rule the rest of the
 * board's counters follow.
 */
@Composable
private fun LeaderboardBody(state: CommunityUiState) {
    val table = state.leaderboard
    when {
        state.leaderboardLoading && table == null -> CentreState(
            stringResource(R.string.community_loading),
            busy = true,
        )
        table == null || table.leaders.isEmpty() -> CoineProEmptyState(
            icon = CoineProIcons.Assistant,
            message = stringResource(R.string.community_leaderboard_empty),
            modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
        )
        else -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            table.leaders.forEach { leader ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CoineProShapes.small)
                        .background(
                            if (leader.isMe) {
                                CoineProTint.fill(CoineProColors.pageAccentInk)
                            } else {
                                CoineProColors.Surface
                            },
                        )
                        .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = leader.rank.toPersianDigits(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (leader.isMe) CoineProColors.pageAccentInk else CoineProColors.TextMuted,
                    )
                    Text(
                        text = leader.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoineProColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(
                            R.string.community_leaderboard_posts,
                            leader.completed.toPersianDigits(),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                    )
                    Text(
                        text = stringResource(
                            R.string.community_leaderboard_points,
                            leader.xp.toPersianDigits(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = CoineProColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PostList(
    controller: CommunityController,
    posts: List<CommunityPost>,
    canLoadMore: Boolean,
    loadingMore: Boolean,
    onOpenThread: (Long) -> Unit,
    onLike: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onReport: (Long) -> Unit,
    onCopy: (CommunityPost) -> Unit,
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
                modifier = rowMotion(),
                post = post,
                image = rememberPostImage(controller, post),
                onOpen = { onOpenThread(post.id) },
                onLike = { onLike(post.id) },
                onReport = { onReport(post.id) },
                onCopy = { onCopy(post) },
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
            Chip(
                label = stringResource(category.chipLabelRes()),
                active = active,
                onClick = {
                    // Only a change is worth a tick.
                    if (!active) haptics.select()
                    onSelect(category)
                },
            )
        }
    }
}

/** One pill of the strip, and of the composer's category row: the same shape in both places. */
@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = if (active) CoineProColors.OnAccent else CoineProColors.TextSecondary,
        maxLines = 1,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = if (active) CoineProColors.Accent else Color.Transparent,
                shape = CoineProPillShape,
            )
            .border(1.dp, if (active) CoineProColors.Accent else CoineProColors.Border, CoineProPillShape)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.Half),
    )
}

/**
 * Choosing a name.
 *
 * One field and one button, and a sentence about what the name is: the only thing anybody else
 * sees. Said plainly because the reader has just been asked for something on a screen that asked
 * for nothing to read, and «چرا؟» is the question the card has to answer before the field does.
 *
 * The server's refusal — taken, too short, a character outside the rules — goes under the field
 * the reader is still standing in, in the server's own words.
 */
@Composable
private fun NameCard(
    state: CommunityUiState,
    onConfirm: (String) -> Unit,
    onDismiss: (() -> Unit)?,
) {
    var draft by rememberSaveable { mutableStateOf(state.displayName.orEmpty()) }
    val trimmed = draft.trim()
    val min = NetworkCommunityGateway.MIN_NAME_LENGTH
    val max = NetworkCommunityGateway.MAX_NAME_LENGTH
    val sendable = trimmed.length in min..max && !state.registering && trimmed != state.displayName

    CoineProCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CoineProSpacing.Gutter),
        accent = CoineProColors.Accent,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Text(
                text = stringResource(R.string.community_name_title),
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.community_name_body),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
            CoineProTextField(
                value = draft,
                onValueChange = { if (it.length <= max) draft = it },
                label = stringResource(R.string.community_name_label),
                enabled = !state.registering,
                isError = state.nameError != null,
                supporting = state.nameError ?: stringResource(
                    R.string.community_name_rule,
                    min.toPersianDigits(),
                    max.toPersianDigits(),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoineProPrimaryButton(
                    text = stringResource(R.string.community_name_confirm),
                    onClick = { onConfirm(trimmed) },
                    enabled = sendable,
                    modifier = Modifier.weight(1f),
                )
                if (onDismiss != null) {
                    CoineProSecondaryButton(
                        text = stringResource(R.string.community_compose_close),
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

/**
 * Writing a post.
 *
 * The counter is shown from the moment there is any text, not only once the limit is close: a
 * reader who has written nineteen hundred characters and finds out at two thousand has lost the
 * end of their post. It is a **prose count**, so Persian digits.
 *
 * A refusal keeps the text. The server said which rule the text broke; taking the text away with
 * the sentence would leave the reader retyping a paragraph to remove one link.
 */
@Composable
private fun Composer(
    posting: Boolean,
    refusal: String?,
    onChangeName: () -> Unit,
    onSubmit: (String, CommunityCategory, ByteArray?) -> Unit,
    onDismissRefusal: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(CommunityCategory.DEFAULT) }
    // The chosen photograph, already downscaled and JPEG-encoded — see `CommunityPhoto.encode`.
    //
    // Plain `remember` and not `rememberSaveable`: a few hundred kilobytes of image is far past
    // what a saved-instance `Bundle` will carry, and a `TransactionTooLargeException` on rotation
    // would crash the app to preserve a picture the reader can pick again in two taps.
    var picture by remember { mutableStateOf<ByteArray?>(null) }
    var preparing by remember { mutableStateOf(false) }
    val trimmed = text.trim()
    val sendable = trimmed.length in MIN_POST..MAX_POST && !posting && !preparing
    val haptics = rememberCoineProHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        preparing = true
        scope.launch {
            picture = CommunityPhoto.encode(context, uri)
            preparing = false
        }
    }

    CoineProCard(modifier = Modifier.fillMaxWidth().padding(horizontal = CoineProSpacing.Gutter), elevated = true) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    if (it.length <= MAX_POST) text = it
                    if (refusal != null) onDismissRefusal()
                },
                label = { Text(stringResource(R.string.community_compose_label)) },
                // Room for a paragraph without becoming a page. A single line would make a
                // two-thousand-character limit look like a joke.
                minLines = 4,
                // Twelve, not eight. The ceiling is eight thousand characters now — «طول متنش
                // بیشتر بشه» — and a box that shows a twelfth of what it will accept makes a long
                // post something the reader writes through a letterbox.
                maxLines = 12,
                enabled = !posting,
                isError = refusal != null,
                supportingText = {
                    Text(
                        text = refusal ?: stringResource(R.string.community_compose_rules),
                        color = if (refusal != null) CoineProColors.Sell else CoineProColors.TextMuted,
                    )
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            ComposerPicture(
                picture = picture,
                preparing = preparing,
                enabled = !posting,
                onPick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onClear = { picture = null },
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                items(CommunityCategory.entries.toList(), key = CommunityCategory::name) { option ->
                    Chip(
                        label = stringResource(option.chipLabelRes()),
                        active = option == category,
                        onClick = {
                            if (option != category) haptics.select()
                            category = option
                        },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.community_name_change),
                        style = CoineProTextStyles.Eyebrow,
                        color = CoineProColors.Accent,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.clickable(onClick = onChangeName).padding(vertical = 4.dp),
                    )
                }
                CoineProPrimaryButton(
                    text = stringResource(R.string.community_send),
                    onClick = {
                        onSubmit(trimmed, category, picture)
                        // Kept until the server has spoken: a refused post must still be here, and
                        // so must its picture — the refusal is usually about the text, and making
                        // the reader find the photograph again to remove one link is a punishment.
                        if (refusal == null) {
                            text = ""
                            picture = null
                        }
                    },
                    enabled = sendable,
                )
            }
        }
    }
}

/**
 * The composer's picture row: a button while there is none, and the picture itself once there is.
 *
 * ### One picture, and it is shown at the size it was chosen
 *
 * A reader who attached a photograph and cannot see it does not know whether the tap worked, so
 * the preview is the picture and not a filename or a paperclip. It is capped in height rather than
 * in width because a chart screenshot is wide and a phone photograph is tall, and a fixed square
 * would crop the middle out of both; `ContentScale.Fit` inside a bounded height gives each its own
 * shape.
 *
 * The remove control is a second button under it rather than an X on the corner of the image. An X
 * over a picture is a target the size of a fingernail sitting on top of the thing it destroys, and
 * on a board where the picture is often the whole point of the post that is the wrong risk to take
 * to save a row.
 */
@Composable
private fun ComposerPicture(
    picture: ByteArray?,
    preparing: Boolean,
    enabled: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val decoded = remember(picture) { picture?.let(CommunityPhoto::preview) }
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
        decoded?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.community_photo_chosen),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = COMPOSER_PICTURE_HEIGHT)
                    .clip(MaterialTheme.shapes.medium),
            )
        }
        // `CoineProSecondaryButton` has no disabled state, and giving it one for this would be a
        // new visual state in the design system for one row. While the picture is being prepared,
        // or while the post is in flight, the button is drawn and does nothing — which is a second
        // tap that changes nothing rather than a control that looks broken.
        val idle = enabled && !preparing
        CoineProSecondaryButton(
            text = when {
                preparing -> stringResource(R.string.community_photo_preparing)
                decoded != null -> stringResource(R.string.community_photo_remove)
                else -> stringResource(R.string.community_photo_add)
            },
            onClick = {
                if (!idle) return@CoineProSecondaryButton
                if (decoded != null) onClear() else onPick()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Tall enough to read a chart screenshot in, short enough to leave the text field on screen. */
private val COMPOSER_PICTURE_HEIGHT = 220.dp

/** The server's own word about something the reader just did, until they dismiss it. */
@Composable
private fun NoticeStrip(text: String, accent: Color, onDismiss: () -> Unit) {
    CoineProCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter),
        accent = accent,
        onClick = onDismiss,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextPrimary,
                textAlign = TextAlign.Right,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.community_dismiss),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
        }
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

/** `MIN_POST = 5` in `app_community.py`. Mirrored so the send button can be off rather than lying. */
private const val MIN_POST = NetworkCommunityGateway.MIN_POST_LENGTH

/** `MAX_TEXT = 2000`, on both write routes. */
private const val MAX_POST = NetworkCommunityGateway.MAX_POST_LENGTH
