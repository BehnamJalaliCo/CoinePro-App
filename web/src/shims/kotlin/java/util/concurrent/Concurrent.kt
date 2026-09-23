@file:Suppress("unused")

package java.util.concurrent

import kotlinx.coroutines.launch

enum class TimeUnit(private val nanos: Long) {
    NANOSECONDS(1), MICROSECONDS(1_000), MILLISECONDS(1_000_000), SECONDS(1_000_000_000),
    MINUTES(60_000_000_000), HOURS(3_600_000_000_000), DAYS(86_400_000_000_000);

    fun toNanos(d: Long): Long = d * nanos
    fun toMicros(d: Long): Long = d * nanos / 1_000
    fun toMillis(d: Long): Long = d * nanos / 1_000_000
    fun toSeconds(d: Long): Long = d * nanos / 1_000_000_000
    fun toMinutes(d: Long): Long = d * nanos / 60_000_000_000
    fun toHours(d: Long): Long = d * nanos / 3_600_000_000_000
    fun toDays(d: Long): Long = d * nanos / 86_400_000_000_000
    fun convert(d: Long, unit: TimeUnit): Long = unit.toNanos(d) / nanos
}

fun interface Executor { fun execute(command: Runnable) }

typealias Runnable = com.coinepro.web.jvm.WebRunnable

fun interface ThreadFactory { fun newThread(r: Runnable): com.coinepro.web.jvm.WebThread }

interface ExecutorService : Executor {
    fun shutdown() {}
    fun shutdownNow(): List<Runnable> = emptyList()
    val isShutdown: Boolean get() = false
}

/** The page's one thread: work handed to an executor runs on the next turn of the event loop. */
object Executors {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    private val queued = object : ExecutorService {
        override fun execute(command: Runnable) {
            scope.launch { command.run() }
        }
    }
    fun newSingleThreadExecutor(): ExecutorService = queued
    fun newSingleThreadExecutor(factory: ThreadFactory): ExecutorService = queued
    fun newCachedThreadPool(): ExecutorService = queued
    fun newFixedThreadPool(n: Int): ExecutorService = queued
    fun newSingleThreadScheduledExecutor(): ExecutorService = queued
}


class ConcurrentHashMap<K, V> : MutableMap<K, V> by LinkedHashMap() {
    fun putIfAbsent(key: K, value: V): V? = get(key) ?: run { put(key, value); null }
}

class CopyOnWriteArrayList<E> : MutableList<E> by ArrayList()

class CancellationException(message: String? = null) : IllegalStateException(message)
class TimeoutException(message: String? = null) : Exception(message)
class ExecutionException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
