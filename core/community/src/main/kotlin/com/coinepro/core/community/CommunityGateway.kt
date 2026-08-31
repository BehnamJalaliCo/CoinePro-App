package com.coinepro.core.community

import com.coinepro.core.marketdata.AcademyTokenStore
import com.coinepro.core.network.serverTextOrNull
import com.google.gson.JsonElement
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The community, on CoinePro-FX only.
 *
 * ### Why there is no TradeYar implementation, not even an empty one
 *
 * Because TradeYar has no community routes. Not "has them switched off", not "answers an empty
 * list" — its OpenAPI has no `/community` path at all, in the same way it has no `/academy`. A
 * gateway wired to routes that answer 404 would turn a feature that is *absent* into one that looks
 * *broken*, which is the distinction `MenuCatalogue` is built around: an absent surface is not
 * drawn, an account-locked one is drawn and marked. So this module ships one implementation, the
 * injector binds it against the forex Retrofit only, and the shell adds `community` to its `absent`
 * set on the crypto platform. Nothing here needs a platform parameter, and that is the point —
 * there is no branch to get wrong.
 *
 * ### The credential
 *
 * Every route below is `Depends(require_vip)`, which is `current_student` plus a tier test.
 * `current_student` reads the `Authorization` header, rejects anything whose JWT scope is not
 * `academy`, and answers `401 {"detail":"ورود لازم است."}` otherwise — which is exactly what a live
 * `curl` against the host returns. That is the **same** gate `core:academy` already passes, so this
 * takes the same [AcademyTokenStore] rather than minting a second credential: the store holds one
 * twelve-hour token for the process and `NetworkFactory`'s interceptor was already taught to leave
 * an explicit `Authorization` header alone, so a second minter would be a second request per
 * twelve hours and a second thing to invalidate on sign-out.
 *
 * ### Two refusals that are not the same refusal
 *
 * `401` means "no academy token" — sign in. `403` means "your tier is not VIP" — buy a
 * subscription. They arrive as the same `HttpException` and are told apart here rather than at the
 * screen, because a screen that gets them wrong sends half its readers to a button that cannot help
 * them. See [CommunityLockedException].
 */
interface CommunityGateway {
    /**
     * One page of published posts, newest first.
     *
     * [category] null asks for everything: the handler filters only when the value is in its own
     * tuple, so an empty string and an unknown string both mean "no filter" — but sending an empty
     * string is what the route documents, so that is what a null sends.
     */
    suspend fun feed(page: Int = 1, category: CommunityCategory? = null): CommunityFeedPage

    /**
     * Published posts whose body contains [query].
     *
     * The route requires at least two characters and answers `{"items":[]}` below that rather than
     * an error, so a short query is not a failure — it is simply no result, and this returns the
     * empty list without a round trip for the same reason.
     *
     * **The author comes back masked** here and only here: `community_search` runs the username
     * through `_mask_username`, so «reza» becomes «re***». That is the server protecting the search
     * index, not a bug, and the app must not try to un-mask it by looking the post up again.
     */
    suspend fun search(query: String): List<CommunityPost>

    /** One post with its published replies. Throws on a post that is missing or unpublished. */
    suspend fun thread(id: Long): CommunityThread

    /**
     * Writes a post.
     *
     * Five characters minimum and two thousand maximum, ten posts per rolling day, and a hard
     * moderation block on contact details, links and advertising — all enforced server-side, each
     * with its own Persian sentence. The length bounds are checked here too so the composer's button
     * can be off rather than the reader discovering the rule from a failed request; the rest is not,
     * because a client-side copy of somebody else's moderation rules is a copy that goes stale.
     */
    suspend fun post(content: String, category: CommunityCategory = CommunityCategory.DEFAULT): CommunityWriteOutcome

    /** Replies to a post, or to a reply under it. */
    suspend fun reply(postId: Long, content: String, parentId: Long? = null): CommunityWriteOutcome

    /**
     * Toggles this reader's like.
     *
     * One per person, held in a Redis set, so the count is a cardinality rather than a counter and
     * pressing twice genuinely removes the like. [currentLikes] is only a fallback for a body that
     * arrived without a count; see [CommunityWire.readLike].
     */
    suspend fun like(postId: Long, currentLikes: Int = 0): CommunityLikeOutcome

