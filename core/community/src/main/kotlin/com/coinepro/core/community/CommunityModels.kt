package com.coinepro.core.community

import java.time.Instant

/**
 * The five buckets a post can be filed under, and they are the server's own.
 *
 * `academy.py` holds them as a tuple — `_POST_CATS = ("تحلیل", "سوال", "تجربه", "اخبار", "عمومی")`
 * — and does two things with it: the feed's `category` query filters only when the value is in the
 * tuple, and a post whose category is not in it is silently rewritten to «عمومی» on the way in. So
 * the strings here are not labels, they are **wire values**, and translating them would produce a
 * chip that filters nothing and a post filed under the wrong heading.
 *
 * That is why [wire] carries the Persian text rather than a Latin key. It reads oddly next to every
 * other enum in this repository and it is the correct shape: the alternative is a lookup table in
 * this app that has to be edited in lockstep with a tuple on a server nobody here deploys.
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
 * `POST /academy/community/{pid}/react` refuses anything outside this set with
 * `400 {"detail":"ایموجی مجاز نیست."}`, so the picker is built from this list rather than from an
 * emoji keyboard — a keyboard would offer several hundred taps of which five work.
 */
object CommunityReactions {
    val ALLOWED: List<String> = listOf("👍", "🔥", "🤔", "❤️", "💡")

    fun allows(emoji: String): Boolean = emoji in ALLOWED
}

/**
 * One post in the feed.
 *
 * ### What this deliberately does not have
 *
 * A **title**, a **cover image** and an **avatar**. The reference screenshot has all three and this
 * backend has none of them: `_post_dict` in `academy.py` returns `id`, `content`, `author`,
 * `likes`, `replies_count`, `status`, `category`, `reactions`, `best_reply_id` and `created_at`,
 * and `AcademyPost` has no column for a picture or a heading. Inventing a title by cutting the
 * first line off the body, or a cover by picking a stock chart, would be this app asserting
 * something the author did not write — which is the same fault as a lettered disc standing in for
 * a logo, in prose instead of in artwork.
 *
 * So a card is the author, the moment, the category and the text, and it is honest at any length.
 *
 * @param author the display name the route resolved — `full_name` where the student has one, the
 *   username otherwise, and `—` where the student row has gone. Already masked to `ab***` on the
 *   **search** route only; see [CommunityGateway.search].
 * @param categoryLabel the server's own word for the category, kept even when [category] resolved,
 *   so a heading can be drawn for a bucket this build predates.
 * @param liked whether **this** reader has liked it. Only the feed route carries it — the detail
 *   route builds its post dictionary without consulting Redis — so it is false rather than unknown
 *   on a thread opened directly, and the like control there reads as "not yet liked". Toggling it
 *   still tells the truth, because the route answers with the count and the new state.
 * @param pending a post the AI moderator sent to the review queue instead of publishing. The feed
 *   never contains one — it filters on `status == "published"` — so this is only ever true for the
 *   post a reader has just written, which is the one case where saying so matters.
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

/** A post with everything published under it. `GET /academy/community/{pid}`. */
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
        /** `per = 20` in `community_feed`. Not configurable from the client. */
        const val PAGE_SIZE = 20
    }
}

/**
 * What writing produced — a post or a reply.
 *
 * The route answers `{"id":…, "status":"published"|"pending", "message":…}` for a post and only
 * `{"status":…}` for a reply, so [id] is nullable and the message is the server's own sentence:
 * «منتشر شد.» or «برای بازبینیِ ادمین ارسال شد.». It is shown verbatim rather than replaced with
 * app copy, because the two outcomes are the server's decision and its wording is the one that will
 * still be right after their moderation rules change.
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

/** One row of `GET /academy/leaderboard`. `xp` is `completed × 10 + Σ quiz_score`, server-side. */
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
 * [myRank] is computed against **every** student rather than against the fifty rows sent, so a
 * reader outside the top fifty still learns their position — which is the only number on this
 * screen most people are looking for.
 */
data class CommunityLeaderboard(
    val leaders: List<CommunityLeader>,
    val myRank: Int?,
    val totalStudents: Int,
)

/**
 * Thrown when the community is behind the subscription rather than behind the sign-in.
 *
 * `require_vip` answers `403 {"detail":"این بخش ویژهٔ اعضای VIP است. برای دسترسی، اشتراک تهیه
 * کنید."}` for a free or expired student, and `current_student` answers `401 {"detail":"ورود لازم
 * است."}` for anyone without an academy token. Those are two different sentences with two different
 * buttons under them — buy a subscription, or sign in — and a screen that cannot tell them apart
 * sends half its readers to the wrong one.
 *
 * The server's own text is carried so the screen can print it instead of app copy; their wording
 * changes with their pricing and ours would not.
 */
class CommunityLockedException(val serverText: String?) : Exception("community_vip_locked")
