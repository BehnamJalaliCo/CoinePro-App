package com.coinepro.app.brief

import com.coinepro.core.chart.BriefMarket
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.MorningBrief
import com.coinepro.core.chart.RasadBrief
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.common.AppResult
import com.coinepro.core.datastore.NotificationSettingsStore
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.guest.GuestCandle
import com.coinepro.core.guest.GuestGateway
import com.coinepro.core.notifications.MorningBriefSchedule
import kotlinx.coroutines.flow.first

/** What one pass of the brief decided, in the worker's own vocabulary. */
sealed interface BriefPassResult {

    /** The brief was composed and handed to the deliverer. */
    data class Delivered(val brief: MorningBrief) : BriefPassResult

    /** Nothing to do, and nothing was wrong: switched off, no markets, already sent today. */
    data object Idle : BriefPassResult

    /**
     * Nothing was decided and nothing was written — the price route could not be reached.
     *
     * Distinct from [Idle] because the worker's answer differs: this one is worth retrying and
     * being idle is not.
     */
    data object Unavailable : BriefPassResult
}

/**
 * The morning brief's one pass: read the reader's list, fetch it, compose, deliver.
 *
 * ### Everything about *what is said* is somewhere else
 *
 * `RasadBrief` composes the sentences and `MorningBriefSchedule` owns the calendar, both without
 * Android and both tested at their boundaries. What is left here is the part that can only be
 * done against real stores: which list, which symbols, and the two guards that decide whether a
 * notification happens at all.
 *
 * ### The two guards, and why each exists
 *
 * **Once a day.** WorkManager fires when it can rather than when it was asked to, and a phone
 * coming out of Doze can have two runs queued. Two identical briefs four minutes apart is the
 * fastest way to teach somebody to switch a feature off, so the last delivered local day is
 * written down and checked.
 *
 * **Only the reader's first list.** Not every symbol they have ever starred across every list: the
 * default list is the one the app treats as theirs everywhere else, and a brief about forty markets
 * is not a brief. The cap is [MAX_MARKETS] and it is a cap on the *fetch* as well, so a long list
 * costs one bounded request rather than a long one.
 *
 * ### The guest route, for the reason every other background reader uses it
 *
 * It needs no account. A brief that only worked for members would be invisible to most of the
 * people who install this app, and the public price route is the same feed the guest home already
 * reads — so this adds no new surface to either backend.
 */
class MorningBriefEngine(
    private val watchlists: WatchlistStore,
    private val gateway: GuestGateway,
    private val settings: NotificationSettingsStore,
    /**
     * Which language to write the brief in.
     *
     * A supplier rather than a stored value, and read at delivery rather than at construction: this
     * object outlives a reader switching language, and a brief in the language they used yesterday
     * is exactly the kind of thing nobody would notice for a week. `AppLocale.language` is the
     * process-wide answer kept for code with no screen to ask — the widget worker formats its
     * counts from the same place.
     */
    private val languageOf: () -> AppLanguage,
    private val delivery: BriefDeliveryStore,
    private val deliverer: MorningBriefDeliverer,
) {

    suspend fun run(nowEpochMillis: Long, todayLocalDay: Long): BriefPassResult {
        val current = settings.settings.first()
        if (!current.briefScheduled) return BriefPassResult.Idle
        if (
            MorningBriefSchedule.alreadyDeliveredToday(
                lastDeliveredEpochMillis = delivery.lastDeliveredAt(),
                lastDeliveredLocalDay = delivery.lastDeliveredDay(),
                todayLocalDay = todayLocalDay,
            )
        ) {
            return BriefPassResult.Idle
        }
        val symbols = symbolsOf(watchlists.lists().first())
        if (symbols.isEmpty()) return BriefPassResult.Idle
        val quotes = when (val result = gateway.prices(symbols)) {
            is AppResult.Success -> result.value.quotes.associateBy { it.symbol.uppercase() }
            is AppResult.Failure -> return BriefPassResult.Unavailable
        }
        val english = languageOf() == AppLanguage.ENGLISH
        // In the reader's own order, because that is what decides a tie in `RasadBrief.moverOf`.
        val markets = symbols.map { symbol ->
            BriefMarket(symbol = symbol, changePercent = quotes[symbol]?.changePercent24h)
        }
        val mover = RasadBrief.moverOf(markets)?.symbol
        val series = mover?.let { seriesOf(it) }
        val brief = RasadBrief.of(markets, moverSeries = series, english = english)
            // Null means every change came back unreadable — the route answered and said nothing.
            // Not a failure to retry: the same answer is what the next pass would get.
            ?: return BriefPassResult.Idle
        deliverer.deliver(brief, sparkline = series)
        // Written after the delivery, so a crash between the two leaves the reader with a brief to
        // come rather than a day with none. A duplicate is an annoyance; a missing brief is the
        // feature not working.
        delivery.record(nowEpochMillis, todayLocalDay)
        return BriefPassResult.Delivered(brief)
    }

    /**
     * The markets the brief is about.
     *
     * The reader's **first** list, which is the default one unless they have reordered — and if
     * they have, the first is the one they put first. Deduplicated and upper-cased because the
     * quote map is keyed that way and a symbol stored in two spellings would otherwise be counted
     * twice.
     */
    private fun symbolsOf(lists: List<Watchlist>): List<String> = lists
        .firstOrNull()
        ?.symbols
        ?.map { it.trim().uppercase() }
        ?.filter(String::isNotEmpty)
        ?.distinct()
        ?.take(MAX_MARKETS)
        .orEmpty()

    /**
     * The mover's bars, or null.
     *
     * Null is a shorter brief rather than no brief: `RasadBrief.of` drops the coach's sentence and
     * keeps the count and the move, which is most of what the reader came for. Only the **closed**
     * bars go in, for the reason `GuestAlertMarketSource` gives — a forming bar repaints, and a
     * sentence about a candle that is still changing is a sentence that was not true by breakfast.
     */
    private suspend fun seriesOf(symbol: String): CandleSeries? {
        val loaded = when (val result = gateway.candles(symbol, CANDLE_TIMEFRAME, CANDLE_LIMIT)) {
            is AppResult.Success -> result.value.candles.filter(GuestCandle::closed)
            is AppResult.Failure -> return null
        }
        if (loaded.isEmpty()) return null
        return CandleSeries(
            loaded.map { bar ->
                Candle(t = bar.timeSeconds, o = bar.open, h = bar.high, l = bar.low, c = bar.close, v = bar.volume)
            },
        )
    }

    private companion object {
        /**
         * How many of the reader's markets the brief counts.
         *
         * Twelve. Enough that a serious list is covered whole, small enough that the headline is
         * still a sentence somebody reads on a lock screen rather than a tally. A longer list is
         * truncated rather than refused — the count then describes what was measured, which is
         * what it says.
         */
        const val MAX_MARKETS = 12

        /** Hourly bars, and enough of them for the coach's own minimum plus a margin. */
        const val CANDLE_TIMEFRAME = "1h"
        const val CANDLE_LIMIT = 200
    }
}

/** Puts one composed brief in front of the reader. Android's half, so the engine has no view of it. */
interface MorningBriefDeliverer {
    suspend fun deliver(brief: MorningBrief, sparkline: CandleSeries?)
}

/** When the last brief went out, so a second wake-up on the same day does not send another. */
interface BriefDeliveryStore {
    suspend fun lastDeliveredAt(): Long?
    suspend fun lastDeliveredDay(): Long?
    suspend fun record(atEpochMillis: Long, localDay: Long)
}
