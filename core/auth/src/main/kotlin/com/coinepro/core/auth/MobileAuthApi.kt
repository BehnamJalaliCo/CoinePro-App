package com.coinepro.core.auth

import com.coinepro.core.model.MarketPlatform
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * The wire shapes of the email-first auth surface, exactly as the two servers documented them from
 * live responses.
 *
 * Every call takes its path rather than declaring one, because the two backends serve the same
 * eight routes under different prefixes — `user/auth` on CoinePro-FX, `api/mobile/v1/auth` on
 * TradeYar. A single hard-coded prefix is what made the whole crypto surface unreachable once
 * before: a path built for one server reaches nothing on the other, and arrives as an ordinary HTTP
 * error worded like a status line, so it reads as the server being down rather than as the app
 * asking for something that was never there. [AuthPaths] is the only place either prefix is written.
 *
 * Field names are Kotlin camelCase and reach the wire as snake_case through the Gson naming policy
 * the shared client is built with. Every response field is nullable with a default: these are
 * written against a running server, and a field that stops arriving must degrade rather than throw
 * inside a parser where the failure is indistinguishable from a network fault.
 */
internal interface MobileAuthApi {
    @GET
    suspend fun methods(@Url path: String): AuthMethodsDto

    @POST
    suspend fun registerStart(@Url path: String, @Body body: RegisterStartRequest): RegistrationStartDto

    @POST
    suspend fun registerVerify(@Url path: String, @Body body: RegisterVerifyRequest): TokenResponseDto

    @POST
    suspend fun login(@Url path: String, @Body body: LoginRequest): TokenResponseDto

    @POST
    suspend fun google(@Url path: String, @Body body: GoogleRequest): TokenResponseDto

    @POST
    suspend fun forgotPassword(@Url path: String, @Body body: ForgotPasswordRequest): ForgotPasswordDto

    @POST
    suspend fun resetPassword(@Url path: String, @Body body: ResetPasswordRequest): ResetPasswordDto

    @POST
    suspend fun refresh(@Url path: String, @Body body: RefreshRequest): TokenResponseDto

    @POST
    suspend fun logout(@Url path: String, @Body body: RefreshRequest): LogoutDto
}

/**
 * Where one platform's auth routes live.
 *
 * The prefix is the whole difference between the two deployments; the eight names after it are
 * identical, which is why they are built rather than listed twice.
 */
internal class AuthPaths(private val prefix: String) {
    val methods = "$prefix/methods"
    val registerStart = "$prefix/register/start"
    val registerVerify = "$prefix/register/verify"
    val login = "$prefix/login"
    val google = "$prefix/google"
    val forgotPassword = "$prefix/password/forgot"
    val resetPassword = "$prefix/password/reset"
    val refresh = "$prefix/refresh"
    val logout = "$prefix/logout"

    companion object {
        fun of(platform: MarketPlatform): AuthPaths = when (platform) {
            MarketPlatform.COINEPRO_FX -> AuthPaths("user/auth")
            MarketPlatform.TRADEYAR -> AuthPaths("api/mobile/v1/auth")
        }
    }
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
    val user: AuthUserDto? = null,
)

/**
 * The signed-in account, in whichever of the two spellings arrived.
 *
 * The shared client converts Kotlin names to snake_case, which is what CoinePro-FX sends. TradeYar
 * sends the same object in camelCase and has confirmed that is deliberate. With a single naming
 * policy the mismatched half simply arrives null — no error, no log line, just a profile with no
 * name, no email and every entitlement false, which is indistinguishable from a free account. So
 * each field names both spellings explicitly rather than relying on the policy.
 *
 * `@SerializedName` overrides the policy entirely, which is why the snake_case form is written out
 * as the primary name rather than left implied.
 */
internal data class AuthUserDto(
    @SerializedName(value = "telegram_id", alternate = ["telegramId"])
    val telegramId: Long = 0,
    @SerializedName(value = "name", alternate = ["full_name", "fullName", "display_name", "displayName"])
    val name: String? = null,
    val username: String? = null,
    val phone: String? = null,
    val email: String? = null,
    @SerializedName(value = "email_verified", alternate = ["emailVerified"])
    val emailVerified: Boolean = false,
    @SerializedName(value = "kyc_status", alternate = ["kycStatus"])
    val kycStatus: String? = null,
    @SerializedName(value = "is_vip", alternate = ["isVip", "vip"])
    val isVip: Boolean = false,
    @SerializedName(value = "is_paid", alternate = ["isPaid", "paid"])
    val isPaid: Boolean = false,
    @SerializedName(value = "panel_approved", alternate = ["panelApproved"])
    val panelApproved: Boolean = false,
    @SerializedName(value = "panel_allowed", alternate = ["panelAllowed"])
    val panelAllowed: Boolean = false,
    @SerializedName(value = "panel_state", alternate = ["panelState"])
    val panelState: String? = null,
    val plan: String? = null,
    @SerializedName(value = "plan_expires_at", alternate = ["planExpiresAt"])
    val planExpiresAt: String? = null,
    @SerializedName(value = "disclaimer_accepted", alternate = ["disclaimerAccepted"])
    val disclaimerAccepted: Boolean = false,
)

/**
 * Defaults are applied here rather than in the DTO so that an absent field and an explicitly empty
 * one land on the same value. A profile with no usable name keeps an empty one: the screens that
 * greet the reader already handle that, and inventing a placeholder would put a word in the
 * server's mouth that the reader would then see as their own name.
 */
internal fun AuthUserDto.toDomain(): UserProfile = UserProfile(
    telegramId = telegramId,
    name = name?.trim().orEmpty(),
    username = username?.trim()?.takeIf(String::isNotEmpty),
    phone = phone?.trim()?.takeIf(String::isNotEmpty),
    email = email?.trim()?.takeIf(String::isNotEmpty),
    emailVerified = emailVerified,
    kycStatus = kycStatus?.trim()?.takeIf(String::isNotEmpty) ?: "none",
    isVip = isVip,
    isPaid = isPaid,
    panelApproved = panelApproved,
    panelAllowed = panelAllowed,
    panelState = panelState?.trim()?.takeIf(String::isNotEmpty) ?: "buy",
    plan = plan?.trim()?.takeIf(String::isNotEmpty) ?: "free",
    planExpiresAt = planExpiresAt?.trim()?.takeIf(String::isNotEmpty),
    disclaimerAccepted = disclaimerAccepted,
)

internal data class ForgotPasswordDto(val sent: Boolean = false)

internal data class ResetPasswordDto(val reset: Boolean = false)

internal data class LogoutDto(val ok: Boolean = false)
