package com.coinepro.app.pulse

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

/**
 * Runs [MarketPulseEngine] every fifteen minutes — WorkManager's own floor, batched with the rest
 * of the phone's work and asleep in Doze — whenever there is a network (5.27.0).
 */
@HiltWorker
class MarketPulseWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val engine: MarketPulseEngine,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        // Success either way: a missed quarter-hour is made up by the next, and a retry backoff
        // would only bunch the runs together.
        runCatching { engine.run() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "market-pulse"
        private const val PERIOD_MINUTES = 15L

        /** Booked once; `KEEP`, so every launch does not restart the clock. */
        fun schedule(context: Context) {
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<MarketPulseWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build(),
                )
            }
        }
    }
}
