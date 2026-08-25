package com.coinepro.core.auth

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * The wire shapes of CoinePro-FX's `user/auth` surface, exactly as the server documented them from
 * live responses.
 *
 * Field names are Kotlin camelCase and reach the wire as snake_case through the Gson naming policy
 * the shared client is built with. Every response field is nullable with a default: these are
 * written against a running server, and a field that stops arriving must degrade rather than throw
 * inside a parser where the failure is indistinguishable from a network fault.
 */
internal interface MobileAuthApi {
    @GET("user/auth/methods")
    suspend fun methods(): AuthMethodsDto

    @POST("user/auth/register/start")
    suspend fun registerStart(@Body body: RegisterStartRequest): RegistrationStartDto

    @POST("user/auth/register/verify")
    suspend fun registerVerify(@Body body: RegisterVerifyRequest): TokenResponseDto

    @POST("user/auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponseDto

    @POST("user/auth/google")
    suspend fun google(@Body body: GoogleRequest): TokenResponseDto

    @POST("user/auth/password/forgot")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): ForgotPasswordDto

    @POST("user/auth/password/reset")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): ResetPasswordDto

    @POST("user/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenResponseDto

    @POST("user/auth/logout")
    suspend fun logout(@Body body: RefreshRequest): LogoutDto
}

/* ---------------------------------------------------------------- requests */

internal data class RegisterStartRequest(
    val email: String,
    val password: String,
    val fullName: String,
)

/** The server names the code `otp`; the app calls it a code everywhere a reader can see. */
internal data class RegisterVerifyRequest(val registrationToken: String, val otp: String)

internal data class LoginRequest(val email: String, val password: String)

internal data class GoogleRequest(val idToken: String)

internal data class ForgotPasswordRequest(val email: String)

internal data class ResetPasswordRequest(val resetToken: String, val newPassword: String)

internal data class RefreshRequest(val refreshToken: String)

/* --------------------------------------------------------------- responses */

internal data class AuthMethodsDto(
    val emailPassword: Boolean = false,
    val google: Boolean = false,
    val googleClientId: String? = null,
    val telegram: Boolean = false,
    val telegramBotUsername: String? = null,
    val push: Boolean = false,
    val chartVision: Boolean = false,
    val assistant: Boolean = false,
    val aiSignals: Boolean = false,
)

internal data class RegistrationStartDto(
    val registrationToken: String? = null,
    val otpSent: Boolean = false,
    val cooldownSeconds: Int? = null,
)

/**
 * `user` is absent from the refresh response by design, so it is nullable here and the caller
 * decides whether its absence matters.
 */
internal data class TokenResponseDto(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenType: String? = null,
    val expiresIn: Long? = null,
    val refreshExpiresIn: Long? = null,
    val user: UserProfile? = null,
)

internal data class ForgotPasswordDto(val sent: Boolean = false)

internal data class ResetPasswordDto(val reset: Boolean = false)

internal data class LogoutDto(val ok: Boolean = false)
