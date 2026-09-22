package com.coinepro.core.auth

import com.coinepro.core.model.MarketPlatform
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * **Every field a server might omit is nullable with a default, and that is not defensiveness.**
 *
 * Gson builds these through `Unsafe` when there is no no-argument constructor, which means it
 * writes fields directly and **Kotlin's null checks never run**. A `val x: String` that the JSON
 * does not carry is therefore `null` at runtime, in a type the compiler has promised is not — and
 * the program does not fail there. It fails at the first thing that touches it, several frames
 * away, as a `NullPointerException` in code that reads as though it cannot throw one.
 *
 * That is not hypothetical here. TradeYar's `auth/methods` answers `"telegram": true` with **no**
 * `telegram_bot_username` at all — measured 2026-09-22 — so a deployment reporting Telegram while
 * omitting its bot name is a shape this product actually serves.
 *
 * And the landing place made it worse than a crash: an NPE inside `call { }` is a `Throwable`, so
 * it became `ErrorKind.UNKNOWN`, and `UNKNOWN` read on screen as «پاسخی نرسید» — *no answer came*.
 * An answer came. The app could not read it. See [AuthFailureReason.UNREADABLE].
 */
internal data class AuthConfigDto(
    /**
     * **Both spellings, for the reason `AuthMethodsDto` gives at length.** The app's Gson is set to
     * `LOWER_CASE_WITH_UNDERSCORES`, so this field asked for `bot_username` and nothing else — and
     * CoinePro-FX's config answer carries `botUsername`, camelCase, in the very same object where
     * it spells other keys with underscores. A camelCase key under a snake_case policy does not
     * fail; it parses as the default. Before this that default was a null in a non-null type, and
     * the Telegram button vanished behind «پاسخی نرسید».
     */
    @SerializedName(value = "bot_username", alternate = ["botUsername"])
    val botUsername: String? = null,
)

/**
 * The Telegram sign-in answer. Both fields nullable for the reason above; the caller turns an
 * absent one into a stated failure rather than a null dereference two frames later.
 */
internal data class LoginResponseDto(val token: String? = null, val profile: AuthUserDto? = null)

/**
 * The Telegram-era surface, which only CoinePro-FX serves in full.
 *
 * Paths are passed in for the same reason as on [MobileAuthApi]: the profile read lives at
 * `user/me` on CoinePro-FX and `api/mobile/v1/me` on TradeYar, and a hard-coded prefix silently
 * turned every crypto session restore into a 404 that the app reported as "session exists but could
 * not be revalidated" — an outage message for a wiring mistake.
 */
internal interface AuthApi {
    @GET
    suspend fun authConfig(@Url path: String): AuthConfigDto

    @POST
    suspend fun loginTelegram(@Url path: String, @Body payload: TelegramAuthPayload): LoginResponseDto

    @GET
    suspend fun me(@Url path: String): AuthUserDto

    @GET
    suspend fun wrappedMe(@Url path: String): MeEnvelopeDto
}

/**
 * [telegram] is null where the deployment has no Telegram sign-in at all.
 *
 * Null rather than a path that would 404: a route that was never built is not a failure to report,
 * and asking for it would fill the log with errors that describe the app rather than the server.
 */
internal class SessionPaths(
    val me: String,
    val telegram: TelegramPaths?,
    /** Whether the profile arrives inside a `user` key rather than on its own. */
    val profileIsWrapped: Boolean,
) {
    class TelegramPaths(val config: String, val login: String)

    companion object {
        fun of(platform: MarketPlatform): SessionPaths = when (platform) {
            MarketPlatform.COINEPRO_FX -> SessionPaths(
                me = "user/me",
                telegram = TelegramPaths("user/auth/config", "user/auth/telegram"),
                profileIsWrapped = false,
            )
            MarketPlatform.TRADEYAR -> SessionPaths(
                me = "api/mobile/v1/me",
                telegram = null,
                profileIsWrapped = true,
            )
        }
    }
}