    /** Toggles one emoji reaction. Refused server-side for anything outside [CommunityReactions]. */
    suspend fun react(postId: Long, emoji: String): CommunityReactionOutcome

    /** Reports a post. Three reports pull it back into the review queue. */
    suspend fun report(postId: Long)

    /**
     * Marks a reply as the best answer, or clears the mark with [replyId] `0`.
     *
     * Only the post's own author may; anyone else gets a `403` with its own sentence, which is a
     * different 403 from the tier lock and is deliberately **not** mapped to
     * [CommunityLockedException] — see the implementation.
     */
    suspend fun bestReply(postId: Long, replyId: Long)

    /** The academy leaderboard, which is the community's own scoreboard. */
    suspend fun leaderboard(): CommunityLeaderboard
}

internal interface CommunityApi {
    @GET("academy/community")
    suspend fun feed(
        @Header("Authorization") authorization: String,
        @Query("page") page: Int,
        @Query("category") category: String,
    ): JsonElement

    @GET("academy/community/search")
    suspend fun search(
        @Header("Authorization") authorization: String,
        @Query("q") query: String,
    ): JsonElement

    @GET("academy/community/{pid}")
    suspend fun thread(
        @Header("Authorization") authorization: String,
        @Path("pid") pid: Long,
    ): JsonElement

    @POST("academy/community")
    suspend fun post(
        @Header("Authorization") authorization: String,
        @Body body: PostBody,
    ): JsonElement

    @POST("academy/community/{pid}/reply")
    suspend fun reply(
        @Header("Authorization") authorization: String,
        @Path("pid") pid: Long,
        @Body body: ReplyBody,
    ): JsonElement

    @POST("academy/community/{pid}/like")
    suspend fun like(
        @Header("Authorization") authorization: String,
        @Path("pid") pid: Long,
    ): JsonElement

    @POST("academy/community/{pid}/react")
    suspend fun react(
        @Header("Authorization") authorization: String,
        @Path("pid") pid: Long,
        @Body body: ReactBody,
    ): JsonElement

    @POST("academy/community/{pid}/report")
    suspend fun report(
        @Header("Authorization") authorization: String,
        @Path("pid") pid: Long,
    ): JsonElement

    @POST("academy/community/{pid}/best-reply/{rid}")
    suspend fun bestReply(
        @Header("Authorization") authorization: String,
        @Path("pid") pid: Long,
        @Path("rid") rid: Long,
    ): JsonElement

    @GET("academy/leaderboard")
    suspend fun leaderboard(@Header("Authorization") authorization: String): JsonElement
}

internal data class PostBody(val content: String, val category: String)

/**
 * A reply body.
 *
 * `parentId` serialises to `parent_id` through the app's Gson naming policy — the same arrangement
 * `AcademyGateway`'s `ProgressBody` relies on — and Gson omits a null field entirely, which is what
 * the route's `Body(None, embed=True)` default wants. Spelling it `parent_id` here instead would
 * come out as `parent__id` under that policy.
 */
internal data class ReplyBody(val content: String, val parentId: Long?)

internal data class ReactBody(val emoji: String)

