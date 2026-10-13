package com.coinepro.core.marketdata

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * CoinePro-FX's guest chart allowance (5.33.0): 120 requests per address per ten minutes, and a
 * lock-out that doubles each time it is crossed. The server report behind this counted eighty chart
 * requests in the minute a forex chart opened and twenty-seven refusals.
 */
class CandleBudgetTest {

    private class Clock(var now: Long = 1_000_000L)

    private fun budget(clock: Clock, waits: MutableList<Long> = mutableListOf()) = CandleRequestBudget(
        windowMillis = 600_000L,
        readerShare = 5,
        backgroundShare = 3,
        bulkShare = 2,
        nowMillis = { clock.now },
        pause = { waits += it; clock.now += it },
    )

    @Test
    fun `bulk work stops at its share and waits for the window to open again`() = runTest {
        val clock = Clock()
        val waits = mutableListOf<Long>()
        val budget = budget(clock, waits)
        budget.admit(CandleUrgency.BULK, metered = true)
        budget.admit(CandleUrgency.BULK, metered = true)
        assertTrue(waits.isEmpty())
        budget.admit(CandleUrgency.BULK, metered = true)
        assertEquals("the third waits until the first has left the window", 600_000L, waits.sum())
    }

    @Test
    fun `the chart the reader opens is never queued behind the history walk`() = runTest {
        val clock = Clock()
        val budget = budget(clock)
        repeat(2) { budget.admit(CandleUrgency.BULK, metered = true) }
        repeat(3) { budget.admit(CandleUrgency.READER, metered = true) }
        assertEquals(5, budget.spent())
    }

    @Test
    fun `a reader request past the allowance is refused without being sent`() = runTest {
        val clock = Clock()
        val budget = budget(clock)
        repeat(5) { budget.admit(CandleUrgency.READER, metered = true) }
        try {
            budget.admit(CandleUrgency.READER, metered = true)
            fail("the sixth must not go out")
        } catch (refused: CandleRateLimitedException) {
            assertEquals(600L, refused.retryAfterSeconds)
        }
        assertEquals(5, budget.spent())
    }

    @Test
    fun `a student's credential is not metered`() = runTest {
        val budget = budget(Clock())
        repeat(50) { budget.admit(CandleUrgency.BULK, metered = false) }
        assertEquals(0, budget.spent())
    }

    @Test
    fun `after a 429 a reader is refused at once and background work waits it out`() = runTest {
        val clock = Clock()
        val waits = mutableListOf<Long>()
        val budget = budget(clock, waits)
        budget.penalise(30)
        try {
            budget.admit(CandleUrgency.READER, metered = false)
            fail("nothing goes out during a lock-out")
        } catch (refused: CandleRateLimitedException) {
            assertEquals(30L, refused.retryAfterSeconds)
        }
        budget.admit(CandleUrgency.BACKGROUND, metered = true)
        assertEquals(30_000L, waits.sum())
    }

    @Test
    fun `the wait is read from Retry-After, else from the guest scope's own sentence`() {
        assertEquals(42L, CandleRequestBudget.retryAfterSeconds("42", null))
        assertEquals(412L, CandleRequestBudget.retryAfterSeconds(null, """{"detail":"درخواستِ زیاد. 412 ثانیه‌ی دیگر دوباره تلاش کنید."}"""))
        assertEquals(60L, CandleRequestBudget.retryAfterSeconds(null, """{"detail":"too many"}"""))
    }

    // ── the gateway ───────────────────────────────────────────────────────────────────

    private class Refusing(private val code: Int, private val body: String) : Interceptor {
        val asked = mutableListOf<String>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            asked += request.url.queryParameter("tf").orEmpty()
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("refused")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private class GuestToken : AcademyTokenStore {
        override suspend fun token(): String = "guest"
        override fun clear() = Unit
        override fun isGuest(token: String): Boolean = true
    }

    private fun gateway(refusing: Refusing, budget: CandleRequestBudget) = CoineProFxCandleGateway(
        Retrofit.Builder()
            .baseUrl("https://example.test/api/")
            .client(OkHttpClient.Builder().addInterceptor(refusing).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build(),
        GuestToken(),
        budget = budget,
    )

    @Test
    fun `a 429 is not asked again one feed finer, and locks the gateway for the wait it named`() = runTest {
        val refusing = Refusing(429, """{"detail":"درخواستِ زیاد. 300 ثانیه‌ی دیگر دوباره تلاش کنید."}""")
        val clock = Clock()
        val gateway = gateway(refusing, budget(clock))
        try {
            gateway.load("EURUSD", ChartInterval.Preset(Timeframe.H1))
            fail("a refusal is a failure")
        } catch (refused: CandleRateLimitedException) {
            assertEquals(300L, refused.retryAfterSeconds)
        }
        assertEquals("H1 only — not M15 × 1200 and M5 × 3000 after it", listOf("H1"), refusing.asked)

        try {
            gateway.load("GBPUSD", Timeframe.H1)
            fail("the lock-out holds")
        } catch (_: CandleRateLimitedException) {
        }
        assertEquals("nothing was sent into the lock-out", 1, refusing.asked.size)
    }

    @Test
    fun `a feed that is out is still covered one feed finer`() = runTest {
        val refusing = Refusing(404, """{"detail":"داده‌ی این نماد نیست."}""")
        val gateway = gateway(refusing, budget(Clock()))
        runCatching { gateway.load("EURUSD", ChartInterval.Preset(Timeframe.H1)) }
        assertEquals(listOf("H1", "M15", "M5"), refusing.asked)
    }
}
