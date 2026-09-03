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
 * Why the board is not on screen, or why the last thing the reader did was refused.
 *
 * Five answers rather than one, because each has a different control under it and a screen that
 * cannot tell them apart puts the wrong one there:
 *
 * * [NETWORK] — try again.
 * * [UNREGISTERED] — choose a name. `401` from a write with a key the server has no name for.
 * * [LOCKED] — nothing to press. `403`: the key is banned, and the server's own sentence about it
 *   is carried separately in [CommunityUiState.serverText].
 * * [REFUSED] — fix the text. `400`: a link, a phone number, too short; the sentence says which.
 * * [UNREADABLE] — nothing anybody can press. Rows arrived and none of them parsed, which is a
 *   report to send rather than a state to retry out of.
 */
enum class CommunityError {
    NETWORK,
    UNREGISTERED,
    LOCKED,
    REFUSED,
    UNREADABLE,
}

/**
 * The feed's state.
 *
 * @param displayName the name this install writes under, or null before the reader has chosen
 *   one. Read from [CommunityIdentityStore] and kept current from it, so the composer knows
 *   whether to ask for a name before the first request rather than after.
 * @param registering a name is on its way to the server.
 * @param nameError the server's sentence about the last name that was refused — taken, too
 *   short, a character outside the rules — or null.
 * @param serverText the backend's own words for whatever [error] names, where it gave any. Kept
 *   beside the enum rather than instead of it: the enum decides which control to draw, the sentence
 *   decides what the reader is told, and neither substitutes for the other.
 * @param notice the one-line answer to something the reader just did — a post published, a
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
    val displayName: String? = null,
    val registering: Boolean = false,
    val nameError: String? = null,
    val error: CommunityError? = null,
    val serverText: String? = null,
    val notice: String? = null,
    /** How many rows the last page carried but this build could not read. See [CommunityFeedPage]. */
    val unreadable: Int = 0,
    /**
     * What the reader is searching for, or blank.
     *
     * Not blank means [posts] is a **result list** rather than a page of the board: the category
     * chips do not apply to it, «بیشتر» is not offered — the route answers one page and no cursor —
     * and the empty state says "nothing matched" rather than "nobody has posted".
     */
    val query: String = "",
    val searching: Boolean = false,
    /** The board's scoreboard, once somebody has asked for it. See [loadLeaderboard]. */
    val leaderboard: CommunityLeaderboard? = null,
    val leaderboardLoading: Boolean = false,
) {
    /** Whether the list on screen is a search result rather than the board itself. */
    val isSearch: Boolean get() = query.isNotBlank()

    /** Nothing to show and nothing loading — the case that needs an empty state rather than a spinner. */
    val empty: Boolean get() = posts.isEmpty() && !loading && error == null

    /** Whether a «بیشتر» control belongs at the foot of the list. */
    val canLoadMore: Boolean
        get() = posts.isNotEmpty() && !endReached && !loadingMore && error == null && !isSearch

    /** Whether this install can write. Reading never needs a name. */
    val named: Boolean get() = !displayName.isNullOrBlank()
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
 * The community's state, for the feed, for one thread, and for who this install is.
 *
 * One controller rather than two, and for the same reason [com.coinepro.core.academy] keeps its
 * lesson and its roadmap together: the two screens share the thing that actually changes. Liking a
 * post inside a thread has to move the count on the card behind it, and replying has to move the
 * reply count — with two controllers the reader would come back to a feed still showing the numbers
 * from before they acted, which reads as an app that did not save what they did.
 *
 * ### The name
 *
 * The board is readable by anyone and writable by anyone who has chosen a name. The name comes
 * from [CommunityIdentityStore] and is mirrored into [CommunityUiState.displayName] so a screen
 * can ask for one *before* the first write instead of after the server refuses it; the refusal is
 * still handled — [CommunityError.UNREGISTERED] — for the day the store and the server disagree,
 * and on that day the store is corrected from the server rather than the other way round.
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
 * incremented locally. The server counts rows and returns the count, so a local `+1` on a post the
 * reader had already liked from another device would show a number that disagrees with the board.
 */
class CommunityController(
    private val gateway: CommunityGateway,
    private val identity: CommunityIdentityStore,
    private val scope: CoroutineScope,
) {
    private companion object {
        /** The route answers an empty list under two characters; asking for one is a wasted trip. */
        const val MIN_QUERY = 2
    }

    private val _state = MutableStateFlow(CommunityUiState())
    val state: StateFlow<CommunityUiState> = _state.asStateFlow()

    private val _thread = MutableStateFlow(CommunityThreadUiState())
    val thread: StateFlow<CommunityThreadUiState> = _thread.asStateFlow()

    private var feedJob: Job? = null
    private var threadJob: Job? = null

    init {
        scope.launch {
            identity.displayName.collect { name -> _state.update { it.copy(displayName = name) } }
        }
    }

    /** Loads the first page once. Called when the screen appears; safe to call on every entry. */
    fun start() {
        if (_state.value.posts.isEmpty() && feedJob?.isActive != true && _state.value.error == null) {
            load(page = 1, replacing = true)
        }
    }

    fun refresh() = load(page = 1, replacing = true)

    fun retry() = load(page = 1, replacing = true)

    /**
     * Chooses the name this install writes under.
     *
     * The server's answer is what the store records — its normalised spelling, not what was typed
     * — so the name on screen is the name other readers see. A refusal leaves the store alone and
     * puts the server's sentence in [CommunityUiState.nameError], where the form shows it under the
     * field the reader is still standing in.
     */
    fun register(displayName: String) {
        val name = displayName.trim()
        if (_state.value.registering) return
        if (name.length !in NetworkCommunityGateway.MIN_NAME_LENGTH..NetworkCommunityGateway.MAX_NAME_LENGTH) {
            _state.update { it.copy(nameError = null) }
            return
        }
        _state.update { it.copy(registering = true, nameError = null) }
        scope.launch {
            runCatching { gateway.register(name) }
                .onSuccess { member ->
                    identity.setDisplayName(member.displayName)
                    _state.update { it.copy(registering = false, displayName = member.displayName, nameError = null) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            registering = false,
                            nameError = failure.serverText() ?: failure.message,
                            // A ban is a state of the board, not of the form.
                            error = if (failure is CommunityLockedException) CommunityError.LOCKED else it.error,
                            serverText = if (failure is CommunityLockedException) failure.serverText else it.serverText,
                        )
                    }
                }
        }
    }

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
     * Only when the server actually published it, which on this board is every post it did not
     * refuse at the door. A refusal — a link, a phone number — comes back as [CommunityError.REFUSED]
     * with the server's sentence about which rule, and the text stays in the composer for the
     * reader to fix.
     */
    fun submit(
        content: String,
        category: CommunityCategory = CommunityCategory.DEFAULT,
        /** The encoded picture the reader attached, or null. See [CommunityGateway.post]. */
        image: ByteArray? = null,
    ) {
        if (_state.value.posting) return
        _state.update { it.copy(posting = true, error = null, serverText = null, notice = null) }
        scope.launch {
            runCatching { gateway.post(content, category, image) }
                .onSuccess { outcome ->
                    _state.update { it.copy(posting = false, notice = outcome.message) }
                    if (outcome.published) refresh()
                }
                .onFailure { failure -> onWriteFailure(failure) }
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
                    onWriteFailure(failure)
                }
        }
    }

    /** Toggles one emoji. The counts that come back replace the whole map, which is what the route sends. */
    fun react(postId: Long, emoji: String) {
        if (!CommunityReactions.allows(emoji)) return
        scope.launch {
            runCatching { gateway.react(postId, emoji) }
                .onSuccess { outcome -> applyToPost(postId) { it.copy(reactions = outcome.counts) } }
                .onFailure { failure -> onWriteFailure(failure) }
        }
    }

    /**
     * Reports a post.
     *
     * The card is **not** removed. Three reports are what hides a post, and hiding it after one
     * would tell a reporter their single tap took it down — which is both untrue and an invitation
     * to press it on anything disagreeable.
     */
    fun report(postId: Long, notice: String? = null) {
        scope.launch {
            runCatching { gateway.report(postId) }
                .onSuccess { _state.update { current -> current.copy(notice = notice) } }
                .onFailure { failure -> onWriteFailure(failure) }
        }
    }

    /**
     * Search the board — the route's own `q`, at least two characters.
     *
     * A search **replaces** the list rather than filtering it in place, because the server searches
     * every published post and the client holds one page: filtering here would search twenty rows
     * and call it the board. Below two characters, and on a cleared field, the feed comes back —
     * the reader is put where they were rather than looking at an empty screen with a stale query.
     *
     * Debounced by the caller, not here: this is what a search *is*, and a controller that waited
     * would make the one legitimate immediate search — the submit key — wait too.
     */
    fun search(query: String) {
        val trimmed = query.trim()
        _state.update { it.copy(query = query) }
        feedJob?.cancel()
        if (trimmed.length < MIN_QUERY) {
            // Back to the board, and only when there was a search to come back *from*: an empty
            // field on first paint must not re-fetch a page the screen has just asked for.
            if (_state.value.posts.isEmpty() || _state.value.searching) load(page = 1, replacing = true)
            _state.update { it.copy(searching = false) }
            return
        }
        _state.update { it.copy(searching = true, loading = it.posts.isEmpty(), error = null, serverText = null) }
        feedJob = scope.launch {
            runCatching { gateway.search(trimmed) }
                .onSuccess { found ->
                    _state.update {
                        it.copy(
                            posts = found,
                            loading = false,
                            refreshing = false,
                            loadingMore = false,
                            // A result list has no next page, and offering one would fetch the
                            // board's second page under a search's heading.
                            endReached = true,
                            unreadable = 0,
                        )
                    }
                }
                .onFailure { failure -> onFailure(failure) }
        }
    }

    /** Clears the search and puts the board back. */
    fun clearSearch() = search("")

    /**
     * The board's scoreboard, read on demand.
     *
     * Not part of the feed's own load: it is a sheet somebody opens, it costs a request, and a
     * reader who never opens it should never pay for one. Re-read on each open rather than cached,
     * because the numbers move as the board does and a stale table is a wrong table.
     */
    fun loadLeaderboard() {
        if (_state.value.leaderboardLoading) return
        _state.update { it.copy(leaderboardLoading = true) }
        scope.launch {
            val table = runCatching { gateway.leaderboard() }.getOrNull()
            _state.update { it.copy(leaderboard = table, leaderboardLoading = false) }
        }
    }

    /**
     * Say one line to the reader, for something this controller did not do itself.
     *
     * The clipboard is the case: the screen copies a post's text and the answer belongs in the same
     * strip every other answer on this board appears in, rather than in a second kind of message.
     */
    /**
     * The bytes of a post's picture, for a card that has scrolled into view.
     *
     * On the controller rather than in the screen because the screen has no gateway, and null
     * rather than a throw because a picture that will not load is one empty frame in a list — see
     * [CommunityGateway.image].
     */
    suspend fun imageOf(post: CommunityPost): ByteArray? = gateway.image(post)

    fun notice(text: String) {
        _state.update { it.copy(notice = text) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
        _thread.update { it.copy(notice = null) }
    }

    /** Clears the last refusal, once the reader has seen it. The board itself is untouched. */
    fun dismissError() {
        _state.update { current ->
            if (current.posts.isEmpty() && current.error != CommunityError.REFUSED) {
                current
            } else {
                current.copy(error = null, serverText = null)
            }
        }
        _thread.update { it.copy(error = null, serverText = null) }
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
                // The thread's copy of the post is the fresher one — it was fetched just now, and
                // this route resolves `liked` for the caller — so the card behind it is brought up
                // to date rather than left showing an older count.
                applyToPost(postId) { loaded.post }
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
                    if (outcome.published) {
                        fetchThread(postId)
                        applyToPost(postId) { it.copy(replyCount = it.replyCount + 1) }
                    }
                }
                .onFailure { failure ->
                    forgetNameIfUnregistered(failure)
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

    /** Forgets the board — for a sign-out that wipes the app's state. The name is kept: it is not a session. */
    fun clear() {
        feedJob?.cancel()
        threadJob?.cancel()
        _state.value = CommunityUiState(displayName = _state.value.displayName)
        _thread.value = CommunityThreadUiState()
    }

    /**
     * A write was refused. The board stays; the refusal is reported beside it.
     *
     * A `401` here means the store and the server disagree about whether this install has a name
     * — the server wins, and the cached name is dropped so the next write asks for one.
     */
    private suspend fun onWriteFailure(failure: Throwable) {
        forgetNameIfUnregistered(failure)
        _state.update {
            it.copy(posting = false, error = failure.toCommunityError(), serverText = failure.serverText())
        }
    }

    private suspend fun forgetNameIfUnregistered(failure: Throwable) {
        if (failure.toCommunityError() == CommunityError.UNREGISTERED) identity.setDisplayName(null)
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
 * Which of the five answers a thrown request is.
 *
 * `401` is the no-name case and nothing else: the only thing on these routes that produces one is
 * a write from a key the server holds no name for. `403` and `400` have already become their own
 * types at the gateway, where the refusals could still be told apart by status; by the time they
 * reach here they are types rather than numbers.
 */
internal fun Throwable.toCommunityError(): CommunityError = when {
    this is CommunityLockedException -> CommunityError.LOCKED
    this is CommunityRefusedException -> CommunityError.REFUSED
    this is HttpException && code() == 401 -> CommunityError.UNREGISTERED
    else -> CommunityError.NETWORK
}

/** The server's own sentence, where it wrote one. See `ServerMessage` for why not `message`. */
internal fun Throwable.serverText(): String? = when (this) {
    is CommunityLockedException -> serverText
    is CommunityRefusedException -> serverText
    is CommunityNameTakenException -> serverText
    else -> serverTextOrNull()
}
