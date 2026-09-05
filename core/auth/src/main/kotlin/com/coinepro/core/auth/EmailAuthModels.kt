package com.coinepro.core.auth

import com.coinepro.core.model.MarketPlatform

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
    /**
     * Which backend issued this.
     *
     * Carried rather than assumed, because one sign-in screen now serves two user tables: a new
     * account is made on TradeYar, and an account made before that decision lives on CoinePro-FX.
     * A token is only ever valid against the server that minted it, so the caller has to know which
     * session to put it in — writing a CoinePro-FX token into TradeYar's storage produces a signed
     * -in app whose every request comes back 401.
     */
    val platform: MarketPlatform,
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
    /** Supplied only when [google] is on; it is the audience Google Sign-In must be given. */
    val googleClientId: String? = null,
    val telegram: Boolean = false,
    val telegramBotUsername: String? = null,
    /**
     * Whether this deployment can actually deliver a push at all.
     *
     * False means the app must not ask for the notification permission. Asking spends the one
     * prompt Android grants for a capability that would deliver nothing, and a reader who grants it
     * and then never hears anything has been told something untrue by the request itself.
     */
    val push: Boolean = false,
    /** Whether chart-image analysis exists here. False hides the screen rather than failing in it. */
    val chartVision: Boolean = false,
    /**
     * Whether a conversational assistant exists, or null where the server did not say.
     *
     * Separate from [aiSignals] because the two are different products that happen to share a
     * model: this is a thread, that is a one-shot analysis. TradeYar has the second and not the
     * first, and collapsing them would hide a working feature or offer a missing one.
     *
     * Null rather than false when absent, and the difference matters. Both servers now report
     * these two — CoinePro-FX from the model access it really has rather than a fixed flag — but
     * an older deployment of either may not, and it was the platform with both products that used
     * to say nothing about them. So the rule for every other flag here — a capability the server
     * never mentioned is one the app must not assume it has — stays inverted for exactly these
     * two: silence is an out-of-date server's default rather than a refusal.
     */
    val assistant: Boolean? = null,
    /**
     * Whether one-shot AI analysis is available *right now*, or null where the server did not say.
     *
     * Where reported it tracks the model bridge's actual reachability rather than a static setting,
     * so a sleeping bridge turns the screen off instead of filling it with failed jobs.
     */
    val aiSignals: Boolean? = null,
    /**
     * Whether this deployment can delete an account when asked.
     *
     * Off by default, like every other flag here, and for the usual reason turned sharp: a delete
     * button that does nothing is the worst button in the app. Where the server has not said yes,
     * the screen shows the out-of-app route instead — which works today, and is what Google Play
     * requires be published anyway.
     */
    val accountDeletion: Boolean = false,
    /**
     * Whether this deployment mints a guest token — a short-lived credential that opens the market
     * to somebody who has not signed in, and nothing else.
     *
     * Off by default. A guest sign-in offered against a server that has none is a spinner ending in
     * an error on the first screen a stranger ever sees.
     */
    val guestAuth: Boolean = false,
    /**
     * Where this deployment's full web terminal lives, or null where it has none.
     *
     * The address is the server's to state, not the build's. The one that used to be compiled in
     * pointed at a host that had been decommissioned — the domain stopped resolving — and no
     * release could have known. A server always knows where it is serving from.
     */
    val terminalUrl: String? = null,
) {
    val any: Boolean get() = emailPassword || googleUsable || telegram

    /**
     * Whether the Google button can do anything if it is pressed.
     *
     * [google] alone is the *server's* claim that the method exists; the audience is what the app
     * needs to ask Google for a token, and Credential Manager refuses outright — with copy about a
     * developer console — when it is given something that is not an OAuth client id. A deployment
     * that reports the method on and sends no id, or sends a placeholder somebody left in a config
     * file, produced a button that could only ever fail. The class note above already states the
     * rule this closes: an action that is certain to fail is worse than a missing one.
     *
     * The test is the shape Google guarantees and nothing more — a non-blank id ending in
     * `.apps.googleusercontent.com`. It cannot tell whether the client behind that id still exists
     * (CoinePro-FX's had been **deleted** in the console while the server went on advertising it,
     * which no client-side check can see) and it deliberately does not try: a probe against
     * Google's authorize endpoint on the sign-in screen would be a network round trip in front of
     * the reader for a fault only the console can fix.
     */
    val googleUsable: Boolean
        get() = google && googleClientId?.trim()?.endsWith(GOOGLE_CLIENT_SUFFIX) == true
}

/** What every Google OAuth client id ends with. See [AuthMethods.googleUsable]. */
private const val GOOGLE_CLIENT_SUFFIX = ".apps.googleusercontent.com"

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
