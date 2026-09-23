package com.google.common.util.concurrent

/** A value that is ready now or later; the listener runs once it is. */
class ListenableFuture<V> internal constructor() {
    private var value: Result<V>? = null
    private val listeners = ArrayList<Pair<Runnable, java.util.concurrent.Executor>>()
    fun addListener(listener: Runnable, executor: java.util.concurrent.Executor) {
        if (value != null) executor.execute(listener) else listeners += listener to executor
    }
    fun get(): V = value?.getOrThrow() ?: throw IllegalStateException("Not complete")
    fun isDone(): Boolean = value != null
    internal fun complete(result: Result<V>) {
        value = result
        listeners.forEach { (l, e) -> e.execute(l) }
        listeners.clear()
    }
    companion object {
        fun <V> of(value: V): ListenableFuture<V> = ListenableFuture<V>().also { it.complete(Result.success(value)) }
    }
}

typealias Runnable = java.util.concurrent.Runnable
