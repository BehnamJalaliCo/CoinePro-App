package com.coinepro.core.network

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkFactory {

    /** The most a single HTTP call may take, end to end. See the builder for why it exists. */
    const val CALL_TIMEOUT_SECONDS = 30L

    /**
     * [installId] identifies this install to the server's rate limiter, which cannot use the client
     * IP for the purpose: carrier-grade NAT puts a very large number of Iranian mobile subscribers
     * behind one address, so a per-IP sign-in limit is shared by all of them at once. It is called
     * on a background thread per request and may return null, in which case the header is simply
     * left off — an identifier the app could not read is not one it should invent.
     */
    fun okHttpClient(
        bearerToken: () -> String? = { null },
        onUnauthorized: () -> Unit = {},
        installId: () -> String? = { null },
        appVersion: String? = null,
        /**
         * Installed ahead of the auth interceptor so it times the whole call, and so a request the
         * auth layer never got to still appears in the log rather than vanishing.
         */
        recorder: Interceptor? = null,
        enableHttpLogging: Boolean = false,
    ): OkHttpClient {
        val auth = Interceptor { chain ->
            // A call that set its own Authorization keeps it. CoinePro-FX's chart routes take an
            // academy-scoped token minted from the mobile one, and `header()` replaces rather than
            // appends — so without this the interceptor would quietly overwrite the very token the
            // call exists to send, and the route would answer 403 for no visible reason.
            val explicit = chain.request().header("Authorization") != null
            val token = bearerToken()?.takeIf { it.isNotBlank() && !explicit }
            val builder = chain.request().newBuilder()
            if (token != null) builder.header("Authorization", "Bearer $token")
            // Hyphens, not underscores: nginx drops headers containing underscores by default, and
            // the failure would be invisible from here — the request succeeds, the limiter just
            // never sees the value and falls back to bucketing everyone by IP again.
            installId()?.takeIf { it.isNotBlank() }?.let { builder.header("X-Install-Id", it) }
            // Recorded against the session server-side. Sent on every request rather than at
            // sign-in because a session outlives an update, and the version that matters when
            // diagnosing a report is the one that made the call.
            builder.header("X-App-Platform", "android")
            appVersion?.takeIf { it.isNotBlank() }?.let { builder.header("X-App-Version", it) }
            val request = builder.build()
            val response = chain.proceed(request)
            if (token != null && response.code == 401) onUnauthorized()
            response
        }

        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // **A ceiling on the whole call, and it is the one that was missing.**
            //
            // The three above bound single steps — one connect, one read, one write — and none
            // of them bounds a DNS lookup, a connect that is retried across several addresses, or
            // a body that keeps trickling a byte at a time. On the networks this app is used on
            // those are exactly what a filtered or throttled host does: the request neither
            // succeeds nor fails, it just sits. Every screen with a spinner on it — the chart on
            // a fresh timeframe, the calendar, the headlines — waited on precisely that, which is
            // what «گیر کرد» looked like from the glass.
            //
            // Thirty seconds is long enough for a slow mobile link to fetch a screenful of
            // candles and short enough that a reader is shown the retry rather than a spinner
            // they give up on. It does not touch the socket: OkHttp exits the call timeout the
            // moment a WebSocket handshake completes, so the live feed is not cut every half
            // minute.
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .apply { recorder?.let(::addInterceptor) }
            .addInterceptor(auth)

        if (enableHttpLogging) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")
                redactHeader("Cookie")
                redactHeader("Set-Cookie")
            }
            builder.addInterceptor(logging)
        }

        return builder.build()
    }

    fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit {
        require(baseUrl.startsWith("https://")) { "Only HTTPS API endpoints are allowed" }
        val normalizedBaseUrl = if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
}
