package com.coinepro.core.common

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Reads a timestamp the way the two backends actually write one.
 *
 * `Instant.parse` accepts only the `Z` form. Both servers are FastAPI over Postgres and reach for
 * Python's `datetime.isoformat()`, which writes the offset out in full — `2026-09-24T12:00:00+00:00`
 * — and that is rejected. The failure is silent by design in every caller here: an unreadable
 * timestamp drops the row it belongs to. So a subscription simply never shows its expiry date, and a
 * news feed arrives permanently empty, with nothing anywhere to say why.
 *
 * Three forms are read, in the order they are likely:
 *  - `2026-09-24T12:00:00Z` — what the app asked both servers for.
 *  - `2026-09-24T12:00:00+03:30` — what `datetime.isoformat()` produces on an aware value.
 *  - `2026-09-24T12:00:00` — a naive value, read as UTC. Servers that store UTC and drop the zone
 *    are common enough to be worth reading, and the alternative is discarding the row outright.
 *    Guessing the device's own zone instead would move the timestamp by hours on most phones.
 *
 * Anything else is null. This is deliberately not a general date parser: a format nobody sends is
 * one nobody has tested, and accepting it would mean showing a date the server never meant.
 */
fun parseWireInstant(raw: String?): Instant? {
    val text = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
    runCatching { return Instant.parse(text) }
    runCatching { return OffsetDateTime.parse(text).toInstant() }
    runCatching { return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC) }
    return null
}
