package com.coinepro.core.auth

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The gap between what a Kotlin declaration promises and what Gson actually writes.
 *
 * Gson builds a class with no no-argument constructor through `Unsafe`: it allocates the object and
 * assigns fields directly, so **the constructor never runs and neither do Kotlin's null checks.**
 * A `val x: String` whose key is absent from the JSON is therefore `null` at runtime, in a type the
 * compiler has guaranteed cannot be. Nothing fails at that point. It fails at the first
 * dereference, frames away, as a `NullPointerException` in code that reads as though it cannot
 * throw one — and in this module that landed in `catch (error: Throwable)`, became
 * `ErrorKind.UNKNOWN`, and reached the reader as «پاسخی نرسید»: *no answer came*.
 *
 * **This is not a hypothetical shape.** Measured against the live deployment on 2026-09-22,
 * TradeYar's `auth/methods` answers `"telegram": true` and carries no `telegram_bot_username` at
 * all. A server that reports the method and omits the name is something this product serves today.
 *
 * So every field a server might leave out is nullable with a default, and the tests below are the
 * proof that the parse survives it rather than a reminder to be careful.
 */
class AuthDtoNullabilityTest {

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    @Test
    fun `a config answer with no bot name parses, and the name is absent rather than empty-by-accident`() {
        val dto = gson.fromJson("{}", AuthConfigDto::class.java)
        assertNull(dto.botUsername)
        // And the gateway's reading of it is a blank name, which is what makes the screen leave
        // the Telegram button out — a deployment that does not name its bot is not a broken one.
        assertEquals("", dto.botUsername.orEmpty())
    }

    @Test
    fun `a config answer carrying the name keeps it, in either spelling`() {
        // **This test failed on its first run, and that is how the second fault was found.**
        // `AuthConfigDto` had no `@SerializedName`, so under `LOWER_CASE_WITH_UNDERSCORES` it
        // asked for `bot_username` and nothing else — while CoinePro-FX's config answer carries
        // `botUsername`, camelCase, in the same object where it spells other keys with
        // underscores. A camelCase key under a snake_case policy does not fail: it parses as the
        // default, which was a null in a non-null type.
        assertEquals(
            "CoineProFxBot",
            gson.fromJson("""{"botUsername":"CoineProFxBot"}""", AuthConfigDto::class.java).botUsername,
        )
        assertEquals(
            "CoineProFxBot",
            gson.fromJson("""{"bot_username":"CoineProFxBot"}""", AuthConfigDto::class.java).botUsername,
        )
    }

    @Test
    fun `a telegram sign-in answer missing its token parses rather than throwing mid-parse`() {
        // The point is that the *failure is the caller's to state*. Parsing succeeds, the caller's
        // `requireNotNull` names the missing field, and the reader is told the answer could not be
        // read — instead of an NPE that says nothing about which field was absent.
        val dto = gson.fromJson("""{"profile":{}}""", LoginResponseDto::class.java)
        assertNull(dto.token)
    }

    @Test
    fun `the methods answer survives a deployment that reports telegram without naming its bot`() {
        // TradeYar's live shape, trimmed to the fields that matter. The 442-symbol `symbols` array
        // it also carries has no field here at all, which is correct: Gson ignores what the DTO
        // does not declare, and a capability answer is not where a symbol catalogue belongs.
        val body = """
            {"email_password":true,"google":true,
             "google_client_id":"1033486124390-07nqc4h9j1agsrcrpvq7cgsa5k6evced.apps.googleusercontent.com",
             "telegram":true,"push":true,"chart_vision":false,"ai_signals":true,"assistant":false,
             "accountDeletion":true,"account_deletion":true,
             "symbols":[{"symbol":"BTCUSDT","display_name":"بیت‌کوین"}],"symbols_count":442}
        """.trimIndent()
        val dto = gson.fromJson(body, AuthMethodsDto::class.java)
        assertEquals(true, dto.emailPassword)
        assertEquals(true, dto.telegram)
        // The one the server does not send, and the reason this test exists.
        assertNull(dto.telegramBotUsername)
        // `accountDeletion` arrives in both spellings; the alternate list means either fills it.
        assertEquals(true, dto.accountDeletion)
    }
}
