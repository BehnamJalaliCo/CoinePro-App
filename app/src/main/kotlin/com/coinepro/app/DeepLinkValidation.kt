package com.coinepro.app

internal sealed interface CoineProDeepLink {
    data class Signal(val signalId: Long) : CoineProDeepLink
    data object Activity : CoineProDeepLink
}

internal fun positiveSignalId(raw: String?): Long? =
    raw?.toLongOrNull()?.takeIf { it > 0L }

internal fun parseCoineProDeepLink(
    scheme: String?,
    host: String?,
    pathSegments: List<String>,
): CoineProDeepLink? {
    if (scheme != "coinepro") return null
    return when (host) {
        "signal" -> positiveSignalId(pathSegments.singleOrNull())?.let(CoineProDeepLink::Signal)
        "activity" -> if (pathSegments.isEmpty()) CoineProDeepLink.Activity else null
        else -> null
    }
}
