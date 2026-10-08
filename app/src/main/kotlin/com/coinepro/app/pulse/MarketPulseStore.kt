package com.coinepro.app.pulse

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** A high-importance release the calendar announced, kept until it has been and gone. */
data class PulseEvent(
    val id: String,
    val title: String,
    val currency: String?,
    val atMillis: Long,
    val forecast: String?,
    val previous: String?,
)

/**
 * What the market notifications remember between runs (5.27.0): each market's recent prices, when
 * each was last announced, today's count, the day of the last summary, and the calendar.
 */
class MarketPulseStore(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun samples(symbol: String): List<PulseSample> = runCatching {
        preferences.getString(SAMPLES + symbol, null).orEmpty()
            .split(';')
            .filter(String::isNotBlank)
            .mapNotNull { pair ->
                val at = pair.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
                val price = pair.substringAfter(':').toDoubleOrNull() ?: return@mapNotNull null
                PulseSample(at, price)
            }
    }.getOrDefault(emptyList())

    fun setSamples(symbol: String, samples: List<PulseSample>) {
        edit { putString(SAMPLES + symbol, samples.joinToString(";") { "${it.atMillis}:${it.price}" }) }
    }

    fun lastFired(symbol: String): Long = runCatching { preferences.getLong(FIRED + symbol, 0L) }.getOrDefault(0L)

    fun setFired(symbol: String, atMillis: Long) = edit { putLong(FIRED + symbol, atMillis) }

    /** Today's count; a new day starts at zero. */
    fun sentToday(today: Long): Int = runCatching {
        if (preferences.getLong(COUNT_DAY, -1L) == today) preferences.getInt(COUNT, 0) else 0
    }.getOrDefault(0)

    fun countOne(today: Long) {
        val next = sentToday(today) + 1
        edit { putLong(COUNT_DAY, today).putInt(COUNT, next) }
    }

    fun lastDigestDay(): Long? = runCatching { preferences.getLong(DIGEST_DAY, -1L).takeIf { it >= 0 } }.getOrNull()

    fun setDigestDay(day: Long) = edit { putLong(DIGEST_DAY, day) }

    fun calendarFetchedAt(): Long = runCatching { preferences.getLong(CALENDAR_AT, 0L) }.getOrDefault(0L)

    fun events(): List<PulseEvent> = runCatching {
        val array = JSONArray(preferences.getString(EVENTS, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            PulseEvent(
                id = item.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null,
                title = item.optString("t"),
                currency = item.optString("c").takeIf(String::isNotBlank),
                atMillis = item.optLong("at"),
                forecast = item.optString("f").takeIf(String::isNotBlank),
                previous = item.optString("p").takeIf(String::isNotBlank),
            )
        }
    }.getOrDefault(emptyList())

    fun setEvents(events: List<PulseEvent>, fetchedAt: Long) {
        val array = JSONArray()
        events.forEach { event ->
            array.put(
                JSONObject()
                    .put("id", event.id)
                    .put("t", event.title)
                    .put("c", event.currency.orEmpty())
                    .put("at", event.atMillis)
                    .put("f", event.forecast.orEmpty())
                    .put("p", event.previous.orEmpty()),
            )
        }
        edit { putString(EVENTS, array.toString()).putLong(CALENDAR_AT, fetchedAt) }
    }

    fun announced(): Set<String> = runCatching {
        preferences.getStringSet(ANNOUNCED, emptySet()).orEmpty().toSet()
    }.getOrDefault(emptySet())

    fun setAnnounced(ids: Set<String>) = edit { putStringSet(ANNOUNCED, ids.toList().takeLast(MAX_ANNOUNCED).toSet()) }

    private inline fun edit(block: SharedPreferences.Editor.() -> SharedPreferences.Editor) {
        runCatching { preferences.edit().block().apply() }
    }

    private companion object {
        const val FILE = "market_pulse"
        const val SAMPLES = "samples_"
        const val FIRED = "fired_"
        const val COUNT = "count"
        const val COUNT_DAY = "count_day"
        const val DIGEST_DAY = "digest_day"
        const val CALENDAR_AT = "calendar_at"
        const val EVENTS = "events"
        const val ANNOUNCED = "announced"
        const val MAX_ANNOUNCED = 60
    }
}
