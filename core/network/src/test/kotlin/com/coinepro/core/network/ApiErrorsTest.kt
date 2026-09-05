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
        val error = ApiErrors.parse("""{"detail":"پنلِ کاربری ویژه‌ی اعضای VIP است."}""")

        assertNull("A bare string carries no machine-readable code", error.code)
        assertEquals("پنلِ کاربری ویژه‌ی اعضای VIP است.", error.message)
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
        assertEquals("Bad Request", error.untranslatedDetail)
    }

    @Test
    fun `a 7807 title without a detail is diagnostic, not a message`() {
        val error = ApiErrors.parse("""{"title":"Too Many Requests","status":429,"code":"rate_limited"}""")

        assertEquals("rate_limited", error.code)
        assertNull(error.message)
        assertEquals("Too Many Requests", error.untranslatedDetail)
    }

    @Test
    fun `English pydantic defaults never reach the reader, but do name the fields`() {
        // Captured from TradeYar's legacy surface. Rendering "Field required" verbatim to a Persian
        // reader is a language failure wearing honesty's clothes.
        val error = ApiErrors.parse(
            """{"detail":[
                 {"type":"missing","loc":["body","email"],"msg":"Field required","input":{}},
                 {"type":"missing","loc":["body","phone"],"msg":"Field required","input":{}}]}""",
        )

        assertNull("English server text must not be shown as though written for the reader", error.message)
        assertEquals("Field required", error.untranslatedDetail)
        assertEquals(listOf("email", "phone"), error.fields)
        assertEquals("email", error.field)
    }

    @Test
    fun `a Persian validation array is reader-facing and keeps every complaint`() {
        val error = ApiErrors.parse(
            """{"detail":[{"loc":["body","email"],"msg":"ایمیل نامعتبر است.","type":"value_error"},
                         {"loc":["body","password"],"msg":"رمز کوتاه است.","type":"value_error"}]}""",
        )

        assertEquals(
            "A form refused for two reasons that reports one sends the reader back twice",
            "ایمیل نامعتبر است. رمز کوتاه است.",
            error.message,
        )
        assertEquals(listOf("email", "password"), error.fields)
    }

    @Test
    fun `does not repeat an identical validation message once per field`() {
        val error = ApiErrors.parse(
            """{"detail":[{"msg":"این فیلد لازم است."},{"msg":"این فیلد لازم است."}]}""",
        )

        assertEquals("این فیلد لازم است.", error.message)
    }

    @Test
    fun `a bare English detail is diagnostic, a bare Persian one is for the reader`() {
        // Both shapes are live: TradeYar's older paths answer {"detail":"Unauthorized"} while
        // CoinePro-FX's panel routes answer a Persian sentence in the same position.
        val english = ApiErrors.parse("""{"detail":"Unauthorized"}""")
        assertNull(english.message)
        assertEquals("Unauthorized", english.untranslatedDetail)

        val persian = ApiErrors.parse("""{"detail":"دسترسی ندارید."}""")
        assertEquals("دسترسی ندارید.", persian.message)
        assertNull(persian.untranslatedDetail)
    }

    @Test
    fun `carries the field, trace id and code a 7807 body names`() {
        val error = ApiErrors.parse(
            """{"type":"https://api.tradeyar.io/errors/TYR-017","title":"Validation Field Invalid",
                "status":422,"detail":"رمز عبور باید حداقل 10 کاراکتر باشد.","code":"TYR-017",
                "trace_id":"143770cc2b090c509ea8293082dab532","field":"password"}""",
        )

        assertEquals("TYR-017", error.code)
        assertEquals("رمز عبور باید حداقل 10 کاراکتر باشد.", error.message)
        assertEquals("A named field lets the form mark the box, not just the page", "password", error.field)
        assertEquals("143770cc2b090c509ea8293082dab532", error.traceId)
        assertEquals(
            "The English title is kept, but only where a log can use it",
            "Validation Field Invalid",
            error.untranslatedDetail,
        )
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
