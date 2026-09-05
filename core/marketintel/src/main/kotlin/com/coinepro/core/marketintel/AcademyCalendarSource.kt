package com.coinepro.core.marketintel

import com.coinepro.core.common.parseWireInstant
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.time.Instant
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header

/**
 * The economic calendar CoinePro-FX serves, read where it actually lives — now in two places.
 *
 * ### What was measured, against the real host, on 2026-08-30
 *
 * The last version of this file was written against a route nobody here had called. It has now been
 * called, and it is public — no token is needed at all:
 *
 * ```
 * GET https://coineprofx.com/api/academy/bn/calendar  → 200  {"items":[]}
 * GET https://coineprofx.com/api/academy/bn/news      → 200  {"items":[]}
 * GET https://coineprofx.com/api/academy/bn/ads       → 200  {"slots":{}}
 * ```
 *
 * Their own OpenAPI says `academy/bn/calendar` reads Redis key `bn:calendar`, written by a
 * `news-worker`. Every other `bn:*` key is empty too. So the worker is not running, and this
 * fallback was pointed at a key emptied by the same absent module that empties the primary —
 * `BACKEND_ROUTE_MAP.md` already recorded that «آن کلید روی سرور خالی است چون ماژول مربوطه اصلاً در
 * docker-compose نیست», and this is that same fact reaching a second route. **A second opinion from
 * the same broken source is not a second opinion.**
 *
 * ### The route that is actually alive
 *
 * `GET user/economic-calendar` is in the same OpenAPI, summarised «رویدادهای اقتصادیِ امروز (و تا
 * ۲۴ ساعتِ آینده) با درجه‌ی اهمیت», and answers `401` while signed out rather than an empty array —
 * which is what a route with a real handler behind it does. It takes the ordinary user token, so
 * `NetworkFactory`'s auth interceptor fills it in and nothing here has to hold a credential.
 *
 * It is asked **second**: the academy route is public, cheap and is the one that will start
 * answering the day the worker is deployed, and a fallback must not outlive the gap it was built
 * for. [CalendarSourceOutcome.route] records which of the two produced the answer, so the next
 * export says so rather than leaving it to be inferred.
 *
 * ### Why the reader is deliberately loose
 *
 * Neither body has been seen with rows in it. Every field is therefore accepted under the several
 * spellings a calendar feed plausibly uses, the time is read as a string **or** as an epoch, and an
 * event with no id gets one derived from its own title and moment rather than being dropped. A
 * stricter reader would be more principled and would return an empty list against a perfectly good
 * response.
 *
 * That looseness stops at the edge of the data. Nothing is invented: an event with no title or no
 * usable time is still dropped, because a calendar row without a time is not a row. And
 * [CalendarSourceOutcome] reports what happened — which route answered, how many arrived, how many
 * were dropped, and the keys of the first object.
 */
interface AcademyCalendarSource {
    /** Events, or an empty list. Never throws: the primary snapshot has to stand on its own. */
    suspend fun events(): CalendarSourceOutcome
}

/**
 * What one attempt at the fallback calendar produced.
 *
 * The counts are the point. "Empty" from either route can mean the server published nothing, or
 * that it published forty rows this reader could not understand, and those call for opposite next
 * moves — one is a message to their team and the other is a fix here. [sampleKeys] is what tells
 * them apart without another round trip: it is the field names of the first object in the response.
 */
