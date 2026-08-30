package com.coinepro.core.diagnostics

import com.coinepro.core.model.MarketPlatform
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Records every call one platform's client makes.
 *
 * Installed per platform rather than once globally, because the panel has to be able to say *which*
 * backend answered — the whole product rule is that the two never mix, and a merged log would be
 * the one place they did.
 *
 * A failure is recorded and rethrown. Swallowing it here would make the log the reason a request
 * appeared to succeed.
 */
class RequestLogInterceptor(
    private val log: RequestLog,
    private val platform: MarketPlatform,
    private val clock: () -> Long = System::currentTimeMillis,
    /**
     * The narrative log, where the request log is the table.
     *
     * Null in tests and wherever the app has not built one yet, so this interceptor stays usable on
     * its own. The two are not redundant: [RequestLog] is what the diagnostics screen tabulates,
     * and [AppLog] is what puts a failed call *in sequence* with the sign-in that preceded it and
     * the sign-out that followed — which is the difference between "this call 401ed" and knowing
     * why.
     */
    private val appLog: AppLog? = null,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val started = clock()
        val sequence = log.nextSequence()

        return try {
            chain.proceed(request).also { response ->
                log.record(
                    RecordedRequest(
                        sequence = sequence,
                        platform = platform,
                        method = request.method,
                        path = request.url.encodedPath,
                        status = response.code,
                        durationMillis = clock() - started,
                        elapsedRealtimeMillis = started,
                    ),
                )
                val millis = clock() - started
                appLog?.log(
                    // Three levels rather than two, and the middle one is the point. A 401 or a 403
                    // is the *correct* answer to an authenticated route while signed out, and
                    // recording it as a warning is what filled the panel's error count on every
                    // install nobody had signed into — which is what made the whole screen read as
                    // broken on open. A 5xx is an error because the server actually broke.
                    level = when {
                        response.code in 200..399 -> LogLevel.DEBUG
                        response.code == 401 || response.code == 403 -> LogLevel.DEBUG
                        response.code >= 500 -> LogLevel.ERROR
                        else -> LogLevel.WARN
                    },
                    tag = LogTag.NETWORK,
                    message = request.method + " " + request.url.encodedPath,
                    fields = mapOf(
                        "platform" to platform.name,
                        "status" to response.code.toString(),
                        "ms" to millis.toString(),
                    ),
                )
            }
        } catch (error: IOException) {
            log.record(
                RecordedRequest(
                    sequence = sequence,
                    platform = platform,
                    method = request.method,
                    path = request.url.encodedPath,
                    status = null,
                    durationMillis = clock() - started,
                    elapsedRealtimeMillis = started,
                    // The class name, not the message. A message can carry the full URL including a
                    // query, and this string is rendered on screen.
                    failure = error::class.simpleName ?: "IOException",
                ),
            )
            appLog?.error(
                tag = LogTag.NETWORK,
                message = request.method + " " + request.url.encodedPath + " never completed",
                error = error,
                fields = mapOf(
                    "platform" to platform.name,
                    "ms" to (clock() - started).toString(),
                ),
            )
            throw error
        }
    }
}
