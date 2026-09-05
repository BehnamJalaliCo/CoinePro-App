package com.coinepro.core.marketintel

import com.coinepro.core.common.BrandConfig
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * One HTTPS GET that returns text, or null.
 *
 * An interface with one method, because everything interesting in this corner of the module is the
 * *parsing* and the parsing has to be testable against a real captured body rather than against a
 * live feed that changes hourly. The implementation below is four lines; the fixtures under
 * `src/test/resources` are the part worth keeping.
 *
 * Null rather than an exception for a feed that would not answer. These are secondary sources
 * consulted only after the primary route sent nothing (see [PublicMarketIntel]), and a public
 * newswire being unreachable is not an error the reader did anything about or can do anything
 * about — it is one of several feeds, and the others still have stories in them.
 */
fun interface PublicFeedClient {
    suspend fun get(url: String): String?
}

/**
 * The real one.
 *
 * Deliberately built on the app's ordinary [OkHttpClient] **without** the auth interceptor: these
 * hosts are third parties and must never be sent a bearer token for either backend. Passing the
 * wrong client here would leak a session token to a newswire, so the injector builds this from the
 * plain client and the KDoc on that binding says so.
 *
 * ### Why this is `enqueue` and not `execute`, and why that was the whole news screen
 *
 * It used to be `client.newCall(request).execute()` inside a `suspend fun`, which is a blocking
 * socket read on whatever thread the caller happens to be on. The caller is
 * [MarketIntelController], and its scope is the app's shared one — `Dispatchers.Main.immediate`.
 * On a device that is a socket read **on the main thread**, and Android refuses it with
 * `NetworkOnMainThreadException` before a byte is sent.
 *
 * That exception is a `RuntimeException`, so the `catch (IOException)` below never saw it. It
 * escaped through [PublicMarketIntel.calendar] and [PublicMarketIntel.news], through the gateway's
 * fallback chain, and into the controller's `runCatching`, whose `serverTextOrNull` turns a
 * non-HTTP failure into a null error. Net effect on the glass: loading off, error off, no calendar,
 * no headlines — «تقویم هنوز خراب بود» and «اخبار هنوز خراب بود», and nothing in any screen to say
 * why. Every unit test passed, because Robolectric does not enforce the main-thread policy and the
 * fixtures never open a socket.
 *
 * `enqueue` puts the read on OkHttp's own dispatcher and suspends here until it lands, on any
 * thread at all, and cancelling the coroutine cancels the call rather than leaving a socket open
 * behind a screen the reader has left.
 */
class OkHttpPublicFeedClient(private val client: OkHttpClient) : PublicFeedClient {

    override suspend fun get(url: String): String? {
        require(url.startsWith("https://")) { "Public feeds are read over HTTPS only" }
        val request = Request.Builder()
            .url(url)
            // Several of these hosts answer a bare programmatic fetch with a challenge page and a
            // 403. A plain, honest user agent naming the app is what gets an ordinary 200, and it
            // is also the courteous thing: an operator reading their logs can see who is asking.
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, application/rss+xml, application/xml, text/xml")
            .build()
        return client.newCall(request).awaitText()
    }

    private suspend fun Call.awaitText(): String? = suspendCancellableCoroutine { continuation ->
        enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    // The body is read here, on OkHttp's thread, rather than handed across: a
                    // `Response` resumed into a main-thread coroutine and read there is the same
                    // blocking read this class exists to avoid.
                    val text = runCatching {
                        response.use { if (it.isSuccessful) it.body?.string() else null }
                    }.getOrNull()
                    if (continuation.isActive) continuation.resume(text)
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(null)
                }
            },
        )
        continuation.invokeOnCancellation { cancel() }
    }

    private companion object {
        const val USER_AGENT = "ProChart-Android/1.0 (+${BrandConfig.LEGAL_BASE_URL}/)"
    }
}
