package com.coinepro.app.brief

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.coinepro.core.datastore.NotificationSettingsStore
import com.coinepro.core.notifications.MorningBriefSchedule
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Wakes once, delivers the brief, and books tomorrow's.
 *
 * ### Why one-time work chained to itself, and not `PeriodicWorkRequest`
 *
 * A periodic request takes an interval, not a time of day. Android may run it anywhere inside the
 * period, so «every 24 hours starting at 07:00» drifts — each run lands a little later than the
 * last, and after a fortnight the morning brief is arriving at lunchtime with nothing in the code
 * to say why. Worse, an interval cannot follow a reader across a time zone or across the hour the
 * clocks change, because it never asks what time it is.
 *
 * A one-time request with an initial delay computed from the device's own clock asks that question
 * every single day, and [MorningBriefSchedule.delayMillis] is where the answer is worked out and
 * tested. The cost is that the chain has to be re-armed after every run, which is the first thing
 * [doWork] does — **before** the delivery, so a brief that throws still books tomorrow's.
 */
@HiltWorker
class MorningBriefWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val engine: MorningBriefEngine,
    private val scheduler: MorningBriefScheduler,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        // Tomorrow is booked first. A delivery that throws, a store that cannot be read, a fetch
        // that hangs until the worker is stopped — none of them should cost the reader every brief
        // from here on, and every one of them would if the re-arm lived at the end.
        scheduler.sync()
        val result = engine.run(
            nowEpochMillis = System.currentTimeMillis(),
            todayLocalDay = LocalDate.now().toEpochDay(),
        )
        return when (result) {
            // Retry, not failure: an unreachable price route says nothing about whether there was
            // a brief to give, and nothing was written, so the reader's day is still unspent.
            BriefPassResult.Unavailable -> Result.retry()
            BriefPassResult.Idle, is BriefPassResult.Delivered -> Result.success()
        }
    }
}

/**
 * Books the next brief, or cancels the chain when the reader switches it off.
 *
 * Called from three places and idempotent in all of them: at start-up, after every run, and
 * whenever the switch or the hour changes on the settings screen. `REPLACE` rather than `KEEP`
 * precisely because a changed hour must move the pending run — `KEEP` would leave the reader's new
 * time unused until the old one had fired, which looks exactly like a setting that does nothing.
 */
@Singleton
class MorningBriefScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: NotificationSettingsStore,
) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun sync() {
        val current = settings.settings.first()
        if (!current.briefScheduled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = LocalTime.now()
        val delay = MorningBriefSchedule.delayMillis(
            nowMinuteOfDay = now.hour * 60 + now.minute,
            targetMinuteOfDay = current.briefMinuteOfDay,
        )
        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MorningBriefWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        // The brief is a fetch and is worth nothing without one. Waiting for a
                        // network is what makes a phone that was on a plane at seven get its brief
                        // when it lands rather than skipping the day.
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build(),
        )
    }

    private companion object {
        const val WORK_NAME = "coinepro-morning-brief"
    }
}
