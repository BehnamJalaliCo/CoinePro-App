package com.coinepro.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.coinepro.core.auth.SessionMemory
import com.coinepro.core.auth.SessionTokenStorage
import com.coinepro.core.marketdata.MarketDataCache
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.datastore.ActivePlatformSelector
import com.coinepro.core.marketdata.MarketDataSymbols
import com.coinepro.core.marketdata.MarketSnapshotGateway
import com.coinepro.core.signals.SignalHistoryCache
import com.coinepro.core.signals.SignalHistoryLoader
import com.coinepro.core.signals.SignalMembershipRequiredException
import com.coinepro.core.signals.SignalGateway
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

enum class BackgroundSyncOutcome {
    SUCCESS,
    NO_SESSION,
    RETRYABLE_FAILURE,
}

class BackgroundReadSyncEngine @Inject constructor(
    private val storage: SessionTokenStorage,
    private val memory: SessionMemory,
    private val marketGateways: Map<MarketPlatform, @JvmSuppressWildcards MarketSnapshotGateway>,
    private val marketCache: MarketDataCache,
    private val signalGateways: Map<MarketPlatform, @JvmSuppressWildcards SignalGateway>,
    private val signalCache: SignalHistoryCache,
    private val activePlatform: ActivePlatformSelector,
) {
    suspend fun sync(): BackgroundSyncOutcome {
        val existingToken = memory.token()
        val storedToken = if (existingToken.isNullOrBlank()) storage.readToken() else null
        val token = existingToken?.takeIf(String::isNotBlank) ?: storedToken?.takeIf(String::isNotBlank)
            ?: return BackgroundSyncOutcome.NO_SESSION
        val hydratedForWorker = existingToken.isNullOrBlank()
        if (hydratedForWorker) memory.setToken(token)

        var retryableFailure = false
        // The background refresh warms the cache for the platform the reader left the app on.
        // Fetching both would write a mixed snapshot into a cache the screen restores from before
        // the network answers, and the two backends hold different markets.
        val platform = activePlatform.current()
        try {
            try {
                val snapshot = marketGateways.getValue(platform).load(MarketDataSymbols.forPlatform(platform))
                if (snapshot.quotes.isNotEmpty()) {
                    marketCache.replace(snapshot.quotes, System.currentTimeMillis())
                }
            } catch (error: Exception) {
                if (isUnauthorized(error)) {
                    expireSession()
                    return BackgroundSyncOutcome.NO_SESSION
                }
                retryableFailure = true
            }

            try {
                val history = SignalHistoryLoader(signalGateways.getValue(platform)).load()
                signalCache.replace(history)
            } catch (_: SignalMembershipRequiredException) {
                signalCache.clear()
            } catch (error: Exception) {
                if (isUnauthorized(error)) {
                    expireSession()
                    return BackgroundSyncOutcome.NO_SESSION
                }
                retryableFailure = true
            }

            return if (retryableFailure) {
                BackgroundSyncOutcome.RETRYABLE_FAILURE
            } else {
                BackgroundSyncOutcome.SUCCESS
            }
        } finally {
            if (hydratedForWorker && memory.token() == token) {
                memory.setToken(null)
            }
        }
    }

    private suspend fun expireSession() {
        storage.clear()
        memory.setToken(null)
        memory.notifyUnauthorized()
    }

    private fun isUnauthorized(error: Exception): Boolean =
        error is HttpException && error.code() == 401
}

@HiltWorker
class BackgroundReadSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val engine: BackgroundReadSyncEngine,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = when (engine.sync()) {
        BackgroundSyncOutcome.SUCCESS,
        BackgroundSyncOutcome.NO_SESSION,
        -> Result.success()
        BackgroundSyncOutcome.RETRYABLE_FAILURE -> Result.retry()
    }
}

@Singleton
class BackgroundSyncScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val memory: SessionMemory,
) {
    private val workManager = WorkManager.getInstance(context)

    fun enableForAuthenticatedSession() {
        if (memory.token().isNullOrBlank()) return
        val periodicConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val periodic = PeriodicWorkRequestBuilder<BackgroundReadSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(periodicConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic,
        )
        requestImmediate()
    }

    fun requestImmediate() {
        if (memory.token().isNullOrBlank()) return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<BackgroundReadSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun disable() {
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    companion object {
        private const val IMMEDIATE_WORK_NAME = "coinepro-read-sync-now"
        private const val PERIODIC_WORK_NAME = "coinepro-read-sync-periodic"
    }
}
