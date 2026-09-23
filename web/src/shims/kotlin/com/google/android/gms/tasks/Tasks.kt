@file:Suppress("unused", "UNUSED_PARAMETER")

package com.google.android.gms.tasks

/** A Play-services task that has already finished — with a value, or with the reason it has none. */
class Task<T> internal constructor(private val value: T?, private val error: Exception?) {
    val isSuccessful: Boolean get() = error == null
    val isComplete: Boolean get() = true
    val result: T? get() = value
    val exception: Exception? get() = error
    fun addOnSuccessListener(listener: (T) -> Unit): Task<T> = apply { if (error == null) @Suppress("UNCHECKED_CAST") listener(value as T) }
    fun addOnFailureListener(listener: (Exception) -> Unit): Task<T> = apply { error?.let(listener) }
    fun addOnCompleteListener(listener: (Task<T>) -> Unit): Task<T> = apply { listener(this) }
}

object Tasks {
    fun <T> forResult(value: T): Task<T> = Task(value, null)
    fun <T> forException(e: Exception): Task<T> = Task(null, e)
    fun <T> await(task: Task<T>): T = task.result ?: throw (task.exception ?: IllegalStateException())
    fun <T> await(task: Task<T>, timeout: Long, unit: java.util.concurrent.TimeUnit): T = await(task)
}
