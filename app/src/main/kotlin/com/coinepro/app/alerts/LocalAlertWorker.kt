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
import com.coinepro.app.notifications.minuteOfDay
import com.coinepro.core.common.AppResult
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.datastore.LocalAlertStore
import com.coinepro.core.datastore.NotificationSettingsStore
import com.coinepro.core.guest.GuestGateway
import com.coinepro.core.guest.GuestQuote
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.core.notifications.NotificationCategory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Evaluates the device's own price alerts, and tells the reader when one is due.
 *
 * ### Why this runs against the public feed
 *
 * Because it must work with no account. The whole point of a local alert is that somebody who
 * installed the app ten minutes ago can ask to be told when Bitcoin reaches a number — and every
 * other app in this market answers that question with a sign-up form. The public price route needs
 * no token, so this works on a fresh install and keeps working after somebody signs in.
 *
 * ### What it asks for, and what that costs somebody else
 *
 * Only the symbols that have an alert on them, never the whole universe. A worker that pulled
 * several hundred quotes every fifteen minutes to check two alerts would be spending TradeYar's
 * bandwidth to save this app a `joinToString`.
 *
 * ### The honest limit
 *
 * Android schedules periodic work at its own convenience and never more often than every fifteen
 * minutes; on a phone in a battery-saving mode it may be considerably less often. A move that
 * happens and reverses inside that gap is missed. The alerts screen says so in as many words rather
 * than letting somebody find out during the move that mattered, and it says what fixes it: the
 * server's alerts, which watch continuously, once there is an account to attach them to.
 */
@HiltWorker
class LocalAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val alerts: LocalAlertStore,
    private val settings: NotificationSettingsStore,
    private val gateway: GuestGateway,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val stored = alerts.current().filter(LocalPriceAlert::active)
        if (stored.isEmpty()) return Result.success()

        val symbols = stored.map(LocalPriceAlert::symbol).distinct()
        val quotes = when (val result = gateway.prices(symbols)) {
            is AppResult.Success -> result.value.quotes.associateBy(GuestQuote::symbol)
            // Retried rather than failed: a price route that was unreachable once says nothing
            // about whether the alert should have fired, and giving up would silently stop the
            // whole schedule.
            is AppResult.Failure -> return Result.retry()
        }

        val now = System.currentTimeMillis()
        val current = settings.settings.first()
        val due = stored.filter { alert ->
            val quote = quotes[alert.symbol] ?: return@filter false
            LocalPriceAlert.due(alert, quote.price, quote.changePercent24h, now)
        }
        if (due.isEmpty()) return Result.success()

        // Stamped before anything is shown. If the process dies between the two, the reader loses
        // one notification; stamping afterwards would instead re-fire the same alert on every run
        // for as long as the condition held, which is the failure that empties an inbox.
        alerts.markFired(due, now)

        if (current.shouldShow(NotificationCategory.PRICE_ALERT, now, minuteOfDay())) {
            due.forEach { alert -> notify(alert, quotes[alert.symbol]?.price) }
        }
        return Result.success()
    }

    private fun notify(alert: LocalPriceAlert, price: Double?) {
        val context = applicationContext
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("coinepro://activity")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = price
            ?.let { context.getString(R.string.alert_fired_body, alert.symbol, MarketNumberFormatter.priceAuto(it)) }
            ?: context.getString(R.string.alert_fired_body_no_price, alert.symbol)
        val notification = NotificationCompat.Builder(
            context,
            NotificationChannels.channelId(NotificationCategory.PRICE_ALERT),
        )
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(context.getString(R.string.alert_fired_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(alert.id.hashCode(), notification)
    }
}

/**
 * Turns the alert schedule on and off with the alert list.
 *
 * Scheduled only while there is something to check, and cancelled the moment the last alert is
 * removed. A periodic worker that wakes every fifteen minutes to read an empty list is a battery
 * cost with no possible benefit, and it is the sort of thing that never shows up in testing and
 * shows up in a review.
 */
@Singleton
class LocalAlertScheduler @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    fun sync(hasActiveAlerts: Boolean) {
        if (!hasActiveAlerts) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<LocalAlertWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        // KEEP, not UPDATE: replacing the request resets the period, so a reader who adds an alert
        // every few minutes would push the next run away each time and never be told anything.
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WORK_NAME = "coinepro-local-alerts"
    }
}
