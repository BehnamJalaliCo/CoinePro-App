package com.coinepro.core.auth

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import com.coinepro.core.common.RetryAfter
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.network.ApiErrors
import java.io.IOException
import retrofit2.HttpException
import retrofit2.Retrofit

/**
 * The email-first flow against one platform's live surface.
 *
 * Written from the servers' own captured responses rather than from the design document, so the
 * places where the two differ are settled in favour of the server: the code field is `otp`, the
 * recovery token is a short dashed code rather than an opaque blob, refresh returns no profile, and
 * a 429's wait arrives in the body on CoinePro-FX but only in a header on TradeYar.
 *
 * [paths] is what makes one implementation serve both deployments. The routes are the same eight
 * names under different prefixes, and building them in one place is what keeps a crypto sign-in
 * from being posted to a forex path — which does not fail loudly, it simply 404s in wording that
 * reads like an outage.
 */
class NetworkEmailAuthGateway internal constructor(
    private val api: MobileAuthApi,
    private val paths: AuthPaths,
) : EmailAuthGateway {

    override suspend fun methods(): AppResult<AuthMethods> = call {
        api.methods(paths.methods).let {
            AuthMethods(
                emailPassword = it.emailPassword,
                google = it.google,
                googleClientId = it.googleClientId,
                telegram = it.telegram,
                telegramBotUsername = it.telegramBotUsername,
                push = it.push,
                chartVision = it.chartVision,
                assistant = it.assistant,
                aiSignals = it.aiSignals,
                accountDeletion = it.accountDeletion,
            )
        }
    }

    override suspend fun startRegistration(
        email: String,
        password: String,
        fullName: String,
    ): AppResult<RegistrationChallenge> = call {
        val response = api.registerStart(paths.registerStart, RegisterStartRequest(email, password, fullName))
        RegistrationChallenge(
            registrationToken = requireNotNull(response.registrationToken) {
                "The server accepted the registration without issuing a token."
            },
            cooldownSeconds = response.cooldownSeconds,
        )
    }

    override suspend fun verifyRegistration(
        registrationToken: String,
        code: String,
    ): AppResult<EmailAuthSession> = call {
        api.registerVerify(paths.registerVerify, RegisterVerifyRequest(registrationToken, code)).toSession()
    }

    override suspend fun signIn(email: String, password: String): AppResult<EmailAuthSession> =
        call { api.login(paths.login, LoginRequest(email, password)).toSession() }

    override suspend fun signInWithGoogle(idToken: String): AppResult<EmailAuthSession> =
        call { api.google(paths.google, GoogleRequest(idToken)).toSession() }

    override suspend fun requestPasswordReset(email: String): AppResult<Unit> =
        call { api.forgotPassword(paths.forgotPassword, ForgotPasswordRequest(email)) }

    override suspend fun resetPassword(
        resetToken: String,
        newPassword: String,
    ): AppResult<Unit> = call {
        api.resetPassword(paths.resetPassword, ResetPasswordRequest(normalizeResetCode(resetToken), newPassword))
    }

    override suspend fun refresh(refreshToken: String): AppResult<AuthTokens> =
        call { api.refresh(paths.refresh, RefreshRequest(refreshToken)).toTokens() }

    override suspend fun signOut(refreshToken: String): AppResult<Unit> =
        call { api.logout(paths.logout, RefreshRequest(refreshToken)) }

    private fun TokenResponseDto.toTokens() = AuthTokens(
        accessToken = requireNotNull(accessToken) { "A token response without an access token." },
        refreshToken = refreshToken.orEmpty(),
        accessValidForSeconds = expiresIn,
        refreshValidForSeconds = refreshExpiresIn,
        tokenType = tokenType ?: "Bearer",
    )

    private fun TokenResponseDto.toSession() = EmailAuthSession(
        tokens = toTokens(),
        profile = requireNotNull(user) { "A sign-in response without a profile." }.toDomain(),
    )

    private suspend fun <T> call(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (error: HttpException) {
        val apiError = ApiErrors.from(error)
        val kind = when (error.code()) {
            401, 403 -> ErrorKind.AUTH
            400, 409, 422 -> ErrorKind.VALIDATION
            429 -> ErrorKind.RATE_LIMIT
            in 500..599 -> ErrorKind.SERVER
            else -> ErrorKind.UNKNOWN
        }
        AppResult.Failure(
            kind = kind,
            // The server's Persian text, never the exception's. HttpException.message() is the
            // status line, which would read to a Persian reader as untranslated noise.
            message = apiError.message,
            cause = error,
            retryAfterSeconds = if (kind == ErrorKind.RATE_LIMIT) {
                apiError.retryAfterSeconds
                    ?: RetryAfter.parseSeconds(error.response()?.headers()?.get("Retry-After"))
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
        fun create(retrofit: Retrofit, platform: MarketPlatform): NetworkEmailAuthGateway =
            NetworkEmailAuthGateway(
                api = retrofit.create(MobileAuthApi::class.java),
                paths = AuthPaths.of(platform),
            )

        /**
         * The recovery code is emailed as `XXXX-XXXX` from an alphabet with the ambiguous letters
         * removed, and the server accepts it lower-cased and without the dash.
         *
         * Normalising here means someone who typed it by hand from a phone screen — the likely
         * case, since the emailed link may have been opened on another device — is not refused for
         * a dash or a capital. Only spacing and case are touched; the characters themselves are
         * passed through, so a genuinely wrong code is still the server's to reject.
         */
        internal fun normalizeResetCode(raw: String): String =
            raw.trim().replace(" ", "").uppercase()
    }
}
