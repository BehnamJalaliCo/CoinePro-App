package com.coinepro.core.announcements

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import java.time.Instant
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The address and the parsing, checked against a Retrofit built the way the app builds one.
 *
 * The client answers from an interceptor rather than from a socket, so the test is offline and
 * instant, but everything between the gateway and the response body is real: the same converter,
 * the same field-naming policy, the same annotation processing that turns `announcements(30)` into
 * a URL. Asserting a path constant instead would assert this module against itself and would have
 * passed just as happily with the prefix spelled wrong.
 */
class AnnouncementsGatewayTest {

    @Test
    fun `it asks for the route TradeYar delivered, with the limit as a query`() = runTest {
        val requests = mutableListOf<Request>()
        val gateway = gateway(body = """{"announcements":[]}""", seen = requests)

        gateway.announcements()

        assertEquals(
            "https://tradeyar.invalid/api/mobile/v1/announcements?limit=30",
            requests.single().url.toString(),
        )
    }

    @Test
    fun `a caller may ask for fewer`() = runTest {
        val requests = mutableListOf<Request>()
        val gateway = gateway(body = """{"announcements":[]}""", seen = requests)

        gateway.announcements(limit = 5)

        assertEquals("5", requests.single().url.queryParameter("limit"))
    }

    @Test
    fun `a published row arrives whole`() = runTest {
        val gateway = gateway(
            body = """
            {"announcements":[{
              "id":"ty-2026-08-29-001",
              "title":"اتصال به صرافی موقتاً قطع است",
              "summary":"قیمت‌ها تا رفع مشکل تازه نمی‌شوند.",
              "source":"تریدیار",
              "url":"https://tradeyar.example/status",
              "published_at":"2026-08-29T08:30:00Z",
              "importance":"high"
            }]}
            """.trimIndent(),
        )

        val result = gateway.announcements()

        assertEquals(
            listOf(
                Announcement(
                    id = "ty-2026-08-29-001",
                    title = "اتصال به صرافی موقتاً قطع است",
                    body = "قیمت‌ها تا رفع مشکل تازه نمی‌شوند.",
                    source = "تریدیار",
                    url = "https://tradeyar.example/status",
                    publishedAt = Instant.parse("2026-08-29T08:30:00Z"),
                    importance = AnnouncementImportance.HIGH,
                ),
            ),
            (result as AppResult.Success).value,
        )
    }

    /**
     * The state this screen ships in, and it must arrive as a success.
     *
     * The news pipeline tags everything it ingests as `news`; an announcement is something a person
     * decides to say, so until somebody says one the list is empty and correct. A gateway that
     * reported that as anything but a success would make the reader's first sight of the feature an
     * error message about a service that is working.
     */
    @Test
    fun `an empty channel is a success and not a failure`() = runTest {
        val gateway = gateway(body = """{"announcements":[]}""")

        val result = gateway.announcements()

        assertEquals(emptyList<Announcement>(), (result as AppResult.Success).value)
    }

    @Test
    fun `the list is read under items and under news as well`() = runTest {
        val row = """{"id":"a","title":"t","published_at":"2026-08-29T08:30:00Z"}"""

        for (envelope in listOf("announcements", "items", "news")) {
            val result = gateway(body = """{"$envelope":[$row]}""").announcements()

            assertEquals(
                "the $envelope envelope should have been read",
                listOf("a"),
                (result as AppResult.Success).value.map(Announcement::id),
            )
        }
    }

    @Test
    fun `a malformed row is dropped and its neighbours survive`() = runTest {
        val gateway = gateway(
            body = """
            {"announcements":[
              {"id":"","title":"بدون شناسه","published_at":"2026-08-29T08:30:00Z"},
              {"id":"no-title","published_at":"2026-08-29T08:30:00Z"},
              {"id":"no-date","title":"بدون تاریخ"},
              {"id":"bad-date","title":"تاریخ ناخوانا","published_at":"29 Aug 2026"},
              {"id":"good","title":"سالم","published_at":"2026-08-29T08:30:00Z"}
            ]}
            """.trimIndent(),
        )

        val result = gateway.announcements()

        assertEquals(listOf("good"), (result as AppResult.Success).value.map(Announcement::id))
    }

    /**
     * The offset form is what `datetime.isoformat()` writes, and `Instant.parse` refuses it.
     * A route whose every row was silently dropped for that reason would look exactly like the
     * empty state this feature is expected to have.
     */
    @Test
    fun `a timestamp with a full offset is read`() = runTest {
        val gateway = gateway(
            body = """{"announcements":[{"id":"a","title":"t","published_at":"2026-08-29T12:00:00+03:30"}]}""",
        )

        val result = gateway.announcements()

        assertEquals(
            Instant.parse("2026-08-29T08:30:00Z"),
            (result as AppResult.Success).value.single().publishedAt,
        )
    }

    @Test
    fun `a link that is not https is dropped but the announcement is kept`() = runTest {
        val gateway = gateway(
            body = """
            {"announcements":[{
              "id":"a","title":"t","published_at":"2026-08-29T08:30:00Z",
              "url":"intent://evil/#Intent;scheme=http;end"
            }]}
            """.trimIndent(),
        )

        val announcement = (gateway.announcements() as AppResult.Success).value.single()

        assertNull(announcement.url)
        assertEquals("t", announcement.title)
    }

    @Test
    fun `an ungraded announcement is unknown rather than low`() = runTest {
        val gateway = gateway(
            body = """{"announcements":[{"id":"a","title":"t","published_at":"2026-08-29T08:30:00Z"}]}""",
        )

        val announcement = (gateway.announcements() as AppResult.Success).value.single()

        assertEquals(AnnouncementImportance.UNKNOWN, announcement.importance)
    }

    @Test
    fun `a server refusal keeps the server's own wording`() = runTest {
        val gateway = gateway(
            code = 503,
            body = """{"detail":"سرویس اطلاعیه موقتاً در دسترس نیست."}""",
        )

        val result = gateway.announcements()

        assertEquals(ErrorKind.SERVER, (result as AppResult.Failure).kind)
        assertEquals("سرویس اطلاعیه موقتاً در دسترس نیست.", result.message)
    }

    @Test
    fun `a signed-out reader is an auth failure and not an empty channel`() = runTest {
        val gateway = gateway(code = 401, body = """{"detail":"نشست شما منقضی شده است."}""")

        assertEquals(ErrorKind.AUTH, (gateway.announcements() as AppResult.Failure).kind)
    }

    /**
     * A body in a shape this module cannot read must fail loudly. Absorbed into an empty list it
     * would be indistinguishable from the state this feature is designed to have on day one, and
     * nobody would ever find it.
     */
    @Test
    fun `a body that is not the expected envelope fails rather than reading as empty`() = runTest {
        val gateway = gateway(body = """[{"id":"a","title":"t"}]""")

        val result = gateway.announcements()

        assertTrue("a bare array must not parse into an empty success", result is AppResult.Failure)
    }

    private fun gateway(
        body: String,
        code: Int = 200,
        seen: MutableList<Request>? = null,
    ): AnnouncementsGateway {
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    seen?.add(chain.request())
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message(if (code == 200) "OK" else "ERROR")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            )
            .build()
        // The same Gson the app's own `NetworkFactory` builds, so `published_at` reaches
        // `publishedAt` here exactly as it does in the running app.
        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://tradeyar.invalid/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        return NetworkAnnouncementsGateway.create(retrofit)
    }
}
