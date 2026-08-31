package com.coinepro.core.community

import com.google.gson.JsonParser
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The community bodies, read against the JSON the handlers actually build.
 *
 * Every payload below is transcribed from `academy.py` — `_post_dict`, `community_feed`,
 * `community_search`, `community_detail`, `community_like`, `community_react`, `leaderboard` —
 * rather than invented, because the OpenAPI declares `schema: {}` for all of them and an invented
 * fixture would only prove that the reader agrees with itself.
 *
 * The camel-case cases are not hypothetical either. `NetworkAcademyTokenStore` carries a
 * `@SerializedName(value = "expires_in", alternate = ["expiresIn"])` for exactly this reason: a
 * route on this host already answers camel where the app expected snake, and reading a null out of
 * that spelling drops the row without any error to explain it.
 */
class CommunityWireTest {

    private fun json(raw: String) = JsonParser.parseString(raw.trimIndent())

    @Test
    fun `the feed body from community_feed maps intact`() {
        val page = CommunityWire.readFeed(
            json(
                """
                {"posts":[
                  {"id":41,"content":"طلا از سقف کانال برگشت و الان روی ۲۶۴۰ حمایت دارد.",
                   "author":"رضا محمدی","likes":12,"replies_count":3,"status":"published",
                   "category":"تحلیل","reactions":{"🔥":4,"👍":2},"best_reply_id":88,
                   "created_at":"2026-08-30T09:14:00+00:00","liked":true},
                  {"id":40,"content":"کسی با بروکر جدید کار کرده؟","author":"sara",
                   "likes":0,"replies_count":0,"status":"published","category":"سوال",
                   "reactions":{},"best_reply_id":null,
                   "created_at":"2026-08-30T08:02:11+00:00","liked":false}
                ]}
                """,
            ),
            page = 1,
        )

        assertEquals(2, page.posts.size)
        assertEquals(2, page.received)
        assertEquals(0, page.dropped)
        // Twenty is the route's page size, so a two-row page is the last one.
        assertTrue(page.last)

        val first = page.posts.first()
        assertEquals(41L, first.id)
        assertEquals("رضا محمدی", first.author)
        assertEquals(CommunityCategory.ANALYSIS, first.category)
        assertEquals(12, first.likes)
        assertTrue(first.liked)
        assertEquals(3, first.replyCount)
        assertEquals(mapOf("👍" to 2, "🔥" to 4), first.reactions)
        assertEquals(88L, first.bestReplyId)
        assertFalse("published is not pending", first.pending)
        assertEquals(Instant.parse("2026-08-30T09:14:00Z"), first.createdAt)

        // `best_reply_id: null` is a post nobody has crowned, not a reply with id zero.
        assertNull(page.posts[1].bestReplyId)
        assertEquals(emptyMap<String, Int>(), page.posts[1].reactions)
    }

    @Test
    fun `a camel-cased body reads the same, which is the failure that leaves no error behind`() {
        // The measured shape of this class of bug: `repliesCount`, `createdAt` and `bestReplyId`
        // resolved only under snake_case would each come back absent, and a post with a null date
        // is a card whose whole footer disappears — behind an HTTP 200, with nothing anywhere to
        // say why.
        val page = CommunityWire.readFeed(
            json(
                """
                {"posts":[{"id":"41","content":"متن","author":"رضا","likes":"7",
                           "repliesCount":2,"createdAt":"2026-08-30T09:14:00Z",
                           "bestReplyId":5,"isLiked":"true","category":"تجربه"}]}
                """,
            ),
            page = 1,
        )

        val post = page.posts.single()
        assertEquals(41L, post.id)
        // A bigint that arrived quoted. Refusing it would drop the whole row.
        assertEquals(7, post.likes)
        assertEquals(2, post.replyCount)
        assertEquals(5L, post.bestReplyId)
        assertTrue(post.liked)
        assertEquals(CommunityCategory.EXPERIENCE, post.category)
        assertEquals(Instant.parse("2026-08-30T09:14:00Z"), post.createdAt)
    }

    @Test
    fun `a post with no id and one with no text are dropped, and the drop is counted`() {
        // The count is the whole diagnosis. Without it an unreadable page and an empty board are
        // the same screen, and they need opposite next moves.
        val page = CommunityWire.readFeed(
            json("""{"posts":[{"content":"بدون شناسه"},{"id":9},{"id":10,"content":"سالم"}]}"""),
            page = 1,
        )

        assertEquals(listOf(10L), page.posts.map(CommunityPost::id))
        assertEquals(3, page.received)
        assertEquals(2, page.dropped)
        assertEquals("content", page.sampleKeys)
    }

    @Test
    fun `an unknown category keeps the server's own word rather than being relabelled`() {
        val post = CommunityWire.readFeed(
            json("""{"posts":[{"id":1,"content":"متن","category":"استراتژی"}]}"""),
            page = 1,
        ).posts.single()

        assertNull("not one of the five the route filters on", post.category)
        assertEquals("استراتژی", post.categoryLabel)
    }

    @Test
    fun `a reaction outside the accepted five is not drawn, because it could not be toggled`() {
        // `community_react` answers 400 for anything outside its tuple, so a chip for one would be
        // a control that fails on every press.
        val post = CommunityWire.readFeed(
            json("""{"posts":[{"id":1,"content":"متن","reactions":{"🔥":3,"🍕":9,"👍":0}}]}"""),
            page = 1,
        ).posts.single()

        // Zero counts go too: the route drops them and a chip reading «۰» is a chip about nothing.
        assertEquals(mapOf("🔥" to 3), post.reactions)
    }

