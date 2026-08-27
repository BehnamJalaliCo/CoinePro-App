package com.coinepro.core.network

import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkFactorySecurityTest {
    @Test
    fun `HTTP logging is absent by default`() {
        val client = NetworkFactory.okHttpClient()

        assertFalse(client.interceptors.any { it is HttpLoggingInterceptor })
    }

    @Test
    fun `debug HTTP logging is BASIC when explicitly enabled`() {
        val client = NetworkFactory.okHttpClient(enableHttpLogging = true)
        val loggers = client.interceptors.filterIsInstance<HttpLoggingInterceptor>()

        assertEquals(1, loggers.size)
        assertEquals(HttpLoggingInterceptor.Level.BASIC, loggers.single().level)
    }

    @Test
    fun `retrofit rejects cleartext base URLs`() {
        val client = NetworkFactory.okHttpClient()

        val failure = runCatching {
            NetworkFactory.retrofit("http://example.invalid/", client)
        }

        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("HTTPS"))
    }

    /**
     * The rate-limit identifier goes on **every** request, signed in or not.
     *
     * TradeYar keys its public rate limiter on this header when it is present and on the client IP
     * when it is not — and under Iranian carrier-grade NAT a per-IP bucket is shared by a whole
     * city, so one script can lock everybody out. The guest surface is exactly where that matters
     * and exactly where it would be easy to leave the header off, because there is no token to
     * attach it to.
     */
    @Test
    fun `the install id is sent with no bearer token present`() {
        val sent = capture(
            NetworkFactory.okHttpClient(
                bearerToken = { null },
                installId = { "install-abc" },
                appVersion = "1.20.1",
            ),
        )

        assertEquals("install-abc", sent.header("X-Install-Id"))
        assertEquals("android", sent.header("X-App-Platform"))
        assertEquals("1.20.1", sent.header("X-App-Version"))
        // No token, so no header at all — rather than `Bearer null`, which some servers accept and
        // then fail to parse in a way nobody can read from the client.
        assertEquals(null, sent.header("Authorization"))
    }

    @Test
    fun `an install id the app could not read is left off rather than invented`() {
        val sent = capture(NetworkFactory.okHttpClient(installId = { null }))

        assertEquals(null, sent.header("X-Install-Id"))
    }

    /** Runs the client's own interceptor chain over one request and returns what it produced. */
    private fun capture(client: okhttp3.OkHttpClient): okhttp3.Request {
        var seen: okhttp3.Request? = null
        val request = okhttp3.Request.Builder().url("https://example.invalid/probe").build()
        client.interceptors.fold<okhttp3.Interceptor, (okhttp3.Request) -> okhttp3.Response>(
            { built ->
                seen = built
                okhttp3.Response.Builder()
                    .request(built)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(okhttp3.ResponseBody.create(null, ""))
                    .build()
            },
        ) { next, interceptor ->
            { built -> interceptor.intercept(StubChain(built, next)) }
        }(request)
        return checkNotNull(seen)
    }
}

/** The smallest chain that lets one interceptor run without a server behind it. */
private class StubChain(
    private val request: okhttp3.Request,
    private val next: (okhttp3.Request) -> okhttp3.Response,
) : okhttp3.Interceptor.Chain {
    override fun request(): okhttp3.Request = request
    override fun proceed(request: okhttp3.Request): okhttp3.Response = next(request)
    override fun connection(): okhttp3.Connection? = null
    override fun call(): okhttp3.Call = throw UnsupportedOperationException()
    override fun connectTimeoutMillis(): Int = 0
    override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    override fun readTimeoutMillis(): Int = 0
    override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    override fun writeTimeoutMillis(): Int = 0
    override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
}