data class CalendarSourceOutcome(
    val events: List<EconomicEvent> = emptyList(),
    /** How many objects the response contained, before any were dropped. */
    val received: Int = 0,
    /** The first object's keys, comma separated, or null where nothing arrived. */
    val sampleKeys: String? = null,
    /** Why nothing came back, where that was a failure rather than an empty publication. */
    val failure: String? = null,
    /** Which route produced this, so an export does not have to guess which one answered. */
    val route: String? = null,
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
 * Reads the two CoinePro-FX calendar routes, in order, and reports which one answered.
 *
 * The constructor is unchanged from the version that read one route, so the injector that builds it
 * does not have to change: [academyToken] is still the academy-scoped minter, still passed as a
 * suspending function so this module takes no dependency on the store that holds it.
 */
class NetworkAcademyCalendarSource(
    retrofit: Retrofit,
    private val academyToken: suspend () -> String,
) : AcademyCalendarSource {

    private val api = retrofit.create(CalendarFallbackApi::class.java)

    override suspend fun events(): CalendarSourceOutcome {
        // The route is public. The token is sent when the reader has one and simply omitted when
        // they do not: the minter needs a forex session, and a reader without one used to be
        // stopped here — by the minter throwing — before the public route was ever asked. That is
        // how a signed-out forex reader had no calendar on a server publishing fifty events.
        val bearer = runCatching { "Bearer " + academyToken() }.getOrNull()
        val academy = attempt(ACADEMY_ROUTE) { api.academyCalendar(bearer) }
        if (academy.events.isNotEmpty()) return academy
        val user = attempt(USER_ROUTE) { api.userCalendar() }
        // The academy answer is kept when the second route produced nothing either, because it is
        // the one whose emptiness is diagnostic: it is public, so an empty body from it is a
        // statement about the server rather than about this reader's token.
        return if (user.events.isNotEmpty() || academy.failure != null) user else academy
    }

    private suspend fun attempt(route: String, call: suspend () -> JsonElement): CalendarSourceOutcome =
        runCatching { readCalendar(call(), route) }.getOrElse { failure ->
            // Swallowed on purpose and reported rather than thrown. This is a second opinion on a
            // screen that already has an answer; a route that is switched off must leave the primary
            // snapshot exactly as it was.
            CalendarSourceOutcome(failure = failure::class.simpleName ?: "error", route = route)
        }

    private companion object {
        const val ACADEMY_ROUTE = "academy/bn/calendar"
        const val USER_ROUTE = "user/economic-calendar"
    }
}

private interface CalendarFallbackApi {
    /**
     * Public, despite the header. Their OpenAPI marks it «عمومی» and it answers 200 with no
     * credential at all — but the header is sent anyway because it costs nothing and because
     * `NetworkFactory` leaves an explicit `Authorization` alone precisely so a route with a
     * different credential can set its own, which this one may yet start requiring.
     */
    @GET("academy/bn/calendar")
    suspend fun academyCalendar(@Header("Authorization") authorization: String?): JsonElement

    /**
     * The VIP panel's own calendar — today and the next twenty-four hours, with importance.
     *
     * No explicit header: this takes the ordinary user bearer, which the auth interceptor adds to
     * every request that has not set one. Adding an academy token here would replace the credential
     * the route actually wants and turn a working call into a silent 403.
     */
    @GET("user/economic-calendar")
    suspend fun userCalendar(): JsonElement
}

/**
 * The response, whatever shape it turns out to be.
 *
 * [JsonElement] rather than a typed body because there are two plausible envelopes and no way to
 * know which without asking: a bare array, or an object with the array under one of a handful of
 * names. Declaring one and being wrong is a parse failure that reads exactly like an empty
 * calendar, which is the failure mode this whole file exists to end. `academy/bn/calendar` has
 * since been seen answering `{"items":[]}`, which is the second shape.
 */
internal fun readCalendar(body: JsonElement?, route: String? = null): CalendarSourceOutcome {
    val array = when {
        body == null || body.isJsonNull -> null
        body.isJsonArray -> body.asJsonArray
        body.isJsonObject -> ENVELOPE_KEYS.asSequence()
            .mapNotNull { key -> body.asJsonObject.get(key)?.takeIf(JsonElement::isJsonArray)?.asJsonArray }
            .firstOrNull()
        else -> null
    } ?: return CalendarSourceOutcome(failure = "unrecognised envelope", route = route)

    val objects = array.filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
    val events = objects.mapNotNull(::readFallbackEvent).sortedBy(EconomicEvent::scheduledAt)
    return CalendarSourceOutcome(
        events = events,
        received = objects.size,
        sampleKeys = objects.firstOrNull()?.keySet()?.joinToString(","),
        route = route,
    )
}

/** Every envelope name a calendar array plausibly arrives under. Order is preference, not guess. */
private val ENVELOPE_KEYS = listOf("calendar", "events", "items", "data", "results", "rows")

private fun readFallbackEvent(row: JsonObject): EconomicEvent? {
    val title = row.text("title", "event", "name", "title_fa", "titleFa", "event_title") ?: return null
    val scheduled = row.moment("scheduled_at", "scheduledAt", "date", "datetime", "time", "event_time", "release_time", "timestamp")
        ?: return null
    return EconomicEvent(
        // Derived where the feed does not carry one. An id here is a list key and a de-duplicator,
        // not an identity the server round-trips, so a title and a moment make a perfectly good
        // one — and dropping a real event for want of a synthetic field would be absurd.
        id = row.text("id", "event_id", "eventId", "slug") ?: (title + "@" + scheduled.epochSecond),
        title = CalendarPersian.title(title),
        country = CalendarPersian.country(row.text("country", "country_code", "countryCode", "region")),
        currency = row.text("currency", "ccy", "currency_code"),
        scheduledAt = scheduled,
        // `importance` is aliased here and deliberately not on the news feed: a calendar grades an
        // event on a three-point scale and a headline feed grades a story on ten, so the same digit
        // means different things and only one of them is safe to read as an impact.
        impact = parseImpact(row.text("impact", "importance", "severity", "impact_level")),
        actual = row.text("actual", "actual_value"),
        forecast = row.text("forecast", "consensus", "estimate", "forecast_value"),
        previous = row.text("previous", "prior", "previous_value"),
        relevance = parseRelevance(row.strings("relevance", "symbols", "markets", "tags")),
        // Neither route says whether a row is a cached copy. Not marking it stale would be a claim
        // about freshness that nothing here supports.
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
 * The ambiguity a bare integer carries is resolved by magnitude: anything past the year 3000 in
 * seconds is milliseconds. Ten digits is seconds until 2286, so the test does not become wrong
 * within the life of this app.
 */
private fun JsonObject.moment(vararg names: String): Instant? {
    for (name in names) {
        val value = get(name)?.takeIf { it.isJsonPrimitive } ?: continue
        val raw = value.asString.trim()
        if (raw.isEmpty()) continue
        parseWireInstant(raw)?.let { return it }
        val number = raw.toLongOrNull() ?: continue
        if (number <= 0) continue
        return if (number > FALLBACK_EPOCH_SECONDS_CEILING) {
            Instant.ofEpochMilli(number)
        } else {
            Instant.ofEpochSecond(number)
        }
    }
    return null
}

/** The first of January 3000, in seconds. Above it, a number has to be milliseconds. */
private const val FALLBACK_EPOCH_SECONDS_CEILING = 32_503_680_000L
