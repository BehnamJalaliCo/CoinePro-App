@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.work

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * WorkManager, for a page: the phone's own workers run on the page's clock while it is open — a
 * periodic request every period, a one-time request after its delay, a retry after a back-off. A
 * page cannot be woken when it is closed, so neither can its work; the phone's alerts screen
 * already tells the reader how often a check can run, and in a browser the answer is "while the tab
 * is open". Workers are built by the factory the generated `WebGraph` registers.
 */

class WorkerParameters internal constructor(val id: java.util.UUID, val runAttemptCount: Int)

abstract class ListenableWorker(val applicationContext: Context, val workerParams: WorkerParameters) {
    sealed class Result {
        class Success internal constructor() : Result()
        class Failure internal constructor() : Result()
        class Retry internal constructor() : Result()
        companion object {
            fun success(): Result = Success()
            fun success(data: Data): Result = Success()
            fun failure(): Result = Failure()
            fun retry(): Result = Retry()
        }
    }
    val runAttemptCount: Int get() = workerParams.runAttemptCount
}

abstract class CoroutineWorker(context: Context, params: WorkerParameters) : ListenableWorker(context, params) {
    abstract suspend fun doWork(): Result
}

class Data private constructor() {
    class Builder { fun putString(key: String, value: String?): Builder = this; fun build(): Data = Data() }
    companion object { val EMPTY = Data() }
}

enum class NetworkType { NOT_REQUIRED, CONNECTED, UNMETERED, NOT_ROAMING, METERED }
enum class BackoffPolicy { EXPONENTIAL, LINEAR }
enum class ExistingWorkPolicy { REPLACE, KEEP, APPEND, APPEND_OR_REPLACE }
enum class ExistingPeriodicWorkPolicy { REPLACE, KEEP, UPDATE, CANCEL_AND_REENQUEUE }
enum class OutOfQuotaPolicy { RUN_AS_NON_EXPEDITED_WORK_REQUEST, DROP_WORK_REQUEST }

class Constraints private constructor() {
    class Builder {
        fun setRequiredNetworkType(type: NetworkType): Builder = this
        fun setRequiresBatteryNotLow(value: Boolean): Builder = this
        fun setRequiresCharging(value: Boolean): Builder = this
        fun build(): Constraints = Constraints()
    }
    companion object { val NONE = Constraints() }
}

abstract class WorkRequest internal constructor(
    internal val worker: KClass<out ListenableWorker>,
    internal val initialDelayMillis: Long,
    internal val periodMillis: Long,
    internal val backoffMillis: Long,
)

class OneTimeWorkRequest internal constructor(worker: KClass<out ListenableWorker>, delay: Long, backoff: Long) :
    WorkRequest(worker, delay, 0L, backoff) {
    companion object {
        fun from(worker: KClass<out ListenableWorker>): OneTimeWorkRequest = OneTimeWorkRequest(worker, 0, 30_000)
    }
    class Builder(private val worker: KClass<out ListenableWorker>) {
        private var delay = 0L
        private var backoff = 30_000L
        fun setInitialDelay(duration: Long, unit: TimeUnit): Builder = apply { delay = unit.toMillis(duration) }
        fun setConstraints(constraints: Constraints): Builder = this
        fun setBackoffCriteria(policy: BackoffPolicy, duration: Long, unit: TimeUnit): Builder = apply { backoff = unit.toMillis(duration) }
        fun setExpedited(policy: OutOfQuotaPolicy): Builder = this
        fun addTag(tag: String): Builder = this
        fun setInputData(data: Data): Builder = this
        fun build(): OneTimeWorkRequest = OneTimeWorkRequest(worker, delay, backoff)
    }
}

