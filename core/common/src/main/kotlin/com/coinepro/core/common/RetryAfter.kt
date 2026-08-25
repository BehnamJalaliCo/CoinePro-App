package com.coinepro.core.common

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Reads the `Retry-After` header a rate limiter sends with a 429.
 *
 * The app asks the server when to try again rather than assuming a window of its own, for the same
 * reason it never invents any other server state: the limiter's clock is the only one that decides,
 * and a client-side guess is either too eager — spending the user's next attempt on a request that
 * was always going to be refused — or so cautious it locks someone out longer than the server did.
 *
 * RFC 9110 allows two forms and real servers send both, so both are read. The date form is resolved
 * against a caller-supplied [now] rather than the device clock, because a phone whose clock is
 * wrong would otherwise produce a wait of hours or a wait of nothing; the caller passes the
 * server's own `Date` header when it has one.
 *
 * A value this cannot make sense of returns null, which the UI renders as "try again later" without
 * a countdown. That is the honest outcome — a countdown is a promise about a specific second.
 */
object RetryAfter {
    /** Beyond this, a countdown stops being useful and the value is more likely a bad header. */
    private const val MAX_SECONDS = 24 * 60 * 60

    private val HTTP_DATE: DateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US)

    /**
     * @param header the raw `Retry-After` value, or null when the response carried none.
     * @param now the instant the response was received, for resolving the date form.
     * @return whole seconds to wait, clamped to at least one, or null if there is no usable value.
     */
    fun parseSeconds(header: String?, now: Instant = Instant.now()): Int? {
        val value = header?.trim().orEmpty()
        if (value.isEmpty()) return null

        value.toLongOrNull()?.let { delta ->
            // Zero is a legitimate "retry immediately", but it is reported as one second: the
            // caller's next move is to show a countdown, and a countdown of zero reads as a bug.
            return if (delta < 0) null else delta.coerceIn(1, MAX_SECONDS.toLong()).toInt()
        }

        val target = try {
            ZonedDateTime.parse(value, HTTP_DATE).toInstant()
        } catch (_: DateTimeParseException) {
            return null
        }

        // A date already in the past means the window closed while the response was in flight.
        val seconds = (target.toEpochMilli() - now.toEpochMilli()) / 1000.0
        if (seconds <= 0) return 1
        return seconds.roundToInt().coerceAtMost(MAX_SECONDS)
    }
}
