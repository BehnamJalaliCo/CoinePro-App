package com.coinepro.core.marketintel

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * The economic calendar, read straight from the week's public file.
 *
 * ### Why the app reads this itself
 *
 * `academy/bn/calendar` on CoinePro-FX exists, is public, and its own documentation says what it
 * serves: «تقویمِ اقتصادیِ هفتگی … دادهٔ واقعیِ faireconomy/ForexFactory». It answers
 * `{"items":[]}` and has done since it was written, because the `news-worker` that fills its Redis
 * key has never been deployed. That is measured, not inferred — a plain `curl` returns the empty
 * envelope with a 200 today.
 *
 * So the server was going to read *this exact file* and hand it on. The app now reads it directly
 * when the server sends nothing, which removes a hop and a worker from between the reader and a
 * calendar they have asked for five times. The moment the worker exists, the server's own answer is
 * non-empty and wins — see [PublicMarketIntel]. Nothing here has to be undone for that to happen.
 *
 * ### The shape
 *
 * One JSON array, one object per event:
 *
 *     {"title":"Non-Farm Employment Change","country":"USD",
 *      "date":"2026-09-04T08:30:00-04:00","impact":"High",
 *      "forecast":"75K","previous":"73K"}
 *
 * `date` carries a real offset, so it converts to an instant exactly and the app's own time zone
 * handling does the rest. There is no `actual` field: this file is published ahead of the week and
 * the released figure is not in it. `actual` is therefore null on every row, which is honest — an
 * empty string rendered as a dash would look like "released, and it was nothing".
 */
internal object PublicCalendarFeed {

    /**
     * This week's file.
     *
     * The week, not the day: a calendar that can only answer "today" cannot tell a reader on Friday
     * what Monday holds, and the whole use of an economic calendar is knowing what is coming.
     */
    const val URL = "https://nfs.faireconomy.media/ff_calendar_thisweek.json"

    /**
     * Every event in [body] that has a title and a time, newest last.
     *
     * Sorted ascending, unlike news: a calendar is read forwards. The two lists in this module sort
     * opposite ways for that reason and it is not an inconsistency — a story is interesting because
     * it just happened, an event because it has not happened yet.
     */
    fun parse(body: String?, now: Instant): List<EconomicEvent> {
        if (body.isNullOrBlank()) return emptyList()
        val root = runCatching { JsonParser.parseString(body) }.getOrNull() ?: return emptyList()
        val rows = when {
            root.isJsonArray -> root.asJsonArray.toList()
            // Not the shape this host serves, but the same data is published under an `events` key
            // by more than one mirror, and accepting both costs one branch.
            root.isJsonObject -> root.asJsonObject.getAsJsonArray("events")?.toList().orEmpty()
            else -> emptyList()
        }
        return rows.mapNotNull { row -> event(row, now) }.sortedBy(EconomicEvent::scheduledAt)
    }

    private fun event(row: JsonElement, now: Instant): EconomicEvent? {
        val obj = row.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return null
        val title = obj.string("title")?.takeIf(String::isNotBlank) ?: return null
        val scheduledAt = obj.string("date")?.let(::instantOrNull) ?: return null
        val currency = obj.string("country")?.trim()?.takeIf(String::isNotBlank)
        return EconomicEvent(
            // The feed carries no id, and the natural key is what makes a row *that* row: one
            // indicator, for one currency, at one moment. Two events sharing all three would be the
            // same event listed twice.
            id = "ff:$currency:$scheduledAt:$title",
            title = CalendarPersian.title(title),
            country = CalendarPersian.country(currency),
            currency = currency?.takeIf { !it.equals("All", ignoreCase = true) },
            scheduledAt = scheduledAt,
            impact = impact(obj.string("impact")),
            // Not in this file. See the class comment — null, not "".
            actual = null,
            forecast = obj.string("forecast")?.trim()?.takeIf(String::isNotBlank),
            previous = obj.string("previous")?.trim()?.takeIf(String::isNotBlank),
            // A macro release moves the dollar, and the dollar is the other half of every metal
            // quote. Both metals, therefore, and never crypto: this file has nothing in it about a
            // listing or an unlock, and claiming otherwise would put a rate decision in front of a
            // reader looking at a coin chart as though it were about that coin.
            relevance = setOf(MarketRelevance.GOLD, MarketRelevance.SILVER),
            isStale = scheduledAt.isBefore(now.minusSeconds(STALE_AFTER_SECONDS)),
        )
    }

    /**
     * The feed's four levels against the app's three.
     *
     * `Holiday` is the interesting one. It is not a low-impact release — it is a day the market is
     * shut, which is a fact a reader planning a week needs more than most of the Low rows around
     * it. It maps to [MarketImpact.MEDIUM] so it is not buried by an importance filter.
     */
    private fun impact(raw: String?): MarketImpact = when (raw?.trim()?.lowercase()) {
        "high" -> MarketImpact.HIGH
        "medium" -> MarketImpact.MEDIUM
        "holiday" -> MarketImpact.MEDIUM
        "low" -> MarketImpact.LOW
        else -> MarketImpact.UNKNOWN
    }

    private fun instantOrNull(raw: String): Instant? = try {
        OffsetDateTime.parse(raw.trim()).toInstant()
    } catch (error: DateTimeParseException) {
        null
    }

    /** Two hours past its moment, a release has been priced in and is history. */
    private const val STALE_AFTER_SECONDS = 2 * 60 * 60L
}

/**
 * One string field, or null.
 *
 * Local to this file rather than shared with the two `strings(...)` helpers elsewhere in the module:
 * those take a list of candidate spellings because the backends have not settled theirs. A published
 * file has exactly one spelling per field, and pretending otherwise would invite a future reader to
 * add a second one that can never fire.
 */
private fun JsonObject.string(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive }?.asString
