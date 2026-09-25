package com.coinepro.app.alerts

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.coinepro.app.MainActivity
import com.coinepro.app.R
import com.coinepro.app.notifications.NotificationChannels
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.GrowthScan
import com.coinepro.core.common.AppResult
import com.coinepro.core.guest.GuestGateway
import com.coinepro.core.notifications.NotificationCategory
import com.coinepro.feature.screener.ScreenerStore
import com.coinepro.feature.screener.model.ScanWatch
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The screener's growth-scan alerts, in the background (5.17.0).
 *
 * Every half hour, each watch the reader made is run over its markets on its interval, with the
 * same [GrowthScan] the screen uses and the same rule ([ScanWatch.matches]); a market that matches
 * now and did not last time is announced, and the set is recorded for next time. Closed bars only —
 * a setup on the bar still forming is one that can un-happen before the reader looks.
 */
@HiltWorker
class ScanWatchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val store: ScreenerStore,
    private val gateway: GuestGateway,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val watches = store.watches.first()
        if (watches.isEmpty()) return Result.success()
        watches.forEach { watch ->
            val matched = scan(watch)
            val entrants = watch.entrants(matched)
            if (entrants.isNotEmpty()) announce(watch, entrants)
            store.recordMatches(watch.id, matched)
        }
        return Result.success()
    }

    private suspend fun scan(watch: ScanWatch): Set<String> = coroutineScope {
        val gate = Semaphore(CONCURRENCY)
        val nowSeconds = System.currentTimeMillis() / 1_000L
        watch.symbols.take(ScanWatch.MAX_SYMBOLS).map { symbol ->
            async {
                gate.withPermit {
                    val candles = when (val result = gateway.candles(symbol, watch.timeframe, CANDLE_LIMIT)) {
                        is AppResult.Success -> result.value.candles
                        is AppResult.Failure -> return@withPermit null
                    }
                    val closed = candles.filter { it.closed && it.timeSeconds < nowSeconds }
                    if (closed.size < GrowthScan.MIN_BARS) return@withPermit null
                    val series = CandleSeries(
                        closed.map { Candle(it.timeSeconds, it.open, it.high, it.low, it.close, it.volume) },
                    )
                    symbol.takeIf { GrowthScan.of(series)?.let(watch::matches) == true }
                }
            }
        }.awaitAll().filterNotNull().toSet()
    }

    private fun announce(watch: ScanWatch, entrants: List<String>) {
        val context = applicationContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val first = entrants.first()
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(AlertDeepLink.chart(first, watch.timeframe))
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val identity = (NOTIFICATION_PREFIX + watch.id).hashCode()
        val pending = PendingIntent.getActivity(
            context,
            identity,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Tickers are Latin and read as a list; the count is prose and follows the locale.
        val shown = entrants.take(SHOWN).joinToString("، ")
        val body = if (entrants.size > SHOWN) {
            context.getString(R.string.scan_watch_body_more, shown, entrants.size - SHOWN)
        } else {
            shown
        }
        val notification = NotificationCompat.Builder(context, NotificationChannels.channelId(NotificationCategory.PRICE_ALERT))
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(context.getString(R.string.scan_watch_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(identity, notification) }
    }

    companion object {
        private const val CANDLE_LIMIT = 260
        private const val CONCURRENCY = 4
        private const val SHOWN = 5
        private const val NOTIFICATION_PREFIX = "scan-watch:"
        private const val WORK_NAME = "coinepro-scan-watches"
        private const val PERIOD_MINUTES = 30L

        /** Runs the watches every half hour while there are any, and not at all once there are none. */
        fun sync(context: Context, hasWatches: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!hasWatches) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<ScanWatchWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
