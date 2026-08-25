package com.coinepro.core.auth

/**
 * The email-first identity flow, in the app's own vocabulary.
 *
 * These are domain types, deliberately not the wire shapes. The server's JSON key names arrive from
 * the backend team and change independently of anything on screen, so the translation lives in one
 * place — the gateway implementation — and nothing above it has to be rewritten when a key is named
 * differently than expected.
 */

/**
 * What the server handed back after a successful sign-in.
 *
 * [accessValidForSeconds] and [refreshValidForSeconds] are lifetimes, not deadlines, and that is
 * the whole point: they are counted from the moment the response arrived rather than read off the
 * device clock. Phone clocks are wrong often enough — and by enough — that an absolute expiry would
 * make the app refresh far too early on one device and far too late on another. Null means the
 * server did not say, and the app then waits for a 401 instead of inventing a schedule.
 */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessValidForSeconds: Long? = null,
    val refreshValidForSeconds: Long? = null,
    val tokenType: String = "bearer",
) {
    init {
        require(accessToken.isNotBlank()) { "An access token cannot be blank." }
    }
}

/** A sign-in that produced both a session and the profile behind it. */
data class EmailAuthSession(
    val tokens: AuthTokens,
    val profile: UserProfile,
)

/**
 * Registration is two steps, and this is what the app holds between them.
 *
 * [cooldownSeconds] is the server's own answer to "when may another code be sent", carried rather
 * than guessed for the same reason as everything else here: the server is what will refuse.
 */
data class RegistrationChallenge(
    val registrationToken: String,
    val cooldownSeconds: Int?,
)

/**
 * Which ways in this deployment actually offers.
 *
 * CoinePro-FX reports Google sign-in as unavailable until its credentials are configured, and the
 * honest client response is to not draw the button at all. An action that is certain to fail is
 * worse than a missing one: the reader assumes they did something wrong.
 *
 * Everything defaults to off. A capability the server never mentioned is one the app must not
 * assume it has.
 */
data class AuthMethods(
    val emailPassword: Boolean = false,
    val google: Boolean = false,
    val telegram: Boolean = false,
) {
    val any: Boolean get() = emailPassword || google || telegram
}

/**
 * The single reason a credential step failed, as the app needs to act on it.
 *
 * [message] is the server's own wording and is shown exactly as written. The app has no better
 * information about why a specific server refused a specific request, and a locally invented
 * explanation would be a guess presented in the voice of the service.
 */
data class AuthFailure(
    val reason: AuthFailureReason,
    val message: String?,
    val retryAfterSeconds: Int? = null,
)

enum class AuthFailureReason {
    /** Wrong credentials, an expired code, or a reset token that has already been spent. */
    REJECTED,

    /** The request itself was malformed — a password below the minimum, a mistyped address. */
    INVALID,

    /** Too many attempts. [AuthFailure.retryAfterSeconds] carries the server's wait, when it sent one. */
    RATE_LIMITED,

    /** The request never reached a verdict: no connectivity, a timeout, a server fault. */
    UNREACHABLE,
}
