package com.coinepro.core.webhook

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Posts one fired alert to one webhook, and says exactly what happened — [142].
 *
 * ### The three seconds
 *
 * TradingView's own limit, kept because the reasoning is right and is not about their servers: a
 * webhook is a side effect of an alert, and an alert that waits on a slow receiver is an alert the
 * reader is not being shown. Three seconds is long enough for any receiver that is working and
 * short enough that a receiver that is not cannot hold the alert pipeline open behind it. A
 * receiver that needs longer needs a queue of its own, and every serious one has one.
 *
 * The timeout covers connect, write and read separately, which is what makes it a real bound. A
 * single overall timeout is not available in OkHttp's builder, and three three-second stages is a
 * worst case of nine — so [deliver] is called per target with nothing held behind it.
 *
 * ### No redirects
 *
 * A redirect would move a signed, secret-bearing POST to a host the reader never approved and that
 * the URL rules never checked. `followRedirects` is off for that reason and not for tidiness: it is
 * the one setting on this client that is a security decision.
 *
 * ### What is sent
 *
 * The body, exactly as [WebhookEvent] composed it, with the content type [WebhookBody] chose, the
 * signature [WebhookSignature] computed, and a user agent that names this app so a receiver's own
 * log says who called. Nothing else — no bearer token, no install id, no device information. This
 * client is deliberately not the app's shared one: that one attaches an `Authorization` header, and
 * sending the reader's own session token to a third-party URL they pasted would be indefensible.
 */
class WebhookPoster(
    /**
     * Built here rather than injected, for the reason in the class note: the app's shared client
     * carries credentials this one must never send. Overridable so a test can hand in a client
     * pointed at a local server.
     */
    private val client: OkHttpClient = defaultClient(),
    /** Network work belongs off the caller's thread; the alert engine's thread is not for waiting. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Injected so a delivery record can be stamped with a clock a test controls. */
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * Delivers [event] to [target] and returns the record of what happened.
     *
     * Never throws. A webhook failing is an ordinary outcome — a receiver goes down, a phone loses
     * signal, a URL was pasted with a character missing — and the whole point of this module is that
     * such a failure becomes a line a reader can look at rather than an exception that unwinds the
     * alert that caused it. Every path returns a [WebhookAttempt], including the one where the URL
     * was refused before anything was sent.
     */
    suspend fun deliver(target: WebhookTarget, event: WebhookEvent): WebhookAttempt {
        val refusal = WebhookUrl.validate(target.url)
        if (refusal != null) {
            return attempt(target, event, WebhookOutcome.BLOCKED, error = refusal.reason)
        }
        val body = event.body
        val startedAt = now()
        val request = Request.Builder()
            .url(target.url)
            .post(body.toRequestBody(event.contentType.toMediaType()))
            .header("User-Agent", WebhookSignature.USER_AGENT)
            .header(WebhookSignature.TIMESTAMP_HEADER, startedAt.toString())
            .apply {
                // Absent rather than empty when the target has no secret. A signature header
                // computed under no key is not a weaker proof, it is a false one.
                WebhookSignature.of(body, target.secret)?.let {
                    header(WebhookSignature.HEADER, it)
                }
            }
            .build()

        return withContext(dispatcher) {
            try {
                client.newCall(request).execute().use { response ->
                    val latency = now() - startedAt
                    attempt(
                        target = target,
                        event = event,
                        outcome = if (response.isSuccessful) {
                            WebhookOutcome.DELIVERED
                        } else {
                            WebhookOutcome.REJECTED
                        },
                        status = response.code,
                        latency = latency,
                        // The receiver's own message is not repeated. It arrives in whatever
                        // language and length that server chose, and a Persian log is not the place
                        // for a wall of somebody else's HTML. The status code is the fact.
                        error = if (response.isSuccessful) null else REJECTED_REASON,
                    )
                }
            } catch (timeout: SocketTimeoutException) {
                attempt(
                    target = target,
                    event = event,
                    outcome = WebhookOutcome.TIMED_OUT,
                    latency = now() - startedAt,
                    error = TIMEOUT_REASON,
                )
            } catch (failure: IOException) {
                // Everything the network can do: no route, DNS failure, a TLS handshake refused, a
                // connection reset. One outcome, because the reader's next step is the same for all
                // of them, and the app's own words rather than the exception's — a Persian screen
                // must never print `Unable to resolve host`.
                attempt(
                    target = target,
                    event = event,
                    outcome = WebhookOutcome.UNREACHABLE,
                    latency = now() - startedAt,
                    error = UNREACHABLE_REASON,
                )
            }
        }
    }

    private fun attempt(
        target: WebhookTarget,
        event: WebhookEvent,
        outcome: WebhookOutcome,
        status: Int? = null,
        latency: Long = 0,
        error: String? = null,
    ) = WebhookAttempt(
        targetId = target.id,
        targetName = target.name,
        alertId = event.alertId,
        at = now(),
        outcome = outcome,
        status = status,
        latencyMillis = latency,
        error = error,
    )

    companion object {
        /** Per stage: connect, write, read. TradingView's own bound; see the class note. */
        const val TIMEOUT_SECONDS = 3L

        private const val REJECTED_REASON = "گیرنده درخواست را نپذیرفت"
        private const val TIMEOUT_REASON = "گیرنده در سه ثانیه پاسخ نداد"
        private const val UNREACHABLE_REASON = "اتصال به گیرنده برقرار نشد"

        /**
         * The client every webhook is posted with unless a caller supplies one.
         *
         * No interceptors, no logging, no credentials, no redirects, no cookies. It is deliberately
         * the smallest client in the app.
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
    }
}
