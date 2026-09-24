package com.coinepro.feature.calendar

import com.coinepro.core.marketintel.EconomicEvent
import com.coinepro.core.marketintel.MarketImpact
import java.time.Instant

/**
 * The old terminal's calendar filters (5.15.0), beside the impact one this screen already had: a
 * currency — `USD`, `EUR` — and a word to find in a release's title or its country. Pure, so the
 * rules are tested without a screen.
 */
internal object CalendarFilter {

    fun apply(events: List<EconomicEvent>, impact: MarketImpact?, currency: String?, query: String): List<EconomicEvent> {
        val words = query.trim().lowercase()
        return events.filter { event ->
            (impact == null || event.impact == impact) &&
                (currency == null || event.currency.equals(currency, ignoreCase = true)) &&
                (words.isEmpty() || event.title.lowercase().contains(words) || event.country.orEmpty().lowercase().contains(words))
        }
    }

    /** The currencies present, most releases first — the order a reader scans for. */
    fun currencies(events: List<EconomicEvent>): List<String> =
        events.mapNotNull { it.currency?.trim()?.uppercase()?.takeIf(String::isNotEmpty) }
            .groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

    /** The first release at or after [now], or null when the week is behind. */
    fun next(events: List<EconomicEvent>, now: Instant): EconomicEvent? =
        events.filter { !it.scheduledAt.isBefore(now) }.minByOrNull { it.scheduledAt }
}
