@file:Suppress("unused", "UNUSED_PARAMETER")

package com.coinepro.web.jvm

import kotlin.coroutines.startCoroutine
import kotlinx.coroutines.launch

/*
 * `java.lang` as the shared code meets it in a page: a thread is the page's one thread, the runtime
 * reports what the browser will say about memory, and an uncaught-exception handler is called by
 * whoever catches the exception — the page's error hook (web/src/wasmJsMain/.../Main.kt).
 */

fun interface WebRunnable { fun run() }

class WebThread(private val runnable: WebRunnable? = null, var name: String = "main") {
    constructor(runnable: WebRunnable?) : this(runnable, "main")
    constructor(name: String) : this(null, name)
    var isDaemon: Boolean = false
    var priority: Int = 5
    val id: Long get() = 1L
    val isInterrupted: Boolean get() = false
    val isAlive: Boolean get() = false
    fun start() { runnable?.run() }
    fun run() { runnable?.run() }
    fun interrupt() {}
    fun join(millis: Long = 0) {}
    fun setUncaughtExceptionHandler(handler: UncaughtExceptionHandler?) {}
    fun getName(): String = name

    fun interface UncaughtExceptionHandler { fun uncaughtException(thread: WebThread, error: Throwable) }

    companion object {
        private val main = WebThread(null, "main")
        private var handler: UncaughtExceptionHandler? = null
        fun currentThread(): WebThread = main
        fun sleep(millis: Long) {}
        fun getDefaultUncaughtExceptionHandler(): UncaughtExceptionHandler? = handler
        fun setDefaultUncaughtExceptionHandler(h: UncaughtExceptionHandler?) { handler = h }
        /** The page's error hook calls this with anything nothing else caught. */
        fun report(error: Throwable) { handler?.uncaughtException(main, error) }
        fun interrupted(): Boolean = false
    }
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun heapLimitJs(): Double = js("(performance && performance.memory) ? performance.memory.jsHeapSizeLimit : 0")
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun heapTotalJs(): Double = js("(performance && performance.memory) ? performance.memory.totalJSHeapSize : 0")
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun heapUsedJs(): Double = js("(performance && performance.memory) ? performance.memory.usedJSHeapSize : 0")
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun coresJs(): Int = js("(navigator && navigator.hardwareConcurrency) || 1")

class WebRuntime private constructor() {
    fun maxMemory(): Long = heapLimitJs().toLong()
    fun totalMemory(): Long = heapTotalJs().toLong()
    fun freeMemory(): Long = (heapTotalJs() - heapUsedJs()).toLong()
    fun availableProcessors(): Int = coresJs()
    fun gc() {}
    fun addShutdownHook(thread: WebThread) {}
    companion object {
        private val instance = WebRuntime()
        fun getRuntime(): WebRuntime = instance
    }
}

/** `Throwable.printStackTrace(PrintWriter)`: the message and the chain of causes. */
fun printStackTraceTo(error: Throwable, out: java.io.Writer) {
    var current: Throwable? = error
    var first = true
    while (current != null) {
        out.write((if (first) "" else "Caused by: ") + (current::class.simpleName ?: "Throwable") + ": " + (current.message ?: "") + "\n")
        out.write(current.stackTraceToString().lines().drop(1).joinToString("\n") + "\n")
        first = false
        current = current.cause
    }
}

/**
 * `LinkedHashMap(capacity, loadFactor, accessOrder = true)`: iteration runs least recently used
 * first, and a read counts as a use — kept by moving an entry to the end whenever it is read.
 */
class AccessOrderedMap<K, V> : MutableMap<K, V> {
    private val inner = LinkedHashMap<K, V>()
    override val size: Int get() = inner.size
    override fun isEmpty(): Boolean = inner.isEmpty()
    override fun containsKey(key: K): Boolean = inner.containsKey(key)
    override fun containsValue(value: V): Boolean = inner.containsValue(value)
    override fun get(key: K): V? {
        if (!inner.containsKey(key)) return null
        @Suppress("UNCHECKED_CAST") val value = inner.remove(key) as V
        inner[key] = value
        return value
    }
    override fun put(key: K, value: V): V? { val old = inner.remove(key); inner[key] = value; return old }
    override fun remove(key: K): V? = inner.remove(key)
    override fun putAll(from: Map<out K, V>) = from.forEach { (k, v) -> put(k, v) }
    override fun clear() = inner.clear()
    override val keys: MutableSet<K> get() = inner.keys
    override val values: MutableCollection<V> get() = inner.values
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = inner.entries
}

private val detached = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

/** Starts work nobody waits for — where the phone would have blocked a thread on it. */
fun launchDetached(block: suspend () -> Unit) {
    detached.launch { runCatching { block() } }
}

/**
 * `runBlocking`, as far as a page can go: the block runs now, on this call, and its answer is
 * returned if it finishes without waiting — a read of a store already in memory, a value already
 * computed. A block that would genuinely wait cannot be waited for on the page's one thread, and
 * says so rather than hanging the page.
 */
fun <T> runBlocking(
    context: kotlin.coroutines.CoroutineContext = kotlin.coroutines.EmptyCoroutineContext,
    block: suspend kotlinx.coroutines.CoroutineScope.() -> T,
): T {
    var outcome: Result<T>? = null
    val scope = kotlinx.coroutines.CoroutineScope(context + kotlinx.coroutines.Dispatchers.Unconfined)
    block.startCoroutine(scope, kotlin.coroutines.Continuation(kotlin.coroutines.EmptyCoroutineContext) { outcome = it })
    return outcome?.getOrThrow() ?: throw IllegalStateException("A browser cannot block its one thread for this call")
}
