package com.coinepro.core.auth

import com.coinepro.core.model.MarketPlatform
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

internal data class AuthConfigDto(val botUsername: String)
internal data class LoginResponseDto(val token: String, val profile: AuthUserDto)

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
) {
    class TelegramPaths(val config: String, val login: String)

    companion object {
        fun of(platform: MarketPlatform): SessionPaths = when (platform) {
            MarketPlatform.COINEPRO_FX -> SessionPaths(
                me = "user/me",
                telegram = TelegramPaths("user/auth/config", "user/auth/telegram"),
            )
            MarketPlatform.TRADEYAR -> SessionPaths(
                me = "api/mobile/v1/me",
                telegram = null,
            )
        }
    }
}
