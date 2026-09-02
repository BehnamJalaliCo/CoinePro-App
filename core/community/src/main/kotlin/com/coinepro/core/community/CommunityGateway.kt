package com.coinepro.core.community

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
 * The community — the app's own board, on TradeYar's host, belonging to neither platform.
 *
 * ### Where it lives and why
 *
 * `api/v1/public/app-community/…` on the TradeYar server. Not CoinePro-FX's academy board, which
 * is what this gateway used to read: that board sits behind an academy sign-in and a VIP tier, so
 * the tab said «ورود لازم است» to every forex reader without a subscription and «انجمن روی تریدیار
 * نیست» to every crypto reader. The owner's instruction is that the community is the *app's* —
 * independent of both platforms' accounts — and that it should live on one of the two servers
 * rather than a third. TradeYar's host is the one an Iranian handset always reaches, so it is
 * there; the routes are public and nothing on them knows what a TradeYar account is.
 *
 * ### The credential
 *
 * Not a bearer token from either platform. Every call carries `X-Community-Key`, a random secret
 * this app minted once — see [CommunityIdentityStore] — and the server holds only its hash against
 * the display name the reader chose. Reading needs no name; writing does, and a write from a key
 * with no name answers `401` with the server's own sentence about choosing one.
 *
 * ### Two refusals that are not the same refusal
 *
 * `401` means "no name yet" — choose one. `403` means "this key is banned" — nothing to press.
 * `400` on a write means the text itself was refused — a link, a phone number, too short — and the
 * server's sentence says which. They arrive as the same `HttpException` and are told apart here
 * rather than at the screen, because a screen that gets them wrong asks a banned reader to pick a
 * name. See [CommunityLockedException] and [CommunityRefusedException].
 */
interface CommunityGateway {
    /** Who this install is on the board, or null before a name has been chosen. */
    suspend fun me(): CommunityMember?

    /**
     * Chooses a display name for this install, or changes it.
     *
     * The server holds one name per key and one key per name, folded so that «علی رضا» and
     * «علی‌رضا» are the same name. A name somebody else holds answers `409` and becomes
     * [CommunityNameTakenException]; a name outside the rules answers `400`.
     */
    suspend fun register(displayName: String): CommunityMember

    /**
     * One page of published posts, newest first.
     *
     * [category] null asks for everything: the handler filters only when the value is one of its
     * own five, so an empty string means "no filter" — which is what a null sends.
     */
    suspend fun feed(page: Int = 1, category: CommunityCategory? = null): CommunityFeedPage

    /**
     * Published posts whose body contains [query].
     *
     * The route requires at least two characters and answers `{"items":[]}` below that rather than
     * an error, so a short query is not a failure — it is simply no result, and this returns the
     * empty list without a round trip for the same reason. The body comes back cut at two hundred
     * characters; opening the thread reads the whole post.
     */
    suspend fun search(query: String): List<CommunityPost>

    /** One post with its published replies. Throws on a post that is missing or hidden. */
    suspend fun thread(id: Long): CommunityThread

    /**
     * Writes a post.
     *
     * Five characters minimum and two thousand maximum, ten posts per rolling day, and a hard block
     * on links, phone numbers and messenger handles — all enforced server-side, each with its own
     * Persian sentence. The length bounds are checked here too so the composer's button can be off
     * rather than the reader discovering the rule from a failed request; the rest is not, because
     * a client-side copy of somebody else's moderation rules is a copy that goes stale.
     */
    suspend fun post(content: String, category: CommunityCategory = CommunityCategory.DEFAULT): CommunityWriteOutcome

    /** Replies to a post, or to a reply under it. */
    suspend fun reply(postId: Long, content: String, parentId: Long? = null): CommunityWriteOutcome

    /**
     * Toggles this reader's like.
     *
     * One per member, held as a row, so the count is a count of rows rather than a counter and
     * pressing twice genuinely removes the like. [currentLikes] is only a fallback for a body that
     * arrived without a count; see [CommunityWire.readLike].
     */
    suspend fun like(postId: Long, currentLikes: Int = 0): CommunityLikeOutcome

    /** Toggles one emoji reaction. Refused server-side for anything outside [CommunityReactions]. */
    suspend fun react(postId: Long, emoji: String): CommunityReactionOutcome

    /** Reports a post. Three reports from three members hide it. */
    suspend fun report(postId: Long)

    /**
     * Marks a reply as the best answer, or clears the mark with [replyId] `0`.
     *
     * Only the post's own author may; anyone else gets a `403` with its own sentence, which is a
     * different 403 from the ban and is deliberately **not** mapped to [CommunityLockedException]
     * — see the implementation.
     */
    suspend fun bestReply(postId: Long, replyId: Long)

