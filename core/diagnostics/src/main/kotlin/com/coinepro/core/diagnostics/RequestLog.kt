package com.coinepro.core.diagnostics

import com.coinepro.core.model.MarketPlatform
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One HTTP call the app made, as the admin panel shows it.
 *
 * Deliberately no bodies and no headers. A request log that captured them would hold bearer tokens,
 * passwords on their way to sign-in, and exchange API keys on their way to Connections — on screen,
 * in a panel five taps from any reader. The method, the path and the status are what diagnose a
 * problem; the payload is what leaks.
 */
data class RecordedRequest(
    val sequence: Long,
    val platform: MarketPlatform?,
    val method: String,
    /** Path only, with the query string dropped: a query can carry an email or a token. */
    val path: String,
    val status: Int?,
    val durationMillis: Long,
    val elapsedRealtimeMillis: Long,
    /** Set when the call never reached a status at all — a timeout, a DNS failure, no route. */
    val failure: String? = null,
) {
    val failed: Boolean get() = failure != null || (status ?: 0) !in 200..399
}

/**
 * The last few hundred calls, newest first.
 *
 * Bounded on purpose and kept only in memory: this is a live view for someone looking at a problem
 * now, not a record. Writing it to disk would turn a diagnostic aid into a file that survives the
 * process, needs a retention rule, and ends up in a backup.
 *
 * Every method is safe to call from OkHttp's dispatcher threads.
 */
class RequestLog(private val capacity: Int = DEFAULT_CAPACITY) {
    private val sequence = AtomicLong(0)
    private val entriesMutable = MutableStateFlow<List<RecordedRequest>>(emptyList())

    val entries: StateFlow<List<RecordedRequest>> = entriesMutable.asStateFlow()

    fun record(entry: RecordedRequest) {
        entriesMutable.update { current -> (listOf(entry) + current).take(capacity) }
    }

    fun nextSequence(): Long = sequence.incrementAndGet()

    fun clear() {
        entriesMutable.value = emptyList()
    }

    /** Calls that did not come back in the 2xx–3xx range, which is what a reader is looking for. */
    fun failures(): List<RecordedRequest> = entriesMutable.value.filter(RecordedRequest::failed)

    private companion object {
        const val DEFAULT_CAPACITY = 200
    }
}
