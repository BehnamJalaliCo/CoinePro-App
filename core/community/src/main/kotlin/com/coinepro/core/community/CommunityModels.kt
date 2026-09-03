package com.coinepro.core.community

import java.time.Instant

/**
 * The five buckets a post can be filed under, and they are the server's own.
 *
 * `app_community.py` holds them as a tuple — `CATEGORIES = ("تحلیل", "سوال", "تجربه", "اخبار",
 * "عمومی")` — and does two things with it: the feed's `category` query filters only when the value
 * is in the tuple, and a post whose category is not in it is silently rewritten to «عمومی» on the
 * way in. So the strings here are not labels, they are **wire values**, and translating them would
 * produce a chip that filters nothing and a post filed under the wrong heading.
 *
 * That is why [wire] carries the Persian text rather than a Latin key. It reads oddly next to every
 * other enum in this repository and it is the correct shape: the alternative is a lookup table in
 * this app that has to be edited in lockstep with a tuple on a server.
 *
 * [DEFAULT] is «عمومی», the same default the route applies, so the composer sends what the server
 * would have picked anyway instead of relying on the rewrite.
 */
enum class CommunityCategory(val wire: String) {
    ANALYSIS("تحلیل"),
    QUESTION("سوال"),
    EXPERIENCE("تجربه"),
    NEWS("اخبار"),
    GENERAL("عمومی"),
    ;

    companion object {
        val DEFAULT: CommunityCategory = GENERAL

        /**
         * The category a wire string names, or null.
         *
         * Null is a real answer rather than a fallback to [GENERAL]: a post that came back under a
         * category this build has never heard of should show the server's own word for it, not be
         * relabelled «عمومی» by an app that guessed. See [CommunityPost.categoryLabel].
         */
        fun of(wire: String?): CommunityCategory? {
            val clean = wire?.trim().orEmpty()
            if (clean.isEmpty()) return null
            return entries.firstOrNull { it.wire == clean }
        }
    }
}

/**
 * The five reactions the route accepts, in the order it lists them.
 *
 * `POST …/posts/{pid}/react` refuses anything outside this set with `400 {"detail":"ایموجی مجاز
 * نیست."}`, so the picker is built from this list rather than from an emoji keyboard — a keyboard
 * would offer several hundred taps of which five work.
 */
object CommunityReactions {
    val ALLOWED: List<String> = listOf("👍", "🔥", "🤔", "❤️", "💡")

    fun allows(emoji: String): Boolean = emoji in ALLOWED
}

/**
 * Who this install is on the board.
 *
 * A number and a name, and nothing else — no phone, no email, no session on either platform. The
 * name is the only thing another reader ever sees, and it is the only thing the server holds
 * beside a hash of the key. See [CommunityIdentityStore].
 */
data class CommunityMember(
    val id: Long,
    val displayName: String,
    /** The key has been banned. Reading still works; nothing else does. */
    val banned: Boolean = false,
)

/**
 * One post in the feed.
 *
 * ### What this deliberately does not have
 *
 * A **title** and an **avatar**. The board stores a body, an author, a category, two counters and
 * — since the owner asked for it — one picture the author chose to attach. Inventing a title by
 * cutting the first line off the body, or a cover by picking a stock chart, would be this app
 * asserting something the author did not write: the same fault as a lettered disc standing in for a
 * logo, in prose instead of in artwork. So a card is the author, the moment, the category, the
 * text, and the picture *if there is one*, and it is honest at any length.
 *
 * @param author the display name the author chose. Never masked, never an account name: the board
 *   has no account names.
 * @param categoryLabel the server's own word for the category, kept even when [category] resolved,
 *   so a heading can be drawn for a bucket this build predates.
 * @param liked whether **this** reader has liked it. Resolved on every route that returns a post,
 *   for the key the request carried; false for a reader with no name, who cannot have liked it.
 * @param pending a post that is not on the board — held or hidden. The feed never contains one, so
 *   this is only ever true on a body from a route that answered about one post in particular.
 */
