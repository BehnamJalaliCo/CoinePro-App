package com.coinepro.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiErrorsTest {

    @Test
    fun `reads the structured envelope the mobile routes return`() {
        val error = ApiErrors.parse(
            """{"detail":{"code":"invalid_credentials","message":"ایمیل یا رمزِ عبور درست نیست."}}""",
        )

        assertEquals("invalid_credentials", error.code)
        assertEquals("ایمیل یا رمزِ عبور درست نیست.", error.message)
        assertNull(error.retryAfterSeconds)
    }

    @Test
    fun `reads the bare-string envelope the panel routes still return`() {
        val error = ApiErrors.parse("""{"detail":"پنلِ کاربری ویژهٔ اعضای VIP است."}""")

        assertNull("A bare string carries no machine-readable code", error.code)
        assertEquals("پنلِ کاربری ویژهٔ اعضای VIP است.", error.message)
    }

    @Test
    fun `takes the rate-limit wait from the body, where this server puts it`() {
        val error = ApiErrors.parse(
            """{"detail":{"code":"rate_limited","message":"تلاش‌های زیاد.","retry_after":60}}""",
        )

        assertEquals(60, error.retryAfterSeconds)
    }

    @Test
    fun `a deployment that sends only the header leaves the body wait absent`() {
        // TradeYar puts the wait in Retry-After and nowhere else; the gateway falls back to the
        // header, so finding nothing here has to stay a clean null rather than a zero.
        assertNull(ApiErrors.parse("""{"detail":"تلاش‌های زیاد."}""").retryAfterSeconds)
    }

    @Test
    fun `reads TradeYar's RFC 7807 shape, where the code sits beside detail rather than inside it`() {
        val error = ApiErrors.parse(
            """{"type":"about:blank","title":"Bad Request","status":400,
                "detail":"این نماد پشتیبانی نمی‌شود.","code":"unsupported_symbol"}""",
        )

        assertEquals("unsupported_symbol", error.code)
        assertEquals("این نماد پشتیبانی نمی‌شود.", error.message)
    }

    @Test
    fun `falls back to a 7807 title when it carries no detail`() {
        val error = ApiErrors.parse("""{"title":"Too Many Requests","status":429,"code":"rate_limited"}""")

        assertEquals("rate_limited", error.code)
        assertEquals("Too Many Requests", error.message)
    }

    @Test
    fun `reads FastAPI's validation array and keeps every complaint`() {
        val error = ApiErrors.parse(
            """{"detail":[{"loc":["body","email"],"msg":"ایمیل نامعتبر است.","type":"value_error"},
                         {"loc":["body","password"],"msg":"رمز کوتاه است.","type":"value_error"}]}""",
        )

        assertEquals(
            "A form refused for two reasons that reports one sends the reader back twice",
            "ایمیل نامعتبر است. رمز کوتاه است.",
            error.message,
        )
    }

    @Test
    fun `does not repeat an identical validation message once per field`() {
        val error = ApiErrors.parse(
            """{"detail":[{"msg":"این فیلد لازم است."},{"msg":"این فیلد لازم است."}]}""",
        )

        assertEquals("این فیلد لازم است.", error.message)
    }

    @Test
    fun `an unreadable body yields no reason rather than a made-up one`() {
        for (body in listOf(null, "", "   ", "not json at all", "[]", """{"detail":null}""", "{}")) {
            val error = ApiErrors.parse(body)
            assertNull("body=$body", error.code)
            assertNull("body=$body", error.message)
        }
    }

    @Test
    fun `ignores a blank message rather than showing an empty refusal`() {
        val error = ApiErrors.parse("""{"detail":{"code":"x","message":"  "}}""")

        assertEquals("x", error.code)
        assertNull(error.message)
    }

    @Test
    fun `ignores a nonsense retry_after`() {
        assertNull(ApiErrors.parse("""{"detail":{"retry_after":"soon"}}""").retryAfterSeconds)
        assertNull(ApiErrors.parse("""{"detail":{"retry_after":-5}}""").retryAfterSeconds)
    }
}
