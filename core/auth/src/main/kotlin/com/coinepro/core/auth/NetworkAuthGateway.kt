package com.coinepro.core.auth

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import com.coinepro.core.common.RetryAfter
import java.io.IOException
import retrofit2.HttpException

class NetworkAuthGateway internal constructor(
    private val api: AuthApi,
    private val paths: SessionPaths,
) : AuthGateway {
    override suspend fun authConfig(): AppResult<AuthConfig> {
        // A deployment without Telegram sign-in is not a broken one. Reporting it as unconfigured
        // is what makes the screen leave the button out, rather than draw one that cannot work.
        val telegram = paths.telegram ?: return AppResult.Success(AuthConfig(botUsername = ""))
        return call { AuthConfig(api.authConfig(telegram.config).botUsername) }
    }

    override suspend fun loginTelegram(payload: TelegramAuthPayload): AppResult<AuthSession> {
        val telegram = paths.telegram ?: return AppResult.Failure(ErrorKind.AUTH)
        return call {
            val response = api.loginTelegram(telegram.login, payload)
            AuthSession(response.token, response.profile.toDomain())
        }
    }

    override suspend fun me(): AppResult<UserProfile> = call {
        if (paths.profileIsWrapped) {
            requireNotNull(api.wrappedMe(paths.me).user) { "A profile response with no profile." }
        } else {
            api.me(paths.me)
        }.toDomain()
    }

    private suspend fun <T> call(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (error: HttpException) {
        val kind = when (error.code()) {
            401, 403 -> ErrorKind.AUTH
            422 -> ErrorKind.VALIDATION
            429 -> ErrorKind.RATE_LIMIT
            in 500..599 -> ErrorKind.SERVER
            else -> ErrorKind.UNKNOWN
        }
        AppResult.Failure(
            kind = kind,
            message = error.message(),
            cause = error,
            // Sign-in is the one place a rate limit is aimed at a person rather than a background
            // job, so the wait is worth surfacing. Read only on 429: a Retry-After on any other
            // status is not about this caller.
            retryAfterSeconds = if (kind == ErrorKind.RATE_LIMIT) {
                RetryAfter.parseSeconds(error.response()?.headers()?.get("Retry-After"))
            } else {
                null
            },
        )
    } catch (error: IOException) {
        AppResult.Failure(ErrorKind.NETWORK, cause = error)
    } catch (error: Throwable) {
        AppResult.Failure(ErrorKind.UNKNOWN, cause = error)
    }

    companion object {
        fun create(
            retrofit: retrofit2.Retrofit,
            platform: com.coinepro.core.model.MarketPlatform,
        ): NetworkAuthGateway = NetworkAuthGateway(
            api = retrofit.create(AuthApi::class.java),
            paths = SessionPaths.of(platform),
        )
    }
}
