package com.coinepro.core.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object NetworkFactory {
    fun okHttpClient(
        enableBodyLogging: Boolean = false,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (enableBodyLogging) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
            redactHeader("Authorization")
            redactHeader("Cookie")
        }

        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit {
        require(baseUrl.startsWith("https://")) { "Only HTTPS API endpoints are allowed" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .build()
    }
}
