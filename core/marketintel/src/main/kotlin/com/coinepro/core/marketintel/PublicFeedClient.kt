package com.coinepro.core.marketintel

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

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
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (error: IOException) {
            null
        }
    }

    private companion object {
        const val USER_AGENT = "ProChart-Android/1.0 (+https://behnamjalalico.github.io/CoinePro-App/)"
    }
}