    /** The board's own scoreboard: posts, replies and likes received, as points. */
    suspend fun leaderboard(): CommunityLeaderboard
}

internal interface CommunityApi {
    @GET("api/v1/public/app-community/me")
    suspend fun me(@Header(KEY_HEADER) key: String): JsonElement

    @POST("api/v1/public/app-community/me")
    suspend fun register(@Header(KEY_HEADER) key: String, @Body body: RegisterBody): JsonElement

    @GET("api/v1/public/app-community/posts")
    suspend fun feed(
        @Header(KEY_HEADER) key: String,
        @Query("page") page: Int,
        @Query("category") category: String,
    ): JsonElement

    @GET("api/v1/public/app-community/posts/search")
    suspend fun search(@Header(KEY_HEADER) key: String, @Query("q") query: String): JsonElement

    @GET("api/v1/public/app-community/posts/{pid}")
    suspend fun thread(@Header(KEY_HEADER) key: String, @Path("pid") pid: Long): JsonElement

    @POST("api/v1/public/app-community/posts")
    suspend fun post(@Header(KEY_HEADER) key: String, @Body body: PostBody): JsonElement

    @POST("api/v1/public/app-community/posts/{pid}/reply")
    suspend fun reply(
        @Header(KEY_HEADER) key: String,
        @Path("pid") pid: Long,
        @Body body: ReplyBody,
    ): JsonElement

    @POST("api/v1/public/app-community/posts/{pid}/like")
    suspend fun like(@Header(KEY_HEADER) key: String, @Path("pid") pid: Long): JsonElement

    @POST("api/v1/public/app-community/posts/{pid}/react")
    suspend fun react(
        @Header(KEY_HEADER) key: String,
        @Path("pid") pid: Long,
        @Body body: ReactBody,
    ): JsonElement

    @POST("api/v1/public/app-community/posts/{pid}/report")
    suspend fun report(@Header(KEY_HEADER) key: String, @Path("pid") pid: Long): JsonElement

    @POST("api/v1/public/app-community/posts/{pid}/best-reply/{rid}")
    suspend fun bestReply(
        @Header(KEY_HEADER) key: String,
        @Path("pid") pid: Long,
        @Path("rid") rid: Long,
    ): JsonElement

    @GET("api/v1/public/app-community/leaderboard")
    suspend fun leaderboard(@Header(KEY_HEADER) key: String): JsonElement
}

/** The header the server reads the install's secret from. Hyphenated: nginx drops underscores. */
internal const val KEY_HEADER = "X-Community-Key"

internal data class RegisterBody(val displayName: String)

internal data class PostBody(val content: String, val category: String)

/**
 * A reply body.
 *
 * `parentId` serialises to `parent_id` through the app's Gson naming policy, and Gson omits a null
 * field entirely, which is what the route wants for a top-level reply. Spelling it `parent_id`
 * here instead would come out as `parent__id` under that policy.
 */
internal data class ReplyBody(val content: String, val parentId: Long?)

internal data class ReactBody(val emoji: String)