    @Test
    fun `a full page is not the last page`() {
        val rows = (1..20).joinToString(",") { """{"id":$it,"content":"متن $it"}""" }
        val page = CommunityWire.readFeed(json("""{"posts":[$rows]}"""), page = 1)

        assertEquals(20, page.posts.size)
        assertFalse(page.last)
    }

    @Test
    fun `the detail body carries the post and its replies, and marks the crowned one`() {
        val thread = CommunityWire.readThread(
            json(
                """
                {"post":{"id":41,"content":"سوال درباره XAUUSD","author":"رضا","likes":5,
                         "replies_count":2,"status":"published","category":"سوال",
                         "reactions":{},"best_reply_id":88,"created_at":"2026-08-30T09:14:00Z"},
                 "replies":[
                   {"id":87,"content":"به کانال روزانه نگاه کن.","author":"sara",
                    "parent_id":null,"is_best":false,"created_at":"2026-08-30T09:20:00Z"},
                   {"id":88,"content":"حمایت ۲۶۳۰ است.","author":"ali",
                    "parent_id":87,"is_best":true,"created_at":"2026-08-30T09:31:00Z"}]}
                """,
            ),
        )!!

        assertEquals(41L, thread.post.id)
        assertEquals(listOf(87L, 88L), thread.replies.map(CommunityReply::id))
        assertFalse(thread.replies[0].best)
        assertTrue(thread.replies[1].best)
        assertEquals(87L, thread.replies[1].parentId)
    }

    @Test
    fun `a reply without is_best still gets its crown from the post's own best_reply_id`() {
        // The two dictionaries are hand-built in different places in `academy.py` and have already
        // disagreed about which fields they carry. Either source is enough here.
        val thread = CommunityWire.readThread(
            json(
                """
                {"post":{"id":1,"content":"متن","best_reply_id":9},
                 "replies":[{"id":9,"content":"پاسخ"},{"id":10,"content":"پاسخ دیگر"}]}
                """,
            ),
        )!!

        assertTrue(thread.replies.first { it.id == 9L }.best)
        assertFalse(thread.replies.first { it.id == 10L }.best)
    }

    @Test
    fun `a detail body with no readable post is null rather than a thread with no head`() {
        assertNull(CommunityWire.readThread(json("""{"replies":[{"id":1,"content":"پاسخ"}]}""")))
    }

    @Test
    fun `search rows are masked and truncated by the server, and read the same way regardless`() {
        val items = CommunityWire.readSearch(
            json(
                """
                {"items":[{"id":40,"content":"کسی با بروکر جدید…","category":"سوال",
                           "author":"sa***","likes":2,"replies_count":1,
                           "created_at":"2026-08-30T08:02:11Z"}]}
                """,
            ),
        )

        val row = items.single()
        assertEquals("sa***", row.author)
        assertEquals(CommunityCategory.QUESTION, row.category)
        // No `status` on this route, and absent must not read as held for review.
        assertFalse(row.pending)
    }

    @Test
    fun `a post held for review is reported as held, and one that published is not`() {
        val held = CommunityWire.readWriteOutcome(
            json("""{"id":42,"status":"pending","message":"برای بازبینیِ ادمین ارسال شد."}"""),
        )
        assertEquals(42L, held.id)
        assertFalse(held.published)
        assertEquals("برای بازبینیِ ادمین ارسال شد.", held.message)

        val published = CommunityWire.readWriteOutcome(
            json("""{"id":43,"status":"published","message":"منتشر شد."}"""),
        )
        assertTrue(published.published)

        // The reply route sends only a status, with no id at all.
        assertNull(CommunityWire.readWriteOutcome(json("""{"status":"published"}""")).id)
    }

    @Test
    fun `a like answers with the set's cardinality, and a body without one keeps what was on screen`() {
        val toggled = CommunityWire.readLike(json("""{"likes":13,"liked":true}"""), fallback = 12)
        assertEquals(13, toggled.likes)
        assertTrue(toggled.liked)

        // Zero here would read as "your like removed every other one".
        assertEquals(12, CommunityWire.readLike(json("""{"ok":true}"""), fallback = 12).likes)
    }

    @Test
    fun `a reaction answers every count and the ones this reader has tapped`() {
        val outcome = CommunityWire.readReaction(
            json("""{"reactions":{"🔥":4,"💡":1},"mine":["🔥"]}"""),
        )

        assertEquals(mapOf("🔥" to 4, "💡" to 1), outcome.counts)
        assertEquals(setOf("🔥"), outcome.mine)
    }

    @Test
    fun `the leaderboard keeps the server's numbering and its rank for a reader outside the board`() {
        val board = CommunityWire.readLeaderboard(
            json(
                """
                {"items":[{"rank":1,"username":"ali","xp":420,"completed":31,"is_me":false},
                          {"rank":2,"username":"sara","xp":390,"completed":29,"is_me":false}],
                 "my_rank":118,"total_students":940}
                """,
            ),
        )

        assertEquals(listOf(1, 2), board.leaders.map(CommunityLeader::rank))
        assertEquals(420, board.leaders.first().xp)
        // Computed against every student rather than against the fifty rows sent, which is the only
        // number most readers open this for.
        assertEquals(118, board.myRank)
        assertEquals(940, board.totalStudents)
    }

    @Test
    fun `an empty board is empty rather than a failure`() {
        assertEquals(0, CommunityWire.readFeed(json("""{"posts":[]}"""), page = 1).posts.size)
        assertEquals(0, CommunityWire.readLeaderboard(json("""{"items":[]}""")).leaders.size)
        assertEquals(0, CommunityWire.readFeed(json("""{}"""), page = 1).received)
    }
}
