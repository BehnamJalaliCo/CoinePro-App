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
            throw error
        }
    }
}
