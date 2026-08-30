package com.coinepro.core.marketintel

import com.coinepro.core.common.parseWireInstant
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.time.Instant
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header

/**
 * The economic calendar CoinePro-FX already serves, read where it actually lives.
 *
 * ### Why there is a second source at all
 *
 * `user/mobile/market-intelligence` returns `calendar: []` and has since it was built — not because
 * the route is broken but because the module that writes its cache key is not deployed, which their
 * own route map says. Meanwhile `academy/bn/calendar` is marked **ready** in that same map and has
 * been serving the web product all along. So the data exists, on the same host, behind a token this
 * app already mints; the only thing missing was a client.
 *
 * Waiting for the first route to be filled in would have been the tidier answer, and it is the one
 * the last version took. It produced an empty screen for a fortnight.
 *
 * ### Why the reader is deliberately loose
 *
 * This is a route this app has never called and whose body nobody here has seen. Every field is
 * therefore accepted under the several spellings a calendar feed plausibly uses, the time is read
 * as a string **or** as an epoch, and an event with no id gets one derived from its own title and
 * moment rather than being dropped. A stricter reader would be more principled and would return an
 * empty list against a perfectly good response.
 *
 * That looseness stops at the edge of the data. Nothing is invented: an event with no title or no
 * usable time is still dropped, because a calendar row without a time is not a row. And
 * [CalendarSourceOutcome] reports what happened — how many arrived, how many were dropped, and the
 * keys of the first object — so the next diagnostic export answers the shape question that this
 * file is currently guessing at.
 */
interface AcademyCalendarSource {
    /** Events, or an empty list. Never throws: the primary snapshot has to stand on its own. */
    suspend fun events(): CalendarSourceOutcome
}

/**
 * What one attempt at the academy calendar produced.
 *
 * The counts are the point. "Empty" from this route can mean the server published nothing, or that
 * it published forty rows this reader could not understand, and those call for opposite next
 * moves — one is a message to their team and the other is a fix here. [sampleKeys] is what tells
 * them apart without another round trip: it is the field names of the first object in the response,
 * which is exactly the information this file was written without.
 */
data class CalendarSourceOutcome(
    val events: List<EconomicEvent> = emptyList(),
    /** How many objects the response contained, before any were dropped. */
    val received: Int = 0,
    /** The first object's keys, comma separated, or null where nothing arrived. */
    val sampleKeys: String? = null,
    /** Why nothing came back, where that was a failure rather than an empty publication. */
    val failure: String? = null,
) {
    val dropped: Int get() = (received - events.size).coerceAtLeast(0)

    companion object {
        val None = CalendarSourceOutcome()
    }
}

/** No second source. The default wherever one is not wired, and on TradeYar, which has no academy. */
object NoAcademyCalendarSource : AcademyCalendarSource {
    override suspend fun events(): CalendarSourceOutcome = CalendarSourceOutcome.None
}

/**
 * Reads `academy/bn/calendar` with the academy-scoped token.
 *
 * The token is passed in as a suspending function rather than a store, so `core:marketintel` does
 * not take a dependency on the module that mints it — the shell already holds both and is the right
 * place for them to meet.
 */
class NetworkAcademyCalendarSource(
    retrofit: Retrofit,
    private val academyToken: suspend () -> String,
) : AcademyCalendarSource {

    private val api = retrofit.create(AcademyCalendarApi::class.java)

    override suspend fun events(): CalendarSourceOutcome = runCatching {
        val body = api.calendar("Bearer " + academyToken())
        readCalendar(body)
    }.getOrElse { failure ->
        // Swallowed on purpose and reported rather than thrown. This is a second opinion on a
        // screen that already has an answer; a route that is switched off must leave the primary
        // snapshot exactly as it was.
        CalendarSourceOutcome(failure = failure::class.simpleName ?: "error")
    }
}

private interface AcademyCalendarApi {
    /**
     * Ready on CoinePro-FX per their own route map, and never called by this app until now.
     *
     * Academy scope, so the header is explicit — `NetworkFactory` leaves an explicit
     * `Authorization` alone precisely so a route with a different credential can set its own.
     */
    @GET("academy/bn/calendar")
    suspend fun calendar(@Header("Authorization") authorization: String): JsonElement
}

/**
 * The response, whatever shape it turns out to be.
 *
 * [JsonElement] rather than a typed body because there are two plausible envelopes and no way to
 * know which without asking: a bare array, or an object with the array under one of a handful of
 * names. Declaring one and being wrong is a parse failure that reads exactly like an empty
 * calendar, which is the failure mode this whole file exists to end.
 */
