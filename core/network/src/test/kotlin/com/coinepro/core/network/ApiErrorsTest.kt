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
