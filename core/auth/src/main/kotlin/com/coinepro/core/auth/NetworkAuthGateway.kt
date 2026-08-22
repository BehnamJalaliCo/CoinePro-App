package com.coinepro.core.auth

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import java.io.IOException
import retrofit2.HttpException

class NetworkAuthGateway internal constructor(
    private val api: AuthApi,
) : AuthGateway {
    override suspend fun authConfig(): AppResult<AuthConfig> = call {
        AuthConfig(api.authConfig().botUsername)
    }

    override suspend fun loginTelegram(payload: TelegramAuthPayload): AppResult<AuthSession> = call {
        val response = api.loginTelegram(payload)
        AuthSession(response.token, response.profile)
    }

    override suspend fun me(): AppResult<UserProfile> = call { api.me() }

    private suspend fun <T> call(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (error: HttpException) {
        AppResult.Failure(
            kind = when (error.code()) {
                401, 403 -> ErrorKind.AUTH
                422 -> ErrorKind.VALIDATION
                429 -> ErrorKind.RATE_LIMIT
                in 500..599 -> ErrorKind.SERVER
                else -> ErrorKind.UNKNOWN
            },
            message = error.message(),
            cause = error,
        )
    } catch (error: IOException) {
        AppResult.Failure(ErrorKind.NETWORK, cause = error)
    } catch (error: Throwable) {
        AppResult.Failure(ErrorKind.UNKNOWN, cause = error)
    }

    companion object {
        fun create(retrofit: retrofit2.Retrofit): NetworkAuthGateway =
            NetworkAuthGateway(retrofit.create(AuthApi::class.java))
    }
}