internal fun readCalendar(body: JsonElement?): CalendarSourceOutcome {
    val array = when {
        body == null || body.isJsonNull -> null
        body.isJsonArray -> body.asJsonArray
        body.isJsonObject -> ENVELOPE_KEYS.asSequence()
            .mapNotNull { key -> body.asJsonObject.get(key)?.takeIf(JsonElement::isJsonArray)?.asJsonArray }
            .firstOrNull()
        else -> null
    } ?: return CalendarSourceOutcome(failure = "unrecognised envelope")

    val objects = array.filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
    val events = objects.mapNotNull(::readEvent).sortedBy(EconomicEvent::scheduledAt)
    return CalendarSourceOutcome(
        events = events,
        received = objects.size,
        sampleKeys = objects.firstOrNull()?.keySet()?.joinToString(","),
    )
}

/** Every envelope name a calendar array plausibly arrives under. Order is preference, not guess. */
private val ENVELOPE_KEYS = listOf("calendar", "events", "items", "data", "results", "rows")

private fun readEvent(row: JsonObject): EconomicEvent? {
    val title = row.text("title", "event", "name", "title_fa", "titleFa", "event_title") ?: return null
    val scheduled = row.moment("scheduled_at", "scheduledAt", "date", "datetime", "time", "event_time", "release_time", "timestamp")
        ?: return null
    return EconomicEvent(
        // Derived where the feed does not carry one. An id here is a list key and a de-duplicator,
        // not an identity the server round-trips, so a title and a moment make a perfectly good
        // one — and dropping a real event for want of a synthetic field would be absurd.
        id = row.text("id", "event_id", "eventId", "slug") ?: (title + "@" + scheduled.epochSecond),
        title = title,
        country = row.text("country", "country_code", "countryCode", "region"),
        currency = row.text("currency", "ccy", "currency_code"),
        scheduledAt = scheduled,
        impact = parseImpact(row.text("impact", "importance", "severity", "impact_level")),
        actual = row.text("actual", "actual_value"),
        forecast = row.text("forecast", "consensus", "estimate", "forecast_value"),
        previous = row.text("previous", "prior", "previous_value"),
        relevance = parseRelevance(row.strings("relevance", "symbols", "markets", "tags")),
        // This route publishes what it has; nothing in it says whether a row is a cached copy. Not
        // marking it stale would be a claim about freshness that nothing here supports.
        isStale = row.get("stale")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true,
    )
}

/** The first of [names] present as a non-blank string or number. */
private fun JsonObject.text(vararg names: String): String? = names.asSequence()
    .mapNotNull { get(it) }
    .filter { it.isJsonPrimitive }
    .map { it.asString.trim() }
    .firstOrNull { it.isNotEmpty() }

/** The first of [names] present as an array of strings, or empty. */
private fun JsonObject.strings(vararg names: String): List<String> = names.asSequence()
    .mapNotNull { get(it) }
    .filter { it.isJsonArray }
    .map { array -> array.asJsonArray.filter(JsonElement::isJsonPrimitive).map { it.asString } }
    .firstOrNull { it.isNotEmpty() }
    .orEmpty()

/**
 * A moment, from a wire string **or** an epoch.
 *
 * The primary contract refuses epochs deliberately — a bare integer is ambiguous between seconds
 * and milliseconds and a feed that sends one has not said which. Here it is accepted, because this
 * route's shape is unknown and an epoch is a real possibility, and the ambiguity is resolved by
 * magnitude: anything past the year 3000 in seconds is milliseconds. Ten digits is seconds until
 * 2286, so the test does not become wrong within the life of this app.
 */
private fun JsonObject.moment(vararg names: String): Instant? {
    for (name in names) {
        val value = get(name)?.takeIf { it.isJsonPrimitive } ?: continue
        val raw = value.asString.trim()
        if (raw.isEmpty()) continue
        parseWireInstant(raw)?.let { return it }
        val number = raw.toLongOrNull() ?: continue
        if (number <= 0) continue
        return if (number > EPOCH_SECONDS_CEILING) {
            Instant.ofEpochMilli(number)
        } else {
            Instant.ofEpochSecond(number)
        }
    }
    return null
}

/** The first of January 3000, in seconds. Above it, a number has to be milliseconds. */
private const val EPOCH_SECONDS_CEILING = 32_503_680_000L