class NetworkCommunityGateway(
    retrofit: Retrofit,
    private val identity: CommunityIdentityStore,
) : CommunityGateway {

    private val api = retrofit.create(CommunityApi::class.java)

    private suspend fun key(): String = identity.key()

    override suspend fun me(): CommunityMember? = mapping {
        CommunityWire.readMember(api.me(key()))
    }

    override suspend fun register(displayName: String): CommunityMember {
        val name = displayName.trim()
        require(name.length in MIN_NAME_LENGTH..MAX_NAME_LENGTH) { "name outside the server's bounds" }
        return try {
            CommunityWire.readMember(api.register(key(), RegisterBody(name)))
                ?: throw CommunityRefusedException(null)
        } catch (failure: HttpException) {
            when (failure.code()) {
                HTTP_CONFLICT -> throw CommunityNameTakenException(failure.serverTextOrNull())
                HTTP_BAD_REQUEST -> throw CommunityRefusedException(failure.serverTextOrNull())
                HTTP_FORBIDDEN -> throw CommunityLockedException(failure.serverTextOrNull())
                else -> throw failure
            }
        }
    }

    override suspend fun feed(page: Int, category: CommunityCategory?): CommunityFeedPage = mapping {
        CommunityWire.readFeed(
            body = api.feed(key(), page.coerceAtLeast(1), category?.wire.orEmpty()),
            page = page.coerceAtLeast(1),
        )
    }

    override suspend fun search(query: String): List<CommunityPost> {
        val term = query.trim()
        // The route's own floor. Asking below it costs a round trip to be told `{"items":[]}`, and
        // the reader is mid-word rather than finished typing.
        if (term.length < MIN_SEARCH_LENGTH) return emptyList()
        return mapping { CommunityWire.readSearch(api.search(key(), term)) }
    }

    override suspend fun thread(id: Long): CommunityThread = try {
        mapping {
            CommunityWire.readThread(api.thread(key(), id))
                // A 200 whose body held no readable post. Raised as the route's own answer for a
                // post that is not there, because from the reader's side the two are the same
                // fact and one of them already has a screen.
                ?: throw CommunityPostNotFoundException(id)
        }
    } catch (failure: HttpException) {
        if (failure.code() == HTTP_NOT_FOUND) throw CommunityPostNotFoundException(id)
        throw failure
    }

    override suspend fun post(content: String, category: CommunityCategory): CommunityWriteOutcome {
        val text = content.trim()
        require(text.length >= MIN_POST_LENGTH) { "post too short" }
        require(text.length <= MAX_POST_LENGTH) { "post too long" }
        return mapping {
            CommunityWire.readWriteOutcome(api.post(key(), PostBody(text, category.wire)))
        }
    }

    override suspend fun reply(postId: Long, content: String, parentId: Long?): CommunityWriteOutcome {
        val text = content.trim()
        require(text.length >= MIN_REPLY_LENGTH) { "reply too short" }
        require(text.length <= MAX_POST_LENGTH) { "reply too long" }
        return mapping {
            CommunityWire.readWriteOutcome(api.reply(key(), postId, ReplyBody(text, parentId)))
        }
    }

    override suspend fun like(postId: Long, currentLikes: Int): CommunityLikeOutcome = mapping {
        CommunityWire.readLike(api.like(key(), postId), fallback = currentLikes)
    }

    override suspend fun react(postId: Long, emoji: String): CommunityReactionOutcome {
        require(CommunityReactions.allows(emoji)) { "emoji not accepted by the route" }
        return mapping { CommunityWire.readReaction(api.react(key(), postId, ReactBody(emoji))) }
    }

    override suspend fun report(postId: Long) {
        mapping { api.report(key(), postId) }
    }

    /**
     * Crowning a reply, with the one 403 on this surface that is **not** a ban.
     *
     * The route answers `403 {"detail":"فقط نویسندهٔ پست می‌تواند پاسخ برگزیده را تعیین کند."}` to
     * anyone who is not the post's author. Mapping that through [mapping] would tell the reader
     * their key is banned, so this call is deliberately outside it and the server's sentence
     * reaches the screen unchanged.
     */
    override suspend fun bestReply(postId: Long, replyId: Long) {
        api.bestReply(key(), postId, replyId)
    }

    override suspend fun leaderboard(): CommunityLeaderboard = mapping {
        CommunityWire.readLeaderboard(api.leaderboard(key()))
    }

    /**
     * Runs a call and turns the two refusals with their own screens into types.
     *
     * `403` is the ban, `400` is "this text was refused" with the server's sentence about why. A
     * `401` stays an `HttpException` and reaches the controller as one, where it becomes "choose a
     * name" — the app has one answer to a missing name and this surface must not grow a second.
     */
    private suspend fun <T> mapping(call: suspend () -> T): T = try {
        call()
    } catch (failure: HttpException) {
        when (failure.code()) {
            HTTP_FORBIDDEN -> throw CommunityLockedException(failure.serverTextOrNull())
            HTTP_BAD_REQUEST -> throw CommunityRefusedException(failure.serverTextOrNull())
            else -> throw failure
        }
    }

    companion object {
        /** `MIN_SEARCH = 2` in `app_community.py`. */
        const val MIN_SEARCH_LENGTH = 2

        /** `MIN_POST = 5`. */
        const val MIN_POST_LENGTH = 5

        /** `MIN_REPLY = 2`. A reply may be «بله». */
        const val MIN_REPLY_LENGTH = 2

        /** `MAX_TEXT = 2000`, on both write routes. */
        const val MAX_POST_LENGTH = 2_000

        /** `MIN_NAME = 2` and `MAX_NAME = 24`. */
        const val MIN_NAME_LENGTH = 2
        const val MAX_NAME_LENGTH = 24

        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_CONFLICT = 409
    }
}

/**
 * The post is gone, or was hidden by its readers.
 *
 * A separate type from a bare 404 because the community has a legitimate way for a published post
 * to stop being published — three reports hide it — and «این پست دیگر در دسترس نیست» is a truer
 * sentence there than «یافت نشد».
 */
class CommunityPostNotFoundException(val postId: Long) : Exception("community_post_missing_$postId")