data class CommunityPost(
    val id: Long,
    val author: String,
    val content: String,
    val category: CommunityCategory?,
    val categoryLabel: String?,
    val likes: Int,
    val liked: Boolean,
    val replyCount: Int,
    /** Emoji to its count. Only reactions with at least one tap are present; the route drops zeros. */
    val reactions: Map<String, Int>,
    /** The reply the author marked best, or null. Set only by the author, through `best-reply`. */
    val bestReplyId: Long?,
    val createdAt: Instant?,
    val pending: Boolean,
    /**
     * Where this post's picture is, relative to the board's host, or null where there is none.
     *
     * The route's own `image_url` — `/api/v1/public/app-community/posts/12/image` — kept as the
     * server wrote it rather than rebuilt here from the id. That is not caution for its own sake:
     * the field is also the *flag*. A post has a picture exactly when the server sent a path for
     * one, so a client that built the path itself would draw an empty frame under every post on
     * the board.
     */
    val imagePath: String? = null,
)

/** One reply under a post. `parentId` makes it a reply to a reply rather than to the post. */
data class CommunityReply(
    val id: Long,
    val author: String,
    val content: String,
    val parentId: Long?,
    val best: Boolean,
    val createdAt: Instant?,
)

/** A post with everything published under it. `GET …/posts/{pid}`. */
data class CommunityThread(
    val post: CommunityPost,
    val replies: List<CommunityReply>,
)

/**
 * One page of the feed, with what the wire actually contained.
 *
 * [received] and [sampleKeys] are here for the same reason `CalendarSourceOutcome` carries them:
 * an empty list has two causes with opposite fixes — nothing is published, or twenty rows arrived
 * in a shape this build could not read — and without the count they are indistinguishable from a
 * screen that says «هنوز چیزی نوشته نشده».
 */
data class CommunityFeedPage(
    val posts: List<CommunityPost>,
    val page: Int,
    val received: Int,
    val sampleKeys: String? = null,
) {
    val dropped: Int get() = (received - posts.size).coerceAtLeast(0)

    /**
     * Whether this is the last page.
     *
     * The route pages at twenty and has no total, so the only signal a client gets is a short page.
     * A full page that happens to be the last costs one more request answering `{"posts":[]}`,
     * which is the cheap half of the trade.
     */
    val last: Boolean get() = received < PAGE_SIZE

    companion object {
        /** `PAGE_SIZE = 20` in `app_community.py`. Not configurable from the client. */
        const val PAGE_SIZE = 20
    }
}

/**
 * What writing produced — a post or a reply.
 *
 * The route answers `{"id":…, "status":"published", "message":"منتشر شد."}`. There is no review
 * queue on this board — a post is refused at the door or it is on the board — but [published] is
 * still read off the wire rather than assumed, so a status this build has never seen is reported
 * as what it says rather than as a hold.
 */
data class CommunityWriteOutcome(
    val id: Long?,
    val published: Boolean,
    val message: String?,
)

/** The answer to a like toggle. The count is the server's, not a local increment. */
data class CommunityLikeOutcome(
    val likes: Int,
    val liked: Boolean,
)

/** The answer to a reaction toggle: every emoji's count, and the ones this reader has tapped. */
data class CommunityReactionOutcome(
    val counts: Map<String, Int>,
    val mine: Set<String>,
)

/** One row of `GET …/leaderboard`. `xp` is posts × 10 + replies × 3 + likes received × 2. */
data class CommunityLeader(
    val rank: Int,
    val username: String,
    val xp: Int,
    val completed: Int,
    val isMe: Boolean,
)

/**
 * The board, and where this reader stands on it.
 *
 * [myRank] is computed against **every** member rather than against the fifty rows sent, so a
 * reader outside the top fifty still learns their position — which is the only number on this
 * screen most people are looking for.
 */
data class CommunityLeaderboard(
    val leaders: List<CommunityLeader>,
    val myRank: Int?,
    val totalStudents: Int,
)

/**
 * Thrown when the key is banned.
 *
 * `403 {"detail":"دسترسی این حساب به انجمن بسته شده است."}`. Distinct from the `401` a key with no
 * name gets, and the distinction is the whole reason this is a type: one has a form under it and
 * the other has nothing to press. The server's own text is carried so the screen can print it
 * instead of app copy.
 */
class CommunityLockedException(val serverText: String?) : Exception("community_locked")

/**
 * Thrown when the text itself was refused — a link, a phone number, too short, an emoji outside
 * the five. `400`, with the server's own sentence about which rule. Shown verbatim: the rules are
 * the server's and its wording is the one that will still be right after they change.
 */
class CommunityRefusedException(val serverText: String?) : Exception("community_refused")

/** Thrown from [CommunityGateway.register] when somebody else already holds the name. `409`. */
class CommunityNameTakenException(val serverText: String?) : Exception("community_name_taken")
