package com.coinepro.core.community

import com.coinepro.core.network.serverTextOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Why the board is not on screen.
 *
 * Four answers rather than one, because each has a different button under it and a screen that
 * cannot tell them apart puts the wrong one there:
 *
 * * [NETWORK] — try again.
 * * [SIGNED_OUT] — sign in. `401 {"detail":"ورود لازم است."}` from `current_student`.
 * * [LOCKED] — buy a subscription. `403` from `require_vip`, and the server's own sentence about
 *   it is carried separately in [CommunityUiState.serverText].
 * * [UNREADABLE] — nothing anybody can press. Rows arrived and none of them parsed, which is a
 *   report to send rather than a state to retry out of.
 */
enum class CommunityError {
    NETWORK,
    SIGNED_OUT,
    LOCKED,
    UNREADABLE,
}

/**
 * The feed's state.
 *
 * @param serverText the backend's own words for whatever [error] names, where it gave any. Kept
 *   beside the enum rather than instead of it: the enum decides which control to draw, the sentence
 *   decides what the reader is told, and neither substitutes for the other. Their tier message
 *   changes with their pricing; ours would not.
 * @param notice the one-line answer to something the reader just did — a post held for review, a
 *   report filed. Cleared by [dismissNotice] rather than by a timer, so a reader who looked away
 *   still sees it.
 */
data class CommunityUiState(
    val posts: List<CommunityPost> = emptyList(),
    val category: CommunityCategory? = null,
    val page: Int = 1,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val posting: Boolean = false,
    val error: CommunityError? = null,
    val serverText: String? = null,
    val notice: String? = null,
    /** How many rows the last page carried but this build could not read. See [CommunityFeedPage]. */
    val unreadable: Int = 0,
) {
    /** Nothing to show and nothing loading — the case that needs an empty state rather than a spinner. */
    val empty: Boolean get() = posts.isEmpty() && !loading && error == null

    /** Whether a «بیشتر» control belongs at the foot of the list. */
    val canLoadMore: Boolean get() = posts.isNotEmpty() && !endReached && !loadingMore && error == null
}

/** One thread's state, held separately because it outlives a scroll of the feed behind it. */
data class CommunityThreadUiState(
    val postId: Long? = null,
    val thread: CommunityThread? = null,
    val loading: Boolean = false,
    val replying: Boolean = false,
    val error: CommunityError? = null,
    val serverText: String? = null,
    val notice: String? = null,
    val missing: Boolean = false,
)

/**
 * The community's state, for the feed and for one thread.
 *
 * One controller rather than two, and for the same reason [com.coinepro.core.academy] keeps its
 * lesson and its roadmap together: the two screens share the thing that actually changes. Liking a
 * post inside a thread has to move the count on the card behind it, and replying has to move the
 * reply count — with two controllers the reader would come back to a feed still showing the numbers
 * from before they acted, which reads as an app that did not save what they did.
 *
 * ### Paging
 *
 * The route pages at twenty with no total, so [loadMore] asks for the next page and stops when one
 * comes back short. Pages are appended rather than replacing, and de-duplicated by id: a post
 * written between two page requests shifts everything down by one and would otherwise arrive twice.
 *
 * ### Liking
 *
 * Optimistic in the *control*, authoritative in the *number*. The pressed state flips immediately
 * so the tap has an answer, and the count is replaced by whatever the route says — never
 * incremented locally. The server holds likes in a Redis set and returns its cardinality, so a
 * local `+1` on a post the reader had already liked from another device would show a number that
 * disagrees with the board.
 */