class PeriodicWorkRequest internal constructor(worker: KClass<out ListenableWorker>, period: Long, delay: Long, backoff: Long) :
    WorkRequest(worker, delay, period, backoff) {
    class Builder(private val worker: KClass<out ListenableWorker>, private val period: Long) {
        private var delay = 0L
        private var backoff = 30_000L
        fun setInitialDelay(duration: Long, unit: TimeUnit): Builder = apply { delay = unit.toMillis(duration) }
        fun setConstraints(constraints: Constraints): Builder = this
        fun setBackoffCriteria(policy: BackoffPolicy, duration: Long, unit: TimeUnit): Builder = apply { backoff = unit.toMillis(duration) }
        fun addTag(tag: String): Builder = this
        fun build(): PeriodicWorkRequest = PeriodicWorkRequest(worker, period, delay, backoff)
    }
}

inline fun <reified W : ListenableWorker> OneTimeWorkRequestBuilder(): OneTimeWorkRequest.Builder = OneTimeWorkRequest.Builder(W::class)

inline fun <reified W : ListenableWorker> PeriodicWorkRequestBuilder(repeatInterval: Long, unit: TimeUnit): PeriodicWorkRequest.Builder =
    PeriodicWorkRequest.Builder(W::class, unit.toMillis(repeatInterval))

inline fun <reified W : ListenableWorker> PeriodicWorkRequestBuilder(repeatInterval: java.time.Duration): PeriodicWorkRequest.Builder =
    PeriodicWorkRequest.Builder(W::class, repeatInterval.toMillis())

class Operation internal constructor()

/** Registered by the generated `WebGraph`: how to build each worker the phone declares. */
object WebWorkers {
    private val factories = HashMap<KClass<*>, (Context, WorkerParameters) -> ListenableWorker>()
    fun register(worker: KClass<out ListenableWorker>, factory: (Context, WorkerParameters) -> ListenableWorker) { factories[worker] = factory }
    internal fun build(worker: KClass<*>, context: Context, attempt: Int): ListenableWorker? =
        factories[worker]?.invoke(context, WorkerParameters(java.util.UUID.randomUUID(), attempt))
}

class WorkManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val unique = HashMap<String, Job>()

    fun enqueue(request: WorkRequest): Operation { start(request); return Operation() }

    fun enqueueUniqueWork(name: String, policy: ExistingWorkPolicy, request: OneTimeWorkRequest): Operation {
        if (policy == ExistingWorkPolicy.KEEP && unique[name]?.isActive == true) return Operation()
        unique.remove(name)?.cancel()
        unique[name] = start(request)
        return Operation()
    }

    fun enqueueUniquePeriodicWork(name: String, policy: ExistingPeriodicWorkPolicy, request: PeriodicWorkRequest): Operation {
        if (policy == ExistingPeriodicWorkPolicy.KEEP && unique[name]?.isActive == true) return Operation()
        unique.remove(name)?.cancel()
        unique[name] = start(request)
        return Operation()
    }

    fun cancelUniqueWork(name: String): Operation { unique.remove(name)?.cancel(); return Operation() }
    fun cancelAllWorkByTag(tag: String): Operation = Operation()
    fun cancelAllWork(): Operation { unique.values.forEach { it.cancel() }; unique.clear(); return Operation() }

    private fun start(request: WorkRequest): Job = scope.launch {
        delay(request.initialDelayMillis)
        var attempt = 0
        while (true) {
            val worker = WebWorkers.build(request.worker, context, attempt)
            val result = (worker as? CoroutineWorker)?.let { runCatching { it.doWork() }.getOrElse { ListenableWorker.Result.retry() } }
                ?: ListenableWorker.Result.failure()
            when {
                result is ListenableWorker.Result.Retry -> { attempt++; delay(request.backoffMillis * attempt) }
                request.periodMillis > 0 -> { attempt = 0; delay(request.periodMillis) }
                else -> return@launch
            }
        }
    }

    companion object {
        private var instance: WorkManager? = null
        fun getInstance(context: Context): WorkManager = instance ?: WorkManager(context).also { instance = it }
    }
}

class Configuration private constructor() {
    class Builder {
        fun setWorkerFactory(factory: Any?): Builder = this
        fun setMinimumLoggingLevel(level: Int): Builder = this
        fun build(): Configuration = Configuration()
    }
    interface Provider { val workManagerConfiguration: Configuration }
}
