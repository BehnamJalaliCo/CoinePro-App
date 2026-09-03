package com.coinepro.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.community.CommunityController
import com.coinepro.core.community.CommunityError
import com.coinepro.core.community.CommunityReply
import com.coinepro.core.designsystem.CoineProAvatar
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProErrorState
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProListHeader
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.rowMotion
import com.coinepro.core.model.AvatarBase
import com.coinepro.core.model.AvatarRing
import com.coinepro.core.model.AvatarSpec

/**
 * One post, everything published under it, and a box to add to it.
 *
 * ### Why the feed needs this page at all
 *
 * Because a card with a reply count and no way to read the replies is a promise the app does not
 * keep. `GET …/posts/{pid}` is the only route that returns them — the feed's rows carry
 * `replies_count` and nothing else — so a board without this page is a board where every
 * conversation is invisible.
 *
 * ### The name
 *
 * Reading needs none. The reply box, the like and the crown do, and a reader without one is shown
 * the same sentence the feed shows — «ابتدا یک نام انتخاب کنید» — in place of the box, with the
 * name card one tap away on the feed. This page does not carry a second copy of that card.
 *
 * ### Replies are a flat list, and that is a decision rather than an omission
 *
 * The route carries `parent_id`, so the data is a tree. It is drawn as a list with the parent
 * *named* on a nested reply, and the reason is the width: this app is Persian, right-to-left, and
 * indenting a thread three levels deep on a 360dp phone leaves about a hundred and eighty points
 * for the words. A named parent costs one short line and reads at any depth. If threads here ever
 * get deep enough for that to fail, the fix is a nesting rule with a cap, not indentation without
 * one.
 *
 * ### The crown
 *
 * `POST …/posts/{pid}/best-reply/{rid}` is the post author's alone — anybody else gets a 403 with a
 * sentence about authorship — and this app has no way to know whether the reader is that author
 * until it asks. So the control is offered on every reply and a refusal is reported rather than
 * pre-empted, which is the honest half of that trade: hiding it from everyone would take the
 * feature away from the one person it is for, and this app cannot tell which person that is.
 */
