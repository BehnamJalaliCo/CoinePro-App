package com.coinepro.core.community

import com.coinepro.core.marketdata.AcademyTokenStore
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The community routes, over a real Retrofit with a real Gson, against the paths and refusals
 * `academy.py` actually serves.
 *
 * The Gson is built the way `NetworkFactory` builds the app's — `LOWER_CASE_WITH_UNDERSCORES` —
 * because two of the assertions here are about what that policy does on the way *out*:
 * `ReplyBody.parentId` has to leave as `parent_id`, and would leave as `parent__id` if the field
 * had been spelled with the underscore already.
 */
class CommunityGatewayTest {

    private val captured = AtomicReference<Request?>()

    private class FixedToken(private val value: String = "academy-token") : AcademyTokenStore {
        override suspend fun token(): String = value

        override fun clear() = Unit
    }

    private fun gateway(
        body: String = "{}",
        code: Int = 200,
        tokens: AcademyTokenStore = FixedToken(),
    ): CommunityGateway {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                captured.set(chain.request())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Refused")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://example.invalid/api/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        return NetworkCommunityGateway(retrofit, tokens)
    }

    private fun sentBody(): String {
        val buffer = Buffer()
        captured.get()!!.body!!.writeTo(buffer)
        return buffer.readUtf8()
    }

    @Test
    fun `every call carries the academy token, not the mobile one`() = runTest {
        gateway("""{"posts":[]}""", tokens = FixedToken("minted")).feed()

        assertEquals("Bearer minted", captured.get()!!.header("Authorization"))
        assertEquals("/api/academy/community", captured.get()!!.url.encodedPath)
    }

    @Test
    fun `a category chip becomes the route's own Persian query value`() = runTest {
        gateway("""{"posts":[]}""").feed(page = 3, category = CommunityCategory.ANALYSIS)

        val url = captured.get()!!.url
        assertEquals("3", url.queryParameter("page"))
        // Not a Latin key. The handler filters only on membership of its own tuple, so any
        // translation of this string would silently mean "no filter".
        assertEquals("تحلیل", url.queryParameter("category"))
    }

    @Test
    fun `no category sends the empty string the route documents, which means everything`() = runTest {
        gateway("""{"posts":[]}""").feed()

        assertEquals("", captured.get()!!.url.queryParameter("category"))
    }

    @Test
    fun `a reply body spells parent_id the way the handler reads it`() = runTest {
        gateway("""{"status":"published"}""").reply(postId = 41, content = "بله", parentId = 87)

        assertEquals("""{"content":"بله","parent_id":87}""", sentBody())
        assertEquals("/api/academy/community/41/reply", captured.get()!!.url.encodedPath)
    }

    @Test
    fun `a top-level reply omits parent_id entirely rather than sending null`() = runTest {
        gateway("""{"status":"published"}""").reply(postId = 41, content = "بله")

        // `Body(None, embed=True)` wants the key absent, and Gson omits a null field by default.
        assertEquals("""{"content":"بله"}""", sentBody())
    }

    @Test
    fun `a search below two characters never reaches the network`() = runTest {
        captured.set(null)
        val gateway = gateway("""{"items":[]}""")

        assertEquals(emptyList<CommunityPost>(), gateway.search("ط"))
        assertNull("no request for a term the route would refuse", captured.get())
    }

    @Test
    fun `the VIP refusal becomes a lock carrying the server's own sentence`() = runTest {
        val text = "این بخش ویژهٔ اعضای VIP است. برای دسترسی، اشتراک تهیه کنید."
        val gateway = gateway("""{"detail":"$text"}""", code = 403)

        val thrown = runCatching { gateway.feed() }.exceptionOrNull()
        assertTrue(thrown is CommunityLockedException)
        assertEquals(text, (thrown as CommunityLockedException).serverText)
    }

    @Test
    fun `a signed-out refusal stays an HTTP failure, because it is a different button`() = runTest {
        // 401 is «ورود لازم است.» — sign in. Folding it into the tier lock would tell a reader with
        // no session to buy a subscription.
        val gateway = gateway("""{"detail":"ورود لازم است."}""", code = 401)

        val thrown = runCatching { gateway.feed() }.exceptionOrNull()
        assertTrue(thrown is HttpException)
        assertEquals(401, (thrown as HttpException).code())
    }

    @Test
    fun `crowning a reply does not map its 403 to a tier lock`() = runTest {
        // `community_best_reply` answers 403 to anyone who is not the post's author. That is a
        // sentence about authorship, and offering a subscription for it would be nonsense.
        val gateway = gateway("""{"detail":"فقط نویسندهٔ پست می‌تواند پاسخِ برگزیده را تعیین کند."}""", code = 403)

        val thrown = runCatching { gateway.bestReply(41, 88) }.exceptionOrNull()
        assertTrue(thrown is HttpException)
    }

    @Test
    fun `clearing the best reply is rid zero, which is the route's own convention`() = runTest {
        gateway("""{"ok":true,"best_reply_id":null}""").bestReply(41, 0)

        assertEquals("/api/academy/community/41/best-reply/0", captured.get()!!.url.encodedPath)
    }

    @Test
    fun `a post shorter than the route accepts is refused here rather than round-tripped`() = runTest {
        captured.set(null)
        val gateway = gateway("""{"id":1,"status":"published"}""")

        assertTrue(runCatching { gateway.post("سلام") }.exceptionOrNull() is IllegalArgumentException)
        assertNull(captured.get())
    }

    @Test
    fun `an emoji the route refuses never leaves the phone`() = runTest {
        captured.set(null)
        val gateway = gateway("""{"reactions":{},"mine":[]}""")

        assertTrue(runCatching { gateway.react(41, "🍕") }.exceptionOrNull() is IllegalArgumentException)
        assertNull(captured.get())

        gateway.react(41, "🔥")
        assertEquals("""{"emoji":"🔥"}""", sentBody())
    }

    @Test
    fun `a 200 with no readable post is the same answer as a missing post`() = runTest {
        val gateway = gateway("""{"replies":[]}""")

        assertTrue(runCatching { gateway.thread(41) }.exceptionOrNull() is CommunityPostNotFoundException)
    }

    @Test
    fun `the leaderboard is read off the academy's own route`() = runTest {
        val board = gateway("""{"items":[{"rank":1,"username":"ali","xp":9}],"my_rank":4,"total_students":80}""")
            .leaderboard()

        assertEquals("/api/academy/leaderboard", captured.get()!!.url.encodedPath)
        assertEquals(1, board.leaders.size)
        assertEquals(4, board.myRank)
    }
}
