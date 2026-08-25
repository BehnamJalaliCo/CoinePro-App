package com.coinepro.core.diagnostics

/**
 * Reduces a credential to something a reader can match against a server log without holding the
 * credential itself.
 *
 * The admin panel is five taps deep in a shipping app, on a phone that gets handed around, in a
 * screenshot that gets pasted into a support chat. A token printed there is a token published. The
 * last four characters are enough to answer "is this the same token the server saw"; the rest
 * answers nothing anyone needs and everything an attacker does.
 *
 * Short values are hidden completely rather than partially: revealing four of six characters is not
 * a redaction.
 */
fun maskSecret(value: String?): String = when {
    value.isNullOrBlank() -> ABSENT
    value.length < MIN_MASKABLE -> MASK
    else -> MASK + value.takeLast(VISIBLE_SUFFIX)
}

/**
 * Hides the host of a URL while keeping its shape.
 *
 * Base URLs are not secrets, but an internal hostname in a screenshot is a gift to anyone mapping
 * the estate, and the panel is a screenshot waiting to happen. The scheme and path survive because
 * those are what diagnose a misconfiguration — an `http://` where `https://` was expected, or a
 * missing `/api/v1` prefix, which is exactly the class of bug the whole panel exists to surface.
 */
fun maskHost(url: String?): String {
    if (url.isNullOrBlank()) return ABSENT
    val scheme = url.substringBefore("://", missingDelimiterValue = "")
    val rest = url.substringAfter("://", missingDelimiterValue = url)
    val host = rest.substringBefore('/')
    val path = rest.removePrefix(host)
    val maskedHost = when {
        host.length <= 6 -> MASK
        // Keep the registrable-looking tail so two environments can be told apart at a glance.
        else -> MASK + host.takeLast(VISIBLE_HOST_SUFFIX)
    }
    return if (scheme.isBlank()) maskedHost + path else "$scheme://$maskedHost$path"
}

/** Rendered where a value is genuinely absent, so it never reads as an empty string. */
const val ABSENT: String = "—"

private const val MASK = "…"
private const val MIN_MASKABLE = 8
private const val VISIBLE_SUFFIX = 4
private const val VISIBLE_HOST_SUFFIX = 10