class NetworkCommunityGateway(
    retrofit: Retrofit,
    private val tokens: AcademyTokenStore,
) : CommunityGateway {

    private val api = retrofit.create(CommunityApi::class.java)

    private suspend fun auth(): String = "Bearer " + tokens.token()

    override suspend fun feed(page: Int, category: CommunityCategory?): CommunityFeedPage = mapping {
        CommunityWire.readFeed(
            body = api.feed(auth(), page.coerceAtLeast(1), category?.wire.orEmpty()),
            page = page.coerceAtLeast(1),
        )
    }

    override suspend fun search(query: String): List<CommunityPost> {
        val term = query.trim()
        // The route's own floor. Asking below it costs a round trip to be told `{"items":[]}`, and
        // the reader is mid-word rather than finished typing.
        if (term.length < MIN_SEARCH_LENGTH) return emptyList()
        return mapping { CommunityWire.readSearch(api.search(auth(), term)) }
    }

    override suspend fun thread(id: Long): CommunityThread = mapping {
        CommunityWire.readThread(api.thread(auth(), id))
            // A 200 whose body held no readable post. Raised as the route's own answer for a post
            // that is not there, because from the reader's side the two are the same fact and one
            // of them already has a screen.
            ?: throw CommunityPostNotFoundException(id)
    }

    override suspend fun post(content: String, category: CommunityCategory): CommunityWriteOutcome {
        val text = content.trim()
        require(text.length >= MIN_POST_LENGTH) { "post too short" }
        require(text.length <= MAX_POST_LENGTH) { "post too long" }
        return mapping {
            CommunityWire.readWriteOutcome(api.post(auth(), PostBody(text, category.wire)))
        }
    }

    override suspend fun reply(postId: Long, content: String, parentId: Long?): CommunityWriteOutcome {
        val text = content.trim()
        require(text.length >= MIN_REPLY_LENGTH) { "reply too short" }
        require(text.length <= MAX_POST_LENGTH) { "reply too long" }
        return mapping {
            CommunityWire.readWriteOutcome(api.reply(auth(), postId, ReplyBody(text, parentId)))
        }
    }

    override suspend fun like(postId: Long, currentLikes: Int): CommunityLikeOutcome = mapping {
        CommunityWire.readLike(api.like(auth(), postId), fallback = currentLikes)
    }

    override suspend fun react(postId: Long, emoji: String): CommunityReactionOutcome {
        require(CommunityReactions.allows(emoji)) { "emoji not accepted by the route" }
        return mapping { CommunityWire.readReaction(api.react(auth(), postId, ReactBody(emoji))) }
    }

    override suspend fun report(postId: Long) {
        mapping { api.report(auth(), postId) }
    }

    /**
     * Crowning a reply, with the one 403 on this surface that is **not** a tier lock.
     *
     * `community_best_reply` answers `403 {"detail":"فقط نویسندهٔ پست می‌تواند پاسخِ برگزیده را
     * تعیین کند."}` to anyone who is not the post's author. Mapping that through [mapping] would
     * tell a paying VIP reader to buy a subscription, so this call is deliberately outside it and
     * the server's sentence reaches the screen unchanged.
     */
    override suspend fun bestReply(postId: Long, replyId: Long) {
        api.bestReply(auth(), postId, replyId)
    }

    override suspend fun leaderboard(): CommunityLeaderboard = mapping {
        CommunityWire.readLeaderboard(api.leaderboard(auth()))
    }

    /**
     * Runs a call and turns the tier refusal into a type.
     *
     * Only `403` is caught. A `401` stays an `HttpException` and reaches the controller as one,
     * where it becomes "sign in" — the app already has one answer to a missing session and this
     * surface must not grow a second.
     */
    private suspend fun <T> mapping(call: suspend () -> T): T = try {
        call()
    } catch (failure: HttpException) {
        if (failure.code() != HTTP_FORBIDDEN) throw failure
        throw CommunityLockedException(failure.serverTextOrNull())
    }

    companion object {
        /** `if len(term) < 2: return {"items": []}` in `community_search`. */
        const val MIN_SEARCH_LENGTH = 2

        /** `if len(text) < 5` in `community_post`. */
        const val MIN_POST_LENGTH = 5

        /** `if len(text) < 2` in `community_reply`. A reply may be «بله». */
        const val MIN_REPLY_LENGTH = 2

        /** `if len(text) > 2000`, on both routes. */
        const val MAX_POST_LENGTH = 2_000

        private const val HTTP_FORBIDDEN = 403
    }
}

/**
 * The post is gone, or was pulled back into the review queue.
 *
 * A separate type from a bare 404 because the community has a legitimate way for a published post
 * to stop being published — three reports set `status` back to `pending` — and «این پست دیگر در
 * دسترس نیست» is a truer sentence there than «یافت نشد».
 */
class CommunityPostNotFoundException(val postId: Long) : Exception("community_post_missing_$postId")
