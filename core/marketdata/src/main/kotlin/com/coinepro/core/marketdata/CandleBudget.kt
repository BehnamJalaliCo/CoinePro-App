package com.coinepro.core.marketdata

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * How much a candle request matters to the reader at the moment it is sent (5.33.0).
 *
 * The chart a reader just opened is [READER]. The live edge's reconciliation and a watchlist row's
 * line are [BACKGROUND]: worth having, not worth a refusal. The history walk behind a chart and the
 * heat map's and screener's sweeps are [BULK]: nobody is looking at them yet.
 */
enum class CandleUrgency { READER, BACKGROUND, BULK }

/** Carries a [CandleUrgency] down to the gateway without a parameter on every `load`. */
class CandleUrgencyElement(val urgency: CandleUrgency) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CandleUrgencyElement>
}

/** Runs [block] with every candle request in it sent at [urgency]. */
suspend fun <T> withCandleUrgency(urgency: CandleUrgency, block: suspend () -> T): T =
    withContext(CandleUrgencyElement(urgency)) { block() }

internal suspend fun currentCandleUrgency(): CandleUrgency =
    coroutineContext[CandleUrgencyElement]?.urgency ?: CandleUrgency.READER

/**
 * The venue said «too many requests», or this app knows it is about to.
 *
 * Carries the token `rate_limited` for the same reason [CandleIntervalUnavailableException] carries
 * its own: the chart reads exception text, and a token survives being wrapped.
 */
class CandleRateLimitedException(val retryAfterSeconds: Long) :
    Exception("rate_limited: retry in ${retryAfterSeconds}s")

/**
 * CoinePro-FX's chart allowance, spent in order of what the reader is looking at (5.33.0).
 *
 * ### The refusal this answers
 *
 * A guest — every reader of pro-chart.com, and every phone with no forex account — reads candles on
 * CoinePro-FX's guest scope, and that scope allows **120 chart requests per address per ten
 * minutes**, then locks the address out for a minute, two, four… The site spent that in under a
 * minute: the history walk behind a freshly opened chart is forty pages, a watchlist is a request a
 * row, the live edge is two a minute, and every refusal used to be asked again one feed finer
 * (H1, then M15 × 1200, then M5 × 3000) and once more by the chart's own retry. The server report
 * counted eighty requests in the minute a forex chart opened and twenty-seven 429s, and a reader who
 * refreshed got a chart with holes in it.
 *
 * ### What it does
 *
 * - Counts every request sent on a [metered] credential in a sliding [windowMillis], which bounds the
 *   server's fixed window too.
 * - Lets each [CandleUrgency] use a share of it: [BULK] stops at [bulkShare], [BACKGROUND] at
 *   [backgroundShare], and the rest is kept for the chart the reader opens. Background and bulk
 *   callers **wait** for room rather than fail — a watchlist line asked for once must not be lost —
 *   and a [READER] request past [readerShare] is refused here, without being sent, because sending
 *   it would earn the lock-out and the next minute of refusals with it.
 * - After a 429 ([penalise]) nothing is sent until the venue's own «try again in N seconds» has
 *   passed: a reader request is refused at once, everything else waits it out.
 */
class CandleRequestBudget(
    private val windowMillis: Long = WINDOW_MILLIS,
    private val readerShare: Int = READER_SHARE,
    private val backgroundShare: Int = BACKGROUND_SHARE,
    private val bulkShare: Int = BULK_SHARE,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private val lock = Mutex()
    private val sent = ArrayDeque<Long>()
    private var blockedUntil = 0L

    /** Requests counted in the current window, for a test. */
    internal suspend fun spent(): Int = lock.withLock { trim(nowMillis()); sent.size }

    /**
     * Returns when a request at [urgency] may go. [metered] is false for a credential the guest
     * allowance does not apply to; such a request still respects a lock-out the venue announced.
     */
    suspend fun admit(urgency: CandleUrgency, metered: Boolean) {
        while (true) {
            val wait = lock.withLock {
                val now = nowMillis()
                if (now < blockedUntil) {
                    if (urgency == CandleUrgency.READER) throw CandleRateLimitedException(secondsUntil(blockedUntil, now))
                    blockedUntil - now
                } else if (!metered) {
                    return
                } else {
                    trim(now)
                    val share = when (urgency) {
                        CandleUrgency.READER -> readerShare
                        CandleUrgency.BACKGROUND -> backgroundShare
                        CandleUrgency.BULK -> bulkShare
                    }
                    if (sent.size < share) {
                        sent.addLast(now)
                        return
                    }
                    // Room opens when enough of the oldest have left the window.
                    val opensAt = sent[sent.size - share] + windowMillis
                    if (urgency == CandleUrgency.READER) throw CandleRateLimitedException(secondsUntil(opensAt, now))
                    opensAt - now
                }
            }
            pause(wait.coerceAtLeast(MIN_WAIT_MILLIS))
        }
    }

    /** The venue answered 429: send nothing for [seconds]. */
    suspend fun penalise(seconds: Long) {
        lock.withLock {
            val until = nowMillis() + seconds.coerceIn(1, MAX_PENALTY_SECONDS) * 1_000
            if (until > blockedUntil) blockedUntil = until
        }
    }

    private fun trim(now: Long) {
        while (sent.isNotEmpty() && sent.first() <= now - windowMillis) sent.removeFirst()
    }

    private fun secondsUntil(at: Long, now: Long): Long = ((at - now + 999) / 1_000).coerceAtLeast(1)

    companion object {
        /** CoinePro-FX's guest window: `guest:chart`, ten minutes. */
        const val WINDOW_MILLIS = 600_000L

        /** Of its 120, a few short so a second tab or a clock skew does not tip it over. */
        const val READER_SHARE = 110
        const val BACKGROUND_SHARE = 70
        const val BULK_SHARE = 20

        private const val MIN_WAIT_MILLIS = 250L

        /** The venue's own lock-out tops out at an hour. */
        private const val MAX_PENALTY_SECONDS = 3_600L

        /**
         * How long a 429 says to wait: `Retry-After` where it is sent, else the first number in the
         * body — the guest scope answers «درخواستِ زیاد. 412 ثانیه‌ی دیگر …» with no header — else a
         * minute.
         */
        fun retryAfterSeconds(header: String?, body: String?): Long {
            header?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.let { return it }
            body?.let { text -> Regex("""\d+""").find(text)?.value?.toLongOrNull()?.takeIf { it > 0 }?.let { return it } }
            return DEFAULT_PENALTY_SECONDS
        }

        private const val DEFAULT_PENALTY_SECONDS = 60L
    }
}