@Composable
fun CommunityThreadScreen(
    controller: CommunityController,
    postId: Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feed by controller.state.collectAsStateWithLifecycle()
    LaunchedEffect(postId) { controller.openThread(postId) }
    // The thread is state on the controller rather than on this composable, because liking a post
    // here has to move the count on the card in the feed behind it. Closed on the way out so a
    // second thread does not open over the first one's replies.
    DisposableEffect(postId) { onDispose { controller.closeThread() } }

    val state by controller.thread.collectAsStateWithLifecycle()
    var draft by rememberSaveable(postId) { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize().background(CoineProColors.Stage),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        CoineProListHeader(
            title = stringResource(R.string.community_thread_title),
            subtitle = state.thread?.post?.author,
        )

        (state.notice ?: state.serverText?.takeIf { state.thread != null && state.error != null })?.let { line ->
            // The server's own sentence about the last thing the reader did — «منتشر شد.», or the
            // rule a reply broke — over the thread rather than instead of it.
            Text(
                text = line,
                style = MaterialTheme.typography.labelMedium,
                color = if (state.error != null) CoineProColors.Sell else CoineProColors.TextSecondary,
                textAlign = TextAlign.Right,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CoineProSpacing.Gutter),
            )
        }

        val thread = state.thread
        when {
            state.loading && thread == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CoineProThinkingDots() }

            // A published post can stop being published: three reports set its status back to
            // pending. «یافت نشد» would be wrong about what happened, and a retry would be a
            // control that repeats one answer.
            state.missing -> CoineProEmptyState(
                icon = CoineProIcons.Warning,
                message = stringResource(R.string.community_thread_gone),
                action = stringResource(R.string.community_thread_back),
                onAction = onClose,
                modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
            )

            thread == null -> CoineProErrorState(
                message = stringResource(
                    if (state.error == CommunityError.LOCKED) {
                        R.string.community_locked
                    } else {
                        R.string.community_unavailable
                    },
                ),
                detail = state.serverText,
                action = stringResource(R.string.community_retry),
                onAction = controller::retryThread,
                modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = CoineProSpacing.Gutter,
                    end = CoineProSpacing.Gutter,
                    bottom = CoineProSpacing.Three,
                ),
                verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            ) {
                item("post") {
                    CommunityPostCard(
                        post = thread.post,
                        // The card is the head of the page; a tap on it would reopen the page the
                        // reader is standing on.
                        onOpen = null,
                        onLike = { controller.toggleLike(thread.post.id) },
                        expanded = true,
                        // Larger here than in the feed: the reader chose this post, and cramping
                        // its picture is cramping the thing they opened. See `POST_IMAGE_OPEN`.
                        image = rememberPostImage(controller, thread.post),
                        onReact = { emoji -> controller.react(thread.post.id, emoji) },
                    )
                }

                item("replies-heading") {
                    Text(
                        // A prose count, so Persian digits.
                        text = stringResource(
                            R.string.community_replies_heading,
                            thread.replies.size.toPersianDigits(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = CoineProColors.TextMuted,
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = CoineProSpacing.One),
                    )
                }

                items(thread.replies, key = CommunityReply::id) { reply ->
                    Column(modifier = rowMotion().fillMaxWidth()) {
                        ReplyCard(
                            reply = reply,
                            parentAuthor = reply.parentId?.let { parent ->
                                thread.replies.firstOrNull { it.id == parent }?.author
                            },
                            onCrown = { controller.chooseBestReply(reply.id) },
                            onUncrown = { controller.chooseBestReply(CLEAR_BEST_REPLY) },
                        )
                    }
                }

                item("composer") {
                    if (feed.named) {
                        ReplyComposer(
                            draft = draft,
                            onDraft = { draft = it },
                            sending = state.replying,
                            onSend = {
                                controller.reply(draft.trim())
                                if (state.error == null) draft = ""
                            },
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.community_name_needed),
                            style = MaterialTheme.typography.bodySmall,
                            color = CoineProColors.TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.One),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One reply.
 *
 * The crowned one is a tinted card — the system's own way of saying "this one is different" without
 * adding a colour to the screen, see [com.coinepro.core.designsystem.CoineProTint] — *and* it says
 * so in a word. The tint alone was not enough: on a phone, one step of surface between two cards is
 * a difference a reader notices only when the two are adjacent, and the only thing naming the state
 * was a button offering to undo it. A reader who had not marked it themselves had to read
 * «برداشتن نشان بهترین پاسخ» backwards to learn what the card was.
 */
@Composable
private fun ReplyCard(
    reply: CommunityReply,
    parentAuthor: String?,
    onCrown: () -> Unit,
    onUncrown: () -> Unit,
) {
    CoineProCard(
        modifier = Modifier.fillMaxWidth(),
        accent = if (reply.best) CoineProColors.Accent else null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            ) {
                // The same mark the post above carries, one size down. Without it the replies read
                // as loose paragraphs under a card rather than as people answering somebody: the
                // post had a face and the answers did not.
                CoineProAvatar(
                    spec = AvatarSpec(base = AvatarBase.Initial, ring = AvatarRing.NONE),
                    initial = reply.author.trim().take(1),
                    size = 24.dp,
                    contentDescription = reply.author,
                )
                Text(
                    text = reply.author,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = CoineProColors.TextPrimary,
                    maxLines = 1,
                )
                reply.createdAt?.let { moment ->
                    Text(
                        text = PersianDateTime.moment(moment),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                }
                if (reply.best) TopicChip(label = stringResource(R.string.community_best_badge))
            }

            // Named rather than indented. See this file's header for why, on a 360dp right-to-left
            // phone, a name costs one line and an indent costs the words.
            parentAuthor?.let { name ->
                Text(
                    text = stringResource(R.string.community_reply_to, name),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    maxLines = 1,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = reply.content,
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )

            // Offered on every reply, because only the server knows who the post's author is —
            // `community_best_reply` answers 403 to anybody else. Hiding it from everyone would take
            // the feature away from the one person it is for, and this app cannot tell which person
            // that is; a refusal is reported rather than pre-empted.
            //
            // A pill rather than a full-width button. Offering it on *every* reply is what makes
            // the weight wrong: a thread of four answers drew four full-width blocks, and a page of
            // stacked buttons reads as a form to fill in rather than as a conversation to read. The
            // words are shorter for the same reason — the badge above now says which reply is the
            // best one, so the control only has to say what pressing it does.
            Row(
                // Start, so it hangs from the same edge as the words above it. `End` put it against
                // the far margin, where a lone pill reads as a floating control rather than as the
                // last line of this reply.
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
            ) {
                CountPill(
                    label = stringResource(
                        if (reply.best) R.string.community_best_clear else R.string.community_best_mark,
                    ),
                    active = reply.best,
                    onClick = if (reply.best) onUncrown else onCrown,
                )
            }
        }
    }
}

/** The reply box, which is the same rules as the composer with a shorter floor. */
@Composable
private fun ReplyComposer(
    draft: String,
    onDraft: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
) {
    val trimmed = draft.trim()
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= MAX_REPLY) onDraft(it) },
                label = { Text(stringResource(R.string.community_reply_label)) },
                minLines = 2,
                maxLines = 6,
                enabled = !sending,
                modifier = Modifier.fillMaxWidth(),
            )
            CoineProPrimaryButton(
                text = stringResource(R.string.community_send),
                onClick = onSend,
                // `MIN_REPLY = 2` on the server. A reply may be «بله», which is why the floor
                // here is two rather than the post route's five.
                enabled = trimmed.length >= MIN_REPLY && !sending,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** `rid = 0` is the route's own way of clearing the crown. See `best_reply` on the server. */
private const val CLEAR_BEST_REPLY = 0L

/** `MIN_REPLY = 2` in `app_community.py`. */
private const val MIN_REPLY = 2

/** `MAX_TEXT = 2000`, the same ceiling as a post. */
private const val MAX_REPLY = 2_000
