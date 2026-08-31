package com.coinepro.core.community

import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * The feed's own behaviour, with the network replaced by a list.
 *
 * The three things asserted here are the three that were easy to get wrong and impossible to see
 * afterwards: a second page that duplicates a post because somebody wrote one between the requests,
 * a like whose count was incremented locally rather than taken from the server, and a post held for
 * review that appears on a board it is not actually on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommunityControllerTest {

    private fun samplePost(id: Long, likes: Int = 0, liked: Boolean = false) = CommunityPost(
        id = id,
        author = "رضا",
        content = "متن $id",
        category = CommunityCategory.ANALYSIS,
        categoryLabel = "تحلیل",
        likes = likes,
        liked = liked,
        replyCount = 0,
        reactions = emptyMap(),
        bestReplyId = null,
        createdAt = Instant.parse("2026-08-30T09:00:00Z"),
        pending = false,
    )

    private class FakeGateway(
        var pages: Map<Int, List<CommunityPost>> = emptyMap(),
        var failure: Throwable? = null,
    ) : CommunityGateway {
        var likeAnswer: CommunityLikeOutcome = CommunityLikeOutcome(likes = 1, liked = true)
        var writeAnswer: CommunityWriteOutcome = CommunityWriteOutcome(1L, published = true, message = "منتشر شد.")
        var lastCategory: CommunityCategory? = null
        var feeds = 0

        override suspend fun feed(page: Int, category: CommunityCategory?): CommunityFeedPage {
            failure?.let { throw it }
            feeds++
            lastCategory = category
            val rows = pages[page].orEmpty()
            return CommunityFeedPage(posts = rows, page = page, received = rows.size)
        }

        override suspend fun search(query: String): List<CommunityPost> = emptyList()

        override suspend fun thread(id: Long): CommunityThread =
            CommunityThread(post = pages.values.flatten().first { it.id == id }, replies = emptyList())

        override suspend fun post(content: String, category: CommunityCategory): CommunityWriteOutcome =
            writeAnswer

        override suspend fun reply(postId: Long, content: String, parentId: Long?): CommunityWriteOutcome =
            writeAnswer

        override suspend fun like(postId: Long, currentLikes: Int): CommunityLikeOutcome = likeAnswer

        override suspend fun react(postId: Long, emoji: String): CommunityReactionOutcome =
            CommunityReactionOutcome(emptyMap(), emptySet())

        override suspend fun report(postId: Long) = Unit

        override suspend fun bestReply(postId: Long, replyId: Long) = Unit

        override suspend fun leaderboard(): CommunityLeaderboard =
            CommunityLeaderboard(emptyList(), null, 0)
    }

    /** A full page, so `last` is false and paging continues. */
    private fun fullPage(from: Long) = (from until from + CommunityFeedPage.PAGE_SIZE).map { samplePost(it) }

    @Test
    fun `a post written between two page requests does not arrive twice`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val gateway = FakeGateway()
        gateway.pages = mapOf(1 to fullPage(from = 100))
        val controller = CommunityController(gateway, scope)

        controller.start()
        advanceUntilIdle()

        // Everything shifts down by one when a new post lands, so the last row of page one is also
        // the first row of page two. Appending blindly would show it in both places.
        gateway.pages = mapOf(2 to listOf(samplePost(100), samplePost(99)))
        controller.loadMore()
        advanceUntilIdle()

        val ids = controller.state.value.posts.map(CommunityPost::id)
        assertEquals(ids.distinct(), ids)
        assertEquals(CommunityFeedPage.PAGE_SIZE + 1, ids.size)
        assertTrue("a short page is the last one", controller.state.value.endReached)
        assertFalse(controller.state.value.canLoadMore)
    }

    @Test
    fun `a like takes its number from the server rather than incrementing`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val gateway = FakeGateway(pages = mapOf(1 to listOf(samplePost(1, likes = 11, liked = false))))
        // The reader had already liked this from another device, so the set's cardinality does not
        // move. A local `+1` would print 12 against a board that says 11.
        gateway.likeAnswer = CommunityLikeOutcome(likes = 11, liked = true)
        val controller = CommunityController(gateway, scope)

        controller.start()
        advanceUntilIdle()
        controller.toggleLike(1)
        advanceUntilIdle()

        val row = controller.state.value.posts.single()
        assertEquals(11, row.likes)
        assertTrue(row.liked)
    }

    @Test
    fun `a like that failed puts the control back`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val gateway = object : CommunityGateway by FakeGateway(pages = mapOf(1 to listOf(samplePost(1)))) {
            override suspend fun feed(page: Int, category: CommunityCategory?) =
                CommunityFeedPage(listOf(samplePost(1)), page = 1, received = 1)

            override suspend fun like(postId: Long, currentLikes: Int): CommunityLikeOutcome =
                throw HttpException(Response.error<Unit>(500, "".toResponseBody("application/json".toMediaType())))
        }
        val controller = CommunityController(gateway, scope)

        controller.start()
        advanceUntilIdle()
        controller.toggleLike(1)
        advanceUntilIdle()

        assertFalse("a control that stays pressed lied about what the server holds", controller.state.value.posts.single().liked)
        assertEquals(CommunityError.NETWORK, controller.state.value.error)
    }

    @Test
    fun `a post held for review is not put on a board it is not on`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val gateway = FakeGateway(pages = mapOf(1 to emptyList()))
        gateway.writeAnswer = CommunityWriteOutcome(42L, published = false, message = "برای بازبینیِ ادمین ارسال شد.")
        val controller = CommunityController(gateway, scope)

        controller.start()
        advanceUntilIdle()
        val feedsBefore = gateway.feeds
        controller.submit("یک تحلیل کامل درباره طلا")
        advanceUntilIdle()

        assertEquals(emptyList<CommunityPost>(), controller.state.value.posts)
        assertEquals("برای بازبینیِ ادمین ارسال شد.", controller.state.value.notice)
        assertEquals("no refresh for a post nobody else can see", feedsBefore, gateway.feeds)
    }

    @Test
    fun `a published post refreshes the board so the writer finds it`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val gateway = FakeGateway(pages = mapOf(1 to emptyList()))
        val controller = CommunityController(gateway, scope)

        controller.start()
        advanceUntilIdle()
        gateway.pages = mapOf(1 to listOf(samplePost(42)))
        controller.submit("یک تحلیل کامل درباره طلا")
        advanceUntilIdle()

        assertEquals(listOf(42L), controller.state.value.posts.map(CommunityPost::id))
    }

    @Test
    fun `switching a category reloads from the server rather than filtering what is in hand`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val gateway = FakeGateway(pages = mapOf(1 to listOf(samplePost(1), samplePost(2))))
        val controller = CommunityController(gateway, scope)

        controller.start()
        advanceUntilIdle()
        controller.setCategory(CommunityCategory.QUESTION)
        advanceUntilIdle()

        // The twenty rows already held are the newest twenty *overall*; a local filter would show
        // this category's three most recent posts and call it the category.
        assertEquals(CommunityCategory.QUESTION, gateway.lastCategory)
        assertEquals(2, gateway.feeds)
    }

    @Test
    fun `a tier lock is not a network failure and carries the server's sentence`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val text = "این بخش ویژهٔ اعضای VIP است."
        val gateway = FakeGateway(failure = CommunityLockedException(text))
        val controller = CommunityController(gateway, scope)

        controller.start()
        advanceUntilIdle()

        assertEquals(CommunityError.LOCKED, controller.state.value.error)
        assertEquals(text, controller.state.value.serverText)
    }

    @Test
    fun `a signed-out refusal is its own answer, because the button under it is different`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val body = """{"detail":"ورود لازم است."}""".toResponseBody("application/json".toMediaType())
        val gateway = FakeGateway(failure = HttpException(Response.error<Unit>(401, body)))
        val controller = CommunityController(gateway, scope)

        controller.start()
        advanceUntilIdle()

        assertEquals(CommunityError.SIGNED_OUT, controller.state.value.error)
    }

    @Test
    fun `rows that arrived and could not be read are not reported as an empty board`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val gateway = object : CommunityGateway by FakeGateway() {
            override suspend fun feed(page: Int, category: CommunityCategory?) =
                // Twenty rows on the wire, none of them readable — an HTTP 200 with nothing behind
                // it. «هنوز چیزی نوشته نشده» would be a false statement about the community.
                CommunityFeedPage(posts = emptyList(), page = 1, received = 20, sampleKeys = "pk,body")
        }
        val controller = CommunityController(gateway, scope)

        controller.start()
        advanceUntilIdle()

        assertEquals(CommunityError.UNREADABLE, controller.state.value.error)
        assertEquals(20, controller.state.value.unreadable)
        assertFalse(controller.state.value.empty)
    }

    @Test
    fun `clearing forgets the board, because the other platform has no community`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val controller = CommunityController(FakeGateway(pages = mapOf(1 to listOf(samplePost(1)))), scope)

        controller.start()
        advanceUntilIdle()
        controller.clear()

        assertEquals(emptyList<CommunityPost>(), controller.state.value.posts)
        assertNull(controller.state.value.error)
    }
}
