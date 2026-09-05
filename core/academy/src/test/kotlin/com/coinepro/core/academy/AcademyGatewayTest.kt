package com.coinepro.core.academy

import com.coinepro.core.marketdata.AcademyTokenStore
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The academy routes, read against the JSON their handlers actually build.
 *
 * Every payload below is transcribed from `academy.py` rather than invented, which is what makes
 * the awkward cases real: `/me` keys its levels in a JSON object with no order, the glossary file
 * writes `def` rather than `definition` and folds the English name into brackets, and a locked
 * lesson answers 403 with a Persian sentence rather than a code.
 */
class AcademyGatewayTest {

    private val captured = AtomicReference<Request?>()

    private class FixedToken(private val value: String = "academy-token") : AcademyTokenStore {
        var mints = 0
            private set

        override suspend fun token(): String {
            mints++
            return value
        }

        override fun clear() = Unit
    }

    private fun gateway(
        body: String,
        code: Int = 200,
        tokens: AcademyTokenStore = FixedToken(),
    ): AcademyGateway {
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    captured.set(chain.request())
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message(if (code == 200) "OK" else "Forbidden")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            )
            .build()
        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://example.invalid/api/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        return NetworkAcademyGateway(retrofit, tokens)
    }

    @Test
    fun `the profile reads, and its levels come back in curriculum order`() = runTest {
        // `/me` sends by_level as a JSON object. Object keys have no order, so a screen that
        // iterated the map would show "advanced" above "beginner" on some runs and not others.
        val body = """
            {"username":"reza","full_name":"رضا","tier":"vip","account_type":null,
             "phone_number":null,"phone_required":true,"expires_at":null,
             "completed":12,"total_lessons":140,"progress_pct":8.6,
             "xp":420,"badges":["level_master_beginner"],
             "by_level":{"advanced":{"name":"پیشرفته","done":0,"total":40,"mastered":false},
                         "beginner":{"name":"مقدماتی","done":12,"total":30,"mastered":false},
                         "intermediate":{"name":"متوسط","done":0,"total":38,"mastered":false}},
             "quizzes_taken":9,"avg_quiz":78,
             "streak":{"current":4,"longest":11},"achievements_count":2}
        """.trimIndent()
        val profile = gateway(body).profile()

        assertEquals(listOf("beginner", "intermediate", "advanced"), profile.byLevel.map { it.key })
        assertEquals("vip", profile.tier)
        assertTrue("no phone on file, so paid lessons stay shut", profile.phoneRequired)
        assertEquals(420, profile.xp)
        assertEquals(78, profile.averageQuiz)
        assertEquals(4, profile.streak.current)
        assertEquals(12.0 / 30.0, profile.byLevel.first().fraction, 1e-9)
    }

    @Test
    fun `a profile before the first quiz reports no average rather than zero`() = runTest {
        // Zero is a failing grade. "Not taken yet" is not.
        val body = """{"username":"new","tier":"free","quizzes_taken":0,"avg_quiz":null}"""
        assertNull(gateway(body).profile().averageQuiz)
    }

    @Test
    fun `every call carries the academy token, not the mobile one`() = runTest {
        val tokens = FixedToken("minted-token")
        gateway("""{"tier":"free","levels":[]}""", tokens = tokens).catalog()

        assertEquals("Bearer minted-token", captured.get()!!.header("Authorization"))
        assertEquals("/api/academy/catalog", captured.get()!!.url.encodedPath)
    }

    @Test
    fun `the catalogue keeps the server's level order and its lock reasons`() = runTest {
        val body = """
            {"tier":"free","levels":[
              {"key":"beginner","name":"مقدماتی","lessons":[
                {"slug":"what-is-forex","title":"فارکس چیست","order":1,"tier":"free",
                 "locked":false,"lock_reason":null,"completed":true,"has_video":true},
                {"slug":"lots","title":"لات","order":2,"tier":"vip",
                 "locked":true,"lock_reason":"tier","completed":false,"has_video":false}]},
              {"key":"intermediate","name":"متوسط","lessons":[
                {"slug":"rsi","title":"آر‌اس‌آی","order":1,"tier":"vip",
                 "locked":true,"lock_reason":"phone","completed":false,"has_video":false}]}
            ]}
        """.trimIndent()
        val catalog = gateway(body).catalog()

        assertEquals(listOf("beginner", "intermediate"), catalog.levels.map { it.key })
        assertEquals(1, catalog.levels.first().completed)
        assertEquals(LockReason.TIER, catalog.levels.first().lessons[1].lockReason)
        assertEquals(LockReason.PHONE, catalog.levels[1].lessons.first().lockReason)
        assertNull("an open lesson has no reason", catalog.levels.first().lessons.first().lockReason)
    }

    @Test
    fun `a lesson reads, video path and watermark included`() = runTest {
        val body = """
            {"slug":"lots","level":"beginner","title":"لات","summary":"اندازه‌ی معامله",
             "content":"<p>یک لات استاندارد ۱۰۰٬۰۰۰ واحد است.</p>","diagram_image":null,
             "tier":"free","video_url":"/api/academy/video/9?t=abc","video_duration":420,
             "reading_time_min":3,"watermark":"reza"}
        """.trimIndent()
        val lesson = gateway(body).lesson("lots")

        assertEquals("/api/academy/video/9?t=abc", lesson.videoPath)
        assertEquals(420, lesson.videoDurationSeconds)
        assertEquals("reza", lesson.watermark)
        assertEquals(3, lesson.readingTimeMinutes)
    }

    @Test
    fun `a locked lesson raises the right lock, told apart by what the message is about`() = runTest {
        val phone = """{"detail":"برای بازکردنِ سطح‌های حرفه‌ای، شماره‌ی موبایلت را در پروفایل وارد کن."}"""
        val tier = """{"detail":"این درس نیازمندِ اشتراکِ بالاتر است."}"""

        val phoneFailure = runCatching { gateway(phone, code = 403).lesson("x") }.exceptionOrNull()
        assertTrue(phoneFailure is LessonLockedException)
        assertEquals(LockReason.PHONE, (phoneFailure as LessonLockedException).reason)

        val tierFailure = runCatching { gateway(tier, code = 403).lesson("x") }.exceptionOrNull()
        assertEquals(LockReason.TIER, (tierFailure as LessonLockedException).reason)
    }

    @Test
    fun `a quiz arrives without its answers`() = runTest {
        // The point of the separate submit route: the correct index is not in this payload at all,
        // so a reader with a debugger cannot read the answers off the wire.
        val body = """
            {"slug":"lots","count":2,"last_score":60,"questions":[
              {"id":11,"question":"یک لات استاندارد چند واحد است؟","options":["۱۰۰۰","۱۰۰۰۰","۱۰۰۰۰۰"]},
              {"id":12,"question":"مینی‌لات چند واحد است؟","options":["۱۰۰۰","۱۰۰۰۰"]}]}
        """.trimIndent()
        val quiz = gateway(body).quiz("lots")

        assertEquals(2, quiz.questions.size)
        assertEquals(60, quiz.lastScore)
        assertEquals(11L, quiz.questions.first().id)
    }

    @Test
    fun `a question with fewer than two options is dropped`() = runTest {
        // One radio button is not a question, and drawing it invites a tap that cannot be wrong.
        val body = """
            {"slug":"x","count":2,"questions":[
              {"id":1,"question":"سؤال","options":["الف"]},
              {"id":2,"question":"سؤال دوم","options":["الف","ب"]}]}
        """.trimIndent()
        assertEquals(listOf(2L), gateway(body).quiz("x").questions.map { it.id })
    }

    @Test
    fun `submitting sends answers keyed by question id as strings`() = runTest {
        val body = """{"score":50,"correct":1,"total":2,"passed":false,"results":[
            {"id":11,"correct_index":2,"your_index":2,"is_correct":true,"explanation":"درست"},
            {"id":12,"correct_index":0,"your_index":1,"is_correct":false,"explanation":"مینی‌لات ۱۰٬۰۰۰ است."}]}"""
        val result = gateway(body).submitQuiz("lots", mapOf(11L to 2, 12L to 1))

        val sent = captured.get()!!.body!!
        val buffer = okio.Buffer().also { sent.writeTo(it) }.readUtf8()
        assertTrue("keys must be strings: $buffer", buffer.contains("\"11\":2"))
        assertTrue(buffer.contains("\"12\":1"))

        assertEquals(50, result.score)
        assertFalse(result.passed)
        assertEquals(2, result.answers.size)
        assertEquals("مینی‌لات ۱۰٬۰۰۰ است.", result.answers[1].explanation)
    }

    @Test
    fun `the roadmap keeps the server's module totals`() = runTest {
        // `total` is the server's count. A lesson dropped here for a missing slug should read as a
        // gap in a module of eight, not shrink the module to seven and claim it is complete.
        val body = """
            {"level":"beginner","name":"مقدماتی","modules":[
              {"index":0,"title":"ماژول ۱","done":2,"total":3,"lessons":[
                {"slug":"a","title":"الف","order":1,"completed":true,"locked":false},
                {"slug":null,"title":"خراب","order":2,"completed":true,"locked":false},
                {"slug":"c","title":"پ","order":3,"completed":false,"locked":true}]}]}
        """.trimIndent()
        val roadmap = gateway(body).roadmap("beginner")

        val module = roadmap.modules.single()
        assertEquals(2, module.lessons.size)
        assertEquals(3, module.total)
        assertEquals(2, module.done)
    }

    @Test
    fun `achievements come back earned and unearned alike`() = runTest {
        val body = """
            {"items":[
              {"badge":"first_lesson","title":"اولین قدم","desc":"اولین درست را کامل کردی.",
               "icon":"🎯","earned":true,"earned_at":"2026-08-01T09:00:00+00:00"},
              {"badge":"streak_7","title":"هفته‌ی آتشین","desc":"۷ روزِ پیاپی فعال بودی.",
               "icon":"🔥","earned":false,"earned_at":null}],
             "earned_count":1,"total":11}
        """.trimIndent()
        val achievements = gateway(body).achievements()

        assertEquals(2, achievements.items.size)
        assertEquals(1, achievements.earnedCount)
        assertEquals(11, achievements.total)
        assertTrue(achievements.items.first().earned)
        assertEquals("🔥", achievements.items[1].icon)
    }

    @Test
    fun `a rank of null means nothing completed, not last place`() = runTest {
        val body = """{"items":[],"my_rank":null,"total_students":312}"""
        val board = gateway(body).leaderboard()
        assertNull(board.myRank)
        assertEquals(312, board.totalStudents)
    }

    @Test
    fun `the glossary splits the English name out of the Persian term`() = runTest {
        // The file writes «پیپ (Pip)» as one string. Split so the two can be styled apart and so a
        // search for "pip" finds the entry a Persian reader wrote down as پیپ.
        val body = """
            {"terms":[
              {"term":"پیپ (Pip)","def":"کوچک‌ترین واحد استاندارد تغییر قیمت.","cat":"مبانی"},
              {"term":"اسپرد","def":"تفاوتِ بید و اَسک.","cat":"مبانی"},
              {"term":"بدون تعریف","def":"","cat":"مبانی"}]}
        """.trimIndent()
        val terms = gateway(body).glossary()

        assertEquals(2, terms.size)
        assertEquals("پیپ", terms.first().term)
        assertEquals("Pip", terms.first().english)
        assertNull("no brackets, no English name", terms[1].english)
    }

    @Test
    fun `a streak that has never started is zero rather than absent`() = runTest {
        val body = """{"current":0,"longest":0,"last_active":null,"today_done":false}"""
        val streak = gateway(body).streak()
        assertEquals(0, streak.current)
        assertFalse(streak.todayDone)
        assertNull(streak.lastActive)
    }
}
