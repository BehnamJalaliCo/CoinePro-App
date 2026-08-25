package com.coinepro.app

internal sealed interface CoineProDeepLink {
    data class Signal(val signalId: Long) : CoineProDeepLink
    data object Activity : CoineProDeepLink

    /** The password-recovery App Link, carrying the token the reset step must present. */
    data class PasswordReset(val token: String) : CoineProDeepLink
}

/**
 * The one host whose recovery links this app claims.
 *
 * Named rather than pattern-matched because that is what the manifest declares and what Android
 * verifies against `assetlinks.json`. Accepting a token from any other host would mean acting on a
 * link nobody proved ownership of.
 */
private const val RESET_HOST = "user.tradeyar.trade-future.ir"

/**
 * A reset token is opaque to the app — only the server can say whether it is valid, unspent and
 * unexpired. The shape check here is not validation; it only keeps obvious rubbish out of a field
 * the reader would then have to clear by hand.
 */
private val RESET_TOKEN = Regex("^[A-Za-z0-9._~+/=-]{16,512}$")

internal fun positiveSignalId(raw: String?): Long? =
    raw?.toLongOrNull()?.takeIf { it > 0L }

internal fun parseCoineProDeepLink(
    scheme: String?,
    host: String?,
    pathSegments: List<String>,
    resetToken: String? = null,
): CoineProDeepLink? {
    // The recovery link is https rather than coinepro://, and only from the verified host: a token
    // is a credential, and a custom scheme any installed app may register is not somewhere to put
    // one.
    if (scheme == "https") {
        if (host != RESET_HOST || pathSegments.firstOrNull() != "reset") return null
        val token = resetToken?.takeIf { RESET_TOKEN.matches(it) } ?: return null
        return CoineProDeepLink.PasswordReset(token)
    }
    if (scheme != "coinepro") return null
    return when (host) {
        "signal" -> positiveSignalId(pathSegments.singleOrNull())?.let(CoineProDeepLink::Signal)
        "activity" -> if (pathSegments.isEmpty()) CoineProDeepLink.Activity else null
        else -> null
    }
}
