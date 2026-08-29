package com.coinepro.app

internal sealed interface CoineProDeepLink {
    data class Signal(val signalId: Long) : CoineProDeepLink
    data object Activity : CoineProDeepLink

    /**
     * One market's chart, from a row of the home-screen widget.
     *
     * The ticker is validated rather than trusted. This scheme is unverified — any installed app
     * may register it — so what arrives here is an arbitrary string from an untrusted sender, and
     * it is about to become a navigation argument and a request path. Restricting it to the shape
     * a ticker actually has is what stops that.
     *
     * [timeframe] is the bar the link asks for — `?tf=H1`, which `AlertDeepLink` writes so a fired
     * alert opens on the interval it was actually decided on. Optional, and shape-checked on the
     * same reading as the ticker: null when it is absent *and* when it is not a timeframe, because
     * a chart on the wrong bar is worse than a chart on the stored one.
     */
    data class Market(val symbol: String, val timeframe: String? = null) : CoineProDeepLink

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
/**
 * The hosts whose recovery links this app claims — one per backend.
 *
 * Named rather than pattern-matched because that is what the manifest declares and what Android
 * verifies against each host's `assetlinks.json`. Accepting a token from any other host would mean
 * acting on a link nobody proved ownership of.
 *
 * The two spell the path differently and that is theirs to decide, not ours to normalise: TradeYar
 * serves `/reset`, CoinePro-FX serves `/reset-password`. A single pattern covering both would also
 * cover a third nobody has vetted.
 */
private val RESET_HOSTS = mapOf(
    "user.tradeyar.trade-future.ir" to "reset",
    "coineprofx.com" to "reset-password",
)

/**
 * A reset token is opaque to the app — only the server can say whether it is valid, unspent and
 * unexpired. The shape check here is not validation; it only keeps obvious rubbish out of a field
 * the reader would then have to clear by hand.
 */
private val RESET_TOKEN = Regex("^[A-Za-z0-9._~+/=-]{16,512}$")

internal fun positiveSignalId(raw: String?): Long? =
    raw?.toLongOrNull()?.takeIf { it > 0L }

/**
 * A ticker as the app writes it, or null.
 *
 * Letters, digits and the slash a pair is written with — `BTC/USDT`, `XAU/USD`, `US500`. Upper-cased
 * because every symbol in this app is, and bounded because an unbounded string from an unverified
 * scheme becomes a navigation route and then a URL path.
 *
 * Rejecting rather than sanitising: a ticker that needed cleaning up was not a ticker, and quietly
 * opening a *different* market than the link named would be worse than opening none.
 */
internal fun tickerOrNull(raw: String?): String? {
    val candidate = raw?.trim()?.uppercase() ?: return null
    return candidate.takeIf { TICKER.matches(it) }
}

private val TICKER = Regex("^[A-Z0-9]{2,12}(/[A-Z0-9]{2,12})?$")

/**
 * A timeframe as this app spells it — `M15`, `H4`, `D1` — or null.
 *
 * Not resolved against `ChartInterval` here, which would put a `core:marketdata` type in the one
 * file whose job is to refuse rubbish before it becomes a route. The shape check is enough for
 * that job and the controller resolves the value properly; an unresolvable one leaves the chart on
 * its stored interval, which is the same outcome as an absent query.
 */
internal fun timeframeOrNull(raw: String?): String? {
    val candidate = raw?.trim()?.uppercase() ?: return null
    return candidate.takeIf { TIMEFRAME.matches(it) }
}

// Loose on purpose, and bounded: `M15`, the alternate `15M` that `Timeframe.of` also accepts, and
// a custom interval's bare minute count `205` are all timeframes this app writes. The narrow job
// here is to keep an unbounded string from an unverified scheme out of a navigation argument.
private val TIMEFRAME = Regex("^[A-Z]{0,2}[0-9]{1,4}[A-Z]{0,2}$")

internal fun parseCoineProDeepLink(
    scheme: String?,
    host: String?,
    pathSegments: List<String>,
    resetToken: String? = null,
    timeframe: String? = null,
): CoineProDeepLink? {
    // The recovery link is https rather than coinepro://, and only from the verified host: a token
    // is a credential, and a custom scheme any installed app may register is not somewhere to put
    // one.
    if (scheme == "https") {
        val expected = RESET_HOSTS[host?.lowercase()] ?: return null
        if (pathSegments.firstOrNull() != expected) return null
        val token = resetToken?.takeIf { RESET_TOKEN.matches(it) } ?: return null
        return CoineProDeepLink.PasswordReset(token)
    }
    if (scheme != "coinepro") return null
    return when (host) {
        "signal" -> positiveSignalId(pathSegments.singleOrNull())?.let(CoineProDeepLink::Signal)
        "activity" -> if (pathSegments.isEmpty()) CoineProDeepLink.Activity else null
        "market" -> tickerOrNull(pathSegments.singleOrNull())
            ?.let { CoineProDeepLink.Market(it, timeframeOrNull(timeframe)) }
        else -> null
    }
}
