package com.coinepro.app.alerts

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates the device's own alerts on Android's schedule, and tells the reader when one is due.
 *
 * ### What is actually in this class, and what is not
 *
 * Almost nothing. Every decision — which alerts are due, what each trigger is compared against,
 * which watchlist member fired, what is written to the audit log, what happens when a delivery
 * fails — lives in [AlertEvaluator], which has no Android in it and is unit-tested at the
 * boundaries that matter. What is left here is the two things only a worker can do: start the pass,
 * and translate its result into WorkManager's vocabulary.
 *
 * ### Why the failure path is `retry` and not `success`
 *
 * A price route that was unreachable once says nothing about whether an alert should have fired.
 * Reporting success would consume the wake-up and wait another fifteen minutes; reporting failure
 * would stop the schedule. Retry is the only answer that leaves the reader's alert exactly as armed
 * as it was, which is the promise this whole feature rests on. [AlertPassResult.Unavailable] is the
 * evaluator saying that nothing was decided *and nothing was written*.
 *
 * ### Why this runs against the public feed
 *
 * Because it must work with no account. The whole point of a local alert is that somebody who
 * installed the app ten minutes ago can ask to be told when Bitcoin reaches a number — and every
 * other app in this market answers that question with a sign-up form. The public routes need no
 * token, so this works on a fresh install and keeps working after somebody signs in. Only the
 * symbols that have an alert on them are ever asked for, and candles only for the alerts whose
 * condition genuinely needs bars; see [GuestAlertMarketSource].
 *
 * ### The honest limit
 *
 * Android schedules periodic work at its own convenience and never more often than every fifteen
 * minutes; on a phone in a battery-saving mode it may be considerably less often. A move that
 * happens and reverses inside that gap is missed. An alert set to fire only on a bar close is not
 * affected by that in the way it looks — it is judged on the bar that has closed rather than on
 * whenever this happens to wake, so it reports the right bar late rather than the wrong bar on
 * time. The alerts screen says all of this in as many words rather than letting somebody find out
 * during the move that mattered, and it says what fixes it: the server's alerts, which watch
 * continuously, once there is an account to attach them to.
 */
@HiltWorker
class LocalAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    alerts: StoredAlertRepository,
    membership: WatchlistAlertMembership,
    fireStates: AlertFireStateStore,
    market: GuestAlertMarketSource,
    audit: PreferencesAlertAuditLog,
    deliverer: AndroidAlertDeliverer,
) : CoroutineWorker(context, parameters) {

    private val evaluator = AlertEvaluator(
        alerts = alerts,
        membership = membership,
        fireStates = fireStates,
        market = market,
        audit = audit,
        deliverer = deliverer,
    )

    override suspend fun doWork(): Result =
        when (evaluator.evaluate(System.currentTimeMillis())) {
            is AlertPassResult.Unavailable -> Result.retry()
            AlertPassResult.Idle, is AlertPassResult.Completed -> Result.success()
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
