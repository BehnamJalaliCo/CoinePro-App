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
        enableHttpLogging: Boolean = false,
    ): OkHttpClient {
        val auth = Interceptor { chain ->
            val token = bearerToken()?.takeIf { it.isNotBlank() }
            val builder = chain.request().newBuilder()
            if (token != null) builder.header("Authorization", "Bearer $token")
            // Hyphens, not underscores: nginx drops headers containing underscores by default, and
            // the failure would be invisible from here — the request succeeds, the limiter just
            // never sees the value and falls back to bucketing everyone by IP again.
            installId()?.takeIf { it.isNotBlank() }?.let { builder.header("X-Install-Id", it) }
            val request = builder.build()
            val response = chain.proceed(request)
            if (token != null && response.code == 401) onUnauthorized()
            response
        }

        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
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
