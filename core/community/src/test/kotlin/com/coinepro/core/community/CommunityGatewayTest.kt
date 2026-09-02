package com.coinepro.core.community

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * `app_community.py` actually serves.
 *
 * The Gson is built the way `NetworkFactory` builds the app's — `LOWER_CASE_WITH_UNDERSCORES` —
 * because two of the assertions here are about what that policy does on the way *out*:
 * `ReplyBody.parentId` has to leave as `parent_id`, and would leave as `parent__id` if the field
 * had been spelled with the underscore already.
 */
class CommunityGatewayTest {

    private val captured = AtomicReference<Request?>()

    private class FixedIdentity(private val value: String = "0123456789abcdef0123456789abcdef") : CommunityIdentityStore {
        override suspend fun key(): String = value

        override val displayName: Flow<String?> = MutableStateFlow(null)

        override suspend fun setDisplayName(name: String?) = Unit
    }

    private fun gateway(
        body: String = "{}",
        code: Int = 200,
        identity: CommunityIdentityStore = FixedIdentity(),
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
        return NetworkCommunityGateway(retrofit, identity)
    }

    private fun sentBody(): String {
        val buffer = Buffer()
        captured.get()!!.body!!.writeTo(buffer)
        return buffer.readUtf8()
    }

    @Test
    fun `every call carries the install's own key, and no platform token`() = runTest {
        gateway("""{"posts":[]}""", identity = FixedIdentity("feedfacefeedfacefeedfacefeedface")).feed()

        assertEquals("feedfacefeedfacefeedfacefeedface", captured.get()!!.header("X-Community-Key"))
        assertNull(captured.get()!!.header("Authorization"))
        assertEquals("/api/api/v1/public/app-community/posts", captured.get()!!.url.encodedPath)
    }

    @Test
    fun `choosing a name posts it and records the server's spelling`() = runTest {
        val member = gateway("""{"registered":true,"id":7,"display_name":"علی رضا","created":true}""")
            .register("  علی   رضا ")

        assertEquals("/api/api/v1/public/app-community/me", captured.get()!!.url.encodedPath)
        assertEquals("""{"display_name":"علی   رضا"}""", sentBody())
        assertEquals("علی رضا", member.displayName)
        assertEquals(7L, member.id)
    }

    @Test
    fun `a name somebody else holds is its own refusal`() = runTest {
        val gateway = gateway("""{"detail":"این نام را کس دیگری برداشته است."}""", code = 409)

        val thrown = runCatching { gateway.register("sara") }.exceptionOrNull()
        assertTrue(thrown is CommunityNameTakenException)
        assertEquals("این نام را کس دیگری برداشته است.", (thrown as CommunityNameTakenException).serverText)
    }

    @Test
    fun `a key with no name reads as nobody`() = runTest {
        assertNull(gateway("""{"registered":false}""").me())
        assertEquals("nima", gateway("""{"registered":true,"id":3,"display_name":"nima"}""").me()?.displayName)
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
        assertEquals("/api/api/v1/public/app-community/posts/41/reply", captured.get()!!.url.encodedPath)
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
    fun `a ban becomes a lock carrying the server's own sentence`() = runTest {
        val text = "دسترسی این حساب به انجمن بسته شده است."
        val gateway = gateway("""{"detail":"$text"}""", code = 403)

        val thrown = runCatching { gateway.feed() }.exceptionOrNull()
        assertTrue(thrown is CommunityLockedException)
        assertEquals(text, (thrown as CommunityLockedException).serverText)
    }

    @Test
    fun `a no-name refusal stays an HTTP failure, because it is a different control`() = runTest {
        // 401 is «ابتدا یک نام نمایشی انتخاب کنید» — a form. Folding it into the ban would tell a
        // reader with no name that they are banned.
        val gateway = gateway("""{"detail":"برای نوشتن در انجمن ابتدا یک نام نمایشی انتخاب کنید."}""", code = 401)

        val thrown = runCatching { gateway.post("سلام به همه") }.exceptionOrNull()
        assertTrue(thrown is HttpException)
        assertEquals(401, (thrown as HttpException).code())
    }

    @Test
    fun `a text the server refuses carries its sentence about which rule`() = runTest {
        val gateway = gateway("""{"detail":"لینک، شماره تماس یا آیدی پیام‌رسان در متن مجاز نیست."}""", code = 400)

        val thrown = runCatching { gateway.post("join https://t.me/pump") }.exceptionOrNull()
        assertTrue(thrown is CommunityRefusedException)
        assertEquals("لینک، شماره تماس یا آیدی پیام‌رسان در متن مجاز نیست.", (thrown as CommunityRefusedException).serverText)
    }

    @Test
    fun `a hidden post is the same answer as a missing one`() = runTest {
        val gateway = gateway("""{"detail":"این پست در دسترس نیست."}""", code = 404)

        assertTrue(runCatching { gateway.thread(41) }.exceptionOrNull() is CommunityPostNotFoundException)
    }

    @Test
    fun `crowning a reply does not map its 403 to a ban`() = runTest {
        // `best_reply` answers 403 to anyone who is not the post's author. That is a sentence
        // about authorship, and telling the reader they are banned for it would be nonsense.
        val gateway = gateway("""{"detail":"فقط نویسندهٔ پست می‌تواند پاسخ برگزیده را تعیین کند."}""", code = 403)

        val thrown = runCatching { gateway.bestReply(41, 88) }.exceptionOrNull()
        assertTrue(thrown is HttpException)
    }

    @Test
    fun `clearing the best reply is rid zero, which is the route's own convention`() = runTest {
        gateway("""{"ok":true,"best_reply_id":null}""").bestReply(41, 0)

        assertEquals("/api/api/v1/public/app-community/posts/41/best-reply/0", captured.get()!!.url.encodedPath)
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
    fun `the leaderboard is read off the board's own route`() = runTest {
        val board = gateway("""{"items":[{"rank":1,"username":"ali","xp":9}],"my_rank":4,"total_students":80}""")
            .leaderboard()

        assertEquals("/api/api/v1/public/app-community/leaderboard", captured.get()!!.url.encodedPath)
        assertEquals(1, board.leaders.size)
        assertEquals(4, board.myRank)
    }
}