class CommunityController(
    private val gateway: CommunityGateway,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(CommunityUiState())
    val state: StateFlow<CommunityUiState> = _state.asStateFlow()

    private val _thread = MutableStateFlow(CommunityThreadUiState())
    val thread: StateFlow<CommunityThreadUiState> = _thread.asStateFlow()

    private var feedJob: Job? = null
    private var threadJob: Job? = null

    /** Loads the first page once. Called when the screen appears; safe to call on every entry. */
    fun start() {
        if (_state.value.posts.isEmpty() && feedJob?.isActive != true && _state.value.error == null) {
            load(page = 1, replacing = true)
        }
    }

    fun refresh() = load(page = 1, replacing = true)

    fun retry() = load(page = 1, replacing = true)

    /**
     * Switches the category chip.
     *
     * Reloads from page one rather than filtering what is in hand: the filter is a server-side
     * `WHERE`, so the twenty posts already loaded are the newest twenty *overall* and filtering them
     * locally would show a category's three most recent posts and call it the category.
     */
    fun setCategory(category: CommunityCategory?) {
        if (_state.value.category == category) return
        _state.update { it.copy(category = category, posts = emptyList(), endReached = false) }
        load(page = 1, replacing = true)
    }

    fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return
        load(page = current.page + 1, replacing = false)
    }

    private fun load(page: Int, replacing: Boolean) {
        feedJob?.cancel()
        val had = _state.value.posts.isNotEmpty()
        _state.update {
            it.copy(
                loading = replacing && !had,
                refreshing = replacing && had,
                loadingMore = !replacing,
                error = null,
                serverText = null,
            )
        }
        feedJob = scope.launch {
            runCatching { gateway.feed(page = page, category = _state.value.category) }
                .onSuccess { fetched -> onPage(fetched, replacing) }
                .onFailure { failure -> onFailure(failure) }
        }
    }

    private fun onPage(fetched: CommunityFeedPage, replacing: Boolean) {
        _state.update { current ->
            val merged = if (replacing) {
                fetched.posts
            } else {
                // De-duplicated by id and the *older* copy is dropped, not the newer: a post whose
                // like count moved between the two requests should show the newer figure.
                val seen = fetched.posts.map(CommunityPost::id).toSet()
                current.posts.filterNot { it.id in seen } + fetched.posts
            }
            current.copy(
                posts = merged,
                page = fetched.page,
                loading = false,
                refreshing = false,
                loadingMore = false,
                endReached = fetched.last,
                unreadable = fetched.dropped,
                // Rows arrived and none of them could be read. Distinct from an empty board, which
                // is a fact about the community rather than about this build — and the only one of
                // the two worth putting a diagnostics line under.
                error = if (fetched.received > 0 && fetched.posts.isEmpty()) {
                    CommunityError.UNREADABLE
                } else {
                    null
                },
            )
        }
    }

    private fun onFailure(failure: Throwable) {
        _state.update {
            it.copy(
                loading = false,
                refreshing = false,
                loadingMore = false,
                error = failure.toCommunityError(),
                serverText = failure.serverText(),
            )
        }
    }

    /**
     * Writes a post and puts it at the top of the board, where the writer expects to find it.
     *
     * Only when the server actually published it. A post the AI moderator held goes nowhere near
     * the list — it is not on the board for anyone else either — and the reader is told so in the
     * server's own words instead. Showing a held post in the feed would be the app promising
     * something the moderation queue has not agreed to.
     */
    fun submit(content: String, category: CommunityCategory = CommunityCategory.DEFAULT) {
        if (_state.value.posting) return
        _state.update { it.copy(posting = true, error = null, serverText = null, notice = null) }
        scope.launch {
            runCatching { gateway.post(content, category) }
                .onSuccess { outcome ->
                    _state.update { it.copy(posting = false, notice = outcome.message) }
                    if (outcome.published) refresh()
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            posting = false,
                            error = failure.toCommunityError(),
                            serverText = failure.serverText(),
                        )
                    }
                }
        }
    }

    /**
     * Toggles a like, in the feed and in the open thread at once.
     *
     * Both are updated because both may be on screen: a thread opens over the feed, and a count
     * that moved in one and not the other is the sort of disagreement a reader reads as a bug in
     * the number rather than in the app.
     */
    fun toggleLike(postId: Long) {
        val known = _state.value.posts.firstOrNull { it.id == postId }
            ?: _thread.value.thread?.post?.takeIf { it.id == postId }
        val optimistic = known?.liked?.not() ?: true
        applyToPost(postId) { post -> post.copy(liked = optimistic) }
        scope.launch {
            runCatching { gateway.like(postId, currentLikes = known?.likes ?: 0) }
                .onSuccess { outcome ->
                    applyToPost(postId) { it.copy(likes = outcome.likes, liked = outcome.liked) }
                }
                .onFailure { failure ->
                    // Put it back. A control that stays pressed after the request behind it failed
                    // is a control that lied about what the server holds.
                    applyToPost(postId) { post -> post.copy(liked = known?.liked ?: false) }
                    _state.update { current ->
                        current.copy(error = failure.toCommunityError(), serverText = failure.serverText())
                    }
                }
        }
    }

    /** Toggles one emoji. The counts that come back replace the whole map, which is what the route sends. */
    fun react(postId: Long, emoji: String) {
        if (!CommunityReactions.allows(emoji)) return
        scope.launch {
            runCatching { gateway.react(postId, emoji) }
                .onSuccess { outcome -> applyToPost(postId) { it.copy(reactions = outcome.counts) } }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.toCommunityError(), serverText = failure.serverText()) }
                }
        }
    }

    /**
     * Reports a post.
     *
     * The card is **not** removed. Three reports are what pulls a post back into review, and hiding
     * it after one would tell a reporter their single tap took it down — which is both untrue and
     * an invitation to press it on anything disagreeable.
     */
    fun report(postId: Long, notice: String? = null) {
        scope.launch {
            runCatching { gateway.report(postId) }
                .onSuccess { _state.update { current -> current.copy(notice = notice) } }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.toCommunityError(), serverText = failure.serverText()) }
                }
        }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
        _thread.update { it.copy(notice = null) }
    }

    // ── one thread ───────────────────────────────────────────────────────────────────────────

    fun openThread(postId: Long) {
        threadJob?.cancel()
        _thread.value = CommunityThreadUiState(postId = postId, loading = true)
        threadJob = scope.launch { fetchThread(postId) }
    }

    fun closeThread() {
        threadJob?.cancel()
        _thread.value = CommunityThreadUiState()
    }

    fun retryThread() {
        val id = _thread.value.postId ?: return
        openThread(id)
    }

    private suspend fun fetchThread(postId: Long) {
        runCatching { gateway.thread(postId) }
            .onSuccess { loaded ->
                _thread.update { it.copy(thread = loaded, loading = false, error = null, missing = false) }
                // The thread's copy of the post is the fresher one — it was fetched just now — so
                // the card behind it is brought up to date rather than left showing an older count.
                // The `liked` flag is the exception and is kept from the feed: the detail route
                // never consults Redis, so its post always arrives with `liked` false, and copying
                // that over would un-press a like the reader can see they made.
                applyToPost(postId) { existing -> loaded.post.copy(liked = existing.liked) }
            }
            .onFailure { failure ->
                _thread.update {
                    it.copy(
                        loading = false,
                        missing = failure is CommunityPostNotFoundException,
                        error = failure.toCommunityError(),
                        serverText = failure.serverText(),
                    )
                }
            }
    }

    fun reply(content: String, parentId: Long? = null) {
        val postId = _thread.value.postId ?: return
        if (_thread.value.replying) return
        _thread.update { it.copy(replying = true, error = null, serverText = null, notice = null) }
        scope.launch {
            runCatching { gateway.reply(postId, content, parentId) }
                .onSuccess { outcome ->
                    _thread.update { it.copy(replying = false, notice = outcome.message) }
                    // Only a published reply changes the thread. One held for review is not under
                    // the post yet, and refetching would show the reader an unchanged page they
                    // would read as a lost reply — the notice is what tells them what happened.
                    if (outcome.published) {
                        fetchThread(postId)
                        applyToPost(postId) { it.copy(replyCount = it.replyCount + 1) }
                    }
                }
                .onFailure { failure ->
                    _thread.update {
                        it.copy(
                            replying = false,
                            error = failure.toCommunityError(),
                            serverText = failure.serverText(),
                        )
                    }
                }
        }
    }

    /** Crowns a reply, or clears the crown with [replyId] `0`. Author only; the route enforces it. */
    fun chooseBestReply(replyId: Long) {
        val postId = _thread.value.postId ?: return
        scope.launch {
            runCatching { gateway.bestReply(postId, replyId) }
                .onSuccess { fetchThread(postId) }
                .onFailure { failure ->
                    _thread.update {
                        it.copy(error = failure.toCommunityError(), serverText = failure.serverText())
                    }
                }
        }
    }

    /** Forgets everything, for a platform switch or a sign-out. The other backend has no community. */
    fun clear() {
        feedJob?.cancel()
        threadJob?.cancel()
        _state.value = CommunityUiState()
        _thread.value = CommunityThreadUiState()
    }

    /** Applies [change] to one post wherever it is held — the feed list, the open thread, or both. */
    private fun applyToPost(postId: Long, change: (CommunityPost) -> CommunityPost) {
        _state.update { current ->
            current.copy(posts = current.posts.map { if (it.id == postId) change(it) else it })
        }
        _thread.update { current ->
            val held = current.thread ?: return@update current
            if (held.post.id != postId) current else current.copy(thread = held.copy(post = change(held.post)))
        }
    }
}

/**
 * Which of the four answers a thrown request is.
 *
 * `401` is the sign-in case and nothing else: `current_student` is the only thing on these routes
 * that can produce one, and it produces it for exactly one reason. `403` has already become
 * [CommunityLockedException] at the gateway, where the two refusals could still be told apart by
 * status; by the time it reaches here it is a type rather than a number.
 */
internal fun Throwable.toCommunityError(): CommunityError = when {
    this is CommunityLockedException -> CommunityError.LOCKED
    this is HttpException && code() == 401 -> CommunityError.SIGNED_OUT
    else -> CommunityError.NETWORK
}

/** The server's own sentence, where it wrote one. See `ServerMessage` for why not `message`. */
internal fun Throwable.serverText(): String? = when (this) {
    is CommunityLockedException -> serverText
    else -> serverTextOrNull()
}
