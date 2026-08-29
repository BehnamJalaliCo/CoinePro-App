package com.coinepro.app.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.coinepro.core.common.AppResult
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.datastore.WidgetMarket
import com.coinepro.core.datastore.WidgetSnapshot
import com.coinepro.core.datastore.WidgetSnapshotStore
import com.coinepro.core.diagnostics.AppLog
import com.coinepro.core.diagnostics.LogTag
import com.coinepro.core.guest.GuestGateway
import com.coinepro.core.guest.GuestQuote
import com.coinepro.core.marketdata.MarketDataSymbols
import com.coinepro.core.symbols.SymbolClassifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Fetches the prices the widget draws, and writes them where the widget can read them.
 *
 * ### Why the *guest* gateway
 *
 * Because it works signed out. A widget that only shows prices to a member is a widget most people
 * who install this app never see working — and the public price route is the same feed the guest
 * home already reads, so this adds no new surface to either backend.
 *
 * It also means the widget keeps working after a sign-out, which is the correct behaviour: the
 * price of gold is not the reader's private data and there is nothing to hide when they leave.
 *
 * ### Why WorkManager rather than `updatePeriodMillis`
 *
 * The manifest's own period is clamped to thirty minutes by Android and wakes the device to honour
 * it. WorkManager batches with other work, respects Doze, waits for a network instead of failing
 * on one that is not there, and can be cancelled the moment the last widget is removed — so a
 * reader with no widget pays nothing at all for this feature.
 */
@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: WidgetRefreshEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val refreshed = engine.refresh()
        // Redrawn whether or not the fetch succeeded: a failure still changes what is on screen,
        // because the freshness line now says «آفلاین». A widget that goes quiet on a failure is
        // one that shows an old price as if it were current.
        MarketsWidget.refreshAll(applicationContext)
        // `retry` rather than `failure`, so WorkManager's own backoff handles a network that came
        // back a minute later. Never `failure`: that stops the chain and the widget would then
        // wait for the next period with nothing having been tried.
        return if (refreshed) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC = "widget-refresh-periodic"
        private const val IMMEDIATE = "widget-refresh-now"

        /**
         * How often the widget refetches while at least one is placed.
         *
         * Fifteen minutes is WorkManager's own floor for periodic work, and it is also about right:
         * a home-screen glance is not a trading screen, and a price fifteen minutes old is a fair
         * answer to "roughly where is gold". Anything more frequent would be spending somebody's
         * battery to make a number on a home screen look busier.
         */
        private const val PERIOD_MINUTES = 15L

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                // `KEEP`, so re-placing a widget does not reset the schedule and start the clock
                // again — a reader adding three widgets should not cause three immediate fetches
                // an hour apart for ever.
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .build(),
            )
        }

        /** A fetch now — the refresh glyph, a resize, or the first widget being placed. */
        fun requestNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE,
                // `KEEP`, so a reader tapping refresh five times queues one fetch rather than five.
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .build(),
            )
        }

        /** The last widget was removed. Nothing should still be waking the device for it. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(PERIODIC)
                cancelUniqueWork(IMMEDIATE)
            }
        }
    }
}

/**
 * The refresh itself, apart from the worker that schedules it.
 *
 * Separated so the fetch-and-format is an ordinary injectable class rather than something reachable
 * only through WorkManager — which is what lets the app call it directly when the reader stars a
 * market, instead of waiting a quarter of an hour to see it appear.
 */
class WidgetRefreshEngine @Inject constructor(
    private val guest: GuestGateway,
    private val watchlist: WatchlistStore,
    private val store: WidgetSnapshotStore,
    private val log: AppLog,
) {

    /** True when fresh prices were written. False leaves what is stored and marks it stale. */
    suspend fun refresh(): Boolean {
        val symbols = symbols()
        if (symbols.isEmpty()) {
            store.write(WidgetSnapshot(capturedAtEpochMillis = System.currentTimeMillis()))
            return true
        }
        return when (val result = guest.prices(symbols)) {
            is AppResult.Success -> {
                val quotes = result.value.quotes.associateBy { it.symbol.uppercase() }
                // Ordered by the watchlist rather than by the response: the reader put their most
                // important market first and the server has no idea which that is.
                val markets = symbols.mapNotNull { symbol -> quotes[symbol]?.toWidgetMarket() }
                store.write(
                    WidgetSnapshot(
                        markets = markets,
                        capturedAtEpochMillis = System.currentTimeMillis(),
                        // The server's own verdict, carried through rather than recomputed — the
                        // same flag the guest home reads.
                        stale = result.value.stale,
                    ),
                )
                log.debug(LogTag.STATE, "widget refreshed", mapOf("markets" to markets.size.toString()))
                true
            }
            is AppResult.Failure -> {
                // The prices are kept and labelled. Throwing them away would put a blank rectangle
                // on somebody's home screen because one request timed out; an hour-old price that
                // *says* it is an hour old is still useful.
                store.markStale()
                log.warn(LogTag.STATE, "widget refresh failed")
                false
            }
        }
    }

    /**
     * Which markets the widget shows.
     *
     * The reader's watchlist, and where that is empty, the crypto majors — which is the same
     * fallback the guest home uses. A widget that says "star something first" on the day it is
     * placed is a widget that gets removed the same day.
     */
    private suspend fun symbols(): List<String> {
        // The default list by name, not the active one. A widget is glanced at from the home
        // screen with the app closed; having it follow whichever list happened to be open last
        // would make its contents change for a reason the reader cannot see from where they are
        // standing.
        val starred = runCatching { watchlist.symbols(Watchlist.DEFAULT_LIST_ID).first() }
            .getOrDefault(emptyList())
        val chosen = starred.ifEmpty { MarketDataSymbols.crypto }
        return chosen.map { it.uppercase() }.distinct().take(WidgetSnapshotStore.MAX_MARKETS)
    }
}

/**
 * A quote as the widget stores it, formatted here rather than at render time.
 *
 * The formatting rules are not trivial — Latin digits, decimals that follow the instrument's
 * magnitude, a real minus sign — and they live in [MarketNumberFormatter], which a `RemoteViews`
 * builder cannot reach. Formatting at write time means the widget and the app spell the same
 * number the same way, which they would not if this were re-implemented in the provider.
 */
private fun GuestQuote.toWidgetMarket(): WidgetMarket {
    val meta = SymbolClassifier.classify(symbol)
    val change = changePercent24h
    return WidgetMarket(
        symbol = meta.pretty,
        name = meta.description,
        priceText = MarketNumberFormatter.priceAuto(price),
        // The sign is carried explicitly and the minus is U+2212, not a hyphen — the app's rule
        // everywhere a signed figure appears. An empty string where the feed sent nothing, because
        // a zero would draw a flat day the server never claimed.
        changeText = change?.let { percent ->
            val magnitude = MarketNumberFormatter.price(kotlin.math.abs(percent), 2)
            val sign = if (percent < 0) "−" else "+"
            "$sign$magnitude٪"
        }.orEmpty(),
        direction = when {
            change == null -> 0
            change > 0 -> 1
            change < 0 -> -1
            else -> 0
        },
    )
}
